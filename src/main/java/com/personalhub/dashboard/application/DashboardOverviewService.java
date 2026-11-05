package com.personalhub.dashboard.application;

import com.personalhub.config.NotionProperties;
import com.personalhub.dashboard.web.DashboardOverviewResponse;
import com.personalhub.dashboard.web.DashboardOverviewResponse.LatestSyncs;
import com.personalhub.dashboard.web.DashboardOverviewResponse.NotionOverview;
import com.personalhub.github.application.GitHubQueryService;
import com.personalhub.notion.domain.NotionSyncRun;
import com.personalhub.notion.repository.NotionRepository;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import com.personalhub.sync.web.SyncRunResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardOverviewService {
    private final GitHubQueryService github;
    private final NotionRepository notion;
    private final SyncRunJpaRepository githubRuns;
    private final NotionProperties properties;

    public DashboardOverviewService(GitHubQueryService github, NotionRepository notion,
            SyncRunJpaRepository githubRuns, NotionProperties properties) {
        this.github = github; this.notion = notion; this.githubRuns = githubRuns; this.properties = properties;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardOverviewResponse overview(int days) {
        var stats = github.stats(days);
        var latestGithub = githubRuns.findFirstByOrderByStartedAtDescIdDesc().map(SyncRunResponse::from).orElse(null);
        NotionOverview summary = new NotionOverview(false, 0, 0, List.of(), null);
        NotionSyncRun latestNotion = null;
        if (properties.enabled()) {
            UUID source = UUID.fromString(properties.dataSourceId());
            var counts = notion.taskCounts(source);
            summary = new NotionOverview(true, counts.activeTasks(), counts.archivedTasks(),
                    counts.tasksByStatus(), notion.checkpoint(source));
            latestNotion = notion.runs(source, 1).stream().findFirst().orElse(null);
        }
        return new DashboardOverviewResponse(Instant.now(), days, stats, summary, new LatestSyncs(latestGithub, latestNotion));
    }
}
