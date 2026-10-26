"use client";

import { useState } from "react";
import { isJsonNumberLiteral, isUuid, resourceCreateBody } from "@/lib/api/bodies";
import { useConsole } from "./ConsoleProvider";
import { ResultView } from "./ResultView";
import { UuidInput } from "./UuidInput";
import { useBackendRequest } from "./useBackendRequest";
import { useHistoryLookup } from "./useHistoryLookup";
import { inputClassName, primaryButtonClassName, secondaryButtonClassName } from "./control-styles";

export function ResourcePanel() {
  const { generation } = useConsole();
  return <ResourcePanelContent key={generation} />;
}

function ResourcePanelContent() {
  const { result, requestsDisabled, send } = useBackendRequest();
  const [name, setName] = useState("");
  const [capacityText, setCapacityText] = useState("");
  const [invalidCapacity, setInvalidCapacity] = useState(false);
  const [resourceId, setResourceId] = useState("");
  const [invalidId, setInvalidId] = useState(false);
  function lookup(id = resourceId) {
    if (!isUuid(id)) {
      setInvalidId(true);
      return;
    }
    setInvalidId(false);
    setResourceId(id);
    return send({ method: "GET", path: `resources/${id}` });
  }

  useHistoryLookup("resource", (id) => { void lookup(id); });

  function create() {
    if (!isJsonNumberLiteral(capacityText)) {
      setInvalidCapacity(true);
      return;
    }
    setInvalidCapacity(false);
    return send({ method: "POST", path: "resources", body: resourceCreateBody(name, capacityText) });
  }

  const resultBody = result?.ok ? result.body : undefined;
  const resultId = resultBody !== null
    && typeof resultBody === "object"
    && "id" in resultBody
    && typeof resultBody.id === "string"
    ? resultBody.id
    : undefined;

  return (
    <section aria-label="예약 대상" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">예약 대상</h2>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        void create();
      }}>
        <label className="grid gap-1">대상 이름
          <input className={inputClassName} type="text" value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <label className="grid gap-1">최초 수량
          <input
            className={inputClassName}
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
        <button type="submit" disabled={requestsDisabled} className={primaryButtonClassName}>대상 생성</button>
      </form>
      <form className="space-y-3" onSubmit={(event) => {
        event.preventDefault();
        void lookup();
      }}>
        <UuidInput
          label="대상 식별자"
          value={resourceId}
          invalid={invalidId}
          errorId="resource-id-error"
          onChange={(value) => {
            setResourceId(value);
            setInvalidId(false);
          }}
        />
        <button type="submit" disabled={requestsDisabled} className={primaryButtonClassName}>대상 조회</button>
      </form>
      <ResultView title="예약 대상" result={result}>
        {resultId !== undefined && (
          <button
            type="button"
            disabled={requestsDisabled}
            className={secondaryButtonClassName}
            onClick={() => { void lookup(resultId); }}
          >대상 다시 조회</button>
        )}
      </ResultView>
    </section>
  );
}
