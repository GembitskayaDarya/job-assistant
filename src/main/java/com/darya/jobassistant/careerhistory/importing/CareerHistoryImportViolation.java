package com.darya.jobassistant.careerhistory.importing;

/**
 * One independent validation failure reported by {@link CareerHistoryImportValidator} - a safe,
 * document-path-addressed record ({@code companies[0].positions[1].title}), a short violation
 * type/category, and a detail message that never echoes complete long field contents (see {@link
 * CareerHistoryImportValidator}'s javadoc).
 */
public record CareerHistoryImportViolation(String path, String violationType, String detail) {

    @Override
    public String toString() {
        return path + ": " + detail;
    }
}
