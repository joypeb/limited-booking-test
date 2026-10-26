"use client";

import { useRef, useState } from "react";
import { callBackend, type CallBackendArgs } from "@/lib/api/client";
import type { ApiResult } from "@/lib/api/types";
import { useConsole } from "./ConsoleProvider";

type BackendRequest = Pick<CallBackendArgs, "method" | "path" | "body" | "idempotencyKey">;

export function useBackendRequest() {
  const { credentials, generation, getGeneration } = useConsole();
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<ApiResult | null>(null);
  const inFlight = useRef(false);

  async function send(request: BackendRequest) {
    if (!credentials || inFlight.current || getGeneration() !== generation) return;
    inFlight.current = true;
    setPending(true);
    try {
      const response = await callBackend({ ...request, credentials });
      if (getGeneration() === generation) {
        setResult(response);
        return response;
      }
    } finally {
      inFlight.current = false;
      if (getGeneration() === generation) setPending(false);
    }
  }

  return { result, pending, requestsDisabled: !credentials || pending, send };
}
