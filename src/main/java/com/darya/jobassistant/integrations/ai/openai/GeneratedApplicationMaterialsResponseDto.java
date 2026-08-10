package com.darya.jobassistant.integrations.ai.openai;

/**
 * The complete raw JSON shape requested from the AI provider via {@code ChatClient}'s structured-
 * output support ({@code .entity(Class)}/{@code .responseEntity(Class)}) - package-private,
 * immediately mapped to the framework-free {@code GeneratedApplicationMaterials} domain model by
 * {@link SpringAiApplicationMaterialsAdapter} and never itself exposed outside this package.
 */
record GeneratedApplicationMaterialsResponseDto(GeneratedCvResponseDto cv, GeneratedCoverLetterResponseDto coverLetter) {
}
