"use client";

import { useState } from "react";
import { isUuid } from "@/lib/api/bodies";
import { useConsole } from "./ConsoleProvider";
import { ResultView } from "./ResultView";
import { UuidInput } from "./UuidInput";
import { useBackendRequest } from "./useBackendRequest";
import { useHistoryLookup } from "./useHistoryLookup";
import { primaryButtonClassName, secondaryButtonClassName } from "./control-styles";

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

  function requestReservation(cancel: boolean, id = reservationId) {
    if (requestsDisabled) return;
    if (!isUuid(id)) {
      setInvalidId(true);
      return;
    }
    setInvalidId(false);
    return reservationRequest.send({
      method: cancel ? "POST" : "GET",
      path: `reservations/${id}${cancel ? "/cancel" : ""}`,
    });
  }

  useHistoryLookup("reservation", (id) => {
    setReservationId(id);
    void requestReservation(false, id);
  });

  return (
    <section aria-label="내 예약" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">내 예약</h2>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        void requestReservation(false);
      }}>
        <UuidInput
          label="예약 식별자"
          value={reservationId}
          invalid={invalidId}
          errorId="reservation-id-error"
          onChange={(value) => {
            setReservationId(value);
            setInvalidId(false);
          }}
        />
        <div className="flex flex-wrap gap-3">
          <button type="submit" disabled={requestsDisabled} className={primaryButtonClassName}>예약 조회</button>
          <button type="button" disabled={requestsDisabled} className={secondaryButtonClassName} onClick={() => { void requestReservation(true); }}>예약 취소</button>
        </div>
      </form>
      <ResultView title="내 예약" result={reservationRequest.result}>
        {resourceId !== undefined && (
          <button
            type="button"
            disabled={requestsDisabled}
            className={secondaryButtonClassName}
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
