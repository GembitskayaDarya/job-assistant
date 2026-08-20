package com.darya.jobassistant.integrations.ai.openai;

/**
 * Sprint 11 Big Block 6: one responsibility/achievement selection within {@link
 * CvPositionTailoringResponseDto}/{@link CvProjectTailoringResponseDto} - {@link #id} is the exact
 * source responsibility/achievement id being selected, {@link #rewrittenText} is {@code null} when
 * the AI chose not to rewrite it (show the original source text unchanged).
 */
record CvBulletTailoringResponseDto(String id, String rewrittenText) {
}
