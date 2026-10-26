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
  void args;
  throw new Error("NOT_IMPLEMENTED");
}
