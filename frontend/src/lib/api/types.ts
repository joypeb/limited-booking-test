export type Account = "ADMIN" | "USER_A" | "USER_B";

export const ACCOUNTS: readonly Account[] = ["ADMIN", "USER_A", "USER_B"];

export type ResourceView = {
  id: string;
  name: string;
  capacity: number;
  available: number;
};

export type CreatedReservationView = {
  id: string;
  resourceId: string;
  status: "CONFIRMED";
  createdAt: string;
};

export type ReservationView = {
  id: string;
  resourceId: string;
  status: "CONFIRMED" | "CANCELLED";
  createdAt: string;
  cancelledAt: string | null;
};

export type Problem = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
};

export type ApiResultHeaders = {
  location?: string;
  idempotentReplayed?: boolean;
  retryAfter?: string;
  upstreamWwwAuthenticate?: string;
};

export type ApiResult = {
  status: number;
  ok: boolean;
  body?: unknown;
  problem?: Problem;
  rawText?: string;
  headers: ApiResultHeaders;
  source: "backend" | "console";
};

export type Credentials = {
  account: Account;
  password: string;
};

export type HistoryEntry = {
  kind: "resource" | "reservation";
  id: string;
  name?: string;
  recordedAt: string;
  account: Account;
};
