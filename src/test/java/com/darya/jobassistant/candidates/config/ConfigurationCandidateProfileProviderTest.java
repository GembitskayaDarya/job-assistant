package com.darya.jobassistant.candidates.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidateProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationCandidateProfileProviderTest {

    @Test
    void getProfile_mapsEveryPropertyToCandidateProfile() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer",
                List.of("Java", "Spring Boot", "PostgreSQL", "Kafka", "Redis", "AWS", "Docker"),
                List.of("English", "Russian", "Polish"),
                6,
                "Product company",
                "Remote Europe");

        ConfigurationCandidateProfileProvider provider = new ConfigurationCandidateProfileProvider(properties);

        CandidateProfile profile = provider.getProfile();

        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Developer");
        assertThat(profile.skills()).containsExactly("Java", "Spring Boot", "PostgreSQL", "Kafka", "Redis", "AWS", "Docker");
        assertThat(profile.languages()).containsExactly("English", "Russian", "Polish");
        assertThat(profile.experienceYears()).isEqualTo(6);
        assertThat(profile.preferredCompanyType()).isEqualTo("Product company");
        assertThat(profile.preferredLocation()).isEqualTo("Remote Europe");
    }

    @Test
    void getProfile_returnsImmutableCollections() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer", List.of("Java"), List.of("English"), 6, "Product company", "Remote Europe");
        ConfigurationCandidateProfileProvider provider = new ConfigurationCandidateProfileProvider(properties);

        CandidateProfile profile = provider.getProfile();

        assertThatThrownBy(() -> profile.skills().add("Kotlin")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.languages().add("Polish")).isInstanceOf(UnsupportedOperationException.class);
    }
}
