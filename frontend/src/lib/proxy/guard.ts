export function checkOrigin(
  headers: Headers,
  allowedPort: number,
): { ok: true } | { ok: false } {
  void headers;
  void allowedPort;
  throw new Error("NOT_IMPLEMENTED");
}
