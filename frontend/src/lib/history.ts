import type { HistoryEntry } from "./api/types";

export const HISTORY_STORAGE_KEY = "limited-booking-console:history:v1";

export function readHistory(storage: Storage): HistoryEntry[] {
  void storage;
  throw new Error("NOT_IMPLEMENTED");
}

export function recordHistory(
  storage: Storage,
  entry: HistoryEntry,
): HistoryEntry[] {
  void storage;
  void entry;
  throw new Error("NOT_IMPLEMENTED");
}

export function clearHistory(storage: Storage): void {
  void storage;
  throw new Error("NOT_IMPLEMENTED");
}
