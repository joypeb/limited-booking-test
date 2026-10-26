"use client";

import { useState } from "react";
import { reservationCreateBody } from "@/lib/api/bodies";
import type { Account } from "@/lib/api/types";
import { newIdempotencyKey } from "@/lib/idempotency";
import { useConsole } from "./ConsoleProvider";
import { ResultView } from "./ResultView";
import { useBackendRequest } from "./useBackendRequest";

type ReservationAttempt = {
  resourceId: string;
  idempotencyKey: string;
  account: Account;
  generation: number;
};

export function ReservationCreatePanel() {
  const { generation } = useConsole();
  return <ReservationCreateContent key={generation} />;
}

function ReservationCreateContent() {
  const { account, generation } = useConsole();
  const { result, pending, requestsDisabled, send } = useBackendRequest();
  const [resourceId, setResourceId] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
  const [lastAttempt, setLastAttempt] = useState<ReservationAttempt | null>(null);
  const [resultKey, setResultKey] = useState<string>();
  const replayDisabled = requestsDisabled || !lastAttempt
    || lastAttempt.account !== account || lastAttempt.generation !== generation;

  async function reserve(attempt: ReservationAttempt) {
    if (requestsDisabled) return;
    setLastAttempt(attempt);
    const response = await send({
      method: "POST",
      path: "reservations",
      body: reservationCreateBody(attempt.resourceId),
      idempotencyKey: attempt.idempotencyKey,
    });
    if (response) setResultKey(attempt.idempotencyKey);
  }

  return (
    <section aria-label="예약 생성" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">예약 생성</h2>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        if (account) void reserve({ resourceId, idempotencyKey, account, generation });
      }}>
        <label className="grid gap-1">예약할 대상 식별자
          <input className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" value={resourceId} onChange={(event) => setResourceId(event.target.value)} />
        </label>
        <label className="grid gap-1">생성 키
          <input className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" value={idempotencyKey} onChange={(event) => setIdempotencyKey(event.target.value)} />
        </label>
        <div className="flex flex-wrap gap-3">
          <button type="button" className="rounded border px-4 py-2 focus-visible:ring-2 focus-visible:ring-blue-600" onClick={() => setIdempotencyKey(newIdempotencyKey())}>새 키</button>
          <button type="submit" disabled={requestsDisabled} className="rounded bg-blue-700 px-4 py-2 text-white focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50">예약</button>
          <button type="button" disabled={replayDisabled} className="rounded border px-4 py-2 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50" onClick={() => {
            if (!replayDisabled && lastAttempt) void reserve(lastAttempt);
          }}>같은 키로 다시 보내기</button>
        </div>
      </form>
      <ResultView title="예약 생성" result={pending ? null : result} idempotencyKey={resultKey} />
    </section>
  );
}
