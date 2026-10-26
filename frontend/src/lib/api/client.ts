import { basicAuthHeader } from "../basic-auth";
import { describeCode, parseResponse } from "./problem";
import type { ApiResult, Credentials } from "./types";

export type CallBackendArgs = {
  method: "GET" | "POST";
  path: string;
  credentials: Credentials;
  body?: string;
  idempotencyKey?: string;
  timeoutMs?: number;
  signal?: AbortSignal;
};

export async function callBackend(args: CallBackendArgs): Promise<ApiResult> {
  const controller = new AbortController();
  let timedOut = false;
  const abort = () => controller.abort();
  args.signal?.addEventListener("abort", abort, { once: true });
  if (args.signal?.aborted) controller.abort();
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, args.timeoutMs ?? 15_000);
  try {
    const headers = new Headers({ Authorization: basicAuthHeader(args.credentials.account, args.credentials.password) });
    if (args.body !== undefined) headers.set("Content-Type", "application/json");
    if (args.idempotencyKey) headers.set("Idempotency-Key", args.idempotencyKey);
    const response = await fetch(`/api/backend/${args.path}`, {
      method: args.method, headers, body: args.body, signal: controller.signal, cache: "no-store",
    });
    return await parseResponse(response);
  } catch {
    const code = timedOut ? "CONSOLE_CLIENT_TIMEOUT" : "CONSOLE_UPSTREAM_UNREACHABLE";
    return {
      status: 0, ok: false, headers: {}, source: "console",
      problem: { type: "about:blank", title: "응답 없음", status: 0, detail: describeCode(code), code },
    };
  } finally {
    clearTimeout(timer);
    args.signal?.removeEventListener("abort", abort);
  }
}
