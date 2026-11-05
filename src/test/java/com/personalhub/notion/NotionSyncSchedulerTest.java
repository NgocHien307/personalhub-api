package com.personalhub.notion;

import com.personalhub.config.NotionProperties;
import com.personalhub.notion.application.NotionSyncConflictException;
import com.personalhub.notion.application.NotionSyncService;
import com.personalhub.notion.domain.NotionSyncRun;
import com.personalhub.notion.scheduler.NotionSyncScheduler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class NotionSyncSchedulerTest {
    @Test
    void disabledIntegrationDoesNotCallSync() {
        var sync = mock(NotionSyncService.class);
        var disabled = new NotionProperties(false, "", "", "https://api.notion.com/v1",
                "2025-09-03", "Name", "Status", "Due", 3, 5);
        new NotionSyncScheduler(sync, disabled).scheduledSync();
        verifyNoInteractions(sync);
    }

    @Test
    void scheduledSyncUsesIncrementalMode() {
        var sync = mock(NotionSyncService.class);
        when(sync.sync(false)).thenReturn(new NotionSyncRun(UUID.randomUUID(), UUID.fromString(NotionFixtures.SOURCE),
                "SUCCESS", Instant.now(), Instant.now(), 0, null));
        new NotionSyncScheduler(sync, NotionFixtures.properties(3)).scheduledSync();
        verify(sync).sync(false);
    }

    @Test
    void concurrentManualSyncDoesNotBreakScheduledInvocation() {
        var sync = mock(NotionSyncService.class);
        when(sync.sync(false)).thenThrow(new NotionSyncConflictException());
        assertThatCode(() -> new NotionSyncScheduler(sync, NotionFixtures.properties(3)).scheduledSync())
                .doesNotThrowAnyException();
    }
}
