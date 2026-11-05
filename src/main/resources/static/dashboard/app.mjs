import {
  createApiClient,
  ApiError,
  isUuid,
  isRunning,
  count,
  formatCount,
  formatDate,
  ago,
  statusLabel,
  statusTone,
  safeExternalUrl,
  commitUrl,
} from "./core.mjs";

const $ = (id) => document.getElementById(id);
const api = createApiClient();
const pageSize = 20;
const pages = {
  overview: [
    "BỨC TRANH CỦA BẠN",
    "Mỗi ngày, một chút tiến bộ.",
    "Công việc, ý tưởng và những dòng code — cùng ở đây.",
    "Tổng quan",
  ],
  github: [
    "GITHUB",
    "Những điều bạn đang xây dựng.",
    "Repository và commit đã được lưu trong không gian của bạn.",
    "GitHub",
  ],
  notion: [
    "NOTION",
    "Dành chỗ cho những việc quan trọng.",
    "Công việc, ghi chú và ngày tháng từ bảng Notion đã kết nối.",
    "Notion",
  ],
  sync: [
    "GIỮ MỌI THỨ KẾT NỐI",
    "Một nhịp cập nhật mới.",
    "Đồng bộ những thay đổi từ nguồn và theo dõi từng lượt xử lý.",
    "Đồng bộ",
  ],
};
let state = freshState();
let pollTimer = null,
  pollDeadline = 0;
const sequence = {
  overview: 0,
  repositories: 0,
  commits: 0,
  tasks: 0,
  history: 0,
};

function freshState() {
  return {
    overview: null,
    repositories: [],
    commits: [],
    tasks: [],
    histories: { github: [], notion: [] },
    commitsPage: 0,
    tasksPage: 0,
    days: 30,
    posting: { github: false, notion: false },
    githubJob: null,
  };
}
function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined && text !== null) element.textContent = String(text);
  return element;
}
function icon(name) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `/dashboard/icons.svg#${name}`);
  svg.append(use);
  return svg;
}
function badge(status, text) {
  return node(
    "span",
    `badge ${statusTone(status)}`,
    text || statusLabel(status),
  );
}
function announce(message) {
  $("live-status").textContent = message;
}
function notice(
  message,
  { title = "Chưa thể hoàn tất", requestId = null, information = false } = {},
) {
  $("notice-title").textContent = title;
  $("notice-message").textContent = message;
  $("notice-reference").hidden = !requestId;
  $("notice-reference").textContent = requestId
    ? `Mã request: ${requestId}`
    : "";
  $("notice").classList.toggle("information", information);
  $("notice").hidden = false;
}
function handleError(error) {
  if (error?.cancelled) return;
  if (error?.status === 401) disconnect();
  notice(
    error instanceof ApiError
      ? error.message
      : "Dữ liệu chưa tải được. Hãy làm mới và thử lại.",
    { requestId: error?.requestId },
  );
}
function empty(container, title, message, source = "tasks") {
  const block = node(container.tagName === "UL" ? "li" : "div", "empty-state");
  block.append(icon(source), node("strong", "", title), node("p", "", message));
  container.replaceChildren(block);
}
function external(text, url, source, className = "") {
  const safe = safeExternalUrl(url, source);
  const element = node(safe ? "a" : "span", className, text);
  if (safe) {
    element.href = safe;
    element.target = "_blank";
    element.rel = "noopener noreferrer";
  }
  return element;
}
function linkIcon(url, source, label) {
  const safe = safeExternalUrl(url, source);
  if (!safe) return node("span");
  const link = external("", safe, source, "icon-button");
  link.setAttribute("aria-label", label);
  link.append(icon("external"));
  return link;
}
function table(headers) {
  const element = node("table", "data-table");
  const head = node("thead"),
    row = node("tr");
  for (const [text, className] of headers) {
    const th = node("th", className, text);
    th.scope = "col";
    row.append(th);
  }
  head.append(row);
  const body = node("tbody");
  element.append(head, body);
  return [element, body];
}
function switchView() {
  const view =
    location.hash.slice(1) in pages ? location.hash.slice(1) : "overview";
  for (const section of document.querySelectorAll("[data-view]"))
    section.hidden = section.dataset.view !== view;
  for (const link of document.querySelectorAll("[data-nav]")) {
    if (link.dataset.nav === view) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  }
  const [eyebrow, title, description, breadcrumb] = pages[view];
  $("page-eyebrow").textContent = eyebrow;
  $("page-title").textContent = title;
  $("page-description").textContent = description;
  $("breadcrumb-page").textContent = breadcrumb;
  document.title = `PersonalHub · ${breadcrumb}`;
}
function disconnect() {
  api.disconnect();
  clearTimeout(pollTimer);
  pollTimer = null;
  pollDeadline = 0;
  state = freshState();
  for (const key of Object.keys(sequence)) sequence[key]++;
  $("api-key").value = "";
  $("workspace").hidden = true;
  $("connect-panel").hidden = false;
  $("disconnect").hidden = true;
  $("connection-label").textContent = "Chưa kết nối";
  $("connection-dot").classList.remove("connected");
  for (const id of [
    "repository-list",
    "commits-list",
    "recent-commits",
    "tasks-list",
    "status-chart",
    "sources-list",
    "history-list",
    "github-sync-state",
    "notion-sync-state",
  ])
    $(id).replaceChildren();
  for (const id of [
    "metric-repos",
    "metric-commits",
    "metric-days",
    "metric-tasks",
    "chart-total",
  ])
    $(id).textContent = "—";
  $("repo-filter").replaceChildren(node("option", "", "Tất cả repository"));
  $("repo-filter").firstChild.value = "";
  $("status-filter").replaceChildren(node("option", "", "Tất cả trạng thái"));
  $("status-filter").firstChild.value = "";
  $("days").value = "30";
  $("history-source").value = "github";
  $("include-archived").checked = false;
  $("notion-full").checked = false;
  for (const id of [
    "updated-label",
    "live-status",
    "notice-title",
    "notice-message",
    "notice-reference",
    "repo-count",
    "metric-commits-note",
    "metric-tasks-note",
    "chart-note",
  ])
    $(id).textContent = "";
  $("notice").hidden = true;
}
function renderOverview() {
  const data = state.overview;
  if (!data) return;
  $("metric-repos").textContent = formatCount(data.github.trackedRepositories);
  $("metric-commits").textContent = formatCount(data.github.authoredCommits);
  $("metric-days").textContent = formatCount(data.github.activeDaysUtc);
  $("metric-commits-note").textContent =
    `${formatCount(data.github.recordedCommits)} commit được ghi nhận · ${state.days} ngày`;
  $("metric-tasks").textContent = data.notion.enabled
    ? formatCount(data.notion.activeTasks)
    : "—";
  $("metric-tasks-note").textContent = data.notion.enabled
    ? `${formatCount(data.notion.archivedTasks)} công việc đã lưu trữ`
    : "Nguồn Notion chưa được bật";
  $("chart-total").textContent = data.notion.enabled
    ? formatCount(data.notion.activeTasks)
    : "—";
  $("updated-label").textContent =
    `Dữ liệu hiển thị được làm mới: ${formatDate(data.generatedAt, true)}`;
  const chart = $("status-chart");
  chart.replaceChildren();
  if (!data.notion.enabled)
    empty(
      chart,
      "Notion chưa được bật",
      "Bật kết nối Notion trong cấu hình PersonalHub để xem công việc.",
    );
  else if (!data.notion.tasksByStatus.length)
    empty(
      chart,
      "Một khởi đầu mới",
      "Chưa có công việc chưa lưu trữ. Bạn có thể đồng bộ Notion để cập nhật.",
    );
  else
    data.notion.tasksByStatus.forEach((group, index) => {
      const item = node("li", `status-row palette-${index % 5}`),
        label = node("span", "status-label");
      label.append(
        node("i", "status-color"),
        node("span", "", group.status ?? "Chưa có trạng thái"),
      );
      const progress = node("progress");
      progress.max = Math.max(1, count(data.notion.activeTasks));
      progress.value = count(group.count);
      progress.setAttribute("aria-hidden", "true");
      item.append(
        label,
        progress,
        node("strong", "", formatCount(group.count)),
      );
      chart.append(item);
    });
  $("chart-note").textContent = data.notion.enabled
    ? "Theo đúng trạng thái trong bảng Notion · Không gồm công việc đã lưu trữ."
    : "GitHub vẫn hoạt động độc lập khi Notion chưa bật.";
  const sources = $("sources-list");
  sources.replaceChildren();
  for (const source of ["github", "notion"]) {
    const run = data.latestSyncs[source],
      enabled = source === "github" || data.notion.enabled;
    const row = node("div", "source-row"),
      avatar = node(
        "span",
        `source-avatar ${source === "notion" ? "notion-avatar" : ""}`,
      );
    avatar.append(icon(source === "github" ? "code" : "tasks"));
    const info = node("div", "source-info");
    info.append(node("strong", "", source === "github" ? "GitHub" : "Notion"));
    const timestamp =
      source === "notion"
        ? data.notion.lastCheckpointAt
        : run?.finishedAt || run?.startedAt;
    info.append(
      node(
        "p",
        "",
        enabled
          ? `${source === "github" ? data.github.username + " · " : ""}${ago(timestamp)}`
          : "Chưa bật trong cấu hình",
      ),
    );
    row.append(
      avatar,
      info,
      badge(enabled ? run?.status : "", enabled ? undefined : "Chưa bật"),
    );
    sources.append(row);
  }
  updateStatusOptions();
  renderSyncState();
}
function updateStatusOptions() {
  const selected = $("status-filter").value;
  const statuses = new Set(
    (state.overview?.notion.tasksByStatus || [])
      .map((x) => x.status)
      .filter((x) => x != null && x !== ""),
  );
  for (const task of state.tasks) if (task.status) statuses.add(task.status);
  if (selected) statuses.add(selected);
  const options = [node("option", "", "Tất cả trạng thái")];
  options[0].value = "";
  for (const label of [...statuses].sort((a, b) => a.localeCompare(b, "vi"))) {
    const option = node("option", "", label);
    option.value = label;
    options.push(option);
  }
  $("status-filter").replaceChildren(...options);
  $("status-filter").value = selected;
  $("status-filter").disabled = !state.overview?.notion.enabled;
  $("include-archived").disabled = !state.overview?.notion.enabled;
}
function renderRepositories() {
  const container = $("repository-list");
  container.replaceChildren();
  $("repo-count").textContent =
    `${formatCount(state.repositories.length)} repository trong danh sách`;
  if (!state.repositories.length)
    empty(
      container,
      "Chưa có repository",
      "Đồng bộ GitHub để bắt đầu ghi lại những gì bạn đang xây dựng.",
      "code",
    );
  for (const repo of state.repositories) {
    const card = node("article", "repo-card"),
      glyph = icon("branch");
    glyph.classList.add("repo-icon");
    const title = node("h3");
    title.append(external(repo.fullName, repo.htmlUrl, "github"));
    const meta = node("div", "repo-meta");
    meta.append(
      node("span", "", repo.defaultBranch || "Chưa có nhánh"),
      node(
        "span",
        "subtle-tag",
        repo.archived ? "Đã lưu trữ" : "Đang theo dõi",
      ),
    );
    card.append(glyph, title, meta);
    container.append(card);
  }
  const selected = $("repo-filter").value,
    options = [node("option", "", "Tất cả repository")];
  options[0].value = "";
  for (const repo of state.repositories) {
    const option = node("option", "", repo.fullName);
    option.value = String(repo.id);
    options.push(option);
  }
  $("repo-filter").replaceChildren(...options);
  $("repo-filter").value = selected;
}
function renderCommits(container, commits, compact = false) {
  if (!commits.length) {
    empty(
      container,
      "Chưa có commit để hiển thị",
      "Đồng bộ GitHub hoặc chọn một repository khác để xem hoạt động.",
      "code",
    );
    return;
  }
  const [element, body] = table([
    ["Commit", "title-cell"],
    ["Repository", "hide-mobile"],
    ["Ngày ghi nhận", "date-cell"],
  ]);
  for (const commit of commits) {
    const repo = state.repositories.find((x) => x.id === commit.repositoryId),
      row = node("tr"),
      titleCell = node("td");
    const firstLine =
      String(commit.message || "").split(/\r?\n/)[0] ||
      "Commit không có lời nhắn";
    const url = commitUrl(repo, commit.sha);
    titleCell.append(external(firstLine, url, "github", "row-title"));
    const meta = node("span", "row-meta");
    meta.append(
      node("code", "", String(commit.sha || "").slice(0, 7)),
      node("span", "", commit.authorLogin || "Chưa gắn tài khoản"),
    );
    titleCell.append(meta);
    row.append(
      titleCell,
      node(
        "td",
        "secondary-cell hide-mobile",
        repo?.fullName || "Repository khác",
      ),
      node("td", "secondary-cell", formatDate(commit.committedAt)),
    );
    body.append(row);
  }
  container.replaceChildren(element);
  if (!compact) {
    $("commits-page").textContent = `Trang ${state.commitsPage + 1}`;
    $("commits-prev").disabled = state.commitsPage === 0;
    $("commits-next").disabled = commits.length < pageSize;
  }
}
function renderTasks() {
  const container = $("tasks-list");
  if (!state.overview?.notion.enabled)
    empty(
      container,
      "Notion chưa được bật",
      "Kết nối Notion trong cấu hình PersonalHub để xem bảng công việc.",
    );
  else if (!state.tasks.length)
    empty(
      container,
      "Chưa có công việc ở trang này",
      "Thử đổi trạng thái, trở về trang trước hoặc đồng bộ Notion để cập nhật.",
    );
  else {
    const [element, body] = table([
      ["Công việc", "title-cell"],
      ["Trạng thái", "hide-mobile"],
      ["Ngày", "date-cell"],
      ["", "action-cell"],
    ]);
    for (const task of state.tasks) {
      const row = node("tr"),
        titleCell = node("td");
      titleCell.append(
        external(
          task.title || "Chưa có tiêu đề",
          task.url,
          "notion",
          "row-title",
        ),
      );
      const meta = node("span", "row-meta");
      meta.append(node("span", "", task.status ?? "Chưa có trạng thái"));
      if (task.archived)
        meta.append(node("span", "badge archived", "Đã lưu trữ"));
      titleCell.append(meta);
      const statusCell = node("td", "hide-mobile");
      statusCell.append(
        node(
          "span",
          "badge neutral status-badge",
          task.status ?? "Chưa có trạng thái",
        ),
      );
      const action = node("td", "action-cell");
      action.append(
        linkIcon(
          task.url,
          "notion",
          `Mở công việc trong Notion: ${task.title || "Chưa có tiêu đề"}`,
        ),
      );
      row.append(
        titleCell,
        statusCell,
        node("td", "secondary-cell", formatDate(task.dueStart)),
        action,
      );
      body.append(row);
    }
    container.replaceChildren(element);
  }
  $("tasks-page").textContent = `Trang ${state.tasksPage + 1}`;
  $("tasks-prev").disabled =
    state.tasksPage === 0 || !state.overview?.notion.enabled;
  $("tasks-next").disabled =
    state.tasks.length < pageSize || !state.overview?.notion.enabled;
  updateStatusOptions();
}
function renderSyncState() {
  if (!state.overview) return;
  for (const source of ["github", "notion"]) {
    const enabled = source === "github" || state.overview.notion.enabled,
      run = state.overview.latestSyncs[source];
    const container = $(`${source}-sync-state`);
    container.replaceChildren(
      badge(enabled ? run?.status : "", enabled ? undefined : "Chưa bật"),
    );
    if (run?.finishedAt)
      container.append(
        node("p", "", `Lượt gần nhất: ${formatDate(run.finishedAt, true)}`),
      );
    const busy = state.posting[source] || isRunning(run?.status);
    $(`sync-${source}`).disabled = !enabled || busy;
    $(`sync-${source}`).textContent = busy
      ? "Đang đồng bộ…"
      : `Đồng bộ ${source === "github" ? "GitHub" : "Notion"}`;
    if (source === "notion") $("notion-full").disabled = !enabled || busy;
  }
}
function renderHistory() {
  const source = $("history-source").value,
    container = $("history-list");
  if (source === "notion" && !state.overview?.notion.enabled) {
    empty(
      container,
      "Notion chưa được bật",
      "Lịch sử sẽ xuất hiện khi nguồn được kết nối và đồng bộ.",
    );
    return;
  }
  const rows = state.histories[source];
  if (!rows.length) {
    empty(
      container,
      "Chưa có lượt đồng bộ",
      "Bắt đầu đồng bộ một nguồn ở phía trên để xem kết quả tại đây.",
      "sync",
    );
    return;
  }
  container.replaceChildren();
  for (const run of rows) {
    const item = node("article", "history-row"),
      title = node("div");
    title.append(badge(run.status), node("code", "history-id", run.id));
    const time = node("div", "history-info");
    time.append(
      node("span", "", "Bắt đầu"),
      node("p", "", formatDate(run.startedAt, true)),
    );
    const totals = node("div", "history-info");
    totals.append(node("span", "", "Đã xử lý"));
    totals.append(
      node(
        "p",
        "",
        source === "github"
          ? `${formatCount(run.repositoriesProcessed)} repo · ${formatCount(run.commitsFetched)} commit`
          : `${formatCount(run.tasksProcessed)} công việc`,
      ),
    );
    item.append(title, time, totals);
    if (run.status === "FAILED")
      item.append(
        node(
          "p",
          "history-error",
          "Lượt đồng bộ chưa hoàn tất. Kiểm tra kết nối nguồn hoặc thử lại sau. Giữ mã lượt ở trên để đối chiếu.",
        ),
      );
    container.append(item);
  }
}
async function loadOverview() {
  const seq = ++sequence.overview;
  const data = await api.request(
    `/api/v1/dashboard/overview?days=${state.days}`,
  );
  if (seq !== sequence.overview) return;
  if (!data?.github || !data?.notion || !data?.latestSyncs)
    throw new ApiError(500);
  state.overview = data;
  renderOverview();
}
async function loadRepositories() {
  const seq = ++sequence.repositories;
  const rows = await api.request("/api/v1/github/repositories?limit=100");
  if (seq !== sequence.repositories) return;
  state.repositories = Array.isArray(rows) ? rows : [];
  renderRepositories();
}
async function loadCommits() {
  const seq = ++sequence.commits;
  const query = new URLSearchParams({
    page: String(state.commitsPage),
    size: String(pageSize),
  });
  if ($("repo-filter").value) query.set("repoId", $("repo-filter").value);
  const rows = await api.request(`/api/v1/github/commits?${query}`);
  if (seq !== sequence.commits) return;
  state.commits = Array.isArray(rows) ? rows : [];
  renderCommits($("commits-list"), state.commits);
  if (!$("repo-filter").value && state.commitsPage === 0)
    renderCommits($("recent-commits"), state.commits.slice(0, 5), true);
  if (!state.commits.length) {
    $("commits-page").textContent = `Trang ${state.commitsPage + 1}`;
    $("commits-prev").disabled = state.commitsPage === 0;
    $("commits-next").disabled = true;
  }
}
async function loadTasks() {
  const seq = ++sequence.tasks;
  if (!state.overview?.notion.enabled) {
    state.tasks = [];
    renderTasks();
    return;
  }
  const query = new URLSearchParams({
    page: String(state.tasksPage),
    size: String(pageSize),
    includeArchived: String($("include-archived").checked),
  });
  if ($("status-filter").value) query.set("status", $("status-filter").value);
  const rows = await api.request(`/api/v1/notion/tasks?${query}`);
  if (seq !== sequence.tasks) return;
  state.tasks = Array.isArray(rows) ? rows : [];
  renderTasks();
}
async function loadHistory() {
  const seq = ++sequence.history;
  const requests = [["github", "/api/v1/sync/runs?limit=20"]];
  if (state.overview?.notion.enabled)
    requests.push(["notion", "/api/v1/notion/sync-runs?limit=20"]);
  const results = await Promise.allSettled(
    requests.map(async ([source, url]) => [source, await api.request(url)]),
  );
  if (seq !== sequence.history) return;
  for (const result of results) {
    if (result.status === "fulfilled")
      state.histories[result.value[0]] = Array.isArray(result.value[1])
        ? result.value[1]
        : [];
    else handleError(result.reason);
  }
  if (api.connected) renderHistory();
}
async function refresh() {
  if (!api.connected) return;
  const generation = api.generation;
  $("refresh-button").disabled = true;
  $("updated-label").textContent = "Đang tải dữ liệu…";
  $("updated-label").classList.add("loading");
  try {
    await loadOverview();
    await loadRepositories();
    const results = await Promise.allSettled([
      loadCommits(),
      loadTasks(),
      loadHistory(),
    ]);
    if (generation !== api.generation) return;
    for (const result of results)
      if (result.status === "rejected") handleError(result.reason);
    announce("Đã làm mới dữ liệu hiển thị.");
  } catch (error) {
    handleError(error);
  } finally {
    if (generation === api.generation) {
      $("refresh-button").disabled = false;
      $("updated-label").classList.remove("loading");
    }
  }
}
async function pageLoad(loader) {
  try {
    await loader();
  } catch (error) {
    handleError(error);
  }
}
function scheduleGithubPoll(runId) {
  clearTimeout(pollTimer);
  if (!isUuid(runId) || !api.connected) return;
  if (Date.now() > pollDeadline) {
    notice(
      "Lượt GitHub vẫn đang xử lý. Dùng Làm mới để kiểm tra tiến độ trong lịch sử.",
      { title: "Đang tiếp tục đồng bộ", information: true },
    );
    return;
  }
  pollTimer = setTimeout(async () => {
    try {
      const run = await api.request(`/api/v1/sync/runs/${runId}`);
      state.overview.latestSyncs.github = run;
      renderSyncState();
      if (isRunning(run.status)) scheduleGithubPoll(runId);
      else {
        state.githubJob = null;
        notice(
          run.status === "SUCCESS"
            ? "Repository và commit đã được cập nhật trong không gian của bạn."
            : "Lượt GitHub chưa hoàn tất. Xem lịch sử để đối chiếu mã lượt.",
          {
            title:
              run.status === "SUCCESS"
                ? "GitHub đã đồng bộ"
                : "Cần kiểm tra GitHub",
            information: run.status === "SUCCESS",
          },
        );
        await refresh();
      }
    } catch (error) {
      handleError(error);
    }
  }, 2000);
}
async function syncSource(source) {
  if (!api.connected || state.posting[source]) return;
  const generation = api.generation;
  state.posting[source] = true;
  renderSyncState();
  $("notice").hidden = true;
  try {
    if (source === "github") {
      const run = await api.request("/api/v1/sync/github", { method: "POST" });
      state.overview.latestSyncs.github = run;
      if (isRunning(run.status)) {
        state.githubJob = run.id;
        pollDeadline = Date.now() + 240_000;
        scheduleGithubPoll(run.id);
        announce("GitHub đang đồng bộ.");
      } else {
        notice(
          run.status === "SUCCESS"
            ? "Dữ liệu GitHub đã được cập nhật."
            : "Xem lịch sử để kiểm tra lượt GitHub vừa xử lý.",
          {
            title: "Kết quả đồng bộ GitHub",
            information: run.status === "SUCCESS",
          },
        );
        await refresh();
      }
    } else {
      const run = await api.request(
        `/api/v1/notion/sync?full=${$("notion-full").checked}`,
        { method: "POST", timeout: 120_000 },
      );
      notice(
        `Đã xử lý ${formatCount(run.tasksProcessed)} công việc từ bảng Notion.`,
        { title: "Notion đã đồng bộ", information: true },
      );
      await refresh();
    }
  } catch (error) {
    handleError(error);
    if (api.connected && generation === api.generation) await refresh();
  } finally {
    if (generation === api.generation) {
      state.posting[source] = false;
      if (api.connected) renderSyncState();
    }
  }
}

$("connect-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("notice").hidden = true;
  $("connect-button").disabled = true;
  $("connect-button").textContent = "Đang kết nối…";
  api.connect($("api-key").value);
  $("api-key").value = "";
  try {
    await loadOverview();
    $("connect-panel").hidden = true;
    $("workspace").hidden = false;
    $("disconnect").hidden = false;
    $("connection-label").textContent = "Đã kết nối";
    $("connection-dot").classList.add("connected");
    await refresh();
    announce("Đã kết nối không gian PersonalHub.");
    $("page-title").focus();
  } catch (error) {
    disconnect();
    handleError(error);
  } finally {
    $("connect-button").disabled = false;
    $("connect-button").textContent = "Kết nối không gian";
  }
});
$("disconnect").addEventListener("click", () => {
  disconnect();
  $("api-key").focus();
  announce("Đã ngắt kết nối và xóa dữ liệu khỏi tab.");
});
$("dismiss-notice").addEventListener("click", () => {
  $("notice").hidden = true;
});
$("refresh-button").addEventListener("click", refresh);
$("days").addEventListener("change", () => {
  state.days = Number($("days").value);
  pageLoad(loadOverview);
});
$("repo-filter").addEventListener("change", () => {
  state.commitsPage = 0;
  pageLoad(loadCommits);
});
$("status-filter").addEventListener("change", () => {
  state.tasksPage = 0;
  pageLoad(loadTasks);
});
$("include-archived").addEventListener("change", () => {
  state.tasksPage = 0;
  pageLoad(loadTasks);
});
for (const [id, resource, delta, loader] of [
  ["commits-prev", "commitsPage", -1, loadCommits],
  ["commits-next", "commitsPage", 1, loadCommits],
  ["tasks-prev", "tasksPage", -1, loadTasks],
  ["tasks-next", "tasksPage", 1, loadTasks],
]) {
  $(id).addEventListener("click", () => {
    state[resource] = Math.max(0, state[resource] + delta);
    pageLoad(loader);
  });
}
$("history-source").addEventListener("change", renderHistory);
$("sync-github").addEventListener("click", () => syncSource("github"));
$("sync-notion").addEventListener("click", () => syncSource("notion"));
window.addEventListener("hashchange", () => {
  switchView();
  window.scrollTo(0, 0);
  if (api.connected) $("page-title").focus({ preventScroll: true });
});
window.addEventListener("pagehide", disconnect);
switchView();
