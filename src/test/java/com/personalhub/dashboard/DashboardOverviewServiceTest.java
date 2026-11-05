package com.personalhub.dashboard;

import com.personalhub.config.NotionProperties;
import com.personalhub.dashboard.application.DashboardOverviewService;
import com.personalhub.github.application.GitHubQueryService;
import com.personalhub.github.web.GitHubStatsResponse;
import com.personalhub.notion.domain.NotionSyncRun;
import com.personalhub.notion.domain.NotionTaskCounts;
import com.personalhub.notion.repository.NotionRepository;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardOverviewServiceTest {
    private final GitHubQueryService github = mock(GitHubQueryService.class);
    private final NotionRepository notion = mock(NotionRepository.class);
    private final SyncRunJpaRepository runs = mock(SyncRunJpaRepository.class);
    private final UUID source = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private DashboardOverviewService service(boolean enabled) {
        var properties = new NotionProperties(enabled, enabled ? "test-token" : "", enabled ? source.toString() : "",
                "https://api.notion.com/v1", "2025-09-03", "Name", "Status", "Due", 3, 5);
        when(github.stats(30)).thenReturn(new GitHubStatsResponse("demo", Instant.now(), 2, 5, 3, 2));
        when(runs.findFirstByOrderByStartedAtDescIdDesc()).thenReturn(Optional.empty());
        return new DashboardOverviewService(github, notion, runs, properties);
    }

    @Test
    void disabledNotionReturnsUsableGithubOverviewWithoutReadingNotion() {
        var result = service(false).overview(30);
        assertThat(result.days()).isEqualTo(30);
        assertThat(result.generatedAt()).isNotNull();
        assertThat(result.github().recordedCommits()).isEqualTo(5);
        assertThat(result.notion().enabled()).isFalse();
        assertThat(result.notion().activeTasks()).isZero();
        assertThat(result.notion().tasksByStatus()).isEmpty();
        assertThat(result.latestSyncs().notion()).isNull();
        verifyNoInteractions(notion);
    }

    @Test
    void failedLatestRunRetainsCountsAndLastSuccessfulCheckpoint() {
        var service = service(true);
        Instant checkpoint = Instant.parse("2026-11-04T00:00:00Z");
        var failed = new NotionSyncRun(UUID.randomUUID(), source, "FAILED", checkpoint.plusSeconds(60),
                checkpoint.plusSeconds(61), 0, "Notion HTTP 503");
        when(notion.taskCounts(source)).thenReturn(new NotionTaskCounts(3, 1,
                List.of(new NotionTaskCounts.StatusCount("Done", 2), new NotionTaskCounts.StatusCount(null, 1))));
        when(notion.checkpoint(source)).thenReturn(checkpoint);
        when(notion.runs(source, 1)).thenReturn(List.of(failed));
        var result = service.overview(30);
        assertThat(result.notion().enabled()).isTrue();
        assertThat(result.notion().activeTasks()).isEqualTo(3);
        assertThat(result.notion().archivedTasks()).isEqualTo(1);
        assertThat(result.notion().lastCheckpointAt()).isEqualTo(checkpoint);
        assertThat(result.notion().tasksByStatus()).hasSize(2);
        assertThat(result.latestSyncs().notion()).isEqualTo(failed);
        verify(notion).taskCounts(source);
    }
}
