package com.personalhub.github.web;

import java.time.Instant;

public record GitHubStatsResponse(String username, Instant since, long trackedRepositories,
                                  long recordedCommits, long authoredCommits, long activeDaysUtc) { }
