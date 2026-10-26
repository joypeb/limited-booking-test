export type AllowedRoute = {
  method: "GET" | "POST";
  upstreamPath: string;
};

export function matchRoute(
  method: string,
  segments: string[],
): AllowedRoute | null {
  void method;
  void segments;
  throw new Error("NOT_IMPLEMENTED");
}
