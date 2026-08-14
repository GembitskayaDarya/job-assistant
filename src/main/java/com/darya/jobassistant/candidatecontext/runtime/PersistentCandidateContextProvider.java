package com.darya.jobassistant.candidatecontext.runtime;

import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.migration.CandidateProfileFactsAssembler;
import com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException;
import com.darya.jobassistant.candidates.runtime.CandidateProfileRuntimeProperties;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 9 Step 8: the only runtime {@link CandidateContextProvider} - reads the persistent
 * Candidate Profile (via {@link CandidateProfileRepositoryPort}, resolved by the same {@link
 * CandidateProfileRuntimeProperties#profileKey()} {@code
 * com.darya.jobassistant.candidates.runtime.PersistentCandidateProfileProvider} uses) together
 * with its optional Career History (via {@link CareerHistoryRepositoryPort}, keyed by the
 * profile's own technical {@link CandidateProfileAggregate#id()}) and its Personal Projects (via
 * {@link PersonalProjectRepositoryPort}, Sprint 11 Step 5, same key) inside one read-only, {@code
 * REPEATABLE_READ} transaction - so all three reads observe the same consistent database snapshot.
 *
 * <p>The transaction closes when {@link #loadCurrentContext()} returns: no transaction is ever
 * held open across the AI call a caller makes afterward. Never creates a Career History row, never
 * falls back to a default profile, and never caches - every call performs fresh reads.
 *
 * <p>A separate use case from {@code PersistentCandidateProfileProvider}: that class remains the
 * one {@code CandidateProfileProvider} bean, used by startup validation and any other
 * non-analysis consumer; this class does not implement that interface and the two are not merged.
 */
@Service
public class PersistentCandidateContextProvider implements CandidateContextProvider {

    private final CandidateProfileRepositoryPort candidateProfileRepositoryPort;
    private final CareerHistoryRepositoryPort careerHistoryRepositoryPort;
    private final PersonalProjectRepositoryPort personalProjectRepositoryPort;
    private final CandidateProfileRuntimeProperties properties;

    public PersistentCandidateContextProvider(
            CandidateProfileRepositoryPort candidateProfileRepositoryPort,
            CareerHistoryRepositoryPort careerHistoryRepositoryPort,
            PersonalProjectRepositoryPort personalProjectRepositoryPort,
            CandidateProfileRuntimeProperties properties) {
        this.candidateProfileRepositoryPort = candidateProfileRepositoryPort;
        this.careerHistoryRepositoryPort = careerHistoryRepositoryPort;
        this.personalProjectRepositoryPort = personalProjectRepositoryPort;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CandidateContextSnapshot loadCurrentContext() {
        String profileKey = properties.profileKey();
        CandidateProfileAggregate aggregate = candidateProfileRepositoryPort.findByProfileKey(profileKey)
                .orElseThrow(() -> new CandidateProfileNotConfiguredException(profileKey));
        CandidateProfileFacts profileFacts = CandidateProfileFactsAssembler.toProfileFacts(aggregate);
        Optional<CareerHistoryAggregate> careerHistory =
                careerHistoryRepositoryPort.findByCandidateProfileId(aggregate.id());
        List<PersonalProject> personalProjects = personalProjectRepositoryPort.findAllByCandidateProfileId(aggregate.id());
        return new CandidateContextSnapshot(
                aggregate.id(), profileKey, aggregate.version(), profileFacts, careerHistory, personalProjects);
    }
}
