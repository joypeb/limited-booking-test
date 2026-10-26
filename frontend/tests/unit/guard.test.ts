import { describe, expect, it } from "vitest";
import { checkOrigin } from "../../src/lib/proxy/guard";

const hosts = ["127.0.0.1:3000", "localhost:3000", "[::1]:3000"];

describe("루프백 출처 검사", () => {
  it.each(hosts)("허용 Host %s와 출처 헤더 생략을 허용한다", (host) => {
    expect(checkOrigin(new Headers({ Host: host }), 3000)).toEqual({ ok: true });
  });

  it.each(hosts)("Host %s에 일치하는 HTTP Origin을 허용한다", (host) => {
    expect(checkOrigin(new Headers({ Host: host, Origin: `http://${host}` }), 3000)).toEqual({ ok: true });
  });

  it.each(["127.0.0.1:3100", "localhost:80", "[::1]:3100", "evil.example:3000"])(
    "포트 또는 호스트가 허용 목록 밖인 %s를 거부한다",
    (host) => {
      expect(checkOrigin(new Headers({ Host: host, Origin: `http://${host}` }), 3000)).toEqual({ ok: false });
    },
  );

  it("Host가 없으면 거부한다", () => {
    expect(checkOrigin(new Headers(), 3000)).toEqual({ ok: false });
  });

  it.each(["https://127.0.0.1:3000", "http://127.0.0.1:3100", "http://localhost:3000", "http://evil.example:3000"])(
    "현재 Host와 출처가 다른 Origin %s를 거부한다",
    (origin) => {
      expect(checkOrigin(new Headers({ Host: hosts[0], Origin: origin }), 3000)).toEqual({ ok: false });
    },
  );

  it.each(["same-origin", "none"])("Sec-Fetch-Site %s를 허용한다", (site) => {
    expect(checkOrigin(new Headers({ Host: hosts[0], "Sec-Fetch-Site": site }), 3000)).toEqual({ ok: true });
  });

  it.each(["cross-site", "same-site"])("Sec-Fetch-Site %s를 거부한다", (site) => {
    expect(checkOrigin(new Headers({ Host: hosts[0], "Sec-Fetch-Site": site }), 3000)).toEqual({ ok: false });
  });

  it.each(["127.0.0.1:3100", "localhost:3100", "[::1]:3100"])("allowedPort 3100에 맞는 %s를 허용한다", (host) => {
    expect(checkOrigin(new Headers({ Host: host, Origin: `http://${host}` }), 3100)).toEqual({ ok: true });
  });

  it("allowedPort가 3100이면 기본 포트 3000을 거부한다", () => {
    expect(checkOrigin(new Headers({ Host: hosts[0] }), 3100)).toEqual({ ok: false });
  });
});
