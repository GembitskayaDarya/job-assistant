package com.darya.jobassistant.candidates;

import java.util.List;

/**
 * The candidate's work preferences beyond raw skills: location/relocation, contract and company
 * type, and salary expectations. Nullable textual fields (e.g. {@code salaryExpectation}) mean
 * "not yet configured", not "none" - callers must not treat a null here as a negative signal.
 */
public record CandidatePreferences(
        String currentCountry,
        String preferredWorkArrangement,
        PreferenceImportance workArrangementImportance,
        List<String> allowedWorkCountries,
        boolean relocationAllowed,
        List<String> preferredContractTypes,
        PreferenceImportance contractTypeImportance,
        String preferredCompanyType,
        PreferenceImportance companyTypeImportance,
        String salaryExpectation
) {
    public CandidatePreferences {
        allowedWorkCountries = allowedWorkCountries == null ? List.of() : List.copyOf(allowedWorkCountries);
        preferredContractTypes = preferredContractTypes == null ? List.of() : List.copyOf(preferredContractTypes);
    }
}
