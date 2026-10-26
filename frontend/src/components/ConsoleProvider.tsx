"use client";

import { createContext } from "react";
import type { ReactNode } from "react";
import type { Account, Credentials, HistoryEntry } from "@/lib/api/types";

export type ConsoleContextValue = {
  credentials: Credentials | null;
  account: Account | null;
  generation: number;
  history: HistoryEntry[];
  applyCredentials: (credentials: Credentials) => void;
  clearCredentials: () => void;
  refreshHistory: () => void;
};

export const ConsoleContext = createContext<ConsoleContextValue | null>(null);

export type ConsoleProviderProps = {
  children: ReactNode;
};

export function ConsoleProvider({ children }: ConsoleProviderProps) {
  return <>{children}</>;
}
