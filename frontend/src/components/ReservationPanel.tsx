"use client";

import { useState } from "react";
import { isUuid } from "@/lib/api/bodies";
import { useConsole } from "./ConsoleProvider";
import { ResultView } from "./ResultView";
import { useBackendRequest } from "./useBackendRequest";

export function ReservationPanel() {
  const { generation } = useConsole();
  return <ReservationPanelContent key={generation} />;
}

function ReservationPanelContent() {
  const reservationRequest = useBackendRequest();
  const resourceRequest = useBackendRequest();
  const [reservationId, setReservationId] = useState("");
  const [invalidId, setInvalidId] = useState(false);
  const requestsDisabled = reservationRequest.requestsDisabled || resourceRequest.requestsDisabled;
  const resultBody = reservationRequest.result?.ok ? reservationRequest.result.body : undefined;
  const resourceId = resultBody !== null
    && typeof resultBody === "object"
    && "resourceId" in resultBody
    && typeof resultBody.resourceId === "string"
    ? resultBody.resourceId
    : undefined;

  function requestReservation(cancel: boolean) {
    if (requestsDisabled) return;
    if (!isUuid(reservationId)) {
      setInvalidId(true);
      return;
    }
    setInvalidId(false);
    return reservationRequest.send({
      method: cancel ? "POST" : "GET",
      path: `reservations/${reservationId}${cancel ? "/cancel" : ""}`,
    });
  }

  return (
    <section aria-label="내 예약" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">내 예약</h2>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        void requestReservation(false);
      }}>
        <label className="grid gap-1">예약 식별자
          <input
            className="w-full rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600"
            value={reservationId}
            aria-invalid={invalidId}
            aria-describedby={invalidId ? "reservation-id-error" : undefined}
            onChange={(event) => {
              setReservationId(event.target.value);
              setInvalidId(false);
            }}
          />
        </label>
        {invalidId && <p id="reservation-id-error" role="alert">식별자는 UUID 형식이어야 합니다</p>}
        <div className="flex flex-wrap gap-3">
          <button type="submit" disabled={requestsDisabled} className="rounded bg-blue-700 px-4 py-2 text-white focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50">예약 조회</button>
          <button type="button" disabled={requestsDisabled} className="rounded border px-4 py-2 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50" onClick={() => { void requestReservation(true); }}>예약 취소</button>
        </div>
      </form>
      <ResultView title="내 예약" result={reservationRequest.result}>
        {resourceId !== undefined && (
          <button
            type="button"
            disabled={requestsDisabled}
            className="rounded border px-4 py-2 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-50"
            onClick={() => {
              if (!requestsDisabled && isUuid(resourceId)) {
                void resourceRequest.send({ method: "GET", path: `resources/${resourceId}` });
              }
            }}
          >대상 다시 조회</button>
        )}
      </ResultView>
      <ResultView title="내 예약 대상" result={resourceRequest.result} />
    </section>
  );
}
