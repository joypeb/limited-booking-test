import { describe, expect, it } from "vitest";
import { isJsonNumberLiteral, isUuid, reservationCreateBody, resourceCreateBody } from "../../src/lib/api/bodies";

const invalidNumbers = ["", "01", "1.", ".5", "abc", " 3", "3 ", "+1", "NaN", "Infinity", "0x1"];

describe("입력 원문으로 요청 본문 만들기", () => {
  it.each(["0", "3", "-1", "1.0", "1e2", "1E+2", "1001"])("업무 범위와 무관하게 JSON 숫자 %j를 허용한다", (text) => {
    expect(isJsonNumberLiteral(text)).toBe(true);
  });

  it.each(invalidNumbers)("JSON 숫자 문법이 아닌 %j를 거부한다", (text) => {
    expect(isJsonNumberLiteral(text)).toBe(false);
  });

  it("이름 앞뒤 공백과 소수 수량 원문을 유지한다", () => {
    expect(resourceCreateBody("  콘서트 A  ", "1.0")).toBe('{"name":"  콘서트 A  ","capacity":1.0}');
  });

  it("이름의 NBSP를 제거하지 않는다", () => {
    expect(resourceCreateBody("\u00a0콘서트 A\u00a0", "3")).toBe('{"name":"\u00a0콘서트 A\u00a0","capacity":3}');
  });

  it("이름의 따옴표와 역슬래시를 JSON 문자열로 인코딩한다", () => {
    expect(resourceCreateBody('공연 "A" \\ 무대', "0")).toBe('{"name":"공연 \\"A\\" \\\\ 무대","capacity":0}');
  });

  it.each(["-1", "1e2", "1E+2", "1001"])("수량 %s를 정규화하거나 범위로 제한하지 않는다", (text) => {
    expect(resourceCreateBody("x", text)).toBe(`{"name":"x","capacity":${text}}`);
  });

  it.each(invalidNumbers)("수량 %j의 문법 위반을 확인하고 본문 생성 시 예외를 던진다", (text) => {
    expect(isJsonNumberLiteral(text)).toBe(false);
    expect(() => resourceCreateBody("x", text)).toThrow();
  });

  it("예약 본문의 대상 식별자를 사전 UUID 검증 없이 인코딩한다", () => {
    expect(reservationCreateBody("abc")).toBe('{"resourceId":"abc"}');
  });

  it.each(["abcdef01-2345-6789-abcd-ef0123456789", "ABCDEF01-2345-6789-ABCD-EF0123456789", "aBcDeF01-2345-6789-aBcD-eF0123456789"])("UUID %s의 대소문자를 허용한다", (text) => {
    expect(isUuid(text)).toBe(true);
  });

  it.each([" abcdef01-2345-6789-abcd-ef0123456789", "abcdef01-2345-6789-abcd-ef0123456789 ", "{abcdef01-2345-6789-abcd-ef0123456789}", "abcdef01-2345-6789-abcd-ef012345678", "abcdef01-2345-6789-abcd-ef01234567890", "abcdef0123456789abcdef0123456789", "gggggggg-2345-6789-abcd-ef0123456789", ""])("UUID 형식이 아닌 %j를 거부한다", (text) => {
    expect(isUuid(text)).toBe(false);
  });
});
