export const isUuid = (value) =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export const isRunning = (status) =>
  status === "RUNNING" || status === "QUEUED";
export const count = (value) =>
  Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value) : 0;
export const formatCount = (value) =>
  new Intl.NumberFormat("vi-VN").format(count(value));
export function statusLabel(status) {
  return (
    {
      SUCCESS: "Thành công",
      FAILED: "Thất bại",
      RUNNING: "Đang chạy",
      QUEUED: "Đang chờ",
      CONFLICT: "Đang bận",
    }[status] || "Chưa đồng bộ"
  );
}
export function statusTone(status) {
  return status === "SUCCESS"
    ? "success"
    : status === "FAILED"
      ? "failed"
      : isRunning(status) || status === "CONFLICT"
        ? "pending"
        : "neutral";
}
export function formatDate(value, withTime = false) {
  if (!value) return "Chưa có ngày";
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value);
  const date = new Date(dateOnly ? `${value}T00:00:00Z` : value);
  if (!Number.isFinite(date.getTime())) return "Chưa có ngày";
  const options = {
    timeZone: dateOnly ? "UTC" : "Asia/Ho_Chi_Minh",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  };
  if (withTime && !dateOnly)
    Object.assign(options, {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  return new Intl.DateTimeFormat("vi-VN", options).format(date);
}
export function ago(value, now = Date.now()) {
  const elapsed = now - new Date(value).getTime();
  if (!value || !Number.isFinite(elapsed)) return "Chưa đồng bộ";
  if (elapsed < 60_000) return "Vừa xong";
  if (elapsed < 3_600_000) return `${Math.floor(elapsed / 60_000)} phút trước`;
  if (elapsed < 86_400_000)
    return `${Math.floor(elapsed / 3_600_000)} giờ trước`;
  return formatDate(value);
}
export function safeExternalUrl(value, source) {
  try {
    const url = new URL(value);
    const allowed =
      source === "github"
        ? url.hostname === "github.com"
        : ["notion.so", "www.notion.so", "notion.site"].includes(
            url.hostname,
          ) || url.hostname.endsWith(".notion.site");
    return allowed &&
      url.protocol === "https:" &&
      !url.username &&
      !url.password &&
      (!url.port || url.port === "443")
      ? url.href
      : null;
  } catch {
    return null;
  }
}
export function commitUrl(repository, sha) {
  if (
    !repository ||
    !/^[\w.-]+\/[\w.-]+$/.test(repository.fullName) ||
    !/^[0-9a-f]{7,64}$/i.test(sha)
  )
    return null;
  return `https://github.com/${repository.fullName.split("/").map(encodeURIComponent).join("/")}/commit/${sha}`;
}
export class ApiError extends Error {
  constructor(status, requestId = null, code = "", cancelled = false) {
    const messages = {
      401: "Khóa truy cập chưa đúng hoặc đã thay đổi. Hãy kết nối lại.",
      403: "Bạn chưa có quyền thực hiện thao tác này.",
      409: "Đã có một lượt đồng bộ đang chạy. Chờ một chút rồi làm mới trạng thái.",
      502: "Nguồn dữ liệu chưa phản hồi thành công. Kiểm tra lịch sử đồng bộ và thử lại sau.",
      503: "Nguồn dữ liệu chưa được bật hoặc hiện chưa sẵn sàng.",
      408: "Đã hết thời gian chờ. Nếu vừa đồng bộ, lượt xử lý có thể vẫn chạy; hãy làm mới để xem lịch sử.",
    };
    super(
      cancelled
        ? "Đã hủy yêu cầu."
        : messages[status] ||
            (status === 0
              ? "Chưa thể kết nối đến PersonalHub. Kiểm tra ứng dụng rồi thử lại."
              : "Chưa thể hoàn tất yêu cầu. Hãy thử lại sau."),
    );
    this.status = status;
    this.requestId = isUuid(requestId) ? requestId : null;
    this.code = code;
    this.cancelled = cancelled;
  }
}

// A key lives only in this closure. No browser storage, cookie, URL or logging is used.
export function createApiClient(fetchImpl = globalThis.fetch.bind(globalThis)) {
  let key = "",
    generation = 0;
  const pending = new Set();
  function disconnect() {
    key = "";
    generation++;
    for (const controller of pending) controller.abort();
    pending.clear();
  }
  return {
    get connected() {
      return Boolean(key);
    },
    get generation() {
      return generation;
    },
    connect(value) {
      disconnect();
      key = String(value || "").trim();
    },
    disconnect,
    async request(path, { method = "GET", timeout = 20_000 } = {}) {
      if (!key) throw new ApiError(401);
      if (
        !path.startsWith("/api/v1/") ||
        path.includes("\\") ||
        !["GET", "POST"].includes(method)
      )
        throw new ApiError(400);
      const current = generation,
        controller = new AbortController();
      pending.add(controller);
      let timedOut = false;
      const timer = setTimeout(() => {
        timedOut = true;
        controller.abort();
      }, timeout);
      try {
        const response = await fetchImpl(path, {
          method,
          credentials: "omit",
          cache: "no-store",
          redirect: "error",
          headers: { "X-API-Key": key, Accept: "application/json" },
          signal: controller.signal,
        });
        let body = null;
        try {
          body = await response.json();
        } catch {
          /* Never display an upstream HTML body. */
        }
        if (current !== generation) throw new ApiError(0, null, "", true);
        if (!response.ok) {
          const error = new ApiError(
            response.status,
            response.headers.get("X-Request-ID"),
            body?.code || "",
          );
          if (response.status === 401) disconnect();
          throw error;
        }
        if (body === null)
          throw new ApiError(500, response.headers.get("X-Request-ID"));
        return body;
      } catch (error) {
        if (error instanceof ApiError) throw error;
        if (current !== generation) throw new ApiError(0, null, "", true);
        throw new ApiError(timedOut ? 408 : 0);
      } finally {
        clearTimeout(timer);
        pending.delete(controller);
      }
    },
  };
}
