"use client";

import { useConsole } from "./ConsoleProvider";
import { secondaryButtonClassName } from "./control-styles";

export function RecentHistory() {
  const { history, credentials, selectEntry, clearEntries } = useConsole();
  return (
    <section aria-label="최근 기록" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">최근 기록</h2>
      {history.length === 0 ? <p>기록 없음</p> : (
        <ul className="space-y-3">
          {history.map((entry) => {
            const kind = entry.kind === "resource" ? "대상" : "예약";
            return (
              <li key={`${entry.kind}:${entry.id}`} className="space-y-2 break-all">
                <p>{[kind, entry.id, ...(entry.name !== undefined ? [entry.name] : []), entry.account, entry.recordedAt].join(" · ")}</p>
                <button type="button" disabled={!credentials} className={secondaryButtonClassName}
                  aria-label={`${kind} ${entry.id} 다시 조회`} onClick={() => selectEntry(entry)}>다시 조회</button>
              </li>
            );
          })}
        </ul>
      )}
      <button type="button" className={secondaryButtonClassName} onClick={clearEntries}>기록 지우기</button>
    </section>
  );
}
