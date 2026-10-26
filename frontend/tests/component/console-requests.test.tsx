import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";
import type { Account } from "@/lib/api/types";

const resource = {
  id: "12345678-1234-4234-8234-123456789abc",
  name: "이전 계정의 대상",
  capacity: 7,
  available: 6,
};

function response() {
  return new Response(JSON.stringify(resource), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function pendingResponse() {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>((done) => { resolve = done; });
  return { promise, resolve };
}

async function applyCredentials(user: ReturnType<typeof userEvent.setup>, account: Account) {
  const panel = within(screen.getByRole("region", { name: "자격 증명" }));
  await user.selectOptions(panel.getByRole("combobox", { name: "계정" }), account);
  await user.clear(panel.getByLabelText("비밀번호", { exact: true }));
  await user.type(panel.getByLabelText("비밀번호", { exact: true }), "pw");
  await user.click(panel.getByRole("button", { name: "자격 증명 적용" }));
  expect(panel.getByText(`현재 계정: ${account}`, { exact: true })).toBeInTheDocument();
}

async function prepareLookup(user: ReturnType<typeof userEvent.setup>) {
  await applyCredentials(user, "USER_A");
  const panel = within(screen.getByRole("region", { name: "예약 대상" }));
  await user.type(panel.getByRole("textbox", { name: "대상 식별자" }), resource.id);
  return panel.getByRole("button", { name: "대상 조회" });
}

afterEach(() => { vi.unstubAllGlobals(); });

describe("콘솔 요청의 자격 증명과 세대", () => {
  it("자격 증명이 없으면 대상 조회를 비활성화한다", () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    render(<Home />);
    expect(screen.getByRole("button", { name: "대상 조회" })).toBeDisabled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("인증 헤더를 넣어 같은 출처로 조회하고 비밀번호를 URL에 넣지 않는다", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    render(<Home />);
    await user.click(await prepareLookup(user));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [input, init] = fetchMock.mock.calls[0];
    const url = input instanceof Request ? input.url : String(input);
    expect(url).toBe(`/api/backend/resources/${resource.id}`);
    expect(url).not.toContain("pw");
    const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
    expect(headers.get("Authorization")).toBe(`Basic ${btoa("USER_A:pw")}`);
  });

  it("응답 대기 중에는 같은 조회를 두 번 전송하지 않는다", async () => {
    const user = userEvent.setup();
    const pending = pendingResponse();
    const fetchMock = vi.fn<typeof fetch>().mockReturnValue(pending.promise);
    vi.stubGlobal("fetch", fetchMock);
    render(<Home />);
    const button = await prepareLookup(user);
    await user.click(button);
    expect(button).toBeDisabled();
    await user.click(button);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => { pending.resolve(response()); await pending.promise; });
  });

  it("계정 재적용 후 도착한 이전 세대의 응답을 표시하지 않는다", async () => {
    const user = userEvent.setup();
    const pending = pendingResponse();
    const fetchMock = vi.fn<typeof fetch>().mockReturnValue(pending.promise);
    vi.stubGlobal("fetch", fetchMock);
    render(<Home />);
    await user.click(await prepareLookup(user));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await applyCredentials(user, "USER_B");
    await act(async () => { pending.resolve(response()); await pending.promise; });
    const result = screen.queryByRole("status", { name: "예약 대상 결과" });
    if (result) {
      expect(within(result).queryByText(resource.id, { exact: true })).not.toBeInTheDocument();
      expect(within(result).queryByText(resource.name, { exact: true })).not.toBeInTheDocument();
      expect(within(result).queryByText("HTTP 200", { exact: true })).not.toBeInTheDocument();
    }
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
