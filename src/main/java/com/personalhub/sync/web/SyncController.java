package com.personalhub.sync.web;

import com.personalhub.sync.application.GitHubSyncCoordinator;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final GitHubSyncCoordinator coordinator;
    private final SyncRunJpaRepository runs;

    public SyncController(GitHubSyncCoordinator coordinator, SyncRunJpaRepository runs) {
        this.coordinator = coordinator;
        this.runs = runs;
    }

    @PostMapping("/github")
    public ResponseEntity<SyncRunResponse> syncGithub() {
        UUID id = coordinator.requestSync();
        SyncRunResponse response = SyncRunResponse.from(runs.findById(id).orElseThrow());
        return ResponseEntity.accepted().location(URI.create("/api/v1/sync/runs/" + id)).body(response);
    }

    @GetMapping("/runs/{id}")
    public SyncRunResponse run(@PathVariable UUID id) {
        return runs.findById(id).map(SyncRunResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync run not found"));
    }

    @GetMapping("/runs")
    public List<SyncRunResponse> runs(@RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1..100");
        }
        return runs.findAll(PageRequest.of(0, limit,
                        Sort.by(Sort.Direction.DESC, "startedAt")))
                .stream().map(SyncRunResponse::from).toList();
    }
}
