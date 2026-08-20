package com.darya.jobassistant.candidatecontext.cv.document.model;

/**
 * Sprint 11 Big Block 6: the fixed, AI-never-touches header block of a {@link TailoredCvDocument} -
 * copied unchanged from {@code CandidateProfileFacts}. No field here is ever selected, reordered, or
 * rewritten by {@code CvTailoringResult}; {@code CvAssembler} copies each one through verbatim.
 */
public record TailoredCvHeader(
        String fullName,
        String cvHeadline,
        String cvLocation,
        String email,
        String phone,
        String linkedinUrl
) {
}
