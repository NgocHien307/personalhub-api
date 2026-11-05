package com.personalhub.dashboard.web;

import com.personalhub.github.web.GitHubStatsResponse;
import com.personalhub.notion.domain.NotionSyncRun;
import com.personalhub.notion.domain.NotionTaskCounts;
import com.personalhub.sync.web.SyncRunResponse;
import java.time.Instant;
import java.util.List;

public record DashboardOverviewResponse(Instant generatedAt, int days, GitHubStatsResponse github,
        NotionOverview notion, LatestSyncs latestSyncs) {
    public record NotionOverview(boolean enabled, long activeTasks, long archivedTasks,
            List<NotionTaskCounts.StatusCount> tasksByStatus, Instant lastCheckpointAt) {}
    public record LatestSyncs(SyncRunResponse github, NotionSyncRun notion) {}
}
