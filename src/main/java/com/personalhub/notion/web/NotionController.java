package com.personalhub.notion.web;

import com.personalhub.notion.application.NotionSyncService;
import com.personalhub.notion.application.NotionSyncFailedException;
import com.personalhub.notion.domain.*;
import com.personalhub.notion.repository.NotionRepository;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/notion")
public class NotionController {
    private final NotionRepository repository;
    private final NotionSyncService sync;
    public NotionController(NotionRepository repository, NotionSyncService sync) {
        this.repository = repository; this.sync = sync;
    }

    @GetMapping("/tasks")
    public List<NotionTask> tasks(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        range(page, 0, 100000); range(size, 1, 100);
        return repository.tasks(sync.source(), status, includeArchived, page, size);
    }

    @GetMapping("/tasks/{id}")
    public NotionTask task(@PathVariable UUID id) {
        return repository.task(id, sync.source()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/sync")
    public ResponseEntity<NotionSyncRun> sync(@RequestParam(defaultValue = "false") boolean full) {
        var run = sync.sync(full);
        if (!"SUCCESS".equals(run.status())) throw new NotionSyncFailedException(run.id());
        return ResponseEntity.ok(run);
    }

    @GetMapping("/sync-runs")
    public List<NotionSyncRun> runs(@RequestParam(defaultValue = "20") int limit) {
        range(limit, 1, 100);
        return repository.runs(sync.source(), limit);
    }

    @GetMapping("/sync-runs/{id}")
    public NotionSyncRun run(@PathVariable UUID id) {
        return repository.run(id, sync.source()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void range(int value, int min, int max) {
        if (value < min || value > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagination out of range");
    }
}
