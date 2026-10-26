"use client";

import { useRef, useState } from "react";
import { isJsonNumberLiteral, isUuid, resourceCreateBody } from "@/lib/api/bodies";
import { callBackend } from "@/lib/api/client";
import type { ApiResult } from "@/lib/api/types";
import { useConsole } from "./ConsoleProvider";
import { ResultView } from "./ResultView";

export function ResourcePanel() {
  const { generation } = useConsole();
  return <ResourceEditor key={generation} />;
}

function ResourceEditor() {
  const { credentials, generation, getGeneration } = useConsole();
  const [name, setName] = useState("");
  const [capacityText, setCapacityText] = useState("");
  const [invalidCapacity, setInvalidCapacity] = useState(false);
  const [resourceId, setResourceId] = useState("");
  const [invalidId, setInvalidId] = useState(false);
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<ApiResult | null>(null);
  const inFlight = useRef(false);

  async function send(method: "GET" | "POST", path: string, body?: string) {
    if (!credentials || inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    try {
      const response = await callBackend({ method, path, body, credentials });
      if (getGeneration() === generation) setResult(response);
    } finally {
      inFlight.current = false;
      if (getGeneration() === generation) setPending(false);
    }
  }

  function lookup(id = resourceId) {
    if (!isUuid(id)) {
      setInvalidId(true);
      return;
    }
    setInvalidId(false);
    setResourceId(id);
    return send("GET", `resources/${id}`);
  }

  function create() {
    if (!isJsonNumberLiteral(capacityText)) {
      setInvalidCapacity(true);
      return;
    }
    setInvalidCapacity(false);
    return send("POST", "resources", resourceCreateBody(name, capacityText));
  }

  const resultId = result?.ok && result.body !== null && typeof result.body === "object"
    && "id" in result.body && typeof result.body.id === "string" ? result.body.id : undefined;

  return (
    <section aria-label="예약 대상" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">예약 대상</h2>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        void create();
      }}>
        <label className="grid gap-1">대상 이름
          <input className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" type="text" value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <label className="grid gap-1">최초 수량
          <input
            className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600"
            type="text"
            inputMode="decimal"
            value={capacityText}
            aria-invalid={invalidCapacity}
            aria-describedby={invalidCapacity ? "resource-capacity-error" : undefined}
            onChange={(event) => {
              setCapacityText(event.target.value);
              setInvalidCapacity(false);
            }}
          />
        </label>
        {invalidCapacity && <p id="resource-capacity-error" role="alert">최초 수량은 숫자 표기여야 합니다</p>}
        <button type="submit" disabled={!credentials || pending} className="rounded bg-blue-700 px-4 py-2 text-white focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50">대상 생성</button>
      </form>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        void lookup();
      }}>
        <label className="grid gap-1">대상 식별자
          <input className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" value={resourceId} aria-invalid={invalidId} aria-describedby={invalidId ? "resource-id-error" : undefined} onChange={(event) => {
            setResourceId(event.target.value);
            setInvalidId(false);
          }} />
        </label>
        {invalidId && <p id="resource-id-error" role="alert">식별자는 UUID 형식이어야 합니다</p>}
        <button type="submit" disabled={!credentials || pending} className="rounded bg-blue-700 px-4 py-2 text-white focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50">대상 조회</button>
      </form>
      <ResultView title="예약 대상" result={result}>
        {resultId !== undefined && (
          <button
            type="button"
            disabled={!credentials || pending}
            className="rounded border px-4 py-2 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50"
            onClick={() => { void lookup(resultId); }}
          >대상 다시 조회</button>
        )}
      </ResultView>
    </section>
  );
}
