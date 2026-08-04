package com.darya.jobassistant.careerhistory.importing;

import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sprint 9 Step 7: semantic comparison of two {@link CareerHistoryAggregate} instances - the
 * source proposal mapped by {@code CareerHistoryImportMapper} and the currently persisted
 * destination (or vice versa for a post-save parity check).
 *
 * <h2>Equality is fingerprint equality, not a second comparison algorithm</h2>
 *
 * {@link #areEqual} delegates to {@link CareerHistoryFingerprint#sha256} equality rather than
 * re-implementing field-by-field comparison - the single canonical serialization in {@link
 * CareerHistoryFingerprint#canonicalize} is the one and only definition of "semantically equal"
 * used everywhere in this package (fingerprinting, equality, and - via {@link #diff}'s {@code
 * equal} field - decision-making), per this step's "do not maintain separate subtly different
 * normalization algorithms" requirement. {@link #diff} additionally walks the two aggregates to
 * produce safe, human-readable structural entries, but that walk is auxiliary reporting only - it
 * never itself decides {@code equal}.
 *
 * <h2>Matching children by id, not by list position</h2>
 *
 * Companies/positions/projects are matched between {@code source} and {@code destination} by
 * {@link CareerCompany#id()}/{@link CareerPosition#id()}/{@link CareerProject#id()} - which, for
 * an entry whose stable import {@code key} is unchanged, is the same deterministic id on both
 * sides (see {@code CareerHistoryImportIdGenerator}) regardless of list position or a changed
 * display name. An id present on only one side is reported as {@code ADDED}/{@code REMOVED};
 * changing a stable key is - correctly - indistinguishable from removing the old entry and adding
 * a new one, since the two no longer share an id at all.
 */
public final class CareerHistorySemanticComparator {

    /** Bounds {@link CareerHistoryDiff#entries()} - {@link CareerHistoryDiff#totalChangeCount()} always reports the true count. */
    private static final int MAX_DIFF_ENTRIES = 50;

    private CareerHistorySemanticComparator() {
    }

    public static boolean areEqual(CareerHistoryAggregate a, CareerHistoryAggregate b) {
        return CareerHistoryFingerprint.sha256(a).equals(CareerHistoryFingerprint.sha256(b));
    }

    public static CareerHistoryDiff diff(CareerHistoryAggregate source, CareerHistoryAggregate destination) {
        boolean equal = areEqual(source, destination);
        List<String> entries = equal
                ? List.of()
                : companyEntries(source.companies(), destination.companies(), "companies");
        int total = entries.size();
        List<String> capped = total > MAX_DIFF_ENTRIES ? List.copyOf(entries.subList(0, MAX_DIFF_ENTRIES)) : entries;
        return new CareerHistoryDiff(equal, capped, total);
    }

    private static List<String> companyEntries(List<CareerCompany> source, List<CareerCompany> destination, String path) {
        List<String> entries = new ArrayList<>();
        Map<UUID, CareerCompany> sourceById = byId(source, CareerCompany::id);
        Map<UUID, CareerCompany> destinationById = byId(destination, CareerCompany::id);
        for (UUID id : union(sourceById.keySet(), destinationById.keySet())) {
            CareerCompany sourceCompany = sourceById.get(id);
            CareerCompany destCompany = destinationById.get(id);
            if (sourceCompany == null) {
                entries.add(path + "[" + destCompany.name() + "]: REMOVED");
            } else if (destCompany == null) {
                entries.add(path + "[" + sourceCompany.name() + "]: ADDED");
            } else {
                String label = path + "[" + sourceCompany.name() + "]";
                if (!companyScalarsEqual(sourceCompany, destCompany)) {
                    entries.add(label + ": CHANGED");
                }
                entries.addAll(positionEntries(sourceCompany.positions(), destCompany.positions(), label + ".positions"));
            }
        }
        return entries;
    }

    private static List<String> positionEntries(List<CareerPosition> source, List<CareerPosition> destination, String path) {
        List<String> entries = new ArrayList<>();
        Map<UUID, CareerPosition> sourceById = byId(source, CareerPosition::id);
        Map<UUID, CareerPosition> destinationById = byId(destination, CareerPosition::id);
        for (UUID id : union(sourceById.keySet(), destinationById.keySet())) {
            CareerPosition sourcePosition = sourceById.get(id);
            CareerPosition destPosition = destinationById.get(id);
            if (sourcePosition == null) {
                entries.add(path + "[" + destPosition.title() + "]: REMOVED");
            } else if (destPosition == null) {
                entries.add(path + "[" + sourcePosition.title() + "]: ADDED");
            } else {
                String label = path + "[" + sourcePosition.title() + "]";
                if (!positionScalarsEqual(sourcePosition, destPosition)) {
                    entries.add(label + ": CHANGED");
                }
                addBulletDiff(entries, label + ".responsibilities", sourcePosition.responsibilities(), destPosition.responsibilities(),
                        CareerResponsibility::text, CareerResponsibility::displayOrder);
                addBulletDiff(entries, label + ".achievements", sourcePosition.achievements(), destPosition.achievements(),
                        CareerAchievement::text, CareerAchievement::displayOrder);
                entries.addAll(projectEntries(sourcePosition.projects(), destPosition.projects(), label + ".projects"));
            }
        }
        return entries;
    }

    private static List<String> projectEntries(List<CareerProject> source, List<CareerProject> destination, String path) {
        List<String> entries = new ArrayList<>();
        Map<UUID, CareerProject> sourceById = byId(source, CareerProject::id);
        Map<UUID, CareerProject> destinationById = byId(destination, CareerProject::id);
        for (UUID id : union(sourceById.keySet(), destinationById.keySet())) {
            CareerProject sourceProject = sourceById.get(id);
            CareerProject destProject = destinationById.get(id);
            if (sourceProject == null) {
                entries.add(path + "[" + destProject.name() + "]: REMOVED");
            } else if (destProject == null) {
                entries.add(path + "[" + sourceProject.name() + "]: ADDED");
            } else {
                String label = path + "[" + sourceProject.name() + "]";
                if (!projectScalarsEqual(sourceProject, destProject)) {
                    entries.add(label + ": CHANGED");
                }
                addBulletDiff(entries, label + ".responsibilities", sourceProject.responsibilities(), destProject.responsibilities(),
                        CareerResponsibility::text, CareerResponsibility::displayOrder);
                addBulletDiff(entries, label + ".achievements", sourceProject.achievements(), destProject.achievements(),
                        CareerAchievement::text, CareerAchievement::displayOrder);
                if (!technologiesEqual(sourceProject.technologies(), destProject.technologies())) {
                    entries.add(label + ".technologies: CHANGED");
                }
            }
        }
        return entries;
    }

    private static <T> void addBulletDiff(
            List<String> entries, String label, List<T> source, List<T> destination,
            Function<T, String> textOf, java.util.function.ToIntFunction<T> orderOf) {
        if (source.size() != destination.size()) {
            entries.add(label + ": count changed (" + destination.size() + " -> " + source.size() + ")");
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            if (!Objects.equals(textOf.apply(source.get(i)), textOf.apply(destination.get(i)))
                    || orderOf.applyAsInt(source.get(i)) != orderOf.applyAsInt(destination.get(i))) {
                entries.add(label + ": CHANGED");
                return;
            }
        }
    }

    private static boolean technologiesEqual(List<CareerTechnology> source, List<CareerTechnology> destination) {
        if (source.size() != destination.size()) {
            return false;
        }
        for (int i = 0; i < source.size(); i++) {
            CareerTechnology a = source.get(i);
            CareerTechnology b = destination.get(i);
            if (!Objects.equals(a.name(), b.name()) || !Objects.equals(a.category(), b.category()) || a.displayOrder() != b.displayOrder()) {
                return false;
            }
        }
        return true;
    }

    private static boolean companyScalarsEqual(CareerCompany a, CareerCompany b) {
        return Objects.equals(a.name(), b.name()) && Objects.equals(a.website(), b.website())
                && Objects.equals(a.industry(), b.industry()) && Objects.equals(a.location(), b.location())
                && Objects.equals(a.description(), b.description()) && a.displayOrder() == b.displayOrder();
    }

    private static boolean positionScalarsEqual(CareerPosition a, CareerPosition b) {
        return Objects.equals(a.title(), b.title()) && Objects.equals(a.employmentType(), b.employmentType())
                && Objects.equals(a.location(), b.location()) && Objects.equals(a.workArrangement(), b.workArrangement())
                && Objects.equals(a.startDate(), b.startDate()) && Objects.equals(a.endDate(), b.endDate())
                && a.currentRole() == b.currentRole() && Objects.equals(a.description(), b.description())
                && a.displayOrder() == b.displayOrder();
    }

    private static boolean projectScalarsEqual(CareerProject a, CareerProject b) {
        return Objects.equals(a.name(), b.name()) && Objects.equals(a.description(), b.description())
                && Objects.equals(a.startDate(), b.startDate()) && Objects.equals(a.endDate(), b.endDate())
                && a.displayOrder() == b.displayOrder();
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> idOf) {
        Map<UUID, T> byId = new LinkedHashMap<>();
        for (T item : items) {
            byId.put(idOf.apply(item), item);
        }
        return byId;
    }

    private static Set<UUID> union(Set<UUID> a, Set<UUID> b) {
        Set<UUID> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return union;
    }
}
