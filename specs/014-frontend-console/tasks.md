# Tasks: 로컬 브라우저 예약 콘솔

**입력**: [명세](spec.md), [계획](plan.md), [조사](research.md), [데이터 모델](data-model.md), [전달 계약](contracts/console-proxy.md), [화면 계약](contracts/console-ui.md), [실행 안내](quickstart.md)
**상태**: 2026-10-26 T001~T042 완료(npm test 263건·E2E 16건·백엔드 Unit 308건, 근거 `docs/test-evidence.md` 14단계 절). T010·US2~US4 RED는 경로가 달라 앞 단계 GREEN과 병행했다. 남은 작업: T043 로컬 리뷰~T048 PR 리뷰.

## 형식과 공통 실행 계약

`- [ ] T번호 [P 선택] [US번호] (담당) 단계: 설명·경로 — 선행: …`

- 담당(Paseo 프로필): **orch**(orchestrator: 실행 검증·문서·Git/PR), **scaf**(scaffolder), **ta**(test-author: `frontend/tests/**`·`frontend/e2e/**`만), **impl**(implementer: `frontend/src/**`, `frontend/next.config.ts`의 `headers()`만), **rev**(reviewer: 수정 금지), **adv**(advisor), **exp**(explorer).
- 단계: RED(ta가 인수 기준 테스트 작성) → RED 확인(orch: 의도한 단언 또는 `NOT_IMPLEMENTED`로 실패, import·타입·문법 오류는 RED 아님) → GREEN(impl 최소 구현) → GREEN 확인(orch) → REFACTOR(impl, 동작 불변) → VERIFY(orch: `npm test`·lint·typecheck·build, 해당 E2E) → REVIEW(rev 로컬 리뷰).
- 테스트 기대값을 바꾸려면 계약(spec·contracts)을 먼저 고친다. 구현에 맞추려고 테스트를 고치지 않는다. 같은 문제 3회 반복 또는 설계 판단 필요 시 adv.
- 같은 파일을 두 에이전트가 동시에 수정하지 않는다. `[P]`는 서로 다른 파일·선행 완료 시에만 병행.
- 매 단계 후 orch가 `git status`로 역할 밖 수정 여부를 확인한다. 위반 시 되돌리고 재할당.
- 커밋은 orch가 검증 대상별로(Conventional Commits, 한국어 제목, `검증:`·`미검증:` 본문).
- E2E 실행 전제: orch가 `reservation_e2e` DB·Redis DB 1로 백엔드를 기동(quickstart §3). 비밀번호는 셸 환경 변수.

## Phase 1: 준비

- [X] T001 (orch) 루트·브랜치 `feature/frontend-sdd-tdd-orchestration`·작업 트리, `node -v`·`npm -v`, 포트 3000 사용 가능 여부 확인·기록. — 선행: 없음
- [X] T002 (scaf) 골격: 저장소 루트에서 `npx create-next-app@16.4.3 frontend --ts --tailwind --eslint --app --src-dir --import-alias "@/*" --use-npm --disable-git --yes`. 이후 (a) `package.json` 의존성 `^` 제거·정확한 버전 고정(next 16.4.3, react·react-dom 19.3.0, tailwindcss·@tailwindcss/turbopack 4.3.3, typescript 5.9.3, eslint 9.39.5, eslint-config-next 16.4.3, @types/* 설치 해석값), (b) dev 의존성 추가(정확한 버전): vitest, @vitejs/plugin-react, vite-tsconfig-paths, jsdom, @testing-library/react, @testing-library/dom, @testing-library/user-event, @testing-library/jest-dom, @playwright/test, (c) 스크립트: `dev`=`next dev -H 127.0.0.1 -p 3000`, `start`=`next start -H 127.0.0.1 -p 3000`, `lint`=`eslint`, `typecheck`=`tsc --noEmit`, `test`=`vitest run --reporter=default --reporter=json --outputFile=test-results/vitest.json && node scripts/assert-vitest-report.mjs test-results/vitest.json`, `test:e2e`=`playwright test`, (d) `vitest.config.mts`: projects `unit`(node, `tests/unit/**/*.test.ts`), `proxy`(node, `tests/proxy/**/*.test.ts`), `component`(jsdom, `tests/component/**/*.test.tsx`, setup `tests/setup/component.ts`에서 jest-dom 등록), `allowOnly: false`, `passWithNoTests: false`, (e) `scripts/assert-vitest-report.mjs`: 총 0건·skipped/pending/todo > 0·failed > 0이면 종료 1과 사유 출력, (f) `playwright.config.ts`: `testDir: 'e2e'`, `forbidOnly: true`, `retries: 0`, `workers: 1`, `fullyParallel: false`(요청 제한 예산 공유), chromium 1개, `baseURL: http://127.0.0.1:3000`, `webServer`: `npm run build && npm run start`(url `http://127.0.0.1:3000`, `reuseExistingServer: false`), (g) `frontend/.gitignore`에 `test-results/`·`playwright-report/`·`blob-report/` 추가(템플릿 항목 유지), (h) 기본 `src/app/page.tsx`는 제목 `한정 수량 예약 콘솔`만 남기고 템플릿 이미지·링크 제거, `public/`의 미사용 템플릿 SVG 제거. 업무 로직·테스트 단언 금지. — 선행: T001
- [X] T003 (orch) 골격 검증: `npm ci`·`lint`·`typecheck`·`build` 종료 0. 테스트 0건 상태 `npm test` → 게이트 종료 1(0건 거부) 확인. 설치된 정확한 버전을 `docs/versions.md`에 기록. 커밋 `build(frontend): Next.js 16.4.3 콘솔 골격과 테스트 게이트를 추가한다`. — 선행: T002

## Phase 2: 기반 (모든 시나리오의 선행)

**독립 인수**: 전달 계층 계약 C1 전체와 공통 결과 표시·자격 증명이 테스트로 고정되고 통과.

- [X] T004 (scaf) 스텁: `src/lib/api/types.ts`(data-model의 ResourceView·CreatedReservationView·ReservationView·Problem·ApiResult·Account 타입), 함수 시그니처만 있고 본문은 `throw new Error('NOT_IMPLEMENTED')`인 `src/lib/proxy/routes.ts`(`matchRoute(method, segments)`), `src/lib/proxy/guard.ts`(`checkOrigin(headers, allowedPort)`), `src/lib/proxy/forward.ts`(`forward(request, segments, { baseUrl, fetchImpl, timeoutMs, allowedPort })`), `src/lib/api/bodies.ts`(`isJsonNumberLiteral`, `resourceCreateBody`, `reservationCreateBody`, `isUuid`), `src/lib/api/problem.ts`(`describeCode`, `parseResponse`), `src/lib/api/client.ts`(`callBackend`), `src/lib/basic-auth.ts`(`basicAuthHeader`), `src/lib/idempotency.ts`(`newIdempotencyKey`), `src/lib/history.ts`(`readHistory`, `recordHistory`, `clearHistory`), 컴포넌트 스텁 `src/components/{ConsoleProvider,CredentialsPanel,ResultView,ResourcePanel,ReservationCreatePanel,ReservationPanel,RecentHistory}.tsx`(`null` 렌더), Route Handler `src/app/api/backend/[...path]/route.ts`(GET·POST가 `forward` 호출). 로직 금지. — 선행: T003
- [X] T005 [P] (ta) RED Unit `tests/unit/`: `routes.test.ts`(C1.1 5개 허용, 목록·DELETE·`..`·`%2F`·UUID 아님·대소문자 UUID 원문 유지), `guard.test.ts`(C1.2: Host 허용 3종·포트 불일치·rebinding Host·Origin 불일치·`Sec-Fetch-Site: cross-site`·`same-origin`/`none` 허용), `bodies.test.ts`(JSON number 문법 `0`,`3`,`-1`,`1.0`,`1e2` 허용·`''`,`01`,`1.`,`abc`,` 3` 거부, 이름 공백·NBSP 원문 유지, 수량 원문 삽입 `{"name":"x","capacity":1.0}`), `problem.test.ts`(C2.4 17개 code 문구·알 수 없는 code·비JSON `rawText` 200자·`Idempotent-Replayed`/`Retry-After`/`X-Upstream-WWW-Authenticate` 헤더 해석), `basic-auth.test.ts`(UTF-8 비밀번호 Base64), `idempotency.test.ts`(`^[A-Za-z0-9_-]{1,64}$` 충족·연속 호출 상이). — 선행: T004
- [X] T006 [P] (ta) RED Proxy `tests/proxy/forward.test.ts`·`route.test.ts`: `node:http` 가짜 업스트림(동적 포트)으로 C1.3~C1.5 — 허용 헤더만 전달(`Cookie`·`X-User-Id` 미전달), 본문 원바이트(중복 키 JSON·8KiB+1 바이트 그대로), 0바이트 취소 본문 없음, 1MiB 초과 413 수신 0회, 응답 상태·본문·4개 헤더 보존, `WWW-Authenticate`→`X-Upstream-WWW-Authenticate`(원 헤더 없음), `Set-Cookie` 미전달, `Cache-Control: no-store`, 연결 거부 502, 지연 응답 504(짧은 timeoutMs), 비루프백 baseUrl 500, 거부 사례 수신 0회, 리다이렉트 비추종. — 선행: T004
- [X] T007 (orch) RED 확인: `npm test` 실패 사유가 단언/`NOT_IMPLEMENTED`인지 확인·기록(수·대표 실패). — 선행: T005, T006
- [X] T008 (impl) GREEN: `src/lib/proxy/*`, `src/lib/api/{bodies,problem,client}.ts`, `src/lib/basic-auth.ts`, `src/lib/idempotency.ts`, Route Handler. Next 16.4.3 번들 문서(`node_modules/next/dist/docs/01-app/01-getting-started/15-route-handlers.md`, `02-guides/backend-for-frontend.md`)를 먼저 읽는다. `BACKEND_BASE_URL`(기본 `http://127.0.0.1:8080`), 콘솔 포트는 서버 환경 변수 `CONSOLE_PORT`(기본 3000). — 선행: T007
- [X] T009 (orch) GREEN 확인·커밋 `feat(frontend): 계약 경로만 백엔드로 전달하는 콘솔 전달 계층을 추가한다`. — 선행: T008
- [X] T010 (ta) [T008과 병행: 경로가 달라 선행 완화, 2026-10-26] RED Component `tests/component/credentials.test.tsx`·`result-view.test.tsx`: C2.2 자격 증명(적용 시 `현재 계정: USER_A`, 지우기 시 `자격 증명 없음`, 요청 버튼 비활성), 비밀번호가 `localStorage`·`sessionStorage`·`document.cookie`에 없음, 요청에 `Authorization: Basic …` 포함·URL에 없음, 계정 변경 후 도착한 이전 응답 미표시(세대), 진행 중 중복 전송 차단, C2.3 결과(`HTTP 201`, `<dl>` 필드 원문, `null`, 헤더 dt, 오류 code·title·detail·설명, 429 `<n>초 후…`, `알 수 없는 응답 형식`, `응답 없음`). `fetch`만 경계 대체(실제 `Response` 객체). — 선행: T009
- [X] T011 (orch) RED 확인. — 선행: T010
- [X] T012 (impl) GREEN: `ConsoleProvider`·`CredentialsPanel`·`ResultView`, `src/app/page.tsx` 배치, `ResourcePanel`의 조회 부분(`console-requests.test.tsx`가 대상 조회로 인증 헤더·중복 전송·세대를 검증하므로 2026-10-26 범위 조정. 생성 부분은 T018). — 선행: T011
- [X] T013 (orch) GREEN 확인·커밋 `feat(frontend): 메모리 전용 자격 증명과 공통 결과 표시를 추가한다`. — 선행: T012
- [X] T014 (impl) REFACTOR(기반 전체, 동작 불변) → (orch) VERIFY 전체 `npm test`·lint·typecheck·build. — 선행: T013

## Phase 3: US1 — 관리자 대상 생성·조회 (P1)

**독립 인수**: 실제 백엔드에서 ADMIN 생성 201·조회 200, USER 403, 잘못된 비밀번호 401(대화상자 0·헤더 원문 표시), 400 원문 전달, 404.

- [X] T015 [P] [US1] (ta) RED Component `tests/component/resource-panel.test.tsx`: 생성 요청이 `POST /api/backend/resources`, `Content-Type: application/json`, 본문 원문(`"  "` 이름, `1.0`·`1001` 수량 그대로), 수량 `abc` 차단 안내(`최초 수량은 숫자 표기여야 합니다`, fetch 0회), 식별자 UUID 아님 차단(`식별자는 UUID 형식이어야 합니다`), 201 결과 필드·`Location`, 403/401/404/400 표시, `대상 다시 조회`. — 선행: T014
- [X] T016 [P] [US1] (ta) RED E2E `e2e/us1-resource.spec.ts`: 실제 백엔드로 수용 시나리오 US1-1~6, `page.on('dialog')` 0회, 콘솔 응답 보안 헤더(FR-019: `x-content-type-options: nosniff`, `referrer-policy: no-referrer`, `x-frame-options: DENY`, CSP `frame-ancestors 'none'`). 공통 도우미 `e2e/support/console.ts`(로그인·환경 변수 누락 시 즉시 실패·백엔드 health 사전 확인 실패 시 실패). — 선행: T014
- [X] T017 [US1] (orch) RED 확인(Component + E2E, 백엔드 e2e 기동). — 선행: T015, T016
- [X] T018 [US1] (impl) GREEN `ResourcePanel` + `next.config.ts` `headers()`(FR-019). — 선행: T017
- [X] T019 [US1] (orch) GREEN 확인(Component·E2E US1)·커밋 `feat(frontend): 관리자 대상 생성·조회 화면을 추가한다`. — 선행: T018
- [X] T020 [US1] (impl) REFACTOR → (orch) VERIFY. — 선행: T019

## Phase 4: US2 — 예약 생성·같은 키 재전송 (P1)

**독립 인수**: 실제 백엔드에서 201 재현 여부 아니오→예(같은 id·createdAt, 잔여 불변), 409 SOLD_OUT·IDEMPOTENCY_CONFLICT 구분, 키 원문 전송.

- [X] T021 [P] [US2] (ta) RED Component `tests/component/reservation-create-panel.test.tsx`: 자동 생성 키 표시·`새 키`, 요청 헤더 `Idempotency-Key` 원문·본문 `{"resourceId":"…"}`, `재현 여부: 아니오/예`·재현 안내 문구·`생성 키: …`, `같은 키로 다시 보내기`가 직전 키·대상 재사용, 계정 변경 시 비활성, 409 두 code 다른 설명, 429 남은 초, 503·502 다른 설명, 자동 재시도 없음(fetch 호출 1회), 빈 키면 헤더 생략. — 선행: T020
- [X] T022 [P] [US2] (ta) RED E2E `e2e/us2-reservation-create.spec.ts`: US2-1~4 실제 백엔드(대상은 테스트가 ADMIN으로 직접 생성), 재전송 후 대상 재조회 잔여 불변. 사용자별 쓰기 수를 요청 제한 미만으로 설계. — 선행: T020
- [X] T023 [US2] (orch) RED 확인. — 선행: T021, T022
- [X] T024 [US2] (impl) GREEN `ReservationCreatePanel`. — 선행: T023
- [X] T025 [US2] (orch) GREEN 확인·커밋 `feat(frontend): 예약 생성과 같은 키 재전송 화면을 추가한다`. — 선행: T024
- [X] T026 [US2] (impl) REFACTOR → (orch) VERIFY. — 선행: T025

## Phase 5: US3 — 예약 조회·취소 (P1)

**독립 인수**: 실제 백엔드에서 조회 CONFIRMED→취소 CANCELLED(잔여 +1)→재취소 같은 cancelledAt(잔여 불변), USER_B·ADMIN 404, 취소 후 같은 키 재전송은 CONFIRMED 재현.

- [X] T027 [P] [US3] (ta) RED Component `tests/component/reservation-panel.test.tsx`: 조회 `GET /api/backend/reservations/{id}`, 취소 `POST …/cancel` 본문 없음(요청 body 0바이트), `cancelledAt` `null` 표시, 404 설명, `대상 다시 조회`. — 선행: T026
- [X] T028 [P] [US3] (ta) RED E2E `e2e/us3-reservation-cancel.spec.ts`: US3-1~5. 마지막에 USER_B로 없는 예약 취소를 최대 21회 보내 429·`Retry-After` 표시를 1회 확인(앞선 USER_B 쓰기로 더 일찍 429가 나와도 통과, 21회 안에 429가 없으면 실패. 파일 내 마지막 테스트, 직렬). — 선행: T026
- [X] T029 [US3] (orch) RED 확인. — 선행: T027, T028
- [X] T030 [US3] (impl) GREEN `ReservationPanel`. — 선행: T029
- [X] T031 [US3] (orch) GREEN 확인·커밋 `feat(frontend): 내 예약 조회·취소 화면을 추가한다`. — 선행: T030
- [X] T032 [US3] (impl) REFACTOR → (orch) VERIFY. — 선행: T031

## Phase 6: US4 — 최근 기록 (P2)

**독립 인수**: 성공한 생성·조회가 기록되고 새로고침 후 선택 시 새로 조회, 지우기 후 `기록 없음`, 기록에 비밀번호·수량·상태 없음.

- [X] T033 [P] [US4] (ta) RED Unit `tests/unit/history.test.ts`(Storage 주입: 최대 50·최신순·중복 갱신·손상 JSON 빈 목록·허용 필드만 직렬화·키 `limited-booking-console:history:v1`) + Component `tests/component/recent-history.test.tsx`(C2.5 항목 텍스트·선택 시 입력 채움·조회 요청·`기록 지우기`·`기록 없음`·저장 값에 password/available/status 없음). — 선행: T032
- [X] T034 [P] [US4] (ta) RED E2E `e2e/us4-history.spec.ts`: 생성 → 새로고침 → 비밀번호 재입력 요구 → 기록 선택 재조회 → 지우기. `localStorage` 전체에 비밀번호 문자열 없음. — 선행: T032
- [X] T035 [US4] (orch) RED 확인. — 선행: T033, T034
- [X] T036 [US4] (impl) GREEN `src/lib/history.ts`·`RecentHistory`·각 패널의 기록 호출. — 선행: T035
- [X] T037 [US4] (orch) GREEN 확인·커밋 `feat(frontend): 이 브라우저의 최근 식별자 기록을 추가한다`. — 선행: T036
- [X] T038 [US4] (impl) REFACTOR → (orch) VERIFY 전체(Unit·Proxy·Component·E2E 전부). — 선행: T037

## Phase 7: 문서·규칙 동기화와 회귀 (orch)

- [X] T039 (orch) `knowledge/domain/frontend-console.md`(템플릿 기반: 책임·데이터·불변식·권한·오류·멱등성/경쟁·장애·검증 근거) 작성, `knowledge/README.md` 목록 등록. — 선행: T038
- [X] T040 (orch) 범위 문구 갱신: `AGENTS.md`(프로젝트 범위·실행 범위·기술 구조·기본 명령), 계획서 1절(24행)·3절 표(97행), `knowledge/rules/architecture.md`·`api-and-security.md`(CSRF·CORS 판단과 콘솔 경유 전제), `knowledge/domain/security.md`. 백엔드 규칙 의미는 유지. — 선행: T038
- [X] T041 (orch) `README.md` 콘솔 절(설치·실행·테스트·E2E 격리 절차), `docs/versions.md`, `docs/decisions.md`(전달 계층·CSRF·헤더 이름 변경·E2E 격리·CI 제외), `docs/test-evidence.md` 14단계 절(RED/GREEN 수·명령·종료 코드·E2E 결과·미검증), 루트 `.gitignore`(필요 시). — 선행: T038
- [X] T042 (orch) 백엔드 무변경 확인 `git diff --stat develop -- backend docs/openapi.yaml` 0줄, `(cd backend && ./gradlew test)` 종료 0·실행 수 기록, 문서 링크 검사(`scripts/check-readme.sh` 등 기존 검사기 실행). 커밋 `docs: 로컬 브라우저 콘솔의 범위·규칙·실행 기록을 반영한다`. — 선행: T039, T040, T041

## Phase 8: 리뷰·수렴·PR

- [X] T043 (rev) 로컬 전체 리뷰: `develop...HEAD` diff를 spec 인수 기준·contracts·헌법 VI·보안(헤더·출처·자격 증명 비영속·경로 우회)으로 검토, 심각도별 보고(수정 금지). — 선행: T042
### 로컬 리뷰 반영(T043 결과: Critical/High 0, Medium 3, Low 1)

- [X] T043a (orch) 계약 갱신: C1.1 OPTIONS·HEAD 명시 처리, C1.3 스트림 상한·10초 전체 기한, data-model·C2.2 생성 키 전송 차단, spec FR-008·US2-5, quickstart E2E 백엔드 18080. — 선행: T043
- [X] T043b (scaf) `playwright.config.ts` webServer `env.BACKEND_BASE_URL` = `E2E_BACKEND_BASE_URL ?? 'http://127.0.0.1:18080'`. — 선행: T043a
- [X] T043c (ta) RED: 생성 키 차단(Component), 미종료·길이 미지정 스트림 상한 초과 즉시 413·본문 읽기 기한 504(Proxy), Route Handler OPTIONS·HEAD 출처 검사 후 404(Proxy), 실제 Next HTTP 경계의 OPTIONS·HEAD 404·DELETE 405(E2E), E2E 지원 코드의 백엔드 주소 가드(8080이면 실패, 기본 18080). — 선행: T043b
- [X] T043d (impl) GREEN: ReservationCreatePanel 키 차단, forward 스트림 누적 상한·전체 기한, route.ts OPTIONS·HEAD export. — 선행: T043c
- [X] T043e (orch) 검증·기록·커밋, (rev) 재리뷰. — 선행: T043d
- [X] T043f (orch) 재리뷰 결과(M3 부분 해소, plan.md 미갱신 Low) 반영: plan D1·D6·quickstart §3·README를 실행기 방식으로 갱신. — 선행: T043e
- [X] T043g (scaf) `frontend/scripts/run-e2e.sh` 실행기와 `test:e2e` 스크립트 교체(quickstart §3의 (1)~(7)). — 선행: T043f
- [X] T043h (ta) `e2e/support/console.ts`가 `E2E_ISOLATED_BACKEND=1`이 없으면 실패. — 선행: T043f
- [X] T043i (orch) 실행기로 E2E 전체·가드 실패 확인·기록, (rev) 재확인. — 선행: T043g, T043h
- [ ] T044 (orch) 리뷰 분류 → 해당 담당 재할당(테스트 누락 ta, 구현 impl, 문서 orch) → 재검증. Critical/High 0까지 반복. — 선행: T043
- [ ] T045 (orch) Spec Kit Analyze 재실행·Converge. Converged가 아니면 누락 작업 추가·분배. — 선행: T044
- [ ] T046 (orch) push·Draft PR(develop 대상): 문제·변경 동작·실제 검증·미검증·읽을 파일. — 선행: T045
- [ ] T047 (rev) PR 정확한 diff에 Codex의 Alibaba Open Code Review를 `ocr` delegate 방식으로 실행, 유효 지적만 GitHub PR Review API/`gh`로 파일·라인 코멘트 게시(Actions 사용 금지, 수정 금지). — 선행: T046
- [ ] T048 (orch) PR 코멘트 조회·분류·재할당·수정·테스트·재리뷰. Critical/High·실패 테스트·누락 요구 0까지 반복. 병합은 사용자 승인 전 하지 않음. — 선행: T047

## 의존 관계 요약

T001 → T002 → T003 → T004 → (T005 ∥ T006) → T007 → T008 → T009 → T010 → … → T014 → US1(T015∥T016 …) → US2 → US3 → US4 → T039∥T040∥T041 → T042 → T043 … T048.
US2~US4는 화면 파일이 다르지만 `page.tsx` 배치와 E2E 요청 제한 예산을 공유하므로 순차 진행한다. MVP는 Phase 2 + US1.
