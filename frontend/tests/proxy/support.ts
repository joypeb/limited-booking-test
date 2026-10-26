import { createServer, type IncomingHttpHeaders, type Server, type ServerResponse } from "node:http";
import { afterEach, expect } from "vitest";

export const UUID = "AABBCCDD-1234-5678-90AB-AABBCCDDEEFF";
export const AUTHORIZATION = "Basic VVN FUl9BOm5vdC1hLXJlYWwtc2VjcmV0".replaceAll(" ", "");

export type Received = {
  method: string | undefined;
  url: string | undefined;
  headers: IncomingHttpHeaders;
  body: Buffer;
};

const servers: Server[] = [];
const timers: ReturnType<typeof setTimeout>[] = [];

export async function upstream(
  respond: (request: Received, response: ServerResponse) => void = (_request, response) => {
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end('{"ok":true}');
  },
) {
  const received: Received[] = [];
  const server = createServer(async (request, response) => {
    const record: Received = {
      method: request.method,
      url: request.url,
      headers: request.headers,
      body: Buffer.alloc(0),
    };
    received.push(record);
    const chunks: Buffer[] = [];
    for await (const chunk of request) chunks.push(Buffer.from(chunk));
    record.body = Buffer.concat(chunks);
    respond(record, response);
  });
  servers.push(server);
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      server.off("error", reject);
      resolve();
    });
  });
  const address = server.address();
  if (address === null || typeof address === "string") throw new Error("동적 포트를 얻지 못했습니다");
  return { baseUrl: `http://127.0.0.1:${address.port}`, received, server };
}

export function delayResponse(response: ServerResponse, milliseconds: number) {
  timers.push(setTimeout(() => response.end("늦은 응답"), milliseconds));
}

export async function closeServer(server: Server) {
  if (!server.listening) return;
  await new Promise<void>((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
    server.closeAllConnections();
  });
}

afterEach(async () => {
  for (const timer of timers.splice(0)) clearTimeout(timer);
  await Promise.all(servers.splice(0).map(closeServer));
});

export function consoleRequest(
  method: string,
  path: string[],
  init: RequestInit = {},
  port = 3000,
) {
  const headers = new Headers({ host: `127.0.0.1:${port}`, authorization: AUTHORIZATION });
  new Headers(init.headers).forEach((value, name) => headers.set(name, value));
  return new Request(`http://127.0.0.1:${port}/api/backend/${path.join("/")}?secret=ignored`, {
    ...init,
    method,
    headers,
  });
}

export async function expectProblem(response: Response, status: number, code: string, baseUrl: string) {
  expect(response.status).toBe(status);
  expect(response.headers.get("content-type")).toMatch(/^application\/problem\+json(?:;|$)/);
  expect(response.headers.get("cache-control")).toBe("no-store");
  const text = await response.text();
  const body = JSON.parse(text);
  expect(body).toMatchObject({ type: "about:blank", status, code });
  expect(body.title).toEqual(expect.any(String));
  expect(body.title.length).toBeGreaterThan(0);
  expect(body.detail).toEqual(expect.any(String));
  expect(body.detail.length).toBeGreaterThan(0);
  expect(text).not.toContain(baseUrl);
  expect(text).not.toContain(AUTHORIZATION);
  expect(text).not.toContain("at ");
}
