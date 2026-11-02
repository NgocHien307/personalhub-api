CREATE TABLE github_repositories (
  id BIGINT PRIMARY KEY,
  owner_login VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  full_name VARCHAR(512) NOT NULL,
  html_url VARCHAR(1024) NOT NULL,
  default_branch VARCHAR(255),
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE github_commits (
  repository_id BIGINT NOT NULL REFERENCES github_repositories(id),
  sha VARCHAR(64) NOT NULL,
  author_login VARCHAR(255),
  message TEXT NOT NULL,
  committed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (repository_id, sha)
);
CREATE INDEX idx_github_commits_committed_at ON github_commits(committed_at DESC);
CREATE INDEX idx_github_commits_author_date ON github_commits(author_login, committed_at DESC);

CREATE TABLE sync_states (
  repository_id BIGINT PRIMARY KEY REFERENCES github_repositories(id),
  last_success_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sync_runs (
  id UUID PRIMARY KEY,
  status VARCHAR(20) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  repositories_processed INT NOT NULL DEFAULT 0,
  commits_fetched INT NOT NULL DEFAULT 0,
  error_message TEXT,
  CONSTRAINT chk_sync_status CHECK (status IN ('QUEUED','RUNNING','SUCCESS','FAILED'))
);
CREATE INDEX idx_sync_runs_started_at ON sync_runs(started_at DESC);
