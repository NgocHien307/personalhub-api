package com.personalhub.github.web;

import com.personalhub.github.application.GitHubQueryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/github")
public class GitHubController {
    private final GitHubQueryService queryService;
    public GitHubController(GitHubQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/repositories")
    public List<GitHubRepositoryResponse> repositories(@RequestParam(defaultValue = "20") int limit) {
        checkRange("limit", limit, 1, 100);
        return queryService.repositories(limit);
    }

    @GetMapping("/commits")
    public List<GitHubCommitResponse> commits(@RequestParam(required = false) Long repoId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        checkRange("page", page, 0, 100000);
        checkRange("size", size, 1, 100);
        return queryService.commits(repoId, page, size);
    }

    @GetMapping("/stats")
    public GitHubStatsResponse stats(@RequestParam(defaultValue = "30") int days) {
        checkRange("days", days, 1, 365);
        return queryService.stats(days);
    }

    private static void checkRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    name + " must be in range " + min + ".." + max);
        }
    }
}
