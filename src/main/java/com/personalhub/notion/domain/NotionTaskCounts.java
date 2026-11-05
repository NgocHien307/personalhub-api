package com.personalhub.notion.domain;

import java.util.List;

public record NotionTaskCounts(long activeTasks, long archivedTasks, List<StatusCount> tasksByStatus) {
    public record StatusCount(String status, long count) {}
}
