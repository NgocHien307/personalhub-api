package com.personalhub.notion.repository;

import com.personalhub.notion.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotionRepository {
    private final JdbcTemplate jdbc;
    public NotionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Must be called inside the sync transaction. PostgreSQL releases this on commit/rollback/crash.
    public boolean tryLock(UUID source) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))", Boolean.class, "notion:" + source));
    }

    public Instant checkpoint(UUID source) {
        return jdbc.query("SELECT last_success_at FROM notion_sync_states WHERE data_source_id = ?",
                (rs, n) -> rs.getTimestamp(1).toInstant(), source).stream().findFirst().orElse(null);
    }

    public NotionTaskCounts taskCounts(UUID source) {
        long[] counts = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE NOT archived), count(*) FILTER (WHERE archived)
                FROM notion_tasks WHERE data_source_id = ?
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, source);
        var statuses = jdbc.query("""
                SELECT status, count(*) FROM notion_tasks WHERE data_source_id = ? AND NOT archived
                GROUP BY status ORDER BY status ASC NULLS LAST
                """, (rs, n) -> new NotionTaskCounts.StatusCount(rs.getString(1), rs.getLong(2)), source);
        return new NotionTaskCounts(counts[0], counts[1], statuses);
    }

    public void saveTasks(List<NotionTask> tasks) {
        jdbc.batchUpdate("""
                INSERT INTO notion_tasks(id, data_source_id, title, status, due_start, due_end,
                    due_time_zone, url, last_edited_at, archived, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET data_source_id = EXCLUDED.data_source_id,
                    title = EXCLUDED.title, status = EXCLUDED.status, due_start = EXCLUDED.due_start,
                    due_end = EXCLUDED.due_end, due_time_zone = EXCLUDED.due_time_zone,
                    url = EXCLUDED.url, last_edited_at = EXCLUDED.last_edited_at,
                    archived = EXCLUDED.archived, synced_at = EXCLUDED.synced_at
                WHERE notion_tasks.last_edited_at <= EXCLUDED.last_edited_at
                """, tasks, 100, (ps, t) -> {
            ps.setObject(1, t.id()); ps.setObject(2, t.dataSourceId()); ps.setString(3, t.title());
            ps.setString(4, t.status()); ps.setString(5, t.dueStart()); ps.setString(6, t.dueEnd());
            ps.setString(7, t.dueTimeZone()); ps.setString(8, t.url());
            ps.setTimestamp(9, Timestamp.from(t.lastEditedAt())); ps.setBoolean(10, t.archived());
            ps.setTimestamp(11, Timestamp.from(t.syncedAt()));
        });
    }

    public void checkpoint(UUID source, Instant time) {
        jdbc.update("""
                INSERT INTO notion_sync_states(data_source_id, last_success_at) VALUES (?, ?)
                ON CONFLICT (data_source_id) DO UPDATE SET last_success_at = EXCLUDED.last_success_at
                """, source, Timestamp.from(time));
    }

    public void start(UUID run, UUID source, Instant started) {
        jdbc.update("INSERT INTO notion_sync_runs(id, data_source_id, status, started_at) VALUES (?, ?, 'RUNNING', ?)",
                run, source, Timestamp.from(started));
    }

    // Caller must hold the source advisory lock, so a live worker cannot be marked failed.
    public int failInterruptedRuns(UUID source, Instant finished) {
        return jdbc.update("""
                UPDATE notion_sync_runs SET status = 'FAILED', finished_at = ?,
                    error_message = 'Notion sync interrupted; trigger sync again'
                WHERE data_source_id = ? AND status = 'RUNNING'
                """, Timestamp.from(finished), source);
    }

    public void recordFailure(UUID run, UUID source, Instant started, String status, String error) {
        jdbc.update("""
                INSERT INTO notion_sync_runs(id, data_source_id, status, started_at,
                    finished_at, tasks_processed, error_message)
                VALUES (?, ?, ?, ?, ?, 0, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                    finished_at = EXCLUDED.finished_at, tasks_processed = 0,
                    error_message = EXCLUDED.error_message
                """, run, source, status, Timestamp.from(started), Timestamp.from(Instant.now()), error);
    }

    public void finish(UUID run, String status, int count, String error) {
        jdbc.update("""
                UPDATE notion_sync_runs SET status = ?, tasks_processed = ?, error_message = ?,
                finished_at = ? WHERE id = ?
                """, status, count, error, Timestamp.from(Instant.now()), run);
    }

    public Optional<NotionSyncRun> run(UUID id, UUID source) {
        return jdbc.query("SELECT * FROM notion_sync_runs WHERE id = ? AND data_source_id = ?",
                (rs, n) -> run(rs), id, source).stream().findFirst();
    }

    public List<NotionSyncRun> runs(UUID source, int limit) {
        return jdbc.query("SELECT * FROM notion_sync_runs WHERE data_source_id = ? ORDER BY started_at DESC LIMIT ?",
                (rs, n) -> run(rs), source, limit);
    }

    public List<NotionTask> tasks(UUID source, String status, boolean includeArchived, int page, int size) {
        return jdbc.query("""
                SELECT * FROM notion_tasks WHERE data_source_id = ?
                  AND (CAST(? AS text) IS NULL OR status = ?) AND (? OR NOT archived)
                ORDER BY last_edited_at DESC, id LIMIT ? OFFSET ?
                """, (rs, n) -> task(rs), source, status, status, includeArchived, size, (long) page * size);
    }

    public Optional<NotionTask> task(UUID id, UUID source) {
        return jdbc.query("SELECT * FROM notion_tasks WHERE id = ? AND data_source_id = ?",
                (rs, n) -> task(rs), id, source).stream().findFirst();
    }

    private static NotionTask task(ResultSet rs) throws SQLException {
        return new NotionTask(rs.getObject("id", UUID.class), rs.getObject("data_source_id", UUID.class),
                rs.getString("title"), rs.getString("status"), rs.getString("due_start"), rs.getString("due_end"),
                rs.getString("due_time_zone"), rs.getString("url"), rs.getTimestamp("last_edited_at").toInstant(),
                rs.getBoolean("archived"), rs.getTimestamp("synced_at").toInstant());
    }

    private static NotionSyncRun run(ResultSet rs) throws SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new NotionSyncRun(rs.getObject("id", UUID.class), rs.getObject("data_source_id", UUID.class),
                rs.getString("status"), rs.getTimestamp("started_at").toInstant(),
                finished == null ? null : finished.toInstant(), rs.getInt("tasks_processed"), rs.getString("error_message"));
    }
}
