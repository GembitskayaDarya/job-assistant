package com.darya.jobassistant.jobdiscovery.listing;

import java.net.URI;

/**
 * One deterministically-extracted, already-resolved-to-absolute link found on a {@code
 * SEARCH_OR_LISTING_PAGE}'s fetched content, before canonicalization/classification/deduplication.
 * {@link #anchorText} is a low-confidence title hint only (Markdown link text) - never treated as
 * verified vacancy data.
 */
public record ListingCandidateLink(URI url, String anchorText) {
}
