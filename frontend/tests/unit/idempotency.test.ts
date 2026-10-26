import { describe, expect, it } from "vitest";
import { newIdempotencyKey } from "../../src/lib/idempotency";

describe("예약 생성 키", () => {
  it("허용 문자와 1~64자 길이로 생성한다", () => {
    expect(newIdempotencyKey()).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
  });
  it("100회 연속 호출한 키가 모두 서로 다르다", () => {
    const keys = Array.from({ length: 100 }, () => newIdempotencyKey());
    for (const key of keys) expect(key).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    expect(new Set(keys).size).toBe(100);
  });
});
