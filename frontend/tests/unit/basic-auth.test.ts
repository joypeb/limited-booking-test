import { Buffer } from "node:buffer";
import { describe, expect, it } from "vitest";
import { basicAuthHeader } from "../../src/lib/basic-auth";

describe("Basic 인증 헤더", () => {
  it("계정과 비밀번호를 콜론으로 연결해 Base64로 인코딩한다", () => {
    expect(basicAuthHeader("USER_A", "pw")).toBe(`Basic ${Buffer.from("USER_A:pw", "utf8").toString("base64")}`);
  });
  it("한글과 이모지 비밀번호를 UTF-8 바이트 기준으로 인코딩한다", () => {
    expect(basicAuthHeader("USER_A", "비밀🔑")).toBe(`Basic ${Buffer.from("USER_A:비밀🔑", "utf8").toString("base64")}`);
  });
});
