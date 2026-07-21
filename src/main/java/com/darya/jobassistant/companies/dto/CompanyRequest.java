package com.darya.jobassistant.companies.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyRequest(
        @NotBlank String name,
        String website,
        String notes
) {
}
