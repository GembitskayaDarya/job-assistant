package com.darya.jobassistant.domain.candidate;

import java.util.List;

/**
 * Domain model of a job candidate, used by the AI analyzer to match vacancies
 * against the candidate's background.
 * <p>
 * This is a plain domain object: it has no framework annotations and no
 * persistence concerns, so it can be reused by any AI provider or matching
 * strategy without depending on Spring or JPA.
 *
 * @param role             the candidate's professional role, e.g. "Senior Java Backend Engineer"
 * @param yearsOfExperience the candidate's total years of professional experience
 * @param technicalSkills  the candidate's technical skills, e.g. "Java", "Spring Boot", "Kafka"
 * @param preferredLocation the candidate's preferred work location, e.g. "Remote, Europe"
 * @param employmentType   the candidate's preferred employment type, e.g. "B2B"
 */
public record CandidateProfile(
        String role,
        int yearsOfExperience,
        List<String> technicalSkills,
        String preferredLocation,
        String employmentType
) {
    public CandidateProfile {
        technicalSkills = technicalSkills == null ? List.of() : List.copyOf(technicalSkills);
    }
}
