import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";

const storageKey = "limited-booking-console:history:v1";
const password = "history-secret-password";
const resource = { id: "12345678-1234-4234-8234-123456789abc", name: "콘서트 A", capacity: 3, available: 2 };
const reservation = {
  id: "98765432-1234-4234-8234-123456789abc", resourceId: resource.id,
  status: "CONFIRMED", createdAt: "2026-10-26T01:02:03.123456Z", cancelledAt: null,
};
const savedEntry = { kind: "resource", id: resource.id, name: resource.name, recordedAt: "2026-10-26T00:00:00.000Z", account: "USER_A" };

function response(body: unknown = resource, status = 200) {
  return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": status === 200 ? "application/json" : "application/problem+json" },
  });
}

async function prepare() {
  const user = userEvent.setup();
  render(<Home />);
  const credentials = within(screen.getByRole("region", { name: "자격 증명" }));
  await user.selectOptions(credentials.getByRole("combobox", { name: "계정" }), "USER_A");
  await user.clear(credentials.getByLabelText("비밀번호", { exact: true }));
  await user.type(credentials.getByLabelText("비밀번호", { exact: true }), password);
  await user.click(credentials.getByRole("button", { name: "자격 증명 적용" }));
  expect(credentials.getByText("현재 계정: USER_A", { exact: true })).toBeInTheDocument();
  return user;
}

async function lookup(user: ReturnType<typeof userEvent.setup>, kind: "resource" | "reservation" = "resource") {
  const isResource = kind === "resource";
  const panel = within(screen.getByRole("region", { name: isResource ? "예약 대상" : "내 예약" }));
  const input = panel.getByRole("textbox", { name: isResource ? "대상 식별자" : "예약 식별자" });
  await user.clear(input);
  await user.type(input, isResource ? resource.id : reservation.id);
  await user.click(panel.getByRole("button", { name: isResource ? "대상 조회" : "예약 조회" }));
  await panel.findByRole("status", { name: isResource ? "예약 대상 결과" : "내 예약 결과" });
}

function history() { return within(screen.getByRole("region", { name: "최근 기록" })); }

function storedStrings() {
  return Array.from({ length: localStorage.length }, (_, index) => {
    const key = localStorage.key(index)!;
    return `${key}:${localStorage.getItem(key)}`;
  }).join("\n");
}

afterEach(() => { vi.unstubAllGlobals(); });

describe("최근 기록 표시와 저장", () => {
  it("처음에는 최근 기록 영역에 기록 없음을 표시한다", () => {
    render(<Home />);
    expect(history().getByText("기록 없음", { exact: true })).toBeInTheDocument();
  });

  it("대상 조회 성공 후 종류와 식별자 및 이름과 당시 계정을 기록한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    const user = await prepare();
    await lookup(user);
    const button = await history().findByRole("button", { name: `대상 ${resource.id} 다시 조회` });
    expect(button).toBeInTheDocument();
    for (const text of ["대상", resource.id, resource.name, "USER_A"]) expect(screen.getByRole("region", { name: "최근 기록" })).toHaveTextContent(text);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("예약 조회 성공 후 예약 식별자와 당시 계정을 기록한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response(reservation));
    vi.stubGlobal("fetch", fetchMock);
    const user = await prepare();
    await lookup(user, "reservation");
    const button = await history().findByRole("button", { name: `예약 ${reservation.id} 다시 조회` });
    expect(button).toBeInTheDocument();
    for (const text of ["예약", reservation.id, "USER_A"]) expect(screen.getByRole("region", { name: "최근 기록" })).toHaveTextContent(text);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each(["resource", "reservation"] as const)("%s 조회의 404 실패는 최근 기록에 추가하지 않는다", async (kind) => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response({ code: "NOT_FOUND", status: 404, title: "찾을 수 없음" }, 404));
    vi.stubGlobal("fetch", fetchMock);
    const user = await prepare();
    await lookup(user, kind);
    expect(history().getByText("기록 없음", { exact: true })).toBeInTheDocument();
    expect(history().queryByRole("button", { name: /다시 조회$/ })).not.toBeInTheDocument();
    const raw = localStorage.getItem(storageKey);
    expect(raw === null || JSON.parse(raw).length === 0).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("대상 기록을 선택하면 입력을 채우고 해당 대상으로 새 GET을 보낸다", async () => {
    localStorage.setItem(storageKey, JSON.stringify([savedEntry]));
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    const user = await prepare();
    await user.click(await history().findByRole("button", { name: `대상 ${resource.id} 다시 조회` }));
    const panel = within(screen.getByRole("region", { name: "예약 대상" }));
    expect(panel.getByRole("textbox", { name: "대상 식별자" })).toHaveValue(resource.id);
    await panel.findByRole("status", { name: "예약 대상 결과" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [input, init] = fetchMock.mock.calls[0];
    expect(input instanceof Request ? input.url : String(input)).toBe(`/api/backend/resources/${resource.id}`);
    expect(init?.method ?? (input instanceof Request ? input.method : undefined)).toBe("GET");
  });

  it("기록 지우기는 화면과 저장소의 기록을 모두 비운다", async () => {
    localStorage.setItem(storageKey, JSON.stringify([savedEntry]));
    const user = await prepare();
    await history().findByRole("button", { name: `대상 ${resource.id} 다시 조회` });
    await user.click(history().getByRole("button", { name: "기록 지우기" }));
    expect(history().getByText("기록 없음", { exact: true })).toBeInTheDocument();
    const raw = localStorage.getItem(storageKey);
    expect(raw === null || JSON.parse(raw).length === 0).toBe(true);
  });

  it("조회 기록을 저장해도 비밀번호와 수량 및 예약 상태는 저장하지 않는다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(response()).mockResolvedValueOnce(response(reservation));
    vi.stubGlobal("fetch", fetchMock);
    const user = await prepare();
    await lookup(user);
    await lookup(user, "reservation");
    await history().findByRole("button", { name: `예약 ${reservation.id} 다시 조회` });
    await waitFor(() => expect(JSON.parse(localStorage.getItem(storageKey)!)).toHaveLength(2));
    const raw = storedStrings();
    expect(raw).not.toContain(password);
    for (const field of ["password", "available", "status", "capacity"]) expect(raw).not.toContain(`"${field}"`);
  });

  it("저장된 기록은 페이지를 다시 열어도 표시한다", async () => {
    localStorage.setItem(storageKey, JSON.stringify([savedEntry]));
    render(<Home />);
    const button = await history().findByRole("button", { name: `대상 ${resource.id} 다시 조회` });
    expect(button).toBeInTheDocument();
    for (const text of ["대상", resource.id, resource.name, "USER_A", savedEntry.recordedAt]) expect(screen.getByRole("region", { name: "최근 기록" })).toHaveTextContent(text);
  });
});
