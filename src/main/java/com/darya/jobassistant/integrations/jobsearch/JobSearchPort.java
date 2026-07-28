package com.darya.jobassistant.integrations.jobsearch;

import java.util.List;

public interface JobSearchPort {

    List<DiscoveredJobReference> search(JobSearchRequest request);
}
