package com.personalhub.github.infrastructure;

import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import java.time.Instant;
import java.util.List;

public interface GitHubApiClient {
    List<GitHubRepoDto> repositories();
    List<GitHubCommitDto> commits(String owner, String repository, Instant since);
}
