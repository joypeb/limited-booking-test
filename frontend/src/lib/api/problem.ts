import type { ApiResult } from "./types";

export const CODE_DESCRIPTIONS: Record<string, string> = {};

export function describeCode(code: string | undefined): string {
  void code;
  throw new Error("NOT_IMPLEMENTED");
}

export function parseResponse(response: Response): Promise<ApiResult> {
  void response;
  throw new Error("NOT_IMPLEMENTED");
}
