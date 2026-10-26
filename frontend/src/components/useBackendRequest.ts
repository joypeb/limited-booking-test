"use client";

import { useRef, useState } from "react";
import { callBackend, type CallBackendArgs } from "@/lib/api/client";
import { isUuid } from "@/lib/api/bodies";
import type { ApiResult } from "@/lib/api/types";
import { useConsole } from "./ConsoleProvider";

type BackendRequest = Pick<CallBackendArgs, "method" | "path" | "body" | "idempotencyKey">;

export function useBackendRequest() {
  const { credentials, generation, getGeneration, recordEntry } = useConsole();
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
        if (response.ok && response.body && typeof response.body === "object" && "id" in response.body
          && typeof response.body.id === "string" && isUuid(response.body.id)) {
          const kind = request.path.startsWith("resources") ? "resource" : "reservation";
          recordEntry({
            kind, id: response.body.id, account: credentials.account, recordedAt: new Date().toISOString(),
            ...(kind === "resource" && "name" in response.body && typeof response.body.name === "string" ? { name: response.body.name } : {}),
          });
        }
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
