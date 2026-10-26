import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ConsoleProvider } from "@/components/ConsoleProvider";
import { CredentialsPanel } from "@/components/CredentialsPanel";

function renderCredentials() {
  render(<ConsoleProvider><CredentialsPanel /></ConsoleProvider>);
  return within(screen.getByRole("region", { name: "자격 증명" }));
}

describe("자격 증명", () => {
  it("계정 선택과 비밀번호 입력 및 적용·지우기 버튼을 제공한다", () => {
    const panel = renderCredentials();
    const account = panel.getByRole("combobox", { name: "계정" });
    expect(within(account).getAllByRole("option").map((option) => [option.getAttribute("value"), option.textContent]))
      .toEqual([["ADMIN", "ADMIN"], ["USER_A", "USER_A"], ["USER_B", "USER_B"]]);
    expect(panel.getByLabelText("비밀번호", { exact: true })).toHaveAttribute("type", "password");
    expect(panel.getByLabelText("비밀번호", { exact: true })).toHaveAttribute("autocomplete", "off");
    expect(panel.getByRole("button", { name: "자격 증명 적용" })).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "자격 증명 지우기" })).toBeInTheDocument();
    expect(panel.getByText("자격 증명 없음", { exact: true })).toBeInTheDocument();
  });

  it("USER_A를 적용하고 지우면 인증 상태와 비밀번호가 비워진다", async () => {
    const user = userEvent.setup();
    const panel = renderCredentials();
    expect(panel.getByText("자격 증명 없음", { exact: true })).toBeInTheDocument();
    await user.selectOptions(panel.getByRole("combobox", { name: "계정" }), "USER_A");
    await user.type(panel.getByLabelText("비밀번호", { exact: true }), "memory-only-password");
    await user.click(panel.getByRole("button", { name: "자격 증명 적용" }));
    expect(panel.getByText("현재 계정: USER_A", { exact: true })).toBeInTheDocument();
    await user.click(panel.getByRole("button", { name: "자격 증명 지우기" }));
    expect(panel.getByText("자격 증명 없음", { exact: true })).toBeInTheDocument();
    expect(panel.queryByText("현재 계정: USER_A", { exact: true })).not.toBeInTheDocument();
    expect(panel.getByLabelText("비밀번호", { exact: true })).toHaveValue("");
  });

  it("적용한 비밀번호를 브라우저 저장소나 쿠키에 남기지 않는다", async () => {
    const user = userEvent.setup();
    const panel = renderCredentials();
    const password = "unique-memory-secret-789";
    await user.selectOptions(panel.getByRole("combobox", { name: "계정" }), "USER_A");
    await user.type(panel.getByLabelText("비밀번호", { exact: true }), password);
    await user.click(panel.getByRole("button", { name: "자격 증명 적용" }));
    expect(panel.getByText("현재 계정: USER_A", { exact: true })).toBeInTheDocument();
    for (const storage of [localStorage, sessionStorage]) {
      for (let index = 0; index < storage.length; index += 1) {
        expect(storage.getItem(storage.key(index)!)).not.toContain(password);
      }
    }
    expect(document.cookie).not.toContain(password);
  });
});
