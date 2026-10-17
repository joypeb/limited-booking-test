# 로컬 인증과 예약 대상 API 요구사항 품질 검토: 로컬 인증과 예약 대상 생성·조회 API

**목적**: 작업 분해(`$speckit-tasks`) 및 구현 착수 전에 명세·계획·데이터 모델·인터페이스 계약의 인증·권한·8KiB 본문 상한·엄격 입력 검증·예약 대상 생성/조회·공통 `problem+json` 오류 응답 및 후속 단계 경계 요구사항이 완전하고 명확한지 검토한다.

**작성일**: 2026-10-17

**기능**: [명세](../spec.md) · [계획](../plan.md) · [데이터 모델](../data-model.md) · [인터페이스 및 API 계약](../contracts/auth-resource-contract.md) · [검증 안내](../quickstart.md) · [작업 목록](../tasks.md)

**범위·깊이·사용자**: 인증·권한·8KiB 본문 상한·엄격 입력 검증·예약 대상 API·`problem+json` 전체 통합 및 4단계/5~8단계 경계 검토, 표준 깊이(34개 항목), 구현 및 작업 분해 전 검토자용 사전 품질 게이트다.

**생성 안내**: `$speckit-checklist`로 만든 맞춤 요구사항 품질 검토 문서다. 실제 코드 동작이나 테스트 통과 여부를 판정하는 목록이 아니다.

**검토 소유권**: 이 체크리스트는 검토자 소유의 요구사항 품질 검토 산출물이다. 모든 항목은 미판정 `[ ]`으로 생성하며, 검토자가 문서의 요구사항 품질 기준 충족을 판단하거나 명시적 평가 수행을 지시한 경우에만 `[x]`로 바꾼다.

**체크 의미**: `[x]`는 요구사항 품질 기준이 검토·충족됐음을 뜻하며, 구현 완료나 통합 테스트 통과를 뜻하지 않는다.

## 요구사항 완결성 (Requirement Completeness)

- [x] CHK001 로컬 인증 계정(`ADMIN`·`USER_A`·`USER_B`)의 고정 UUID 할당, 역할 구분, 환경 비밀번호 주입 및 단방향 해시(BCrypt) 보관 요구사항이 빠짐없이 정의돼 있는가? [완결성, Spec §FR-001, Model §1.1]
- [x] CHK002 무상태 Basic 인증을 통한 보호 경로 검증, 미인증/오류 자격 증명(`401` + `WWW-Authenticate`)과 일반 사용자의 생성 요청 거부(`403`) 요구사항이 모두 명시돼 있는가? [완결성, Spec §FR-002, Contract §2·§3.1]
- [x] CHK003 예약 대상 생성(`POST /api/resources`)의 입력(`name`, `capacity`), 초기 상태(`available = capacity`, UTC `created_at`), 응답 상태(`201`), `Location` 헤더, 반환 본문(`ResourceView`) 요구사항이 모두 정의돼 있는가? [완결성, Spec §FR-005, Model §2.1~2.3, Contract §3.1]
- [x] CHK004 예약 대상 단건 조회(`GET /api/resources/{id}`)의 인증 조건, 성공 응답(`200`), 미존재(`404`), 잘못된 UUID 형식(`400`) 요구사항이 모두 정의돼 있는가? [완결성, Spec §FR-006, Contract §3.2]
- [x] CHK005 필터 단계(413·401·403)와 컨트롤러/예외 처리 단계(400·404·415·503) 모두에서 반환해야 할 `application/problem+json` 미디어 형식과 필수 6개 속성(`type`, `title`, `status`, `detail`, `instance`, `code`)이 정의돼 있는가? [완결성, Spec §FR-009, Model §3.1~3.2]

## 요구사항 명확성 (Requirement Clarity)

- [x] CHK006 인증된 사용자 식별자(`UUID`)의 유일한 신뢰 출처가 `Authentication`의 `Principal`이며, 요청 본문의 `userId`나 임의의 `X-User-Id` 헤더에 대한 처리 방식(본문 `userId`는 `400` 거부, 헤더 `X-User-Id`는 무시)이 모호함 없이 구분돼 있는가? [명확성, Spec §FR-003, Spec §사용자 이야기 1.4, Contract §1.1]
- [x] CHK007 요청 본문 크기 상한(`8,192바이트`) 판정 기준이 `Content-Length` 헤더 값에만 의존하지 않고 실제 읽은 바이트 수 기준(헤더 누락·조작·`Transfer-Encoding: chunked` 포함)임이 명확히 정의돼 있는가? [명확성, Spec §FR-007, Research §3, Contract §2]
- [x] CHK008 엄격한 JSON 역직렬화에서 거부해야 할 세 가지 범주(정의하지 않은 알 수 없는 필드, 동일 JSON 객체 내 중복 키, 부동소수점·문자열 등 숫자 필드의 암묵적 형 변환)가 각각 구체적인 예시와 함께 명시돼 있는가? [명확성, Spec §FR-008, Spec §경계 사례, Research §4]
- [x] CHK009 예약 대상 이름의 앞뒤 제거 대상 공백(일반 공백·탭·줄바꿈)과 보존 대상 공백(내부 공백·U+00A0·U+2007·U+202F), 그리고 코드 포인트 길이(1~100자) 기준이 3단계 순수 규칙과 동일하게 명시돼 있는가? [명확성, Spec §사용자 이야기 2.2, Spec §경계 사례, Model §2.1]
- [x] CHK010 취소 경로(`/api/reservations/{id}/cancel`)에서 4단계가 검증하는 요구사항(8KiB 초과 본문 `413`, 8KiB 이하의 비어 있지 않은 본문 `400`, 잘못된 UUID `400`)이 6단계 비즈니스 취소 로직과 혼동되지 않게 명확히 기술돼 있는가? [명확성, Spec §사용자 이야기 3.5, Spec §전제와 의존성, Contract §3.3]

## 요구사항 일관성 (Requirement Consistency)

- [x] CHK011 요청 처리 우선순위(`① 8KiB 본문 상한(413) -> ② Basic 인증(401) -> ③ 관리자 권한(403) -> ④ 미디어 타입(415)/요청 형식·엄격 JSON·취소 빈 본문(400) -> ⑤ 도메인 규칙(400)/대상 조회(404)`)가 명세·계획·계약 문서 간에 충돌 없이 일치하는가? [일관성, Spec §사용자 이야기 3.1~3.5, Plan §상세 설계 1~3, Contract §2]
- [x] CHK012 제공 Java 인터페이스 시그니처(`LocalIdentityResolver.userId(Authentication): UUID`, `ResourceService.create(String, int): ResourceView`, `ResourceService.get(UUID): ResourceView`)와 실제 패키지 경로(`com.limitedbooking.backend.*`)가 계획서와 계약 문서 사이에서 일치하는가? [일관성, Plan §프로젝트 구조, Contract §1.1~1.3]
- [x] CHK013 HTTP 상태 코드별 고정 오류 코드(`INVALID_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `DEPENDENCY_UNAVAILABLE`)가 명세, 데이터 모델, OpenAPI 계약과 일치하는가? [일관성, Spec §FR-001~009, Model §3.2, Contract §3.1~3.3]
- [x] CHK014 최초 수량 `0` 허용(`0 <= capacity <= 1000`, 생성 직후 `available == capacity`) 요구사항이 2단계 DB 체크 제약·3단계 `ResourceRules`·4단계 API 계약 사이에서 일관되게 유지되는가? [일관성, Spec §FR-005, Model §2.1~2.3, Contract §4.2]

## 인수 기준의 측정 가능성 (Acceptance Criteria Quality)

- [x] CHK015 인증·권한 인수 기준(SC-001)이 상태 코드(`401`, `403`, `201`, `200`), 필수 헤더(`WWW-Authenticate`, `Location`), 고정 UUID 추출, 외부 식별자 불신뢰를 각각 관측 가능하게 정의하고 있는가? [측정 가능성, Spec §SC-001, Contract §4.1]
- [x] CHK016 입력 검증 인수 기준(SC-002)에서 거부된 요청이 `400`/`404` 응답뿐 아니라 저장소에 대상이 0건 저장됐음(부작용 없음)까지 확인하도록 측정 기준이 정의돼 있는가? [측정 가능성, Spec §SC-002, Contract §4.2]
- [x] CHK017 본문 상한 필터 인수 기준(SC-003)이 미인증 상태에서의 `413` 우선 반환과 `8,192바이트` 이하 본문의 손실 없는 하위 전달을 객관적으로 구분해 판정할 수 있게 정의돼 있는가? [측정 가능성, Spec §SC-003, Contract §4.1~4.2]
- [x] CHK018 검증 증거 요구사항(FR-010, SC-005)이 기능 미구현 상태의 의도된 실패(RED)와 구현 후 통과(GREEN), 그리고 실행 명령·종료 코드·실행 수·실패 0건·건너뜀 0건 기록 기준을 명확히 요구하는가? [측정 가능성, Spec §FR-010, Spec §SC-005, Quickstart §2~4]

## 시나리오 범위 (Scenario Coverage)

- [x] CHK019 정상 흐름(관리자 대상 생성 `201` 후 `ADMIN`·`USER_A`·`USER_B` 단건 조회 `200`, 이름 앞뒤 공백 정리 후 저장·조회)이 인수 시나리오에 빠짐없이 포함돼 있는가? [시나리오 범위, Spec §사용자 이야기 1.3·2.1~2.3]
- [x] CHK020 인증·권한 실패 시나리오(자격 증명 누락, 잘못된 비밀번호, 일반 사용자의 생성 시도, 임의 `X-User-Id` 헤더 및 미정의 `userId` 본문 필드 전송)가 모두 다뤄지고 있는가? [시나리오 범위, Spec §사용자 이야기 1.1~1.2·1.4, Contract §4.1]
- [x] CHK021 프로토콜·역직렬화 예외 흐름(미지원 `Content-Type` `415`, 깨진 JSON `400`, 중복 JSON 키 `400`, 알 수 없는 필드 `400`, 부동소수점·문자열 수량 `400`, 잘못된 UUID `400`, 미존재 UUID `404`)이 모두 정의돼 있는가? [시나리오 범위, Spec §사용자 이야기 2.4·3.3~3.5, Contract §4.2]
- [x] CHK022 저장소 장애(`503 DEPENDENCY_UNAVAILABLE`)와 분류되지 않은 내부 런타임 결함(`500` 유지, 4xx/503으로 숨기지 않음)의 구분 기준이 문서화돼 있는가? [시나리오 범위, Spec §FR-009, Model §3.2, Plan §상세 설계 2.3]

## 경계 조건 범위 (Edge Case Coverage)

- [x] CHK023 본문 크기의 정확한 경계(`8,192바이트` 허용 vs `8,193바이트` `413` 거부)와 전송 방식 변형(`Content-Length` 포함, 헤더 생략, 작게 표시된 헤더, `Transfer-Encoding: chunked`)이 모두 경계 사례로 정의돼 있는가? [경계 조건, Spec §경계 사례, Contract §2·§4.1~4.2]
- [x] CHK024 수량 경계값(`-1`, `0`, `1`, `1000`, `1001`)과 비정수 입력(`0.5`, `1.0`, `"1"`, `"10"`)에 대한 허용/거부 기대 결과가 누락 없이 정의돼 있는가? [경계 조건, Spec §경계 사례, Contract §4.2]
- [x] CHK025 이름 경계값(`null`, 빈 문자열, 일반 공백/탭/줄바꿈만 있는 문자열, 정리 후 1자·100자·101자, 보충 평면 이모지 `😀` 100개·101개, NBSP 보존)이 모두 정의돼 있는가? [경계 조건, Spec §경계 사례, Contract §4.2]
- [x] CHK026 취소 경로(`/api/reservations/{id}/cancel`)에 전달된 본문의 크기별 경계(0바이트 빈 본문 vs 1~8,192바이트 비어 있지 않은 본문 `400` vs 8,193바이트 이상 `413`)가 명확히 구분돼 있는가? [경계 조건, Spec §경계 사례, Contract §3.3]

## 비기능 요구사항과 정보 보호 (Non-Functional & Security Requirements)

- [x] CHK027 localhost 비브라우저 API 전제, 무상태 세션 정책(`SessionCreationPolicy.STATELESS`, `JSESSIONID` 미생성), CSRF 비활성화 사유 및 외부/브라우저 공개 시 재검토 조건, CORS 와일드카드(`*`) 미추가 요구사항이 명시돼 있는가? [보안·비기능, Spec §FR-002·FR-004, Research §2, Plan §상세 설계 1.1]
- [x] CHK028 실행 환경 주입 비밀번호(`.env` / 환경 변수)와 테스트용 가짜 비밀번호의 분리 원칙, 그리고 코드·문서·로그·오류 응답에서 비밀번호 원문/해시·SQL·스택 트레이스 노출 금지 요구사항이 정의돼 있는가? [정보 보호, Spec §FR-001·FR-009, Model §1.1·§3.1]
- [x] CHK029 생성 시각(`created_at`) 기록을 위해 시스템 기본 시계 대신 주입 가능한 UTC `Clock`(`ClockConfig`)과 마이크로초 정규화 기준을 사용한다는 요구사항이 명시돼 있는가? [비기능·재현성, Spec §FR-005, Research §6, Plan §상세 설계 2.1·3.2]

## 의존성과 전제 (Dependencies & Assumptions)

- [x] CHK030 2단계 산출물(`resources` 스키마, `ResourceRepository.find`, `ResourceView`, `TestData`)과 3단계 산출물(`ResourceRules.normalizeName`, `ResourceRules.validateCapacity`)을 재사용·연결한다는 전제가 명확히 문서화돼 있는가? [의존성, Spec §전제와 의존성, Plan §상세 설계 3]
- [x] CHK031 1단계 기동 확인용 임시 계정(`toolchain`)을 4단계의 고정 UUID 3계정(`ADMIN`·`USER_A`·`USER_B`) 구조로 전환하면서 기존 기동·스모크 통합 테스트(`BackendApplicationIT` 등)가 깨지지 않게 유지한다는 요구사항이 명시돼 있는가? [의존성, Spec §전제와 의존성, Research §1, Plan §프로젝트 구조]
- [x] CHK032 5~8단계 후속 범위(예약 생성·멱등 키 재전송·롤백, 예약 단건 조회·취소 상태 전이·수량 복구·소유자 검증, 동시성 경쟁 검증, Redis 요청 제한 429/503)를 4단계에서 선구현하지 않는다는 제외 경계가 명확히 정의돼 있는가? [범위 경계, Spec §전제와 의존성, Plan §헌법 준수 점검 V]

## 모호성·충돌과 완료 의미 (Ambiguities, Conflicts & Completion Semantics)

- [x] CHK033 실제 서블릿 컨테이너와 보안 필터 체인을 거치는 통합 검증(`HttpClientTestSupport` + `ContainerTestSupport`) 요구사항이 MockMvc나 필터 우회 테스트와 혼동되지 않게 정의돼 있는가? [명확성, Plan §헌법 준수 점검 III, Contract §4]
- [x] CHK034 명세·계획·체크리스트 문서 작성 완료와 실제 프로덕션 코드 구현·`./gradlew verify` 실행 통과 완료의 의미가 엄격히 분리돼 있으며, 미구현·미실행 상태를 완료로 오인하게 하는 문구가 없는가? [추적성·무결성, Spec §FR-010, Plan §TDD 구현 및 검증 순서]

## 참고와 판정 방법

- `Spec`은 [spec.md](../spec.md), `Plan`은 [plan.md](../plan.md), `Model`은 [data-model.md](../data-model.md), `Contract`는 [auth-resource-contract.md](../contracts/auth-resource-contract.md), `Research`는 [research.md](../research.md), `Quickstart`는 [quickstart.md](../quickstart.md), `Tasks`는 [tasks.md](../tasks.md)다. `§` 뒤에는 해당 문서의 절 제목·식별자·시나리오 번호를 사용한다.
- 검토자는 항목별로 요구사항 품질 기준이 충족됐는지 확인한 뒤에만 `[x]`로 표시한다. 미체크(`[ ]`)는 미판정 또는 문서 보완 필요를 뜻하며 자동으로 코드 결함을 뜻하지 않는다.
- `$speckit-implement`는 이 체크리스트의 체크 상태를 게이트로 읽을 수 있으나, 체크 표시를 임의로 수정하지 않는다. 명시적인 검토자 평가 요청 없이 에이전트가 `[x]`로 변경하지 않는다.
- 내장 [checklists/requirements.md](requirements.md)의 16개 항목은 `$speckit-specify`·`$speckit-clarify`에서 관리하는 별도 명세 품질 체크리스트이며, 본 맞춤 체크리스트의 34개 항목 판정을 대신하지 않는다.

## 검토자 요청에 따른 품질 판정 (2026-10-17)

`$speckit-analyze` 후속 조치 제안에 대한 사용자의 명시적 승인("진행해.")에 따라 U1·U2 문서 보완을 반영한 뒤 34개 항목의 요구사항 품질 근거를 대조했다. **34/34 충족**이며, 이는 문서의 요구사항 품질이 충족됐음을 뜻할 뿐 프로덕션 코드 구현이나 통합 테스트 실행 완료를 뜻하지 않는다. 내장 [requirements.md](requirements.md)는 16/16 상태를 유지한다.

| 항목 | 판정 | 대조 근거 |
| --- | --- | --- |
| CHK001 | 충족 | Spec FR-001 및 Model §1.1에 `ADMIN`·`USER_A`·`USER_B` 고정 UUID, 역할, 환경 변수 비밀번호 주입 및 BCrypt 해시 보관이 정의돼 있다. |
| CHK002 | 충족 | Spec FR-002 및 Contract §2·§3.1에 무상태 Basic 인증, `401`(`WWW-Authenticate` 포함), 일반 사용자의 대상 생성 `403` 거부가 명시돼 있다. |
| CHK003 | 충족 | Spec FR-005, Model §2.1~2.3, Contract §3.1에 `POST /api/resources` 입력·초기 상태(`available = capacity`, UTC `created_at`)·`201`·`Location`·`ResourceView`가 정의돼 있다. |
| CHK004 | 충족 | Spec FR-006 및 Contract §3.2에 인증된 단건 조회 `200`, 미존재 `404`, 잘못된 UUID `400`이 정의돼 있다. |
| CHK005 | 충족 | Spec FR-009 및 Model §3.1~3.2에 필터(413·401·403)와 컨트롤러/예외 처리기(400·404·415·503) 공통 `application/problem+json`과 필수 6속성이 명시돼 있다. |
| CHK006 | 충족 | Spec FR-003·이야기 1.4 및 Contract §1.1에 `Principal` 단일 신뢰, `X-User-Id` 헤더 무시, 본문 `userId` 필드 `400` 거부가 명확히 구분돼 있다. |
| CHK007 | 충족 | Spec FR-007, Research §3, Contract §2에 `Content-Length`뿐 아니라 헤더 생략·조작·`chunked` 요청의 실제 읽은 바이트 기준 `8,192바이트` 상한이 정의돼 있다. |
| CHK008 | 충족 | Spec FR-008·경계 사례 및 Research §4에 알 수 없는 필드, 중복 JSON 키, 부동소수점·문자열 등 암묵적 형 변환 거부 3범주가 예시와 함께 명시돼 있다. |
| CHK009 | 충족 | Spec 이야기 2.2·경계 사례 및 Model §2.1에 앞뒤 일반 공백/탭/줄바꿈 제거, 내부 공백·NBSP 3종·이모지 보존, 코드 포인트 1~100자 기준이 명시돼 있다. |
| CHK010 | 충족 | Spec 이야기 3.5·전제와 의존성, Contract §3.3, Plan 상세 설계 1.1, Tasks `T021`에 4단계 취소 검증 범위(8KiB 초과 `413`, 비어 있지 않은 본문 `400`, 잘못된 UUID `400`)와 6단계 비즈니스 로직 분리가 명시돼 있다. |
| CHK011 | 충족 | Spec 이야기 3.1~3.5, Plan 상세 설계 1~3, Contract §2의 5단계 요청 처리 우선순위가 문서 간에 일치한다. |
| CHK012 | 충족 | Plan 프로젝트 구조와 Contract §1.1~1.3에서 `com.limitedbooking.backend.*` 패키지 경로와 `LocalIdentityResolver`·`ResourceService`·`ResourceRepository` 시그니처가 일치한다. |
| CHK013 | 충족 | Spec FR-001~009, Model §3.2, Contract §3.1~3.3, `docs/openapi.yaml`의 상태별 `code` 열거값이 일치한다. |
| CHK014 | 충족 | Spec FR-005, Model §2.1~2.3, Contract §4.2에서 `capacity = 0` 허용 및 생성 시 `available == capacity` 불변식이 일관되게 유지된다. |
| CHK015 | 충족 | Spec SC-001 및 Contract §4.1에 상태 코드·헤더·고정 UUID 추출·외부 식별자 무시가 관측 가능하게 정의돼 있다. |
| CHK016 | 충족 | Spec SC-002 및 Contract §4.2에 거부된 요청으로 생성되는 대상이 0건(DB 미저장)임이 측정 기준으로 포함돼 있다. |
| CHK017 | 충족 | Spec SC-003 및 Contract §4.1~4.2에 미인증 `413` 우선 반환과 `8,192바이트` 이하 재사용 전달이 구분돼 있다. |
| CHK018 | 충족 | Spec FR-010·SC-005 및 Quickstart §2~4에 TDD RED 선확인·GREEN·`./gradlew verify` 실패 0·건너뜀 0 증거 기준이 명시돼 있다. |
| CHK019 | 충족 | Spec 이야기 1.3·2.1~2.3에 관리자 생성 `201` 및 세 계정 조회 `200`, 이름 정리 후 저장·조회 정상 흐름이 포함돼 있다. |
| CHK020 | 충족 | Spec 이야기 1.1~1.2·1.4 및 Contract §4.1에 인증·권한 실패 및 식별자 위조 차단 시나리오가 모두 포함돼 있다. |
| CHK021 | 충족 | Spec 이야기 2.4·3.3~3.5 및 Contract §4.2에 `415`, 깨진 JSON, 중복 키, 미정의 필드, 비정수 수량, 잘못된 UUID(`400`), 미존재 UUID(`404`)가 정의돼 있다. |
| CHK022 | 충족 | Spec FR-009, Model §3.2, Plan 상세 설계 2.3에 `DataAccessException` 기반 `503 DEPENDENCY_UNAVAILABLE`과 내부 런타임 결함 `500` 유지가 구분돼 있다. |
| CHK023 | 충족 | Spec 경계 사례 및 Contract §2·§4.1~4.2에 `8,192`/`8,193`바이트 경계와 길이 헤더 유무·조작·`chunked` 전송 변형이 정의돼 있다. |
| CHK024 | 충족 | Spec 경계 사례 및 Contract §4.2에 `-1`, `0`, `1`, `1000`, `1001` 및 `0.5`, `1.0`, `"1"`, `"10"` 기대 결과가 정의돼 있다. |
| CHK025 | 충족 | Spec 경계 사례 및 Contract §4.2에 `null`, 빈 값, 공백만 있는 값, 정리 후 1·100·101자, `😀` 100·101개, NBSP 보존이 정의돼 있다. |
| CHK026 | 충족 | Spec 경계 사례 및 Contract §3.3에 취소 경로의 0바이트·1~8,192바이트(`400`)·8,193바이트 이상(`413`) 경계가 구분돼 있다. |
| CHK027 | 충족 | Spec FR-002·FR-004, Research §2, Plan 상세 설계 1.1에 localhost 비브라우저 전제, 무세션, CSRF 사유 기록, CORS 와일드카드 미추가가 명시돼 있다. |
| CHK028 | 충족 | Spec FR-001·FR-009 및 Model §1.1·§3.1에 환경 비밀번호/테스트 가짜 비밀번호 분리 및 비밀번호·SQL·스택 트레이스 노출 금지가 정의돼 있다. |
| CHK029 | 충족 | Spec FR-005, Research §6, Plan 상세 설계 2.1·3.2에 `ClockConfig`의 UTC `Clock` 주입과 마이크로초 정규화가 명시돼 있다. |
| CHK030 | 충족 | Spec 전제와 의존성 및 Plan 상세 설계 3에 2단계(`ResourceRepository.find`, `ResourceView`, `TestData`)와 3단계(`ResourceRules`) 재사용이 문서화돼 있다. |
| CHK031 | 충족 | Spec 전제와 의존성, Research §1, Plan 프로젝트 구조, Tasks `T003`·`T005`에 임시 `toolchain` 계정 대체 및 `BackendApplicationIT` 유지가 명시돼 있다. |
| CHK032 | 충족 | Spec 전제와 의존성 및 Plan 헌법 준수 점검 V에 5~8단계 기능 선구현 금지 경계가 명확히 정의돼 있다. |
| CHK033 | 충족 | Plan 헌법 준수 점검 III 및 Contract §4에 `HttpClientTestSupport` + `ContainerTestSupport` 기반 실제 서블릿·보안 필터 통합 검증이 정의돼 있다. |
| CHK034 | 충족 | Spec FR-010, Plan TDD 순서, Tasks 상태 안내 및 `knowledge/README.md`에서 문서 작성 완료와 실제 구현·검증 완료가 엄격히 분리돼 있다. |
