"use client";

import type { ApiResult } from "@/lib/api/types";

export type ResultViewProps = {
  title: string;
  result: ApiResult | null;
  idempotencyKey?: string;
};

export function ResultView(props: ResultViewProps) {
  void props;
  return null;
}
