import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";

const reservation = {
  id: "98765432-1234-4234-8234-123456789abc",
  resourceId: "12345678-1234-4234-8234-123456789abc",
  status: "CONFIRMED",
  createdAt: "2026-10-26T01:02:03.123456Z",
  cancelledAt: null,
};
const cancelledAt = "2026-10-26T02:03:04.654321Z";
const cancelled = { ...reservation, status: "CANCELLED", cancelledAt };

function response(body: unknown = reservation, status = 200) {
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
  await user.type(credentials.getByLabelText("비밀번호", { exact: true }), "pw");
  await user.click(credentials.getByRole("button", { name: "자격 증명 적용" }));
  expect(credentials.getByText("현재 계정: USER_A", { exact: true })).toBeInTheDocument();
  return { user, panel: within(screen.getByRole("region", { name: "내 예약" })) };
}

type Setup = Awaited<ReturnType<typeof prepare>>;

async function enterId({ user, panel }: Setup, id = reservation.id) {
  const input = panel.getByRole("textbox", { name: "예약 식별자" });
  await user.clear(input);
  await user.type(input, id);
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

function expectField(result: HTMLElement, name: string, value: string) {
  const term = within(result).getByText(name, { exact: true, selector: "dt" });
  expect(term.nextElementSibling?.tagName).toBe("DD");
  expect(term.nextElementSibling?.textContent).toBe(value);
}

afterEach(() => { vi.unstubAllGlobals(); });

describe("내 예약 조회와 취소", () => {
  it("예약 조회는 GET을 한 번 보내고 CONFIRMED와 취소 시각 null을 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await enterId(setup);
    await setup.user.click(setup.panel.getByRole("button", { name: "예약 조회" }));
    const result = await setup.panel.findByRole("status", { name: "내 예약 결과" });
    expect(within(result).getByText("HTTP 200", { exact: true })).toBeInTheDocument();
    for (const [name, value] of Object.entries(reservation)) expectField(result, name, String(value));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(requestAt(fetchMock)).toMatchObject({ url: `/api/backend/reservations/${reservation.id}`, method: "GET" });
  });

  it("예약 취소는 본문과 Content-Type 없는 POST를 보내고 취소 상태와 시각 원문을 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response(cancelled));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await enterId(setup);
    await setup.user.click(setup.panel.getByRole("button", { name: "예약 취소" }));
    const result = await setup.panel.findByRole("status", { name: "내 예약 결과" });
    expect(within(result).getByText("HTTP 200", { exact: true })).toBeInTheDocument();
    expectField(result, "status", "CANCELLED");
    expectField(result, "cancelledAt", cancelledAt);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const request = requestAt(fetchMock);
    expect(request.url).toBe(`/api/backend/reservations/${reservation.id}/cancel`);
    expect(request.method).toBe("POST");
    expect([undefined, null, ""]).toContain(request.body);
    expect(request.headers.has("Content-Type")).toBe(false);
  });

  it("다시 취소해도 백엔드가 반환한 최초 취소 시각을 그대로 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => response(cancelled));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await enterId(setup);
    const button = setup.panel.getByRole("button", { name: "예약 취소" });
    await setup.user.click(button);
    const result = await setup.panel.findByRole("status", { name: "내 예약 결과" });
    expectField(result, "cancelledAt", cancelledAt);
    await waitFor(() => expect(button).toBeEnabled());
    await setup.user.click(button);
    await waitFor(() => expect(button).toBeEnabled());
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(requestAt(fetchMock, 1)).toMatchObject({ url: `/api/backend/reservations/${reservation.id}/cancel`, method: "POST" });
    const latest = setup.panel.getByRole("status", { name: "내 예약 결과" });
    expectField(latest, "status", "CANCELLED");
    expectField(latest, "cancelledAt", cancelledAt);
  });

  it.each(["예약 조회", "예약 취소"])("%s의 404는 NOT_FOUND 코드와 접근 불가 설명을 표시한다", async (action) => {
    const problem = { title: "예약을 찾을 수 없습니다", detail: "예약이 없거나 소유자가 아닙니다.", status: 404, code: "NOT_FOUND" };
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response(problem, 404));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await enterId(setup);
    await setup.user.click(setup.panel.getByRole("button", { name: action }));
    const result = within(await setup.panel.findByRole("status", { name: "내 예약 결과" }));
    for (const text of ["HTTP 404", "NOT_FOUND", "대상을 찾을 수 없거나 접근할 수 없습니다.", problem.title, problem.detail]) {
      expect(result.getByText(text, { exact: true })).toBeInTheDocument();
    }
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each(["예약 조회", "예약 취소"])("UUID가 아닌 식별자의 %s는 전송 없이 형식 안내를 표시한다", async (action) => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await enterId(setup, "abc");
    await setup.user.click(setup.panel.getByRole("button", { name: action }));
    expect(setup.panel.getByRole("alert")).toHaveTextContent("식별자는 UUID 형식이어야 합니다");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(["예약 조회", "예약 취소"])("%s 결과에서 대상 다시 조회를 누르면 결과의 resourceId로 GET을 보낸다", async (action) => {
    const resource = { id: reservation.resourceId, name: "콘서트 A", capacity: 3, available: 2 };
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(action === "예약 취소" ? cancelled : reservation))
      .mockResolvedValueOnce(response(resource));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await enterId(setup);
    await setup.user.click(setup.panel.getByRole("button", { name: action }));
    const result = await setup.panel.findByRole("status", { name: "내 예약 결과" });
    await setup.user.click(within(result).getByRole("button", { name: "대상 다시 조회" }));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(requestAt(fetchMock, 1)).toMatchObject({ url: `/api/backend/resources/${reservation.resourceId}`, method: "GET" });
  });

  it("자격 증명이 없으면 예약 조회와 예약 취소를 비활성화한다", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<Home />);
    const panel = within(screen.getByRole("region", { name: "내 예약" }));
    for (const name of ["예약 조회", "예약 취소"]) {
      const button = panel.getByRole("button", { name });
      expect(button).toBeDisabled();
      await user.click(button);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
