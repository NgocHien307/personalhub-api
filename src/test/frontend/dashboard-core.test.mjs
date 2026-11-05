import test from "node:test";
import assert from "node:assert/strict";
import {
  createApiClient,
  ApiError,
  safeExternalUrl,
  commitUrl,
  formatDate,
  count,
  ago,
  isRunning,
  statusLabel,
} from "../../main/resources/static/dashboard/core.mjs";

const response = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "X-Request-ID": "a0000000-0000-0000-0000-000000000001",
    },
  });

test("credentials are sent only in headers with browser credentials and redirects disabled", async () => {
  let observed;
  const api = createApiClient(async (...args) => {
    observed = args;
    return response({ value: 1 });
  });
  api.connect("browser-test-only-api-key");
  assert.deepEqual(await api.request("/api/v1/dashboard/overview?days=7"), {
    value: 1,
  });
  assert.equal(observed[0], "/api/v1/dashboard/overview?days=7");
  assert.equal(observed[1].headers["X-API-Key"], "browser-test-only-api-key");
  assert.equal(observed[1].credentials, "omit");
  assert.equal(observed[1].redirect, "error");
  assert.equal(observed[1].cache, "no-store");
  api.disconnect();
  assert.equal(api.connected, false);
  await assert.rejects(
    api.request("/api/v1/dashboard/overview"),
    (error) => error.status === 401,
  );
});

test("client refuses external destinations and unsupported methods before sending a key", async () => {
  let calls = 0;
  const api = createApiClient(async () => {
    calls++;
    return response({});
  });
  api.connect("browser-test-only-api-key");
  for (const path of [
    "https://other.example/api/v1/tasks",
    "//other.example/api/v1/tasks",
    "/dashboard/core.mjs",
    "/api/v1/\\evil",
  ]) {
    await assert.rejects(api.request(path), (error) => error.status === 400);
  }
  await assert.rejects(
    api.request("/api/v1/notion/tasks", { method: "DELETE" }),
    (error) => error.status === 400,
  );
  assert.equal(calls, 0);
});

test("401 clears the in-memory key and errors never expose upstream messages", async () => {
  const api = createApiClient(async () =>
    response(
      { detail: "private-upstream-body-and-token", code: "UNAUTHORIZED" },
      401,
    ),
  );
  api.connect("browser-test-only-api-key");
  await assert.rejects(api.request("/api/v1/dashboard/overview"), (error) => {
    assert.equal(error.status, 401);
    assert.equal(error.requestId, "a0000000-0000-0000-0000-000000000001");
    assert.equal(error.message.includes("private-upstream"), false);
    return true;
  });
  assert.equal(api.connected, false);
});

test("disconnect aborts requests and rejects late responses from the previous session", async () => {
  let finish, signal;
  const api = createApiClient((_path, options) => {
    signal = options.signal;
    return new Promise((resolve) => {
      finish = resolve;
    });
  });
  api.connect("first-test-key-value");
  const pending = api.request("/api/v1/notion/tasks");
  api.disconnect();
  api.connect("second-test-key-value");
  assert.equal(signal.aborted, true);
  finish(response([{ title: "private-old-session" }]));
  await assert.rejects(pending, (error) => error.cancelled === true);
});

test("timeout explains uncertainty without retrying a sync POST", async () => {
  let calls = 0;
  const api = createApiClient((_path, options) => {
    calls++;
    return new Promise((_resolve, reject) =>
      options.signal.addEventListener("abort", () =>
        reject(new DOMException("aborted", "AbortError")),
      ),
    );
  });
  api.connect("browser-test-only-api-key");
  await assert.rejects(
    api.request("/api/v1/notion/sync", { method: "POST", timeout: 5 }),
    (error) => error.status === 408,
  );
  assert.equal(calls, 1);
});

test("unexpected HTML and private HTTP errors become safe local messages", async () => {
  const api = createApiClient(
    async () =>
      new Response("<html>private-server-stack-and-token</html>", {
        status: 502,
      }),
  );
  api.connect("browser-test-only-api-key");
  await assert.rejects(
    api.request("/api/v1/notion/sync", { method: "POST" }),
    (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 502);
      assert.equal(error.message.includes("private"), false);
      return true;
    },
  );
});

test("network failures do not expose request details", async () => {
  const api = createApiClient(async () => {
    throw new TypeError("private-url-and-key");
  });
  api.connect("browser-test-only-api-key");
  await assert.rejects(
    api.request("/api/v1/dashboard/overview"),
    (error) => error.status === 0 && !error.message.includes("private"),
  );
});

test("only HTTPS links for the expected source are accepted", () => {
  assert.equal(
    safeExternalUrl("https://github.com/demo/app", "github"),
    "https://github.com/demo/app",
  );
  assert.equal(
    safeExternalUrl("https://workspace.notion.site/page", "notion"),
    "https://workspace.notion.site/page",
  );
  for (const url of [
    "javascript:alert(1)",
    "https://github.com.evil.example/path",
    "http://github.com/app",
    "https://secret@github.com/app",
    "https://github.com:444/app",
    "data:text/html,test",
  ])
    assert.equal(safeExternalUrl(url, "github"), null);
  assert.equal(safeExternalUrl("https://github.com/demo/app", "notion"), null);
});

test("commit URLs cannot inject an external URL or script", () => {
  assert.equal(
    commitUrl({ fullName: "demo/app" }, "abcdef12345"),
    "https://github.com/demo/app/commit/abcdef12345",
  );
  assert.equal(commitUrl({ fullName: "../evil/app" }, "abcdef12345"), null);
  assert.equal(
    commitUrl({ fullName: "demo/app" }, "javascript:alert(1)"),
    null,
  );
});

test("date-only Notion values preserve the original date and datetimes use Vietnam time", () => {
  assert.equal(formatDate("2026-11-04"), "04/11/2026");
  assert.equal(formatDate("2026-11-04T23:00:00Z", true), "06:00 05/11/2026");
  assert.equal(formatDate(null), "Chưa có ngày");
  assert.equal(formatDate("invalid-private-value"), "Chưa có ngày");
});

test("counts and ages have safe fallbacks without implying a new successful sync", () => {
  assert.equal(count(-1), 0);
  assert.equal(count("invalid"), 0);
  assert.equal(count(12), 12);
  assert.equal(ago(null), "Chưa đồng bộ");
  assert.equal(
    ago("2026-11-04T00:00:00Z", Date.parse("2026-11-04T00:02:00Z")),
    "2 phút trước",
  );
  assert.equal(isRunning("RUNNING"), true);
  assert.equal(isRunning("QUEUED"), true);
  assert.equal(isRunning("FAILED"), false);
  assert.equal(statusLabel("FAILED"), "Thất bại");
});
