import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";
import type { Account } from "@/lib/api/types";

const resource = {
  id: "12345678-1234-4234-8234-123456789abc",
  name: "콘서트 A",
  capacity: 3,
  available: 3,
};
const location = `/api/resources/${resource.id}`;

function resourceResponse(status = 201, capacity = 3) {
  return new Response(JSON.stringify({ ...resource, capacity, available: capacity }), {
    status,
    headers: { "Content-Type": "application/json", Location: location },
  });
}

async function prepare(account: Account = "ADMIN") {
  const user = userEvent.setup();
  render(<Home />);
  const credentials = within(screen.getByRole("region", { name: "자격 증명" }));
  await user.selectOptions(credentials.getByRole("combobox", { name: "계정" }), account);
  await user.clear(credentials.getByLabelText("비밀번호", { exact: true }));
  await user.type(credentials.getByLabelText("비밀번호", { exact: true }), "pw");
  await user.click(credentials.getByRole("button", { name: "자격 증명 적용" }));
  expect(credentials.getByText(`현재 계정: ${account}`, { exact: true })).toBeInTheDocument();
  return { user, panel: within(screen.getByRole("region", { name: "예약 대상" })) };
}

type ConsoleSetup = Awaited<ReturnType<typeof prepare>>;

async function create({ user, panel }: ConsoleSetup, name = "콘서트 A", capacity = "3") {
  const nameInput = panel.getByRole("textbox", { name: "대상 이름" });
  const capacityInput = panel.getByRole("textbox", { name: "최초 수량" });
  await user.clear(nameInput);
  if (name) await user.type(nameInput, name);
  await user.clear(capacityInput);
  if (capacity) await user.type(capacityInput, capacity);
  await user.click(panel.getByRole("button", { name: "대상 생성" }));
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

describe("예약 대상 생성의 입력 원문과 전송 차단", () => {
  it.each([
    ["  콘서트 A  ", "3", '{"name":"  콘서트 A  ","capacity":3}'],
    ["콘서트 A", "1.0", '{"name":"콘서트 A","capacity":1.0}'],
    ["콘서트 A", "1001", '{"name":"콘서트 A","capacity":1001}'],
    ["   ", "3", '{"name":"   ","capacity":3}'],
  ])("이름 %j와 수량 %s를 원문 그대로 JSON POST로 한 번 보낸다", async (name, capacity, expectedBody) => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(resourceResponse());
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await create(setup, name, capacity);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const request = requestAt(fetchMock);
    expect(request.url).toBe("/api/backend/resources");
    expect(request.method).toBe("POST");
    expect(request.headers.get("Content-Type")).toBe("application/json");
    expect(request.body).toBe(expectedBody);
  });

  it.each(["abc", ""])("수량 %j는 전송하지 않고 숫자 표기 안내를 표시한다", async (capacity) => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await create(setup, "콘서트 A", capacity);
    expect(setup.panel.getByRole("alert")).toHaveTextContent("최초 수량은 숫자 표기여야 합니다");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("예약 대상 응답 표시와 다시 조회", () => {
  it.each([3, 0])("수량 %s 생성의 201 결과 필드와 Location을 표시한다", async (capacity) => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(resourceResponse(201, capacity));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await create(setup, "콘서트 A", String(capacity));
    const result = await setup.panel.findByRole("status", { name: "예약 대상 결과" });
    expect(within(result).getByText("HTTP 201", { exact: true })).toBeInTheDocument();
    expectField(result, "id", resource.id);
    expectField(result, "name", resource.name);
    expectField(result, "capacity", String(capacity));
    expectField(result, "available", String(capacity));
    expectField(result, "Location", location);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each<{
    status: number; code: string; description: string; account: Account; name: string; capacity: string;
  }>([
    { status: 403, code: "FORBIDDEN", description: "이 계정에는 해당 작업 권한이 없습니다.", account: "USER_A", name: "콘서트 A", capacity: "3" },
    { status: 401, code: "UNAUTHENTICATED", description: "인증에 실패했습니다. 계정과 비밀번호를 확인하세요.", account: "ADMIN", name: "콘서트 A", capacity: "3" },
    { status: 400, code: "INVALID_REQUEST", description: "요청 형식이나 입력값이 계약에 맞지 않습니다.", account: "ADMIN", name: "   ", capacity: "3" },
    { status: 400, code: "INVALID_REQUEST", description: "요청 형식이나 입력값이 계약에 맞지 않습니다.", account: "ADMIN", name: "콘서트 A", capacity: "1001" },
    { status: 400, code: "INVALID_REQUEST", description: "요청 형식이나 입력값이 계약에 맞지 않습니다.", account: "ADMIN", name: "콘서트 A", capacity: "1.0" },
  ])("$account 생성의 $status $code 오류를 표시한다(이름 $name, 수량 $capacity)", async ({ status, code, description, account, name, capacity }) => {
    const challenge = 'Basic realm="limited-booking", charset="UTF-8"';
    const problem = { type: "about:blank", title: "생성 요청 오류", status, detail: "백엔드가 생성 요청을 거부했습니다.", code };
    const headers = new Headers({ "Content-Type": "application/problem+json" });
    if (status === 401) headers.set("X-Upstream-WWW-Authenticate", challenge);
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(problem), { status, headers }));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare(account);
    await create(setup, name, capacity);
    const result = within(await setup.panel.findByRole("status", { name: "예약 대상 결과" }));
    for (const text of [`HTTP ${status}`, code, description, problem.title, problem.detail]) {
      expect(result.getByText(text, { exact: true })).toBeInTheDocument();
    }
    if (status === 401) {
      expect(result.getByText("X-Upstream-WWW-Authenticate", { exact: true })).toBeInTheDocument();
      expect(result.getByText(challenge, { exact: true })).toBeInTheDocument();
    }
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("없는 대상을 조회하면 404 코드와 한국어 설명을 표시한다", async () => {
    const problem = { title: "조회 요청 오류", status: 404, detail: "요청한 예약 대상이 없습니다.", code: "NOT_FOUND" };
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(problem), {
      status: 404, headers: { "Content-Type": "application/problem+json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const { user, panel } = await prepare("USER_A");
    await user.type(panel.getByRole("textbox", { name: "대상 식별자" }), resource.id);
    await user.click(panel.getByRole("button", { name: "대상 조회" }));
    const result = within(await panel.findByRole("status", { name: "예약 대상 결과" }));
    for (const text of ["HTTP 404", "NOT_FOUND", "대상을 찾을 수 없거나 접근할 수 없습니다.", problem.title, problem.detail]) {
      expect(result.getByText(text, { exact: true })).toBeInTheDocument();
    }
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(requestAt(fetchMock)).toMatchObject({ url: `/api/backend/resources/${resource.id}`, method: "GET" });
  });

  it("UUID가 아닌 조회 식별자는 전송하지 않고 형식 안내를 표시한다", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const { user, panel } = await prepare("USER_A");
    await user.type(panel.getByRole("textbox", { name: "대상 식별자" }), "abc");
    await user.click(panel.getByRole("button", { name: "대상 조회" }));
    expect(panel.getByRole("alert")).toHaveTextContent("식별자는 UUID 형식이어야 합니다");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("생성 결과의 대상 다시 조회 버튼은 결과 식별자로 GET을 보낸다", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(resourceResponse())
      .mockResolvedValueOnce(resourceResponse(200));
    vi.stubGlobal("fetch", fetchMock);
    const setup = await prepare();
    await create(setup);
    const result = await setup.panel.findByRole("status", { name: "예약 대상 결과" });
    await setup.user.click(within(result).getByRole("button", { name: "대상 다시 조회" }));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(requestAt(fetchMock, 1)).toMatchObject({ url: `/api/backend/resources/${resource.id}`, method: "GET" });
    expect(await setup.panel.findByText("HTTP 200", { exact: true })).toBeInTheDocument();
  });
});
