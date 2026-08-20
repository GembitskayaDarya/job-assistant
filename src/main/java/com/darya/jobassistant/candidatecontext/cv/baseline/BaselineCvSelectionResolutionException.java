package com.darya.jobassistant.candidatecontext.cv.baseline;

/**
 * Sprint 11 Big Block 7: thrown by {@link BaselineCvSelectionResolver#resolve} when a configured
 * natural-value reference (company/position/project name, exact bullet text, skill/technology name)
 * cannot be matched against the current {@code CvSourceSnapshot} - e.g. the private career-history
 * source was edited (renamed, reworded, reordered) since the baseline config was last curated. A
 * loud, immediate failure here is deliberate: silently skipping an unmatched entry would produce an
 * incomplete/wrong baseline CV without any indication why - see that class's javadoc.
 */
public class BaselineCvSelectionResolutionException extends RuntimeException {

    public BaselineCvSelectionResolutionException(String message) {
        super(message);
    }
}
