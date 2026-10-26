import { ACCOUNTS, type HistoryEntry } from "./api/types";
import { isUuid } from "./api/bodies";

export const HISTORY_STORAGE_KEY = "limited-booking-console:history:v1";

function cleanEntry(value: unknown): HistoryEntry | null {
  if (!value || typeof value !== "object") return null;
  const entry = value as Record<string, unknown>;
  if ((entry.kind !== "resource" && entry.kind !== "reservation")
    || typeof entry.id !== "string" || !isUuid(entry.id)
    || typeof entry.recordedAt !== "string" || !Number.isFinite(Date.parse(entry.recordedAt))
    || !ACCOUNTS.some((account) => account === entry.account)
    || (entry.name !== undefined && typeof entry.name !== "string")) return null;
  return {
    kind: entry.kind,
    id: entry.id,
    recordedAt: entry.recordedAt,
    account: entry.account as HistoryEntry["account"],
    ...(entry.kind === "resource" && typeof entry.name === "string" ? { name: entry.name } : {}),
  };
}

export function readHistory(storage: Storage): HistoryEntry[] {
  try {
    const parsed: unknown = JSON.parse(storage.getItem(HISTORY_STORAGE_KEY) ?? "[]");
    if (!Array.isArray(parsed)) return [];
    const entries = parsed
      .map(cleanEntry)
      .filter((entry): entry is HistoryEntry => entry !== null)
      .sort((a, b) => Date.parse(b.recordedAt) - Date.parse(a.recordedAt));
    return entries
      .filter((entry, index) => entries.findIndex((other) =>
        other.kind === entry.kind && other.id === entry.id) === index)
      .slice(0, 50);
  } catch {
    return [];
  }
}

export function recordHistory(storage: Storage, entry: HistoryEntry): HistoryEntry[] {
  const clean = cleanEntry(entry);
  const current = readHistory(storage);
  if (!clean) return current;
  const entries = [clean, ...current.filter((other) => other.kind !== clean.kind || other.id !== clean.id)]
    .sort((a, b) => Date.parse(b.recordedAt) - Date.parse(a.recordedAt))
    .slice(0, 50);
  try {
    storage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(entries));
  } catch {
    // 저장소를 사용할 수 없어도 API 결과는 유지한다.
  }
  return entries;
}

export function clearHistory(storage: Storage): void {
  storage.removeItem(HISTORY_STORAGE_KEY);
}
