import { checkOrigin } from "./guard";
import { matchRoute } from "./routes";
import { describeCode } from "../api/problem";

export type ForwardOptions = {
  baseUrl: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  allowedPort?: number;
  maxBodyBytes?: number;
};

function failure(status: number, code: string): Response {
  return consoleProblem(status, code, "콘솔 요청 처리 실패", describeCode(code));
}

export async function forward(request: Request, segments: string[], options: ForwardOptions): Promise<Response> {
  if (!checkOrigin(request.headers, options.allowedPort ?? 3000).ok) {
    return failure(403, "CONSOLE_FORBIDDEN_ORIGIN");
  }
  const route = matchRoute(request.method, segments);
  if (!route) return failure(404, "CONSOLE_ROUTE_NOT_ALLOWED");
  let base: URL;
  try {
    base = new URL(options.baseUrl);
    if (!["http:", "https:"].includes(base.protocol)
      || !["127.0.0.1", "localhost", "[::1]"].includes(base.hostname)
      || base.username || base.password) {
      throw new Error();
    }
  } catch {
    return failure(500, "CONSOLE_MISCONFIGURED");
  }
  const maxBodyBytes = options.maxBodyBytes ?? 1_048_576;
  const contentLength = request.headers.get("Content-Length");
  if (contentLength !== null && /^\d+$/.test(contentLength) && Number(contentLength) > maxBodyBytes) {
    return failure(413, "CONSOLE_PAYLOAD_TOO_LARGE");
  }
  const body = await request.arrayBuffer();
  if (body.byteLength > maxBodyBytes) {
    return failure(413, "CONSOLE_PAYLOAD_TOO_LARGE");
  }
  const headers = new Headers();
  for (const name of ["Authorization", "Content-Type", "Idempotency-Key", "Accept"]) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs ?? 10_000);
  try {
    const upstream = await (options.fetchImpl ?? fetch)(new URL(route.upstreamPath, base), {
      method: route.method,
      headers,
      body: body.byteLength === 0 ? undefined : body,
      redirect: "manual",
      cache: "no-store",
      signal: controller.signal,
    });
    const responseBody = await upstream.arrayBuffer();
    const responseHeaders = new Headers({ "Cache-Control": "no-store" });
    for (const name of ["Content-Type", "Location", "Idempotent-Replayed", "Retry-After"]) {
      const value = upstream.headers.get(name);
      if (value !== null) responseHeaders.set(name, value);
    }
    const challenge = upstream.headers.get("WWW-Authenticate");
    if (challenge !== null) responseHeaders.set("X-Upstream-WWW-Authenticate", challenge);
    return new Response([204, 205, 304].includes(upstream.status) ? null : responseBody, {
      status: upstream.status,
      headers: responseHeaders,
    });
  } catch {
    return controller.signal.aborted
      ? failure(504, "CONSOLE_UPSTREAM_TIMEOUT")
      : failure(502, "CONSOLE_UPSTREAM_UNREACHABLE");
  } finally {
    clearTimeout(timer);
  }
}

export function consoleProblem(status: number, code: string, title: string, detail: string): Response {
  return new Response(JSON.stringify({ type: "about:blank", title, status, detail, code }), {
    status,
    headers: { "Content-Type": "application/problem+json", "Cache-Control": "no-store" },
  });
}
