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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Read-only classification of every legacy {@code Vacancy} (one with {@code canonical_url IS
 * NULL}) into {@code SAFE_TO_BACKFILL} or one of the {@link VacancyCanonicalUrlAuditIssueType}
 * categories, using {@link VacancyUrlCanonicalizer} as the sole canonicalization algorithm - the
 * exact same one {@code VacancyCreationService} uses for new rows, so a row classified safe here
 * would resolve to the identity a real backfill (Step 4B2) would actually write.
 *
 * <p><b>This class never writes.</b> It never calls {@code save}, never issues {@code UPDATE}/
 * {@code DELETE}, never mutates a loaded row, and never derives-and-persists a {@code
 * canonical_url} as a side effect of reading one. See {@code VacancyCanonicalUrlAuditRunner} for
 * the only production caller.
 *
 * <h2>Transaction: one read-only snapshot for the whole run</h2>
 *
 * {@link #audit()} runs in a single {@code readOnly = true}, {@code REPEATABLE_READ} transaction -
 * not one transaction per page and not one per row - so every page of legacy rows and the
 * "currently populated" lookup are all read from the same consistent database snapshot, and a
 * concurrent write elsewhere (an import Save, an ingestion insert) can neither introduce nor
 * remove a collision partway through a run. {@code REPEATABLE_READ} does not by itself replace
 * operational care: production execution should still prefer a maintenance window, or at least
 * pausing ingestion/import writers, since a long-running snapshot on a busy table can still cause
 * lock waits or bloat even though it cannot corrupt this audit's own result.
 *
 * <h2>Memory trade-offs</h2>
 *
 * <ul>
 *   <li>Legacy rows themselves are streamed through in {@code
 *       vacancy-canonical-url-audit.batch-size}-sized pages ({@link LegacyVacancyUrlRow}, two
 *       columns only) - never all held in memory at once, and never loaded as full {@code Vacancy}
 *       entities.
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
 *       classification (see below). Its size is proportional to the legacy backlog, which is
 *       expected to shrink over time as Step 4B2 backfills safe rows.
 *   <li>Reported {@link VacancyCanonicalUrlAuditIssue} instances are collected for every non-safe
 *       row during the run (proportional to problem-row count, not table size) and only trimmed to
 *       {@code vacancy-canonical-url-audit.max-reported-issues} when the final {@link
 *       VacancyCanonicalUrlAuditReport} is built - the true counts in that report are always exact
 *       regardless of this bound.
 * </ul>
 *
 * <h2>Classification algorithm</h2>
 *
 * <ol>
 *   <li>Load every currently-populated {@code canonical_url} into a {@code Map<String, UUID>}.
 *   <li>Scan {@code canonical_url IS NULL} rows in deterministic ({@code id}-ordered) pages. For
 *       each row, try to canonicalize {@code sourceUrl}; a failure is recorded immediately as
 *       {@code INVALID_SOURCE_URL} (a row's own validity does not depend on any other row, so
 *       there's nothing to wait for). A success is only <em>grouped</em> by its candidate value -
 *       not yet classified - because whether it is safe depends on rows that may not have been
 *       scanned yet.
 *   <li>Only after every page has been scanned, each candidate group is classified: more than one
 *       legacy row sharing a candidate is {@code LEGACY_TO_LEGACY_COLLISION} for every row in the
 *       group; a lone candidate already present in the populated-identity map is {@code
 *       LEGACY_TO_CURRENT_COLLISION}; a lone candidate absent from both is {@code
 *       SAFE_TO_BACKFILL}. Groups are classified in canonical-value sorted order ({@code TreeMap}),
 *       and each group's member ids are sorted too, so the result - and the order issues appear in
 *       - never depends on batch size or {@code HashMap} iteration order.
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class VacancyCanonicalUrlAuditService {

    private final VacancyRepository vacancyRepository;
    private final VacancyCanonicalUrlAuditProperties properties;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VacancyCanonicalUrlAuditReport audit() {
        Map<String, UUID> populatedCanonicalUrls = loadPopulatedCanonicalUrls();

        int totalLegacyRows = 0;
        int invalidSourceUrlRows = 0;
        int scannedBatchCount = 0;
        List<VacancyCanonicalUrlAuditIssue> allIssues = new ArrayList<>();
        Map<String, List<UUID>> legacyCandidatesByCanonicalValue = new HashMap<>();

        int page = 0;
        while (true) {
            List<LegacyVacancyUrlRow> batch =
                    vacancyRepository.findLegacyCanonicalUrlRows(PageRequest.of(page, properties.batchSize()));
            if (batch.isEmpty()) {
                break;
            }
            scannedBatchCount++;
            for (LegacyVacancyUrlRow row : batch) {
                totalLegacyRows++;
                String candidate = tryCanonicalize(row.sourceUrl());
                if (candidate == null) {
                    invalidSourceUrlRows++;
                    allIssues.add(new VacancyCanonicalUrlAuditIssue(
                            row.vacancyId(), VacancyCanonicalUrlAuditIssueType.INVALID_SOURCE_URL, null, List.of()));
                } else {
                    legacyCandidatesByCanonicalValue
                            .computeIfAbsent(candidate, ignored -> new ArrayList<>())
                            .add(row.vacancyId());
                }
            }
            if (batch.size() < properties.batchSize()) {
                break;
            }
            page++;
        }

        Classification classification = classify(legacyCandidatesByCanonicalValue, populatedCanonicalUrls, allIssues);

        return buildReport(totalLegacyRows, invalidSourceUrlRows, scannedBatchCount, classification, allIssues);
    }

    private Map<String, UUID> loadPopulatedCanonicalUrls() {
        Map<String, UUID> populated = new HashMap<>();
        for (PopulatedCanonicalUrlRow row : vacancyRepository.findPopulatedCanonicalUrlRows()) {
            populated.put(row.canonicalUrl(), row.vacancyId());
        }
        return populated;
    }

    /**
     * Classifies every accumulated candidate group in deterministic ({@code TreeMap}-sorted)
     * order, appending the resulting collision issues to {@code allIssues} as it goes.
     */
    private Classification classify(
            Map<String, List<UUID>> legacyCandidatesByCanonicalValue,
            Map<String, UUID> populatedCanonicalUrls,
            List<VacancyCanonicalUrlAuditIssue> allIssues) {
        int safeToBackfillRows = 0;
        int legacyToLegacyCollisionGroups = 0;
        int legacyToLegacyCollisionRows = 0;
        int legacyToCurrentCollisionRows = 0;

        for (Map.Entry<String, List<UUID>> entry : new TreeMap<>(legacyCandidatesByCanonicalValue).entrySet()) {
            String canonicalValue = entry.getKey();
            List<UUID> vacancyIds = new ArrayList<>(entry.getValue());
            vacancyIds.sort(null);

            if (vacancyIds.size() > 1) {
                legacyToLegacyCollisionGroups++;
                legacyToLegacyCollisionRows += vacancyIds.size();
                for (UUID vacancyId : vacancyIds) {
                    List<UUID> related = vacancyIds.stream().filter(id -> !id.equals(vacancyId)).toList();
                    allIssues.add(new VacancyCanonicalUrlAuditIssue(
                            vacancyId, VacancyCanonicalUrlAuditIssueType.LEGACY_TO_LEGACY_COLLISION, canonicalValue, related));
                }
                continue;
            }

            UUID onlyVacancyId = vacancyIds.get(0);
            UUID currentOwner = populatedCanonicalUrls.get(canonicalValue);
            if (currentOwner != null) {
                legacyToCurrentCollisionRows++;
                allIssues.add(new VacancyCanonicalUrlAuditIssue(
                        onlyVacancyId, VacancyCanonicalUrlAuditIssueType.LEGACY_TO_CURRENT_COLLISION,
                        canonicalValue, List.of(currentOwner)));
            } else {
                safeToBackfillRows++;
            }
        }

        return new Classification(safeToBackfillRows, legacyToLegacyCollisionGroups, legacyToLegacyCollisionRows, legacyToCurrentCollisionRows);
    }

    private VacancyCanonicalUrlAuditReport buildReport(
            int totalLegacyRows, int invalidSourceUrlRows, int scannedBatchCount,
            Classification classification, List<VacancyCanonicalUrlAuditIssue> allIssues) {
        int maxReportedIssues = properties.maxReportedIssues();
        List<VacancyCanonicalUrlAuditIssue> bounded =
                allIssues.size() > maxReportedIssues ? allIssues.subList(0, maxReportedIssues) : allIssues;
        int omittedIssueCount = Math.max(0, allIssues.size() - maxReportedIssues);

        return new VacancyCanonicalUrlAuditReport(
                totalLegacyRows,
                classification.safeToBackfillRows(),
                invalidSourceUrlRows,
                classification.legacyToLegacyCollisionGroups(),
                classification.legacyToLegacyCollisionRows(),
                classification.legacyToCurrentCollisionRows(),
                scannedBatchCount,
                omittedIssueCount,
                bounded);
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

    private record Classification(
            int safeToBackfillRows,
            int legacyToLegacyCollisionGroups,
            int legacyToLegacyCollisionRows,
            int legacyToCurrentCollisionRows) {
    }
}
