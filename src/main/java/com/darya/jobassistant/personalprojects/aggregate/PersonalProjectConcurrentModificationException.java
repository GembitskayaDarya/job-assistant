package com.darya.jobassistant.personalprojects.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link PersonalProjectRepositoryPort#save} when the supplied {@link
 * PersonalProject#version()} no longer matches that project row's current version - another
 * transaction committed a change to this exact project in between. Carries only safe identifiers -
 * project id, candidate profile id, expected version - never project content.
 */
public class PersonalProjectConcurrentModificationException extends RuntimeException {

    public PersonalProjectConcurrentModificationException(UUID personalProjectId, UUID candidateProfileId, long expectedVersion) {
        super("Personal project '" + personalProjectId + "' (candidateProfileId=" + candidateProfileId
                + ") was concurrently modified by another transaction - expected version " + expectedVersion
                + " no longer matches");
    }
}
