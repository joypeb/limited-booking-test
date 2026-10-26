"use client";

import { useState } from "react";
import { ACCOUNTS, type Account } from "@/lib/api/types";
import { useConsole } from "./ConsoleProvider";

export function CredentialsPanel() {
  const { account, applyCredentials, clearCredentials } = useConsole();
  const [selectedAccount, setSelectedAccount] = useState<Account>("ADMIN");
  const [password, setPassword] = useState("");
  return (
    <section aria-label="자격 증명" className="space-y-4 rounded-xl border border-slate-300 bg-white p-6">
      <h2 className="text-xl font-semibold">자격 증명</h2>
      <form className="flex flex-wrap items-end gap-4" onSubmit={(event) => {
        event.preventDefault();
        applyCredentials({ account: selectedAccount, password });
        setPassword("");
      }}>
        <label className="grid gap-1">계정
          <select className="rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" value={selectedAccount} onChange={(event) => {
            setSelectedAccount(event.target.value as Account);
            setPassword("");
            clearCredentials();
          }}>
            {ACCOUNTS.map((name) => <option key={name} value={name}>{name}</option>)}
          </select>
        </label>
        <label className="grid gap-1">비밀번호
          <input className="rounded border p-2 focus-visible:ring-2 focus-visible:ring-blue-600" type="password" autoComplete="off" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        <button className="rounded bg-blue-700 px-4 py-2 text-white focus-visible:ring-2 focus-visible:ring-blue-600" type="submit">자격 증명 적용</button>
        <button className="rounded border px-4 py-2 focus-visible:ring-2 focus-visible:ring-blue-600" type="button" onClick={() => {
          clearCredentials();
          setPassword("");
        }}>자격 증명 지우기</button>
      </form>
      <p>{account ? `현재 계정: ${account}` : "자격 증명 없음"}</p>
    </section>
  );
}
