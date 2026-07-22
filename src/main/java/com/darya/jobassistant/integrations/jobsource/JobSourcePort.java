package com.darya.jobassistant.integrations.jobsource;

import java.util.List;

public interface JobSourcePort {

    String sourceName();

    List<JobOffer> fetchLatestPostings();
}