package com.darya.jobassistant.candidatecontext.cv;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import java.util.List;

/**
 * Sprint 11 Step 1: deterministic, framework-free (no AI call, no HTTP/network call, no persistence
 * write, no random choice, no current-time dependency) assembly of {@link CvSourceSnapshot} - the
 * complete factual universe a future CV tailoring step is allowed to draw on - from the existing
 * {@link CandidateContextSnapshot}.
 *
 * <p>Every company/position/project/bullet/technology the source Career History has is carried
 * through unfiltered and in its existing display order - {@link CareerHistoryAggregate} and its
 * children already return their children pre-sorted by display order and defensively copied (see
 * {@code CareerHistoryValidation.copySortedByDisplayOrder}), so a plain {@code stream().map()} walk
 * here preserves that order faithfully without re-sorting anything itself. Never selects, ranks, or
 * truncates - that vacancy-specific behavior belongs to {@code
 * CandidateContextForAnalysisSelector}/{@code CandidateContextForApplicationMaterialsSelector}, not
 * this factory.
 *
 * <p>Mirrors {@code candidates.migration.CandidateProfileAnalysisAssembler}'s shape: a stateless,
 * private-constructor utility class rather than a Spring bean, since building this snapshot needs
 * no configuration or injected collaborator.
 */
public final class CvSourceSnapshotFactory {

    private CvSourceSnapshotFactory() {
    }

    public static CvSourceSnapshot from(CandidateContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Candidate context snapshot must not be null");
        }
        CareerHistoryAvailability availability = snapshot.careerHistoryAvailability();
        List<CvSourceCompany> companies = snapshot.careerHistory()
                .map(history -> history.companies().stream().map(CvSourceSnapshotFactory::toCompany).toList())
                .orElse(List.of());
        return new CvSourceSnapshot(snapshot.candidateProfile(), availability, companies);
    }

    private static CvSourceCompany toCompany(CareerCompany company) {
        return new CvSourceCompany(
                company.id(), company.name(), company.website(), company.industry(), company.location(),
                company.description(), company.positions().stream().map(CvSourceSnapshotFactory::toPosition).toList());
    }

    private static CvSourcePosition toPosition(CareerPosition position) {
        return new CvSourcePosition(
                position.id(), position.title(), position.employmentType(), position.location(),
                position.workArrangement(), position.startDate(), position.endDate(), position.currentRole(),
                position.description(),
                position.responsibilities().stream().map(CvSourceSnapshotFactory::toResponsibility).toList(),
                position.achievements().stream().map(CvSourceSnapshotFactory::toAchievement).toList(),
                position.projects().stream().map(CvSourceSnapshotFactory::toProject).toList());
    }

    private static CvSourceProject toProject(CareerProject project) {
        return new CvSourceProject(
                project.id(), project.name(), project.description(), project.startDate(), project.endDate(),
                project.responsibilities().stream().map(CvSourceSnapshotFactory::toResponsibility).toList(),
                project.achievements().stream().map(CvSourceSnapshotFactory::toAchievement).toList(),
                project.technologies().stream().map(CvSourceSnapshotFactory::toTechnology).toList());
    }

    private static CvSourceResponsibility toResponsibility(CareerResponsibility responsibility) {
        return new CvSourceResponsibility(responsibility.id(), responsibility.text());
    }

    private static CvSourceAchievement toAchievement(CareerAchievement achievement) {
        return new CvSourceAchievement(achievement.id(), achievement.text());
    }

    private static CvSourceTechnology toTechnology(CareerTechnology technology) {
        return new CvSourceTechnology(technology.id(), technology.name(), technology.category());
    }
}
