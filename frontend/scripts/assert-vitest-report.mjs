import { readFile } from "node:fs/promises";

const reportPath = process.argv[2];
const fail = (reason) => {
  console.error(`vitest gate: ${reason}`);
  process.exit(1);
};

if (!reportPath) {
  fail("보고서 경로 인자가 필요합니다");
}

let raw;
try {
  raw = await readFile(reportPath, "utf8");
} catch (error) {
  fail(`보고서를 읽을 수 없습니다: ${reportPath} (${error?.code ?? error?.message ?? error})`);
}

let report;
try {
  report = JSON.parse(raw);
} catch (error) {
  fail(`보고서가 올바른 JSON이 아닙니다: ${reportPath} (${error?.message ?? error})`);
}

const total = Number(report.numTotalTests ?? 0);
const passed = Number(report.numPassedTests ?? 0);
const failed = Number(report.numFailedTests ?? 0);
const pending = Number(report.numPendingTests ?? 0);
const todo = Number(report.numTodoTests ?? 0);

const skippedStatuses = new Set(["pending", "skipped", "todo", "disabled"]);
const skippedTests = [];
for (const testResult of report.testResults ?? []) {
  for (const assertion of testResult.assertionResults ?? []) {
    if (skippedStatuses.has(assertion.status)) {
      skippedTests.push(assertion.fullName || assertion.title || "(제목 없음)");
    }
  }
}

const failures = [];
if (!Number.isFinite(total) || total === 0) {
  failures.push("테스트가 0건입니다");
}
if (failed > 0) {
  failures.push(`실패한 테스트가 ${failed}건 있습니다`);
}
if (pending > 0) {
  failures.push(`skip 상태 테스트가 ${pending}건 있습니다`);
}
if (todo > 0) {
  failures.push(`todo 상태 테스트가 ${todo}건 있습니다`);
}
if (skippedTests.length > 0) {
  failures.push(
    `skip/pending/todo 상태 테스트 ${skippedTests.length}건: ${skippedTests.join(", ")}`,
  );
}

if (failures.length > 0) {
  fail(failures.join(" / "));
}

console.log(`vitest gate: total=${total} passed=${passed}`);
