"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import type { Account, Credentials, HistoryEntry } from "@/lib/api/types";
import { clearHistory, readHistory, recordHistory } from "@/lib/history";

type Lookup = (id: string) => void;
type HistoryKind = HistoryEntry["kind"];

export type ConsoleContextValue = {
  credentials: Credentials | null;
  account: Account | null;
  generation: number;
  getGeneration: () => number;
  history: HistoryEntry[];
  applyCredentials: (credentials: Credentials) => void;
  clearCredentials: () => void;
  refreshHistory: () => void;
  recordEntry: (entry: HistoryEntry) => void;
  clearEntries: () => void;
  selectEntry: (entry: HistoryEntry) => void;
  registerLookup: (kind: HistoryKind, lookup: Lookup) => () => void;
};

export const ConsoleContext = createContext<ConsoleContextValue | null>(null);

export function useConsole() {
  const context = useContext(ConsoleContext);
  if (!context) throw new Error("ConsoleProvider가 필요합니다");
  return context;
}

export type ConsoleProviderProps = { children: ReactNode };

export function ConsoleProvider({ children }: ConsoleProviderProps) {
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [generation, setGeneration] = useState(0);
  const currentGeneration = useRef(0);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const lookups = useRef<Partial<Record<HistoryKind, Lookup>>>({});
  const refreshHistory = useCallback(() => {
    try {
      setHistory(readHistory(window.localStorage));
    } catch {
      setHistory([]);
    }
  }, []);
  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (active) refreshHistory();
    });
    return () => {
      active = false;
    };
  }, [refreshHistory]);
  const recordEntry = useCallback((entry: HistoryEntry) => {
    try {
      setHistory(recordHistory(window.localStorage, entry));
    } catch {
      // 저장소 접근이 제한되면 현재 화면을 유지한다.
    }
  }, []);
  const clearEntries = useCallback(() => {
    try {
      clearHistory(window.localStorage);
    } catch {
      // 저장소 접근이 제한되어도 화면의 기록은 지운다.
    }
    setHistory([]);
  }, []);
  const registerLookup = useCallback((kind: HistoryKind, lookup: Lookup) => {
    lookups.current[kind] = lookup;
    return () => {
      if (lookups.current[kind] === lookup) delete lookups.current[kind];
    };
  }, []);
  const selectEntry = useCallback((entry: HistoryEntry) => {
    lookups.current[entry.kind]?.(entry.id);
  }, []);
  const changeCredentials = useCallback((next: Credentials | null) => {
    currentGeneration.current += 1;
    setGeneration(currentGeneration.current);
    setCredentials(next);
  }, []);
  const value = useMemo<ConsoleContextValue>(() => ({
    credentials,
    account: credentials?.account ?? null,
    generation,
    getGeneration: () => currentGeneration.current,
    history,
    refreshHistory,
    recordEntry,
    clearEntries,
    selectEntry,
    registerLookup,
    applyCredentials: (next) => changeCredentials({ ...next }),
    clearCredentials: () => changeCredentials(null),
  }), [credentials, generation, changeCredentials, history, refreshHistory, recordEntry, clearEntries, selectEntry, registerLookup]);

  return <ConsoleContext.Provider value={value}>{children}</ConsoleContext.Provider>;
}
