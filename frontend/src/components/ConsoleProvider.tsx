"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import type { Account, Credentials, HistoryEntry } from "@/lib/api/types";

export type ConsoleContextValue = {
  credentials: Credentials | null;
  account: Account | null;
  generation: number;
  getGeneration: () => number;
  history: HistoryEntry[];
  applyCredentials: (credentials: Credentials) => void;
  clearCredentials: () => void;
  refreshHistory: () => void;
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
    history: [],
    refreshHistory: () => {},
    applyCredentials: (next) => changeCredentials({ ...next }),
    clearCredentials: () => changeCredentials(null),
  }), [credentials, generation, changeCredentials]);

  return <ConsoleContext.Provider value={value}>{children}</ConsoleContext.Provider>;
}
