# Research: 로컬 브라우저 예약 콘솔

조사일 2026-10-26. "공식 자료 확인"과 "설치·실행 호환 검증"을 구분한다. 설치·실행 검증은 구현 단계에서 `docs/versions.md`에 기록한다.

## R1. 버전

- Decision: next 16.4.3, tailwindcss 4.3.3(사용자 지정, `npm view <pkg> dist-tags`의 `latest`가 각각 16.4.3·4.3.3임을 확인). react·react-dom 19.3.0, typescript 5.9.3, eslint 9.39.5, eslint-config-next 16.4.3, @tailwindcss/turbopack 4.3.3.
- Rationale: 저장소 밖 임시 디렉터리에서 `npx create-next-app@16.4.3 --ts --tailwind --eslint --app`를 실행해 생성된 `package.json`·lock 해석값을 확인했다(템플릿은 react 19.3.0을 고정, typescript `^5` → 5.9.3). 프레임워크가 함께 시험한 조합을 따른다. 저장소 규칙(정확한 버전·`latest` 미사용)에 맞춰 `^`를 제거한다.
- Alternatives: react 19.3.1(npm latest) — 템플릿 고정값과 달라 근거 없는 조합이 되므로 기각. TypeScript 7.0.2(npm latest) — 템플릿 범위 `^5` 밖이고 Next 플러그인 호환 미확인이라 기각.
- 미검증: Vitest·Testing Library·jsdom·Playwright의 정확한 버전과 Next 16.4.3·React 19.3.0 호환은 설치·실행으로 확인 후 기록한다.

## R2. Tailwind 통합

- Decision: Next 16.4.3 템플릿의 `@tailwindcss/turbopack` 로더(`next.config.ts`의 `turbopack.rules['*.css']`) + `globals.css`의 `@import "tailwindcss";`.
- Rationale: create-next-app 16.4.3 산출물이 PostCSS 설정 대신 이 방식을 생성한다. 템플릿을 그대로 따르는 것이 가장 적은 변경이다.
- Alternatives: `@tailwindcss/postcss` — 템플릿이 쓰지 않는 경로라 기각.

## R3. Next.js 16.4.3 문서 위치

- 생성된 `AGENTS.md`가 "학습 데이터와 다른 Next.js이므로 `node_modules/next/dist/docs/`를 먼저 읽으라"고 안내한다. 구현 에이전트는 Route Handler(`01-app/01-getting-started/15-route-handlers.md`), BFF 프록시(`01-app/02-guides/backend-for-frontend.md` "Proxying to a backend"), Vitest(`01-app/02-guides/testing/vitest.md`) 문서를 읽고 작업한다.
- 템플릿 기본값 `cacheComponents: true`: Route Handler는 요청 객체 접근 시 요청 시점 실행. 빌드 산출물로 동적 처리를 확인한다.
- 생성된 `frontend/AGENTS.md`는 `next dev`가 다시 써 넣는 파일이므로 커밋한다(파일 안내 문구).

## R4. 전달 방식

- Decision: Route Handler 프록시(D1). 공식 BFF 문서가 "Route Handler를 다른 백엔드의 프록시로 사용하고 전달 전 검증 로직을 둔다"는 패턴을 제시한다.
- Alternatives: `next.config` `rewrites` — 헤더 이름 변경·Host/Origin 검사·경로 허용 목록·업스트림 실패 변환이 불가해 기각. `proxy.ts` — 모든 요청 앞단 처리용이며 응답 본문 전달·오류 변환을 Route Handler보다 단순하게 만들지 못함(Advisor 의견 일치). 백엔드 CORS — 원칙 VI 위반.

## R5. CSRF·DNS rebinding

- Decision: 쿠키·브라우저 자동 인증 없음(비밀번호 메모리 + 명시적 헤더) → 위조 요청은 인증되지 않음. 그 위에 Host 루프백 허용 목록, Origin 허용 목록, `Sec-Fetch-Site` 검사(D1).
- Rationale: Advisor 지적 — Origin==Host 비교만으로는 DNS rebinding(공격 도메인이 127.0.0.1로 해석)에서 둘 다 공격 도메인이 되어 통과한다. Host 허용 목록이 이를 막는다.

## R6. `WWW-Authenticate`

- Decision: `X-Upstream-WWW-Authenticate`로 이름 변경. 브라우저 기본 인증 대화상자는 401 + `WWW-Authenticate: Basic` 조합에서 뜨므로 이름 변경으로 억제되고, 원문은 화면에서 관찰된다(Advisor 권고).

## R7. 본문 전달과 413

- Decision: 원바이트 전달, 콘솔 자체 상한 1MiB. 브라우저 `fetch`는 문자열 본문에 Content-Length를 붙여 보내므로 chunked 경로는 콘솔로 재현할 수 없다(chunked 413은 기존 백엔드 테스트가 담당, 콘솔 범위 밖으로 기록).

## R8. E2E 격리

- Decision: 같은 Compose PostgreSQL에 `reservation_e2e` DB, Redis DB 1. 백엔드는 `SPRING_DATASOURCE_URL`·`SPRING_DATA_REDIS_DATABASE` 환경 변수로 덮어써 `local` 프로필로 기동(Spring 외부 설정 우선순위: 환경 변수 > 프로필 YAML).
- Rationale: 로컬 개발 DB를 테스트에 쓰지 않는 규칙, 백엔드 무변경. 요청 제한 키도 개발 Redis DB 0과 분리된다.
- 미검증: 실제 기동으로 덮어쓰기 적용(Flyway가 `reservation_e2e`에 적용됨)을 확인해 기록한다.

## R9. 테스트 게이트

- Decision: Vitest JSON 보고서를 검사해 총 0건·skip·todo·실패를 거부하는 스크립트. Playwright는 비밀번호·백엔드 미준비 시 실패(skip 금지), `forbidOnly: true`.
- Rationale: 헌법 III — 0건·skip은 성공이 아니다.
