import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";
import type { Account } from "@/lib/api/types";

const resourceId = "12345678-1234-4234-8234-123456789abc";
const otherResourceId = "abcdef01-2345-6789-abcd-ef0123456789";
const reservation = {
  id: "98765432-1234-4234-8234-123456789abc",
  resourceId,
  status: "CONFIRMED",
  createdAt: "2026-10-26T01:02:03.123456Z",
};
const replayNotice = "재현 응답은 최초 CONFIRMED 응답입니다. 현재 상태는 예약 조회로 확인하세요.";

function response(replayed = false) {
  return new Response(JSON.stringify(reservation), {
    status: 201,
    headers: {
      "Content-Type": "application/json",
      Location: `/api/reservations/${reservation.id}`,
      "Idempotent-Replayed": String(replayed),
    },
  });
}

async function applyCredentials(user: ReturnType<typeof userEvent.setup>, account: Account) {
  const panel = within(screen.getByRole("region", { name: "자격 증명" }));
  await user.selectOptions(panel.getByRole("combobox", { name: "계정" }), account);
  await user.clear(panel.getByLabelText("비밀번호", { exact: true }));
  await user.type(panel.getByLabelText("비밀번호", { exact: true }), "pw");
  await user.click(panel.getByRole("button", { name: "자격 증명 적용" }));
  expect(panel.getByText(`현재 계정: ${account}`, { exact: true })).toBeInTheDocument();
}

async function prepare() {
  const user = userEvent.setup();
  render(<Home />);
  await applyCredentials(user, "USER_A");
  return { user, panel: within(screen.getByRole("region", { name: "예약 생성" })) };
}

type Setup = Awaited<ReturnType<typeof prepare>>;

async function fillAttempt({ user, panel }: Setup, key = "attempt-key", target = resourceId) {
  const targetInput = panel.getByRole("textbox", { name: "예약할 대상 식별자" });
  const keyInput = panel.getByRole("textbox", { name: "생성 키" });
  await user.clear(targetInput);
  if (target) await user.type(targetInput, target);
  await user.clear(keyInput);
  if (key) await user.type(keyInput, key);
}

async function send(setup: Setup, key = "attempt-key", target = resourceId) {
  await fillAttempt(setup, key, target);
  await setup.user.click(setup.panel.getByRole("button", { name: "예약" }));
}

function requestAt(fetchMock: ReturnType<typeof vi.fn<typeof fetch>>, index = 0) {
  const [input, init] = fetchMock.mock.calls[index];
  return {
    url: input instanceof Request ? input.url : String(input),
    method: init?.method ?? (input instanceof Request ? input.method : undefined),
    headers: new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined)),
    body: init?.body,
  };
}

async function resultPanel(panel: Setup["panel"]) {
  return within(await panel.findByRole("status", { name: "예약 생성 결과" }));
}

function expectField(result: Awaited<ReturnType<typeof resultPanel>>, name: string, value: string) {
  const term = result.getByText(name, { exact: true, selector: "dt" });
  expect(term.nextElementSibling?.tagName).toBe("DD");
  expect(term.nextElementSibling?.textContent).toBe(value);
}

afterEach(() => { vi.unstubAllGlobals(); });

describe("예약 생성 키와 요청 원문", () => {
  it("생성 키가 처음부터 채워져 있고 새 키를 누르면 다른 유효 키를 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const { user, panel } = await prepare();
    const input = panel.getByRole("textbox", { name: "생성 키" }) as HTMLInputElement;
    const first = input.value;
    expect(first).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    await user.click(panel.getByRole("button", { name: "새 키" }));
    expect(input.value).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    expect(input.value).not.toBe(first);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["custom_KEY-1", resourceId],
    ["my key!", resourceId],
    ["custom_KEY-1", "  abc  "],
  ])("키 %j와 대상 %j를 수정하지 않고 JSON POST로 한 번 보낸다", async (key, target) => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup, key, target);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const request = requestAt(fetchMock);
    expect(request.url).toBe("/api/backend/reservations");
    expect(request.method).toBe("POST");
    expect(request.headers.get("Idempotency-Key")).toBe(key);
    expect(request.headers.get("Content-Type")).toBe("application/json");
    expect(request.body).toBe(`{"resourceId":${JSON.stringify(target)}}`);
  });

  it("생성 키를 비우면 키 헤더를 생략한 채 요청한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('{"code":"INVALID_REQUEST"}', {
      status: 400, headers: { "Content-Type": "application/problem+json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup, "");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(requestAt(fetchMock).headers.has("Idempotency-Key")).toBe(false);
  });
});

describe("최초 예약 결과와 직전 시도 재전송", () => {
  it("201의 모든 필드와 보존 헤더 및 최초 재현 여부와 사용한 키를 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup);
    const result = await resultPanel(setup.panel);
    expect(result.getByText("HTTP 201", { exact: true })).toBeInTheDocument();
    for (const [name, value] of Object.entries(reservation)) expectField(result, name, value);
    expectField(result, "Location", `/api/reservations/${reservation.id}`);
    expectField(result, "Idempotent-Replayed", "false");
    expect(result.getByText("재현 여부: 아니오", { exact: true })).toBeInTheDocument();
    expect(result.getByText("생성 키: attempt-key", { exact: true })).toBeInTheDocument();
  });

  it("입력을 바꿔도 같은 키 재전송은 직전 키와 대상을 쓰고 재현 안내를 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(response()).mockResolvedValueOnce(response(true));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup);
    await resultPanel(setup.panel);
    await fillAttempt(setup, "changed-key", otherResourceId);
    await setup.user.click(setup.panel.getByRole("button", { name: "같은 키로 다시 보내기" }));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const first = requestAt(fetchMock);
    const replay = requestAt(fetchMock, 1);
    expect(replay.url).toBe(first.url);
    expect(replay.method).toBe("POST");
    expect(replay.body).toBe(first.body);
    expect(replay.headers.get("Idempotency-Key")).toBe("attempt-key");
    expect([...replay.headers.entries()]).toEqual([...first.headers.entries()]);
    const result = await resultPanel(setup.panel);
    expect(await result.findByText("재현 여부: 예", { exact: true })).toBeInTheDocument();
    expect(result.getByText(replayNotice, { exact: true })).toBeInTheDocument();
    expect(result.getByText("생성 키: attempt-key", { exact: true })).toBeInTheDocument();
    expectField(result, "id", reservation.id);
    expectField(result, "createdAt", reservation.createdAt);
  });

  it("예약 시도 전에는 같은 키로 다시 보내기가 비활성이다", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const { user, panel } = await prepare();
    const button = panel.getByRole("button", { name: "같은 키로 다시 보내기" });
    expect(button).toBeDisabled();
    await user.click(button);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("시도 후 USER_B 자격 증명을 적용하면 이전 시도 재전송이 비활성이다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup);
    await resultPanel(setup.panel);
    expect(setup.panel.getByRole("button", { name: "같은 키로 다시 보내기" })).toBeEnabled();
    await applyCredentials(setup.user, "USER_B");
    const panel = within(screen.getByRole("region", { name: "예약 생성" }));
    expect(panel.getByRole("button", { name: "같은 키로 다시 보내기" })).toBeDisabled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("시도 후 새 키를 만들어도 재전송은 직전 시도의 키를 사용한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(response()).mockResolvedValueOnce(response(true));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup);
    await resultPanel(setup.panel);
    await setup.user.click(setup.panel.getByRole("button", { name: "새 키" }));
    expect(setup.panel.getByRole("textbox", { name: "생성 키" })).not.toHaveValue("attempt-key");
    await setup.user.click(setup.panel.getByRole("button", { name: "같은 키로 다시 보내기" }));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(requestAt(fetchMock, 1).headers.get("Idempotency-Key")).toBe("attempt-key");
    expect(requestAt(fetchMock, 1).body).toBe(`{"resourceId":"${resourceId}"}`);
  });
});

describe("예약 실패 구분과 자동 재시도 금지", () => {
  it.each([
    [409, "SOLD_OUT", "잔여 수량이 없어 예약할 수 없습니다(품절)."],
    [409, "IDEMPOTENCY_CONFLICT", "같은 생성 키가 다른 대상의 예약에 이미 사용되었습니다."],
    [429, "RATE_LIMITED", "쓰기 요청 제한을 넘었습니다."],
    [503, "DEPENDENCY_UNAVAILABLE", "백엔드 의존성(DB·Redis) 장애로 처리하지 못했습니다. 변경은 반영되지 않았습니다."],
    [502, "CONSOLE_UPSTREAM_UNREACHABLE", "백엔드에 연결할 수 없습니다. 백엔드 실행 여부를 확인하세요."],
  ] as const)("HTTP %s %s의 설명을 표시하고 자동 재시도하지 않는다", async (status, code, description) => {
    const problem = { title: "예약 실패", status, detail: "예약 요청을 처리하지 못했습니다.", code };
    const headers = new Headers({ "Content-Type": "application/problem+json" });
    if (status === 429) headers.set("Retry-After", "30");
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => new Response(JSON.stringify(problem), { status, headers }));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await send(setup);
    const result = await resultPanel(setup.panel);
    for (const text of [`HTTP ${status}`, code, description, problem.title, problem.detail]) {
      expect(result.getByText(text, { exact: true })).toBeInTheDocument();
    }
    if (status === 429) {
      expect(result.getByText("30초 후 다시 시도할 수 있습니다", { exact: true })).toBeInTheDocument();
      expectField(result, "Retry-After", "30");
    }
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
