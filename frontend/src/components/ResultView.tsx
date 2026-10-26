"use client";

import { Fragment, type ReactNode } from "react";
import { describeCode } from "@/lib/api/problem";
import type { ApiResult } from "@/lib/api/types";

export type ResultViewProps = {
  title: string;
  result: ApiResult | null;
  idempotencyKey?: string;
  children?: ReactNode;
};

export function ResultView({ title, result, idempotencyKey, children }: ResultViewProps) {
  if (!result) return null;
  const fields = result.ok && result.body !== null && typeof result.body === "object"
    ? Object.entries(result.body) : [];
  const headers = [
    ["Location", result.headers.location],
    ["Idempotent-Replayed", result.headers.idempotentReplayed],
    ["Retry-After", result.headers.retryAfter],
    ["X-Upstream-WWW-Authenticate", result.headers.upstreamWwwAuthenticate],
  ].filter(([, value]) => value !== undefined);
  return (
    <div role="status" aria-live="polite" aria-label={`${title} 결과`} className="space-y-3 rounded-lg bg-slate-100 p-4">
      <p className="font-semibold">{result.status === 0 ? "응답 없음" : `HTTP ${result.status}`}</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 break-all">
        {[...fields, ...headers].map(([name, value]) => (
          <Fragment key={String(name)}>
            <dt className="font-medium">{name}</dt>
            <dd>{String(value)}</dd>
          </Fragment>
        ))}
      </dl>
      {result.problem && <>
        <p>{result.problem.code}</p>
        <p>{result.problem.title}</p>
        <p>{result.problem.detail}</p>
        <p>{describeCode(result.problem.code)}</p>
      </>}
      {result.status === 429 && result.headers.retryAfter !== undefined && <p>{result.headers.retryAfter}초 후 다시 시도할 수 있습니다</p>}
      {result.rawText !== undefined && <>
        <p>알 수 없는 응답 형식</p>
        <pre className="whitespace-pre-wrap break-all">{result.rawText}</pre>
      </>}
      {result.ok && result.headers.idempotentReplayed !== undefined && <>
        <p>재현 여부: {result.headers.idempotentReplayed ? "예" : "아니오"}</p>
        {idempotencyKey !== undefined && <p>생성 키: {idempotencyKey}</p>}
        {result.headers.idempotentReplayed && <p>재현 응답은 최초 CONFIRMED 응답입니다. 현재 상태는 예약 조회로 확인하세요.</p>}
      </>}
      {children}
    </div>
  );
}
