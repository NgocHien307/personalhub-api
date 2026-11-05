// Isolated UI fixture. Never reads .env or forwards requests to the real PersonalHub API.
import http from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { randomUUID } from "node:crypto";

const root = new URL("../../main/resources/static/dashboard/", import.meta.url);
const port = Number(process.env.DASHBOARD_TEST_PORT || 18080);
if (!Number.isInteger(port) || port < 1024 || port > 65535)
  throw new Error("Invalid fixture port");
const key = "dashboard-test-only-key";
const csp =
  "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
const source = "30000000-0000-0000-0000-000000000003";
let scenario = "normal",
  posts = [],
  calls = [],
  job = null,
  polls = 0,
  tasks,
  commits,
  repositories,
  runs;
function reset(mode = "normal") {
  scenario = mode;
  posts = [];
  calls = [];
  job = null;
  polls = 0;
  repositories = ["personalhub", "reading-notes", "tiny-garden"].map(
    (name, index) => ({
      id: index + 1,
      fullName: `demo-test/${name}`,
      htmlUrl: `https://github.com/demo-test/${name}`,
      defaultBranch: "main",
      archived: false,
    }),
  );
  commits = Array.from({ length: 21 }, (_, index) => ({
    repositoryId: (index % 3) + 1,
    sha: (index + 10).toString(16).padStart(40, "a"),
    authorLogin: "demo-test",
    message: [
      "Hoàn thiện giao diện tổng quan",
      "Thêm bộ lọc công việc Notion",
      "Cập nhật tài liệu cấu hình",
      "Cải thiện trải nghiệm mobile",
      "Bổ sung kiểm tra đồng bộ",
    ][index % 5],
    committedAt: `2026-11-${String(4 - (index % 3)).padStart(2, "0")}T08:00:00Z`,
  }));
  const titles = [
    "Hoàn thiện dashboard cá nhân",
    "Lên kế hoạch cho tuần mới",
    "Đọc chương sách tiếp theo",
    "Ghi chú buổi học Java",
    "Rà soát mục tiêu tháng 11",
    "Dọn dẹp không gian làm việc",
    "Viết lại ghi chú dự án",
    "Ôn tập kiến thức PostgreSQL",
    "Lưu ý tưởng cho cuối tuần",
    "Chuẩn bị buổi học tiếp theo",
    "Cập nhật nhật ký học tập",
    "Dành thời gian cho dự án nhỏ",
  ];
  tasks = Array.from({ length: 14 }, (_, index) => ({
    id: `20000000-0000-0000-0000-${String(index + 1).padStart(12, "0")}`,
    dataSourceId: source,
    title: titles[index % titles.length],
    status:
      index >= 12
        ? "Done"
        : index < 4
          ? "In progress"
          : index < 10
            ? "Done"
            : "To do",
    dueStart: index % 3 === 0 ? null : "2026-11-04",
    dueEnd: null,
    dueTimeZone: null,
    url: `https://www.notion.so/${index + 1}`,
    archived: index >= 12,
    lastEditedAt: "2026-11-04T08:00:00Z",
    syncedAt: "2026-11-04T08:01:00Z",
  }));
  const run = {
    id: randomUUID(),
    status: "SUCCESS",
    startedAt: "2026-11-04T08:00:00Z",
    finishedAt: "2026-11-04T08:00:03Z",
    errorMessage: null,
  };
  runs = {
    github: [{ ...run, repositoriesProcessed: 3, commitsFetched: 2 }],
    notion: [
      { ...run, id: randomUUID(), dataSourceId: source, tasksProcessed: 14 },
    ],
  };
  if (mode === "empty") {
    tasks = [];
    commits = [];
    repositories = [];
    runs = { github: [], notion: [] };
  }
  if (mode === "unsafe") {
    tasks[0].title = '<img src=x onerror="window.__xss=true">';
    tasks[0].url = "javascript:alert(1)";
    repositories[0].fullName = "<script>unsafe</script>";
    repositories[0].htmlUrl = "https://github.com.evil.example/";
  }
}
reset();
function overview(days) {
  const enabled = scenario !== "disabled",
    active = tasks.filter((x) => !x.archived),
    statuses = new Map();
  for (const task of active)
    statuses.set(task.status, (statuses.get(task.status) || 0) + 1);
  return {
    generatedAt: new Date().toISOString(),
    days,
    github: {
      username: "demo-test",
      since: "2026-10-05T00:00:00Z",
      trackedRepositories: repositories.length,
      recordedCommits: commits.length
        ? 27 + (job?.status === "SUCCESS" ? 1 : 0)
        : 0,
      authoredCommits: commits.length
        ? 24 + (job?.status === "SUCCESS" ? 1 : 0)
        : 0,
      activeDaysUtc: commits.length ? 8 : 0,
    },
    notion: {
      enabled,
      activeTasks: enabled ? active.length : 0,
      archivedTasks: enabled ? tasks.length - active.length : 0,
      tasksByStatus: enabled
        ? [...statuses].map(([status, count]) => ({ status, count }))
        : [],
      lastCheckpointAt: enabled && tasks.length ? "2026-11-04T08:00:00Z" : null,
    },
    latestSyncs: {
      github: job || runs.github[0] || null,
      notion: enabled ? runs.notion[0] || null : null,
    },
  };
}
function json(response, body, status = 200) {
  response.writeHead(status, {
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
    "X-Request-ID": "a0000000-0000-0000-0000-000000000001",
  });
  response.end(JSON.stringify(body));
}
const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${port}`);
  if (url.pathname === "/__test/reset" && request.method === "POST") {
    reset(url.searchParams.get("scenario") || "normal");
    return json(response, { scenario });
  }
  if (url.pathname === "/__test/observations")
    return json(response, { scenario, calls, posts });
  if (url.pathname.startsWith("/api/v1/")) {
    calls.push({
      method: request.method,
      path: url.pathname,
      query: url.search,
    });
    if (request.headers["x-api-key"] !== key)
      return json(response, { code: "UNAUTHORIZED" }, 401);
    if (request.method === "POST") posts.push(url.pathname + url.search);
    if (url.pathname === "/api/v1/dashboard/overview")
      return json(
        response,
        overview(Number(url.searchParams.get("days") || 30)),
      );
    if (url.pathname === "/api/v1/github/repositories")
      return json(response, repositories);
    if (url.pathname === "/api/v1/github/commits") {
      const filtered = url.searchParams.has("repoId")
        ? commits.filter(
            (x) => x.repositoryId === Number(url.searchParams.get("repoId")),
          )
        : commits;
      const start =
        Number(url.searchParams.get("page") || 0) *
        Number(url.searchParams.get("size") || 20);
      return json(
        response,
        filtered.slice(
          start,
          start + Number(url.searchParams.get("size") || 20),
        ),
      );
    }
    if (url.pathname === "/api/v1/notion/tasks") {
      if (scenario === "disabled")
        return json(response, { code: "INTEGRATION_UNAVAILABLE" }, 503);
      const filtered = tasks.filter(
        (x) =>
          (url.searchParams.get("includeArchived") === "true" || !x.archived) &&
          (!url.searchParams.get("status") ||
            x.status === url.searchParams.get("status")),
      );
      const start = Number(url.searchParams.get("page") || 0) * 20;
      return json(response, filtered.slice(start, start + 20));
    }
    if (url.pathname === "/api/v1/sync/runs")
      return json(response, job ? [job, ...runs.github] : runs.github);
    if (url.pathname === "/api/v1/notion/sync-runs")
      return json(response, runs.notion);
    if (url.pathname === "/api/v1/sync/github" && request.method === "POST") {
      job = {
        id: randomUUID(),
        status: "QUEUED",
        startedAt: new Date().toISOString(),
        finishedAt: null,
        repositoriesProcessed: 0,
        commitsFetched: 0,
        errorMessage: null,
      };
      return json(response, job, 202);
    }
    if (url.pathname.startsWith("/api/v1/sync/runs/") && job) {
      polls++;
      job.status = polls < 2 ? "RUNNING" : "SUCCESS";
      if (job.status === "SUCCESS")
        Object.assign(job, {
          finishedAt: new Date().toISOString(),
          repositoriesProcessed: 3,
          commitsFetched: 1,
        });
      return json(response, job);
    }
    if (url.pathname === "/api/v1/notion/sync" && request.method === "POST") {
      const failed = url.searchParams.get("full") === "true";
      const run = {
        id: randomUUID(),
        dataSourceId: source,
        status: failed ? "FAILED" : "SUCCESS",
        startedAt: new Date().toISOString(),
        finishedAt: new Date().toISOString(),
        tasksProcessed: failed ? 0 : tasks.length,
        errorMessage: failed ? "private-upstream-response" : null,
      };
      runs.notion.unshift(run);
      return json(
        response,
        failed ? { code: "UPSTREAM_ERROR", syncRunId: run.id } : run,
        failed ? 502 : 200,
      );
    }
    return json(response, { code: "NOT_FOUND" }, 404);
  }
  const assets = {
    "/dashboard": ["index.html", "text/html"],
    "/dashboard/": ["index.html", "text/html"],
    "/dashboard/index.html": ["index.html", "text/html"],
    "/dashboard/dashboard.css": ["dashboard.css", "text/css"],
    "/dashboard/app.mjs": ["app.mjs", "text/javascript"],
    "/dashboard/core.mjs": ["core.mjs", "text/javascript"],
    "/dashboard/icons.svg": ["icons.svg", "image/svg+xml"],
  };
  const asset = assets[url.pathname];
  if (!asset) {
    response.writeHead(404);
    return response.end();
  }
  try {
    const data = await readFile(new URL(asset[0], root));
    response.writeHead(200, {
      "Content-Type": asset[1],
      "Content-Security-Policy": csp,
      "Cache-Control": "no-store",
    });
    response.end(data);
  } catch {
    response.writeHead(500);
    response.end();
  }
});
server.listen(port, "127.0.0.1", () =>
  console.log(
    `UI fixture ready at http://127.0.0.1:${port}/dashboard (synthetic data only)`,
  ),
);
