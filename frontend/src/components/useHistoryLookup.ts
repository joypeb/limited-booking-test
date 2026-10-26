"use client";

import { useEffect } from "react";
import type { HistoryEntry } from "@/lib/api/types";
import { useConsole } from "./ConsoleProvider";

export function useHistoryLookup(kind: HistoryEntry["kind"], lookup: (id: string) => void) {
  const { registerLookup } = useConsole();
  useEffect(() => registerLookup(kind, lookup), [kind, lookup, registerLookup]);
}
