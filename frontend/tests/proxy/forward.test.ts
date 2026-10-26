import { describe, expect, it } from "vitest";
import { forward } from "../../src/lib/proxy/forward";
import {
  AUTHORIZATION, UUID, closeServer, consoleRequest, delayResponse, expectProblem, upstream,
} from "./support";

const MEBIBYTE = 1_048_576;

describe("전달 계층의 실제 HTTP 계약", () => {
  it("허용한 요청 헤더 값만 전달하고 쿠키와 사용자·전달자 헤더는 제거한다", async () => {
    const server = await upstream();
    const request = consoleRequest("POST", ["reservations"], {
      headers: {
        Authorization: AUTHORIZATION,
        "Content-Type": "application/json; charset=utf-8",
        "Idempotency-Key": "AbC_Key-01",
        Accept: "application/problem+json, application/json",
        Cookie: "session=secret",
        "X-User-Id": UUID,
        "X-Forwarded-For": "203.0.113.10",
        "X-Forwarded-Host": "evil.example",
        "X-Internal": "client-internal",
      },
      body: '{"resourceId":"' + UUID + '"}',
    });
    await forward(request, ["reservations"], { baseUrl: server.baseUrl });
    expect(server.received).toHaveLength(1);
    expect(server.received[0].headers).toMatchObject({
      authorization: AUTHORIZATION,
      "content-type": "application/json; charset=utf-8",
      "idempotency-key": "AbC_Key-01",
      accept: "application/problem+json, application/json",
    });
    for (const name of ["cookie", "x-user-id", "x-forwarded-for", "x-forwarded-host", "x-internal"]) {
      expect(server.received[0].headers[name]).toBeUndefined();
    }
  });

  it.each([
    ["중복 JSON 키", Buffer.from('{"name":"a","name":"b","capacity":1}')],
    ["백엔드 상한보다 1바이트 큰 본문", Buffer.alloc(8193, 0x61)],
    ["정확히 1MiB 본문", Buffer.alloc(MEBIBYTE, 0x62)],
    ["UTF-8와 바이너리 바이트", Buffer.from([0, 0xff, 0xc3, 0x28, 0x0d, 0x0a])],
  ])("%s을 파싱하거나 재직렬화하지 않고 전달한다", async (_name, body) => {
    const server = await upstream();
    await forward(consoleRequest("POST", ["resources"], {
      headers: { "Content-Type": "application/json" },
      body: new Uint8Array(body),
    }), ["resources"], { baseUrl: server.baseUrl });
    expect(server.received).toHaveLength(1);
    expect(server.received[0].body).toEqual(body);
  });

  it("취소 POST의 0바이트 본문을 빈 상태로 전달한다", async () => {
    const server = await upstream();
    const path = ["reservations", UUID, "cancel"];
    await forward(consoleRequest("POST", path), path, { baseUrl: server.baseUrl });
    expect(server.received).toHaveLength(1);
    expect(server.received[0].method).toBe("POST");
    expect(server.received[0].body.length).toBe(0);
  });

  it("1MiB 초과 본문은 자체 413으로 거부하고 업스트림을 호출하지 않는다", async () => {
    const server = await upstream();
    const response = await forward(consoleRequest("POST", ["resources"], {
      body: new Uint8Array(MEBIBYTE + 1),
    }), ["resources"], { baseUrl: server.baseUrl });
    await expectProblem(response, 413, "CONSOLE_PAYLOAD_TOO_LARGE", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it.each([201, 409, 429, 503])("업스트림 %i 상태와 본문 바이트·허용 응답 헤더를 보존한다", async (status) => {
    const body = Buffer.from([0x7b, 0x0a, 0xff, 0x00, 0x7d]);
    const headers = {
      "Content-Type": "application/problem+json; charset=utf-8",
      Location: `/api/reservations/${UUID}`,
      "Idempotent-Replayed": "true",
      "Retry-After": "17",
      "Set-Cookie": "secret=value; HttpOnly",
      "X-Internal": "database-secret",
      "Cache-Control": "public, max-age=3600",
    };
    const server = await upstream((_request, response) => {
      response.writeHead(status, headers);
      response.end(body);
    });
    const response = await forward(consoleRequest("POST", ["reservations"]), ["reservations"], { baseUrl: server.baseUrl });
    expect(response.status).toBe(status);
    expect(Buffer.from(await response.arrayBuffer())).toEqual(body);
    for (const name of ["Content-Type", "Location", "Idempotent-Replayed", "Retry-After"] as const) {
      expect(response.headers.get(name)).toBe(headers[name]);
    }
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(response.headers.get("x-internal")).toBeNull();
    expect(server.received).toHaveLength(1);
  });

  it("401 인증 challenge를 관찰 헤더로 옮기고 WWW-Authenticate는 제거한다", async () => {
    const challenge = 'Basic realm="reservation-local"';
    const body = '{"code":"UNAUTHENTICATED"}';
    const server = await upstream((_request, response) => {
      response.writeHead(401, { "WWW-Authenticate": challenge, "Content-Type": "application/problem+json" });
      response.end(body);
    });
    const response = await forward(consoleRequest("GET", ["resources", UUID]), ["resources", UUID], { baseUrl: server.baseUrl });
    expect(response.status).toBe(401);
    expect(await response.text()).toBe(body);
    expect(response.headers.get("www-authenticate")).toBeNull();
    expect(response.headers.get("x-upstream-www-authenticate")).toBe(challenge);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(server.received).toHaveLength(1);
  });

  it.each([
    ["POST", ["resources"], "/api/resources"],
    ["GET", ["resources", UUID], `/api/resources/${UUID}`],
    ["POST", ["reservations"], "/api/reservations"],
    ["GET", ["reservations", UUID], `/api/reservations/${UUID}`],
    ["POST", ["reservations", UUID, "cancel"], `/api/reservations/${UUID}/cancel`],
  ])("%s %s의 경로와 UUID 원문을 매핑하고 쿼리는 제외한다", async (method, path, expected) => {
    const server = await upstream();
    await forward(consoleRequest(method, path), path, { baseUrl: server.baseUrl });
    expect(server.received).toHaveLength(1);
    expect(server.received[0].method).toBe(method);
    expect(server.received[0].url).toBe(expected);
  });

  it.each([
    ["GET", ["resources"]],
    ["DELETE", ["reservations", UUID]],
    ["GET", ["resources", "not-a-uuid"]],
    ["GET", ["..", "resources", UUID]],
    ["GET", ["resources", `${UUID}%2Fcancel`]],
  ])("허용되지 않은 %s %s은 404이며 업스트림 수신은 0회다", async (method, path) => {
    const server = await upstream();
    const response = await forward(consoleRequest(method, path), path, { baseUrl: server.baseUrl });
    await expectProblem(response, 404, "CONSOLE_ROUTE_NOT_ALLOWED", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it.each([
    ["Host", { host: "evil.example:3000" }],
    ["Origin", { origin: "http://evil.example" }],
    ["Sec-Fetch-Site", { "sec-fetch-site": "cross-site" }],
  ])("금지된 %s은 403이며 업스트림 수신은 0회다", async (_name, headers) => {
    const server = await upstream();
    const path = ["resources", UUID];
    const response = await forward(consoleRequest("GET", path, { headers }), path, { baseUrl: server.baseUrl });
    await expectProblem(response, 403, "CONSOLE_FORBIDDEN_ORIGIN", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it("출처 위반과 경로 위반이 겹치면 출처를 먼저 검사한다", async () => {
    const server = await upstream();
    const path = ["not-allowed"];
    const response = await forward(consoleRequest("GET", path, { headers: { host: "evil.example:3000" } }), path, { baseUrl: server.baseUrl });
    await expectProblem(response, 403, "CONSOLE_FORBIDDEN_ORIGIN", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it.each(["http://example.com:8080", "not a valid URL"]) (
    "잘못된 백엔드 주소 %s는 500이며 업스트림 수신은 0회다", async (baseUrl) => {
      const server = await upstream();
      const path = ["resources", UUID];
      const response = await forward(consoleRequest("GET", path), path, { baseUrl });
      await expectProblem(response, 500, "CONSOLE_MISCONFIGURED", baseUrl);
      expect(server.received).toHaveLength(0);
    },
  );

  it("닫힌 포트의 연결 실패는 민감정보 없는 502로 반환한다", async () => {
    const server = await upstream();
    await closeServer(server.server);
    const path = ["resources", UUID];
    const response = await forward(consoleRequest("GET", path), path, { baseUrl: server.baseUrl, timeoutMs: 1000 });
    await expectProblem(response, 502, "CONSOLE_UPSTREAM_UNREACHABLE", server.baseUrl);
    expect(server.received).toHaveLength(0);
  });

  it("1초 지연은 200ms 상한에서 504로 반환하고 재시도하지 않는다", async () => {
    const server = await upstream((_request, response) => delayResponse(response, 1000));
    const path = ["resources", UUID];
    const response = await forward(consoleRequest("GET", path), path, { baseUrl: server.baseUrl, timeoutMs: 200 });
    await expectProblem(response, 504, "CONSOLE_UPSTREAM_TIMEOUT", server.baseUrl);
    expect(server.received).toHaveLength(1);
  });

  it("302와 Location을 그대로 반환하고 리다이렉트 대상을 호출하지 않는다", async () => {
    const target = await upstream();
    const location = `${target.baseUrl}/redirect-target`;
    const server = await upstream((_request, response) => {
      response.writeHead(302, { Location: location });
      response.end("redirect-body");
    });
    const path = ["resources", UUID];
    const response = await forward(consoleRequest("GET", path), path, { baseUrl: server.baseUrl });
    expect(response.status).toBe(302);
    expect(response.headers.get("location")).toBe(location);
    expect(await response.text()).toBe("redirect-body");
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(server.received).toHaveLength(1);
    expect(target.received).toHaveLength(0);
  });
});
