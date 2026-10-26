"use client";

import { useRef, useState } from "react";
import { isUuid } from "@/lib/api/bodies";
import { callBackend } from "@/lib/api/client";
import type { ApiResult } from "@/lib/api/types";
import { useConsole } from "./ConsoleProvider";
import { ResultView } from "./ResultView";

export function ResourcePanel() {
  const { generation } = useConsole();
  return <ResourceLookup key={generation} />;
}

function ResourceLookup() {
  const { credentials, generation, getGeneration } = useConsole();
  const [resourceId, setResourceId] = useState("");
  const [invalidId, setInvalidId] = useState(false);
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<ApiResult | null>(null);
  const inFlight = useRef(false);

  async function lookup() {
    if (!credentials || inFlight.current) return;
    if (!isUuid(resourceId)) {
      setInvalidId(true);
      return;
    }
    setInvalidId(false);
    inFlight.current = true;
    setPending(true);
    try {
      const response = await callBackend({ method: "GET", path: `resources/${resourceId}`, credentials });
      if (getGeneration() === generation) setResult(response);
    } finally {
      inFlight.current = false;
      if (getGeneration() === generation) setPending(false);
    }
  }

  return (
    <section aria-label="예약 대상" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">예약 대상</h2>
      <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); void lookup(); }}>
        <label className="grid gap-1">대상 식별자
          <input className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" value={resourceId} aria-invalid={invalidId} aria-describedby={invalidId ? "resource-id-error" : undefined} onChange={(event) => { setResourceId(event.target.value); setInvalidId(false); }} />
        </label>
        {invalidId && <p id="resource-id-error" role="alert">식별자는 UUID 형식이어야 합니다</p>}
        <button type="submit" disabled={!credentials || pending} className="rounded bg-blue-700 px-4 py-2 text-white focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50">대상 조회</button>
      </form>
      <ResultView title="예약 대상" result={result} />
    </section>
  );
}
