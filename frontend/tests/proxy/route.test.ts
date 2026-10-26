import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { GET, POST } from "../../src/app/api/backend/[...path]/route";
import * as mod from "../../src/app/api/backend/[...path]/route";
import { UUID, consoleRequest, expectProblem, upstream } from "./support";

let originalBaseUrl: string | undefined;
let originalPort: string | undefined;

beforeEach(() => {
  originalBaseUrl = process.env.BACKEND_BASE_URL;
  originalPort = process.env.CONSOLE_PORT;
  process.env.CONSOLE_PORT = "3000";
  process.env.BACKEND_BASE_URL = "http://127.0.0.1:8080";
});

afterEach(() => {
  if (originalBaseUrl === undefined) delete process.env.BACKEND_BASE_URL;
  else process.env.BACKEND_BASE_URL = originalBaseUrl;
  if (originalPort === undefined) delete process.env.CONSOLE_PORT;
  else process.env.CONSOLE_PORT = originalPort;
});

const context = (path: string[]) => ({ params: Promise.resolve({ path }) });

describe("GET·POST Route Handler의 실제 HTTP 연결", () => {
  it.each([
    ["GET", ["resources", UUID], `/api/resources/${UUID}`],
    ["GET", ["reservations", UUID], `/api/reservations/${UUID}`],
    ["POST", ["resources"], "/api/resources"],
    ["POST", ["reservations"], "/api/reservations"],
    ["POST", ["reservations", UUID, "cancel"], `/api/reservations/${UUID}/cancel`],
  ])("%s %s가 비동기 params와 환경 변수의 업스트림을 사용한다", async (method, path, expectedUrl) => {
    const body = '{"name":"a","name":"b","capacity":1}';
    const server = await upstream((_request, response) => {
      response.writeHead(method === "POST" ? 201 : 200, {
        "Content-Type": "application/json",
        Location: `/api/resources/${UUID}`,
        "Idempotent-Replayed": "false",
        "Set-Cookie": "hidden=secret",
      });
      response.end('{"upstream":true}');
    });
    process.env.BACKEND_BASE_URL = server.baseUrl;
    const isCancel = path.at(-1) === "cancel";
    const request = consoleRequest(method, path, method === "POST" && !isCancel ? {
      headers: { "Content-Type": "application/json", "Idempotency-Key": "Original_Key-1" },
      body,
    } : {});
    const handler = method === "GET" ? GET : POST;
    const response = await handler(request, context(path));
    expect(response.status).toBe(method === "POST" ? 201 : 200);
    expect(await response.text()).toBe('{"upstream":true}');
    expect(response.headers.get("location")).toBe(`/api/resources/${UUID}`);
    expect(response.headers.get("idempotent-replayed")).toBe("false");
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(server.received).toHaveLength(1);
    expect(server.received[0].url).toBe(expectedUrl);
    expect(server.received[0].method).toBe(method);
    expect(server.received[0].body).toEqual(Buffer.from(method === "POST" && !isCancel ? body : ""));
  });

  it("CONSOLE_PORT의 지정값으로 Host를 검사한다", async () => {
    const server = await upstream();
    process.env.BACKEND_BASE_URL = server.baseUrl;
    process.env.CONSOLE_PORT = "3107";
    const path = ["resources", UUID];
    const response = await GET(consoleRequest("GET", path, {}, 3107), context(path));
    expect(response.status).toBe(200);
    expect(server.received).toHaveLength(1);
  });

  it("CONSOLE_PORT와 다른 Host 포트는 403이며 업스트림 수신은 0회다", async () => {
    const server = await upstream();
    process.env.BACKEND_BASE_URL = server.baseUrl;
    process.env.CONSOLE_PORT = "3107";
    const path = ["resources", UUID];
    const response = await GET(consoleRequest("GET", path), context(path));
    await expectProblem(response, 403, "CONSOLE_FORBIDDEN_ORIGIN", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it("허용하지 않은 GET 목록 경로는 404이며 업스트림 수신은 0회다", async () => {
    const server = await upstream();
    process.env.BACKEND_BASE_URL = server.baseUrl;
    const path = ["resources"];
    const response = await GET(consoleRequest("GET", path), context(path));
    await expectProblem(response, 404, "CONSOLE_ROUTE_NOT_ALLOWED", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it("POST의 401 인증 challenge를 이름을 바꾼 헤더로 반환한다", async () => {
    const challenge = 'Basic realm="reservation-local"';
    const server = await upstream((_request, response) => {
      response.writeHead(401, { "WWW-Authenticate": challenge, "Content-Type": "application/problem+json" });
      response.end('{"code":"UNAUTHENTICATED"}');
    });
    process.env.BACKEND_BASE_URL = server.baseUrl;
    const path = ["reservations"];
    const response = await POST(consoleRequest("POST", path), context(path));
    expect(response.status).toBe(401);
    expect(response.headers.get("www-authenticate")).toBeNull();
    expect(response.headers.get("x-upstream-www-authenticate")).toBe(challenge);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(server.received).toHaveLength(1);
  });

  it.each(["http://example.com:8080", "invalid URL"])("환경 변수의 잘못된 주소 %s는 500으로 반환한다", async (baseUrl) => {
    const server = await upstream();
    process.env.BACKEND_BASE_URL = baseUrl;
    const path = ["resources", UUID];
    const response = await GET(consoleRequest("GET", path), context(path));
    await expectProblem(response, 500, "CONSOLE_MISCONFIGURED", baseUrl);
    expect(server.received).toHaveLength(0);
  });
});

describe("OPTIONS·HEAD의 명시적 출처 검사와 경로 거부", () => {
  it.each([
    ["OPTIONS", "127.0.0.1:3000", 404, "CONSOLE_ROUTE_NOT_ALLOWED"],
    ["HEAD", "127.0.0.1:3000", 404, "CONSOLE_ROUTE_NOT_ALLOWED"],
    ["OPTIONS", "evil.example:3000", 403, "CONSOLE_FORBIDDEN_ORIGIN"],
    ["HEAD", "evil.example:3000", 403, "CONSOLE_FORBIDDEN_ORIGIN"],
  ] as const)("%s Host %s는 업스트림 호출 없이 %i %s를 반환한다", async (method, host, status, code) => {
    const server = await upstream();
    process.env.BACKEND_BASE_URL = server.baseUrl;
    const path = ["resources", UUID];
    const handler: unknown = Reflect.get(mod, method);
    expect(typeof handler, `${method} Route Handler를 명시적으로 export해야 한다`).toBe("function");
    if (typeof handler !== "function") return;
    const response: Response = await handler(consoleRequest(method, path, { headers: { host } }), context(path));
    if (method === "HEAD") {
      expect(response.status).toBe(status);
      expect(response.headers.get("content-type")).toMatch(/^application\/problem\+json(?:;|$)/);
      expect(await response.text()).toBe("");
    } else {
      await expectProblem(response, status, code, server.baseUrl);
    }
    expect(server.received).toHaveLength(0);
  });
});
