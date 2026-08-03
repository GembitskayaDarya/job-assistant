package com.darya.jobassistant.candidates.runtime;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.migration.CandidateProfileAnalysisAssembler;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 4: the only runtime {@link CandidateProfileProvider} - PostgreSQL, via {@link
 * CandidateProfileRepositoryPort}, is now the sole source AI vacancy analysis reads from. The
 * YAML-backed provider is gone; {@code YamlCandidateProfileMigrationSource} is a separate,
 * migration-only abstraction this class does not depend on and never falls back to.
 *
 * <p>Every call performs a fresh {@link CandidateProfileRepositoryPort#findByProfileKey} read -
 * deliberately no caching, so a profile update committed through {@link
 * CandidateProfileRepositoryPort#save} is visible on the very next call, without an application
 * restart. Read-only: this class never writes, never seeds, and never falls back to a default or
 * empty profile - a missing aggregate is a hard failure ({@link
 * CandidateProfileNotConfiguredException}), not a degraded response.
 *
 * <p>Never exposes {@link CandidateProfileAggregate} (or any JPA entity) beyond this class - only
 * the existing analysis-oriented {@link CandidateProfile}, assembled via the framework-free,
 * stateless {@link CandidateProfileAnalysisAssembler}.
 */
@Component
public class PersistentCandidateProfileProvider implements CandidateProfileProvider {

    private final CandidateProfileRepositoryPort repositoryPort;
    private final CandidateProfileRuntimeProperties properties;

    public PersistentCandidateProfileProvider(
            CandidateProfileRepositoryPort repositoryPort, CandidateProfileRuntimeProperties properties) {
        this.repositoryPort = repositoryPort;
        this.properties = properties;
    }

    @Override
    public CandidateProfile getProfile() {
        String profileKey = properties.profileKey();
        CandidateProfileAggregate aggregate = repositoryPort.findByProfileKey(profileKey)
                .orElseThrow(() -> new CandidateProfileNotConfiguredException(profileKey));
        return CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate);
    }
}
