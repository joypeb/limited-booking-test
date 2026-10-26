# Implementation Plan: 로컬 브라우저 예약 콘솔

**Branch**: `feature/frontend-sdd-tdd-orchestration` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-frontend-console/spec.md`

## Summary

완료된 백엔드 API 5개를 사람이 브라우저에서 호출·관찰하는 localhost 콘솔을 `frontend/`에 만든다. 브라우저는 같은 출처의 Next.js Route Handler(콘솔 서버 전달 계층)만 호출하고, 전달 계층이 경로·메서드·출처를 검사한 뒤 백엔드에 원바이트 그대로 전달한다. 백엔드 코드·계약·보안 설정은 바꾸지 않는다. 화면은 단일 페이지의 기능별 패널(자격 증명, 대상 생성·조회, 예약 생성·재전송, 예약 조회·취소, 최근 기록)과 공통 결과 표시로 구성한다. TDD는 순수 규칙·전달 계층·화면(Vitest)과 실제 백엔드 종단(Playwright)으로 나눈다.

## Technical Context

**Language/Version**: TypeScript 5.9.3(create-next-app 16.4.3 기본 `^5`의 조사일 해석값, 정확한 버전 고정), Node.js 26.11.1(로컬 확인, Next 요구 `>=20.9.0`)

**Primary Dependencies**: next 16.4.3, react 19.3.0, react-dom 19.3.0(create-next-app 16.4.3이 고정하는 값), tailwindcss 4.3.3 + @tailwindcss/turbopack 4.3.3(Next 16.4.3 템플릿의 Tailwind 통합 방식), eslint 9.39.5 + eslint-config-next 16.4.3. 모든 의존성은 `^` 없이 정확한 버전으로 고정하고 `package-lock.json`을 커밋한다.

**Storage**: 서버 저장소 없음. 브라우저 `localStorage`에 최근 기록(식별자 메모)만. 비밀번호는 React 메모리 상태에만.

**Testing**: Vitest(정확한 버전은 설치 시 확인·기록) + @testing-library/react + jsdom(화면), Node 환경 Vitest(순수 규칙·전달 계층, 실제 로컬 HTTP 가짜 업스트림), Playwright(@playwright/test, 실제 백엔드 종단). 0건·skip·실패를 거부하는 결과 게이트 스크립트를 둔다.

**Target Platform**: localhost 데스크톱 브라우저(Chromium 계열 기준), 콘솔 `127.0.0.1:3000`, 백엔드 `127.0.0.1:8080`

**Project Type**: web(기존 `backend/` + 새 `frontend/`)

**Performance Goals**: 해당 없음(단일 로컬 사용자). 성능 수치는 측정·주장하지 않는다.

**Constraints**: 백엔드 무변경, CORS 미추가, 비밀번호 비영속, 루프백 전용, 원바이트 전달, 자동 재시도 없음, 계약 밖 경로 차단.

**Scale/Scope**: 화면 1개(패널 5개), 전달 Route Handler 1개, 사용자 시나리오 4개.

## Constitution Check

*GATE: Phase 0 전 통과, Phase 1 후 재확인.*

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 서비스 경계·API 호환성 | 백엔드 코드·OpenAPI·SQL 무변경, 콘솔은 계약 5개만 호출 | 통과 |
| II. 도메인 근거 | knowledge 규칙 전체·resource/reservation/security/ratelimit 문서·계획서 2~5절 확인. 새 도메인 `frontend-console`은 템플릿으로 문서화 예정(T 작업) | 통과(문서 작성 작업 포함) |
| III. 검증 방식·실행 증거 | 실패 테스트 우선, 0건·skip 게이트, 실제 백엔드 종단 기록, 모의 응답만으로 연동 주장 금지 | 통과 |
| IV. 정책·문서 동기화 | AGENTS.md·계획서 범위 문구·architecture/api-and-security 규칙·README·docs 갱신을 작업으로 포함 | 통과(작업 포함) |
| V. 미확정 정책 | 업무 정책 변경 없음. 설계 모호점은 spec Clarifications에 근거와 함께 기록 | 통과 |
| VI. 얇은 클라이언트 | 백엔드 응답만 사실로 표시, CORS 미추가, 같은 출처 전달, 비밀번호 비영속, CSRF 판단 기록, 정확한 버전·lockfile | 통과 |
| 기술·운영 제약 | localhost 전용(`-H 127.0.0.1`), `.env` 미커밋, CI 변경 없음 | 통과 |

Phase 1 후 재확인: 아래 설계(D1~D8)는 위 판단을 바꾸지 않는다. 재확인 통과.

## 설계

### D1. 전달 계층(Route Handler) — FR-003~FR-006, FR-011

- 위치: `frontend/src/app/api/backend/[...path]/route.ts`(GET·POST, 그리고 프레임워크 자동 응답을 막기 위한 OPTIONS·HEAD를 export — 2026-10-26 로컬 리뷰 L1). 판단 로직은 순수 모듈 `src/lib/proxy/`에 두고 Route Handler는 얇게 유지한다.
- 허용 목록(`src/lib/proxy/routes.ts`): `POST resources`, `GET resources/{uuid}`, `POST reservations`, `GET reservations/{uuid}`, `POST reservations/{uuid}/cancel`. UUID는 8-4-4-4-12 16진수. 그 밖은 404 problem(`CONSOLE_ROUTE_NOT_ALLOWED`), 업스트림 호출 0회.
- 출처 검사(`src/lib/proxy/guard.ts`): `<port>`는 서버 환경 변수 `CONSOLE_PORT`(기본 3000). `Host`가 허용 목록(`127.0.0.1:<port>`, `localhost:<port>`, `[::1]:<port>`)이 아니면 403 `CONSOLE_FORBIDDEN_ORIGIN`. `Origin`이 있으면 `http://` + 허용 Host 중 하나여야 한다. `Sec-Fetch-Site`가 있으면 `same-origin`이어야 한다(`none`도 허용: 주소창 직접 입력). 위반 시 업스트림 호출 0회.
- 전달 헤더: 요청에서 `Authorization`·`Content-Type`·`Idempotency-Key`·`Accept`만 복사. 그 밖(쿠키, `X-User-Id` 등)은 전달하지 않는다. 본문은 스트림으로 읽어 원바이트를 그대로 전달하며, `Content-Length` 또는 누적 바이트가 1MiB를 넘는 즉시 읽기를 중단하고 자체 413 `CONSOLE_PAYLOAD_TOO_LARGE`(업스트림 호출 0회, 로컬 리뷰 M2). 10초 기한은 본문 읽기부터 적용.
- 응답: 상태·본문 바이트·`Content-Type`·`Location`·`Idempotent-Replayed`·`Retry-After`를 보존. `WWW-Authenticate`는 `X-Upstream-WWW-Authenticate`로 이름을 바꿔 전달. 그 밖의 업스트림 헤더는 전달하지 않는다. `Cache-Control: no-store` 추가.
- 업스트림 실패: 연결 실패 502 `CONSOLE_UPSTREAM_UNREACHABLE`, 10초 초과 504 `CONSOLE_UPSTREAM_TIMEOUT`. 재시도 없음. 콘솔 자체 오류도 `application/problem+json`(`type`·`title`·`status`·`detail`·`code`), 내부 예외 메시지·스택 비노출.
- 백엔드 주소: 서버 전용 환경 변수 `BACKEND_BASE_URL`(기본 `http://127.0.0.1:8080`). 호스트가 `127.0.0.1`·`localhost`·`[::1]`이 아니면 전달하지 않고 500 `CONSOLE_MISCONFIGURED`. `NEXT_PUBLIC_` 접두사를 쓰지 않는다.
- 업스트림 호출 함수는 주입 가능하게(`forward(request, { baseUrl, fetchImpl, timeoutMs })`) 만들어 테스트에서 실제 로컬 HTTP 가짜 서버로 검증한다.

### D2. 브라우저 API 클라이언트 — FR-002, FR-005, FR-008~FR-012

- `src/lib/api/client.ts`: `callBackend({ method, path, credentials, body?, idempotencyKey? })` → `ApiResult`. `Authorization: Basic base64(utf8(user:password))`. 본문 문자열을 그대로 보낸다. 클라이언트 상한 15초(AbortController), 초과 시 콘솔 오류 결과. 재시도 없음.
- 요청 본문 생성(`src/lib/api/bodies.ts`): 대상 생성은 `{"name":` + `JSON.stringify(name)` + `,"capacity":` + 수량 원문 + `}`. 수량 원문은 JSON number 문법(`-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?`)일 때만 허용. 예약 생성은 `{"resourceId":` + `JSON.stringify(resourceId)` + `}`(resourceId 형식은 백엔드가 판정, 콘솔은 문자열을 바꾸지 않음). 취소는 본문 없음.
- `ApiResult`(data-model 참조): `status`, `ok`, `body`(성공 JSON), `problem`, `headers`(location·idempotentReplayed·retryAfter·upstreamWwwAuthenticate), `consoleError`, `rawText`(JSON이 아닐 때 앞 200자).
- 응답 해석(`src/lib/api/problem.ts`): problem+json이면 `code`별 한국어 설명 표(백엔드 10종 + 콘솔 7종). 알 수 없는 code는 원문 표시. JSON이 아니면 "알 수 없는 응답 형식".
- 경쟁 방지(`src/lib/api/request-guard.ts` 또는 훅): 패널별 진행 중 플래그로 중복 전송 차단. 요청마다 계정 세대 번호를 기록하고, 응답 시 현재 세대와 다르면 결과를 버린다.

### D3. 화면 — US1~US4, FR-007, FR-013, FR-015, FR-016

- `src/app/page.tsx`(클라이언트 컴포넌트 묶음을 렌더) + `src/components/`:
  - `CredentialsPanel`: 계정 선택(ADMIN·USER_A·USER_B), 비밀번호(password 입력, autocomplete=off), "적용", "자격 증명 지우기". 현재 계정 표시.
  - `ResourcePanel`: 생성(이름·수량), 조회(식별자). 결과의 "이 대상 다시 조회".
  - `ReservationCreatePanel`: 대상 식별자·생성 키(자동 생성 + "새 키" + 직접 편집), "예약", "같은 키로 다시 보내기"(직전 시도 키·대상, 직전 시도 계정과 현재 계정이 다르면 비활성). 재현 여부 표시와 안내.
  - `ReservationPanel`: 예약 식별자 조회, "취소", 결과의 "대상 다시 조회".
  - `RecentHistory`: 최근 기록 목록·선택·지우기.
  - `ResultView`: 상태, 성공 필드 전체, 보존 헤더, problem(code·title·detail·한국어 설명), 429 남은 초, 시각 UTC 원문(+보조 로컬 시각). `role="status"`/`aria-live="polite"`.
- 상태 관리: `src/components/ConsoleProvider.tsx`(React context)에 자격 증명·계정 세대·최근 기록 갱신 함수. 외부 상태 라이브러리 없음.
- 생성 키: `crypto.randomUUID()`(36자, 백엔드 정규식 충족). `src/lib/idempotency.ts`.
- 스타일: Tailwind 유틸리티. 디자인 시스템·애니메이션 없음. 키보드 포커스 가시성 유지.

### D4. 최근 기록 — US4, FR-014

- `src/lib/history.ts`: `Storage` 주입형. 키 `limited-booking-console:history:v1`, 최대 50개, 최신순, 같은 종류·식별자는 갱신. 항목 `{ kind: 'resource'|'reservation', id, name?, recordedAt, account }`. 저장 시 허용 필드만 직렬화(비밀번호·수량·상태 필드는 타입·직렬화 단계에서 배제). 손상된 JSON은 빈 목록.

### D5. Next 설정·보안 헤더 — FR-017, FR-019

- `next.config.ts`: create-next-app 16.4.3 기본(`cacheComponents`·Tailwind turbopack 규칙) 유지 + `headers()`로 모든 경로에 `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `Content-Security-Policy: frame-ancestors 'none'`. 스크립트 CSP는 범위 밖(spec Clarifications).
- `package.json` 스크립트: `dev`·`start`는 `-H 127.0.0.1 -p 3000`. Route Handler는 요청 데이터에 접근하므로 동적 실행(캐시 컴포넌트 설정에서도 요청 시 실행됨을 빌드로 확인).

### D6. 테스트 구성과 게이트 — SC-002~SC-004, SC-007

| 분류 | 위치 | 환경 | 대상 |
|---|---|---|---|
| Unit | `frontend/tests/unit/**/*.test.ts` | node | routes·guard·bodies·problem·history·idempotency·base64 |
| Proxy | `frontend/tests/proxy/**/*.test.ts` | node | `forward()`와 Route Handler를 실제 로컬 HTTP 가짜 업스트림(`node:http`, 동적 포트)으로. 업스트림 수신 횟수·헤더·본문 바이트 단언 |
| Component | `frontend/tests/component/**/*.test.tsx` | jsdom | 패널·결과 표시. `fetch`만 경계에서 대체(응답 객체는 실제 `Response`) |
| E2E | `frontend/e2e/**/*.spec.ts` | Playwright Chromium | 실제 백엔드(전용 DB `reservation_e2e`·Redis DB 1) + `next build && next start` |

- `npm test`: `vitest run` + JSON 보고서 → `scripts/assert-vitest-report.mjs`가 총 0건·skip·todo·실패를 거부(백엔드 게이트와 같은 원칙). `.only`는 `allowOnly: false`.
- `npm run test:e2e`: Playwright. 비밀번호는 셸 환경 변수 `ADMIN_PASSWORD`·`USER_A_PASSWORD`·`USER_B_PASSWORD`에서 읽고 없으면 즉시 실패(skip 금지). 백엔드 health 사전 확인 실패 시 실패. `page.on('dialog')` 0회 단언. 요청 제한(20회/60초)을 넘지 않게 사용자·시나리오별 쓰기 수를 설계하고, 429 시나리오는 USER_B로 마지막에 1회 수행.
- E2E 백엔드 격리: `npm run test:e2e`는 실행기 `frontend/scripts/run-e2e.sh`다. 실행기가 Compose PostgreSQL에 `reservation_e2e`를 준비하고, `bootJar` 산출물을 `SERVER_PORT=18080`·`SPRING_DATASOURCE_URL=…/reservation_e2e`·`SPRING_DATA_REDIS_DATABASE=1`로 직접 기동한 뒤 Playwright를 실행하고 종료 시 백엔드를 내린다. 18080이 이미 사용 중이면 알 수 없는 백엔드를 쓰지 않도록 실패한다. Playwright 지원 코드는 실행기 표식(`E2E_ISOLATED_BACKEND=1`)이 없거나 백엔드 주소가 8080이면 실패한다(로컬 리뷰 M3). 백엔드 코드 무변경, 로컬 개발 DB `reservation_local`을 쓰지 않는다.
- `npm run lint`(eslint), `npm run typecheck`(`tsc --noEmit`), `npm run build`.

### D7. 문서·규칙 동기화 — FR-017, FR-018

- 새 도메인 문서 `knowledge/domain/frontend-console.md`(템플릿 기반) + `knowledge/README.md` 목록.
- `AGENTS.md`·계획서 1절(24행)·3절 표(97행)·`knowledge/rules/architecture.md`·`api-and-security.md`·`knowledge/domain/security.md`의 "프런트엔드/브라우저 범위 밖" 문구를 2026-10-26 사용자 지시와 헌법 1.1.0에 맞게 갱신(백엔드 자체는 계속 비브라우저 API, 브라우저는 콘솔 전달 계층 경유).
- `README.md` 콘솔 실행 절, `docs/versions.md`(프런트 의존성), `docs/decisions.md`(D1·D2·CSRF 판단·헤더 이름 변경), `docs/test-evidence.md`(프런트 Red/Green·E2E 실행 기록), `.gitignore`(node_modules·.next·playwright 산출물·`next-env.d.ts`는 템플릿 기본에 따름).
- `docs/openapi.yaml`·`backend/`는 변경하지 않는다(SC-006 확인: `git diff --stat develop -- backend docs/openapi.yaml` 0).

### D8. 백엔드 회귀 확인 — SC-006

- 콘솔 작업은 백엔드를 바꾸지 않으므로 무거운 심화 검증을 반복하지 않는다. 변경 없음 diff 확인 + `./gradlew test` 1회 실행으로 기존 Unit 게이트 유지를 기록한다.

## Project Structure

### Documentation (this feature)

```text
specs/014-frontend-console/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── console-proxy.md
│   └── console-ui.md
├── checklists/
│   ├── requirements.md
│   └── security-ux.md      # $speckit-checklist 산출
└── tasks.md                # $speckit-tasks 산출
```

### Source Code (repository root)

```text
frontend/
├── package.json / package-lock.json   # 정확한 버전 고정
├── next.config.ts / tsconfig.json / eslint.config.mjs
├── vitest.config.mts / playwright.config.ts
├── scripts/assert-vitest-report.mjs
├── src/
│   ├── app/
│   │   ├── layout.tsx / page.tsx / globals.css
│   │   └── api/backend/[...path]/route.ts
│   ├── components/   # ConsoleProvider, CredentialsPanel, ResourcePanel,
│   │                 # ReservationCreatePanel, ReservationPanel, RecentHistory, ResultView
│   └── lib/
│       ├── api/      # client, bodies, problem, types
│       ├── proxy/    # routes, guard, forward
│       ├── history.ts / idempotency.ts / basic-auth.ts
├── tests/
│   ├── unit/ proxy/ component/
└── e2e/
```

**Structure Decision**: 기존 `backend/`를 건드리지 않는 별도 `frontend/` 웹 앱. 앱 코드는 `frontend/src/`, 테스트는 `frontend/tests/`·`frontend/e2e/`로 나눠 역할별 수정 범위(test-author ↔ implementer)를 경로로 강제한다.

## 역할과 파일 범위

| 역할(프로필) | 수정 허용 | 금지 |
|---|---|---|
| orchestrator | `specs/014-*`, `.specify/memory/constitution.md`, `AGENTS.md`, 계획서, `knowledge/`, `README.md`, `docs/`, `.gitignore`, Git·PR | `frontend/src`, `frontend/tests`, `frontend/e2e` |
| scaffolder | `frontend/` 골격·설정(package.json·lock·설정 파일·`scripts/`), 빈 모듈 스텁·타입 선언(로직 없음) | 업무 로직, 테스트 단언 |
| test-author | `frontend/tests/**`, `frontend/e2e/**` | `frontend/src/**`, 설정 |
| implementer | `frontend/src/**`, `frontend/next.config.ts`의 `headers()` | `frontend/tests/**`, `frontend/e2e/**` |
| reviewer | 없음(코멘트만) | 모든 파일 |
| explorer·advisor | 없음 | 모든 파일 |

## Complexity Tracking

| 항목 | 필요한 이유 | 더 단순한 대안을 기각한 이유 |
|---|---|---|
| 전달 Route Handler | 백엔드 CORS 미추가(원칙 VI) 상태에서 브라우저 호출 | 백엔드 CORS 허용은 규칙 위반, `rewrites`는 헤더 이름 변경·출처 검사·경로 허용 목록을 할 수 없음 |
| 실제 HTTP 가짜 업스트림 테스트 | 원바이트·헤더·호출 0회를 실제 네트워크 경계에서 확인 | `fetch` 모킹만으로는 전달 계층의 실제 직렬화·헤더 처리를 검증하지 못함 |
| E2E 전용 DB·Redis DB 1 | 테스트가 로컬 개발 DB를 쓰지 않는 규칙 | `reservation_local` 사용은 규칙 위반, 백엔드 설정 파일 추가는 백엔드 변경 |
