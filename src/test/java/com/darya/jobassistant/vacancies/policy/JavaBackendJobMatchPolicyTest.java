package com.darya.jobassistant.vacancies.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaBackendJobMatchPolicyTest {

    private final JavaBackendJobMatchPolicy policy = new JavaBackendJobMatchPolicy();

    @Test
    void matches_seniorJavaBackendEngineer() {
        assertThat(policy.matches(offer("Senior Java Backend Engineer", null))).isTrue();
    }

    @Test
    void matches_backendEngineerJavaAndSpringBoot() {
        assertThat(policy.matches(offer("Backend Engineer - Java and Spring Boot", null))).isTrue();
    }

    @Test
    void matches_seniorJvmMicroservicesDeveloper() {
        assertThat(policy.matches(offer("Senior JVM Microservices Developer", null))).isTrue();
    }

    @Test
    void matches_javaSignalInTagsPlusBackendSignalInTitle() {
        JobOffer offer = offer("Backend Developer", null, "java", "senior");
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_javaSignalInDescriptionPlusBackendEngineeringTitle() {
        JobOffer offer = offer("Backend Engineer", "We build everything in Java and love it.");
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_isCaseInsensitive() {
        JobOffer offer = offer("SENIOR JAVA BACKEND ENGINEER", null);
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void doesNotMatch_javaScriptBackendDeveloperWithoutJavaSignal() {
        JobOffer offer = offer("JavaScript Backend Developer", "We use React and Node.js.");
        assertThat(policy.matches(offer)).isFalse();
    }

    @Test
    void doesNotMatch_androidJavaDeveloper() {
        assertThat(policy.matches(offer("Android Java Developer", null))).isFalse();
    }

    @Test
    void doesNotMatch_frontendJavaDeveloper() {
        assertThat(policy.matches(offer("Frontend Java Developer", null))).isFalse();
    }

    @Test
    void doesNotMatch_qaEngineerJavaBackend() {
        assertThat(policy.matches(offer("QA Engineer - Java Backend", null))).isFalse();
    }

    @Test
    void doesNotMatch_customerSupportWithJavaBackendTags() {
        JobOffer offer = offer("Customer Support", null, "java", "backend");
        assertThat(policy.matches(offer)).isFalse();
    }

    @Test
    void doesNotMatch_appointmentSetterWithJavaBackendEngineerTags() {
        JobOffer offer = offer("Appointment Setter", null, "java", "backend", "engineer");
        assertThat(policy.matches(offer)).isFalse();
    }

    @Test
    void doesNotMatch_javaDeveloperWithoutAnyBackendSignal() {
        JobOffer offer = offer("Java Developer", "We build delightful mobile UIs.");
        assertThat(policy.matches(offer)).isFalse();
    }

    @Test
    void doesNotMatch_backendEngineerWithoutAnyJavaSignal() {
        JobOffer offer = offer("Backend Engineer", "We use Go and Python here.");
        assertThat(policy.matches(offer)).isFalse();
    }

    @Test
    void doesNotMatch_nonEngineeringTitleWithJavaAndBackendTextInDescription() {
        JobOffer offer = offer("Customer Success Manager", "Knowledge of Java and backend systems is a plus.");
        assertThat(policy.matches(offer)).isFalse();
    }

    @Test
    void matches_handlesNullDescriptionSafely() {
        JobOffer offer = offer("Senior Java Backend Engineer", null);
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_handlesNullTagsSafely() {
        JobOffer offer = new JobOffer("1", "Senior Java Backend Engineer", "Acme", "Remote",
                null, null, "https://example.com/1", "remoteok", null);
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_handlesEmptyTagsSafely() {
        JobOffer offer = offer("Senior Java Backend Engineer", null, new String[0]);
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_handlesNullTagElementsSafely() {
        List<String> tagsWithNull = Arrays.asList("java", null, "backend");
        JobOffer offer = new JobOffer("1", "Senior Engineer", "Acme", "Remote",
                null, null, "https://example.com/1", "remoteok", tagsWithNull);
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_htmlAndRepeatedWhitespaceDoNotBreakMatching() {
        JobOffer offer = offer("Senior   Java    Backend  Engineer",
                "<p>We  use   <b>Java</b>   and  <i>Spring   Boot</i>.</p>");
        assertThat(policy.matches(offer)).isTrue();
    }

    @Test
    void matches_recognizesBackHyphenEndVariant() {
        assertThat(policy.matches(offer("Java Back-End Engineer", null))).isTrue();
    }

    @Test
    void matches_recognizesBackSpaceEndVariant() {
        assertThat(policy.matches(offer("Java Back End Engineer", null))).isTrue();
    }

    @Test
    void matches_recognizesBackendConcatenatedVariant() {
        assertThat(policy.matches(offer("Java Backend Engineer", null))).isTrue();
    }

    @Test
    void matches_doesNotMutateSourceJobOffer() {
        List<String> originalTags = List.of("java", "backend");
        JobOffer offer = new JobOffer("1", "Senior Java Backend Engineer", "Acme", "Remote",
                null, "Some description", "https://example.com/1", "remoteok", originalTags);

        policy.matches(offer);

        assertThat(offer.tags()).containsExactly("java", "backend");
        assertThat(offer.title()).isEqualTo("Senior Java Backend Engineer");
        assertThat(offer.description()).isEqualTo("Some description");
    }

    private JobOffer offer(String title, String description, String... tags) {
        return new JobOffer("1", title, "Acme", "Remote", null, description,
                "https://example.com/1", "remoteok", Arrays.asList(tags));
    }
}
