package com.darya.jobassistant.integrations.jobsearch;

import java.net.URI;

public interface JobPageFetchPort {

    JobPageContent fetch(URI sourceUrl);
}
