export function checkOrigin(headers: Headers, allowedPort: number): { ok: true } | { ok: false } {
  const host = headers.get("host");
  const hosts = [ `127.0.0.1:${allowedPort}`, `localhost:${allowedPort}`, `[::1]:${allowedPort}` ];
  const origin = headers.get("origin");
  const site = headers.get("sec-fetch-site");
  return { ok: host !== null && hosts.includes(host)
    && (origin === null || origin === `http://${host}`)
    && (site === null || site === "same-origin" || site === "none") };
}
