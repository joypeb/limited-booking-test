export type ForwardOptions = {
  baseUrl: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  allowedPort?: number;
  maxBodyBytes?: number;
};

export async function forward(
  request: Request,
  segments: string[],
  options: ForwardOptions,
): Promise<Response> {
  void request;
  void segments;
  void options;
  throw new Error("NOT_IMPLEMENTED");
}

export function consoleProblem(
  status: number,
  code: string,
  title: string,
  detail: string,
): Response {
  void status;
  void code;
  void title;
  void detail;
  throw new Error("NOT_IMPLEMENTED");
}
