package com.darya.jobassistant.candidates.config;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationCandidateProfileProvider implements CandidateProfileProvider {

    private final CandidateProfileProperties properties;

    public ConfigurationCandidateProfileProvider(CandidateProfileProperties properties) {
        this.properties = properties;
    }

    @Override
    public CandidateProfile getProfile() {
        return new CandidateProfile(
                properties.targetRole(),
                properties.skills(),
                properties.languages(),
                properties.experienceYears(),
                properties.preferredCompanyType(),
                properties.preferredLocation()
        );
    }
}
