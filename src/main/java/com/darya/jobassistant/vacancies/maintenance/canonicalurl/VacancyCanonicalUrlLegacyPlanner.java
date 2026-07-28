package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.InvalidVacancyUrlException;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

/**
 * The single classification algorithm shared by {@code VacancyCanonicalUrlAuditService} (read-only
 * reporting) and {@code VacancyCanonicalUrlBackfillService} (DRY_RUN/APPLY), so the rules for what
 * counts as safe, invalid, or colliding exist in exactly one place. Package-private and not an
 * interface: there is exactly one implementation and exactly two in-package callers, each of which
 * already owns its own transaction - this class carries no transaction annotation of its own and
 * must always be called from inside an already-active transaction (see each caller for its own
 * contract).
 *
 * <h2>Memory trade-offs</h2>
 *
 * <ul>
 *   <li>Legacy rows themselves are streamed through in {@code batchSize}-sized pages ({@link
 *       LegacyVacancyUrlRow}, two columns only) - never all held in memory at once, and never
 *       loaded as full {@code Vacancy} entities.
 *   <li>Every currently-populated {@code canonical_url} ({@link PopulatedCanonicalUrlRow}, two
 *       columns) is loaded once, up front, and held for the whole run as a {@code Map<String,
 *       UUID>} - this does not shrink the way legacy-row paging does, because every legacy
 *       candidate must be checked against the complete identity space, not just the rows seen so
 *       far. For the vacancy-table scale this project runs at (a personal job-search assistant,
 *       not a shared multi-tenant board) this is a small, bounded set; a table with tens of
 *       millions of populated rows would need a different strategy (e.g. a per-candidate database
 *       existence check instead of an in-memory set), which is out of scope here.
 *   <li>Every legacy row's canonical <em>candidate</em> (not the row itself) is also held for the
 *       whole run, grouped by candidate value, so a collision spanning two different pages can
 *       still be detected - this is what makes a page boundary invisible to the final
 *       classification.
 * </ul>
 *
 * <h2>Classification algorithm</h2>
 *
 * <ol>
 *   <li>Load every currently-populated {@code canonical_url} into a {@code Map<String, UUID>}.
 *   <li>Scan {@code canonical_url IS NULL} rows in deterministic ({@code id}-ordered) pages. For
 *       each row, try to canonicalize {@code sourceUrl}; a failure is recorded immediately as
 *       invalid (a row's own validity does not depend on any other row, so there's nothing to wait
 *       for). A success is only <em>grouped</em> by its candidate value - not yet classified -
 *       because whether it is safe depends on rows that may not have been scanned yet.
 *   <li>Only after every page has been scanned, each candidate group is classified: more than one
 *       legacy row sharing a candidate is a {@link LegacyToLegacyCollisionGroup}; a lone candidate
 *       already present in the populated-identity map is a {@link LegacyToCurrentCollision}; a lone
 *       candidate absent from both is a {@link SafeCanonicalUrlAssignment}. Groups are classified in
 *       canonical-value sorted order ({@code TreeMap}), and each group's member ids are sorted too,
 *       so the result never depends on batch size or {@code HashMap} iteration order.
 * </ol>
 */
class VacancyCanonicalUrlLegacyPlanner {

    private final VacancyRepository vacancyRepository;

    VacancyCanonicalUrlLegacyPlanner(VacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    VacancyCanonicalUrlLegacyPlan plan(int batchSize) {
        Map<String, UUID> populatedCanonicalUrls = loadPopulatedCanonicalUrls();

        int totalLegacyRows = 0;
        int scannedBatchCount = 0;
        List<UUID> invalidSourceUrlVacancyIds = new ArrayList<>();
        Map<String, List<UUID>> legacyCandidatesByCanonicalValue = new HashMap<>();

        int page = 0;
        while (true) {
            List<LegacyVacancyUrlRow> batch = vacancyRepository.findLegacyCanonicalUrlRows(PageRequest.of(page, batchSize));
            if (batch.isEmpty()) {
                break;
            }
            scannedBatchCount++;
            for (LegacyVacancyUrlRow row : batch) {
                totalLegacyRows++;
                String candidate = tryCanonicalize(row.sourceUrl());
                if (candidate == null) {
                    invalidSourceUrlVacancyIds.add(row.vacancyId());
                } else {
                    legacyCandidatesByCanonicalValue
                            .computeIfAbsent(candidate, ignored -> new ArrayList<>())
                            .add(row.vacancyId());
                }
            }
            if (batch.size() < batchSize) {
                break;
            }
            page++;
        }

        List<SafeCanonicalUrlAssignment> safeAssignments = new ArrayList<>();
        List<LegacyToLegacyCollisionGroup> legacyToLegacyCollisionGroups = new ArrayList<>();
        List<LegacyToCurrentCollision> legacyToCurrentCollisions = new ArrayList<>();

        for (Map.Entry<String, List<UUID>> entry : new TreeMap<>(legacyCandidatesByCanonicalValue).entrySet()) {
            String canonicalValue = entry.getKey();
            List<UUID> vacancyIds = new ArrayList<>(entry.getValue());
            vacancyIds.sort(null);

            if (vacancyIds.size() > 1) {
                legacyToLegacyCollisionGroups.add(new LegacyToLegacyCollisionGroup(canonicalValue, vacancyIds));
                continue;
            }

            UUID onlyVacancyId = vacancyIds.get(0);
            UUID currentOwner = populatedCanonicalUrls.get(canonicalValue);
            if (currentOwner != null) {
                legacyToCurrentCollisions.add(new LegacyToCurrentCollision(onlyVacancyId, canonicalValue, currentOwner));
            } else {
                safeAssignments.add(new SafeCanonicalUrlAssignment(onlyVacancyId, canonicalValue));
            }
        }

        return new VacancyCanonicalUrlLegacyPlan(
                totalLegacyRows, scannedBatchCount, safeAssignments,
                invalidSourceUrlVacancyIds, legacyToLegacyCollisionGroups, legacyToCurrentCollisions);
    }

    private Map<String, UUID> loadPopulatedCanonicalUrls() {
        Map<String, UUID> populated = new HashMap<>();
        for (PopulatedCanonicalUrlRow row : vacancyRepository.findPopulatedCanonicalUrlRows()) {
            populated.put(row.canonicalUrl(), row.vacancyId());
        }
        return populated;
    }

    /**
     * Catches only the provider-neutral, input-validation failures {@code sourceUrl} data can
     * legitimately produce: {@link InvalidVacancyUrlException} from {@link
     * VacancyUrlCanonicalizer#canonicalize} itself, and the {@link IllegalArgumentException} {@link
     * URI#create} throws for a syntactically malformed string before canonicalization ever runs.
     * Anything else is a programming error, not a legacy-data problem, and is left to propagate.
     */
    private String tryCanonicalize(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        try {
            return VacancyUrlCanonicalizer.canonicalize(URI.create(sourceUrl)).value();
        } catch (InvalidVacancyUrlException | IllegalArgumentException e) {
            return null;
        }
    }
}
