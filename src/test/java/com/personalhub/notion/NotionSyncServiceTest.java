package com.personalhub.notion;

import com.personalhub.notion.application.*;
import com.personalhub.notion.infrastructure.*;
import com.personalhub.notion.repository.NotionRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotionSyncServiceTest {
    private final NotionRepository repo = mock(NotionRepository.class);
    private final NotionApiClient client = mock(NotionApiClient.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final UUID source = UUID.fromString(NotionFixtures.SOURCE);
    private NotionSyncService service;
    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(repo.tryLock(source)).thenReturn(true);
        when(repo.run(any(), eq(source))).thenReturn(Optional.of(new com.personalhub.notion.domain.NotionSyncRun(
                UUID.randomUUID(), source, "SUCCESS", Instant.now(), Instant.now(), 0, null)));
        service = new NotionSyncService(NotionFixtures.properties(3), client,
                new NotionTaskMapper(NotionFixtures.properties(3)), repo, manager);
    }
    @Test
    void incrementalSyncReplaysOverlapAndCommitsCheckpointAfterData() {
        var previous = Instant.parse("2026-11-02T12:00:00Z");
        when(repo.checkpoint(source)).thenReturn(previous);
        when(client.query(previous.minusSeconds(300))).thenReturn(List.of(NotionFixtures.page("Task", previous)));
        service.sync(false);
        verify(client).query(previous.minusSeconds(300));
        var order = inOrder(repo);
        order.verify(repo).tryLock(source);
        order.verify(repo).failInterruptedRuns(eq(source), any(Instant.class));
        order.verify(repo).start(any(), eq(source), any(Instant.class));
        order.verify(repo).checkpoint(source);
        order.verify(repo).saveTasks(argThat(tasks -> tasks.size() == 1 && tasks.getFirst().title().equals("Task")));
        order.verify(repo).checkpoint(eq(source), any(Instant.class));
        order.verify(repo).finish(any(), eq("SUCCESS"), eq(1), isNull());
    }
    @Test
    void upstreamFailureRollsBackAndKeepsCheckpointAndSanitizesErrors() {
        when(client.query(null)).thenThrow(new IllegalStateException("secret-token-should-not-appear"));
        service.sync(false);
        verify(manager).rollback(any());
        verify(repo, never()).saveTasks(any());
        verify(repo, never()).checkpoint(any(), any());
        verify(repo).recordFailure(any(), eq(source), any(), eq("FAILED"),
                eq("Notion sync failed; verify configuration and database"));
    }
    @Test
    void fullSyncIgnoresCheckpoint() {
        when(client.query(null)).thenReturn(List.of());
        service.sync(true);
        verify(repo, never()).checkpoint(source);
        verify(client).query(null);
    }
    @Test
    void conflictingRunDoesNotCallNotion() {
        when(repo.tryLock(source)).thenReturn(false);
        assertThatThrownBy(() -> service.sync(false)).isInstanceOf(NotionSyncConflictException.class);
        verifyNoInteractions(client);
        verify(repo, never()).start(any(), any(), any());
        verify(repo, never()).failInterruptedRuns(any(), any());
        verify(repo).recordFailure(any(), eq(source), any(), eq("CONFLICT"), anyString());
    }

    @Test
    void disabledIntegrationRejectsSyncBeforeOpeningTransaction() {
        var disabled = new com.personalhub.config.NotionProperties(false, "", "", "https://api.notion.com/v1",
                "2025-09-03", "Name", "Status", "Due", 3, 5);
        service = new NotionSyncService(disabled, client, new NotionTaskMapper(disabled), repo, manager);
        assertThatThrownBy(() -> service.sync(false)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(client, repo);
        verify(manager, never()).getTransaction(any());
    }

    @Test
    void startupRecoversOnlyAfterAcquiringSourceLockWithoutCallingNotion() {
        service.recoverInterruptedRuns();
        var order = inOrder(repo);
        order.verify(repo).tryLock(source);
        order.verify(repo).failInterruptedRuns(eq(source), any(Instant.class));
        verifyNoMoreInteractions(repo);
        verifyNoInteractions(client);
        verify(manager).commit(any());
    }

    @Test
    void startupDoesNotModifyRunsWhenSourceLockIsHeld() {
        when(repo.tryLock(source)).thenReturn(false);
        service.recoverInterruptedRuns();
        verify(repo).tryLock(source);
        verifyNoMoreInteractions(repo);
        verifyNoInteractions(client);
    }

    @Test
    void disabledStartupDoesNotOpenTransactionOrAccessDatabase() {
        var disabled = new com.personalhub.config.NotionProperties(false, "", "", "https://api.notion.com/v1",
                "2025-09-03", "Name", "Status", "Due", 3, 5);
        var inactive = new NotionSyncService(disabled, client, new NotionTaskMapper(disabled), repo, manager);
        inactive.recoverInterruptedRuns();
        verifyNoInteractions(repo, client, manager);
    }
}
