import { describe, expect, it } from "vitest";
import { clearHistory, HISTORY_STORAGE_KEY, readHistory, recordHistory } from "../../src/lib/history";
import type { HistoryEntry } from "../../src/lib/api/types";

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();
  get length() { return this.values.size; }
  clear() { this.values.clear(); }
  getItem(key: string) { return this.values.get(key) ?? null; }
  key(index: number) { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string) { this.values.delete(key); }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

function entry(index: number): HistoryEntry {
  return {
    kind: "resource",
    id: `12345678-1234-4234-8234-${String(index).padStart(12, "0")}`,
    name: `콘서트 ${index}`,
    recordedAt: new Date(Date.UTC(2026, 9, 26, 0, 0, index)).toISOString(),
    account: "USER_A",
  };
}

describe("최근 기록 저장 규칙", () => {
  it("빈 저장소에서는 빈 목록을 반환한다", () => {
    expect(readHistory(new MemoryStorage())).toEqual([]);
  });

  it("계약의 저장 키에 기록하고 최신순으로 읽는다", () => {
    const storage = new MemoryStorage();
    recordHistory(storage, entry(1));
    expect(recordHistory(storage, entry(2))).toEqual([entry(2), entry(1)]);
    expect(HISTORY_STORAGE_KEY).toBe("limited-booking-console:history:v1");
    expect(JSON.parse(storage.getItem("limited-booking-console:history:v1")!)).toEqual([entry(2), entry(1)]);
    expect(readHistory(storage)).toEqual([entry(2), entry(1)]);
  });

  it("같은 종류와 식별자를 재기록하면 한 항목으로 갱신해 맨 앞에 둔다", () => {
    const storage = new MemoryStorage();
    recordHistory(storage, entry(1));
    recordHistory(storage, entry(2));
    const updated: HistoryEntry = { ...entry(1), name: "수정된 메모", account: "USER_B", recordedAt: entry(3).recordedAt };
    expect(recordHistory(storage, updated)).toEqual([updated, entry(2)]);
    expect(readHistory(storage)).toEqual([updated, entry(2)]);
  });

  it("식별자가 같아도 종류가 다르면 각각 보관한다", () => {
    const storage = new MemoryStorage();
    const reservation: HistoryEntry = { kind: "reservation", id: entry(1).id, recordedAt: entry(2).recordedAt, account: "USER_A" };
    recordHistory(storage, entry(1));
    recordHistory(storage, reservation);
    expect(readHistory(storage)).toEqual([reservation, entry(1)]);
  });

  it("51개를 기록하면 가장 오래된 항목을 제거하고 최신 50개만 보관한다", () => {
    const storage = new MemoryStorage();
    for (let index = 1; index <= 51; index += 1) recordHistory(storage, entry(index));
    const expected = Array.from({ length: 50 }, (_, index) => entry(51 - index));
    expect(readHistory(storage)).toEqual(expected);
    expect(JSON.parse(storage.getItem(HISTORY_STORAGE_KEY)!)).toHaveLength(50);
  });

  it("여분 필드가 있어도 허용 필드만 직렬화하고 비밀번호와 수량 및 상태를 저장하지 않는다", () => {
    const storage = new MemoryStorage();
    const unsafe = { ...entry(1), password: "비밀-password", available: 2, status: "CONFIRMED", capacity: 3 } as HistoryEntry;
    recordHistory(storage, unsafe);
    const raw = storage.getItem(HISTORY_STORAGE_KEY)!;
    expect(JSON.parse(raw)).toEqual([entry(1)]);
    for (const key of ["password", "available", "status", "capacity"]) expect(raw).not.toContain(`"${key}"`);
    expect(raw).not.toContain("비밀-password");
  });

  it("손상된 JSON은 빈 목록으로 읽는다", () => {
    const storage = new MemoryStorage();
    storage.setItem(HISTORY_STORAGE_KEY, "{not json");
    expect(readHistory(storage)).toEqual([]);
  });

  it("배열의 잘못된 종류나 식별자 누락 항목을 버리고 정상 항목은 유지한다", () => {
    const storage = new MemoryStorage();
    storage.setItem(HISTORY_STORAGE_KEY, JSON.stringify([
      entry(1), { ...entry(2), kind: "unknown" },
      { kind: "resource", recordedAt: entry(3).recordedAt, account: "USER_A" }, null,
    ]));
    expect(readHistory(storage)).toEqual([entry(1)]);
  });

  it("기록을 지우면 빈 목록으로 읽힌다", () => {
    const storage = new MemoryStorage();
    recordHistory(storage, entry(1));
    clearHistory(storage);
    expect(readHistory(storage)).toEqual([]);
  });
});
