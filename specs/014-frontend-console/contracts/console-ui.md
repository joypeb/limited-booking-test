# Contract C2: 콘솔 화면 (테스트·구현 공통 식별자)

test-author와 implementer가 같은 이름을 쓰도록 접근 가능한 이름(레이블·역할·영역 이름)을 고정한다. 테스트는 Testing Library/Playwright의 `getByRole`·`getByLabelText`로 찾는다. 문구를 바꾸려면 이 계약을 먼저 갱신한다.

## C2.1 영역(region, `aria-label` 또는 `<section aria-labelledby>`의 제목)

| 영역 이름 | 내용 |
|---|---|
| `자격 증명` | 계정·비밀번호·적용·지우기 |
| `예약 대상` | 생성·조회·결과 |
| `예약 생성` | 대상 식별자·생성 키·예약·재전송·결과 |
| `내 예약` | 예약 식별자·조회·취소·결과 |
| `최근 기록` | 목록·지우기 |

## C2.2 입력과 버튼

| 영역 | 요소 | 접근 가능한 이름 |
|---|---|---|
| 자격 증명 | combobox | `계정` (옵션 값·표시: `ADMIN`, `USER_A`, `USER_B`) |
| 자격 증명 | 비밀번호 입력(`type=password`, `autocomplete=off`) | `비밀번호` |
| 자격 증명 | button | `자격 증명 적용` |
| 자격 증명 | button | `자격 증명 지우기` |
| 자격 증명 | 상태 텍스트 | 적용 후 `현재 계정: <계정>`, 미적용 시 `자격 증명 없음` |
| 예약 대상 | textbox | `대상 이름` |
| 예약 대상 | textbox(`inputmode=decimal`, type=text) | `최초 수량` |
| 예약 대상 | button | `대상 생성` |
| 예약 대상 | textbox | `대상 식별자` |
| 예약 대상 | button | `대상 조회` |
| 예약 생성 | textbox | `예약할 대상 식별자` |
| 예약 생성 | textbox | `생성 키` |
| 예약 생성 | button | `새 키` |
| 예약 생성 | button | `예약` |
| 예약 생성 | button | `같은 키로 다시 보내기` |
| 내 예약 | textbox | `예약 식별자` |
| 내 예약 | button | `예약 조회` |
| 내 예약 | button | `예약 취소` |
| 최근 기록 | button(항목마다) | `<종류> <식별자> 다시 조회` (종류: `대상` 또는 `예약`) |
| 최근 기록 | button | `기록 지우기` |
| 결과(대상·예약) | button | `대상 다시 조회` (결과에 대상 식별자가 있을 때) |

- 자격 증명이 없으면 요청 버튼은 비활성(`disabled`)이다. 진행 중인 영역의 요청 버튼도 비활성이다.
- `같은 키로 다시 보내기`는 마지막 예약 시도가 없거나, 그 시도의 계정·세대가 현재와 다르면 비활성이다.
- 전송 차단(수량 문법·UUID 형식) 시 해당 입력 아래 `role="alert"` 안내를 표시하고 요청을 보내지 않는다.
  - 수량: `최초 수량은 숫자 표기여야 합니다`
  - 식별자: `식별자는 UUID 형식이어야 합니다`
  - 생성 키(앞뒤 공백·0x20~0x7E 밖 문자): `생성 키는 앞뒤 공백 없이 HTTP 헤더로 보낼 수 있는 ASCII 문자여야 합니다`

## C2.3 결과 표시(`ResultView`)

- 각 영역의 결과는 `role="status"`(`aria-live="polite"`) 컨테이너, 이름은 `<영역 이름> 결과`(예: `예약 대상 결과`).
- `내 예약` 영역의 `대상 다시 조회` 결과는 예약 결과를 덮지 않도록 같은 영역 안의 별도 컨테이너 `내 예약 대상 결과`에 표시한다(2026-10-26 T030 구현 중 추가, 예약 결과와 대상 수량을 함께 관찰).
- 항상 `HTTP <status>` 텍스트를 표시한다(클라이언트 실패는 `HTTP 0` 대신 `응답 없음`).
- 성공: 응답 필드를 `<dl>`로 표시. `dt` 텍스트는 필드 이름 원문(`id`, `name`, `capacity`, `available`, `resourceId`, `status`, `createdAt`, `cancelledAt`), `dd`는 값 원문(`null`은 `null`). 시각 필드는 원문 뒤에 보조 로컬 시각을 괄호로 붙일 수 있다.
- 보존 헤더가 있으면 같은 `<dl>`에 `Location`, `Idempotent-Replayed`, `Retry-After`, `X-Upstream-WWW-Authenticate`를 `dt`로 표시.
- 예약 생성 성공: `재현 여부: 예` 또는 `재현 여부: 아니오`, 사용한 `생성 키: <값>`. 재현이면 `재현 응답은 최초 CONFIRMED 응답입니다. 현재 상태는 예약 조회로 확인하세요.`
- 오류: `code`(원문), `title`, `detail`, 한국어 설명 1문장. 429는 `<n>초 후 다시 시도할 수 있습니다`(n = `Retry-After` 원문).
- JSON이 아닌 응답: `알 수 없는 응답 형식` + 상태.

## C2.4 코드별 한국어 설명(고정 문구)

| code | 설명 |
|---|---|
| `INVALID_REQUEST` | 요청 형식이나 입력값이 계약에 맞지 않습니다. |
| `UNAUTHENTICATED` | 인증에 실패했습니다. 계정과 비밀번호를 확인하세요. |
| `FORBIDDEN` | 이 계정에는 해당 작업 권한이 없습니다. |
| `NOT_FOUND` | 대상을 찾을 수 없거나 접근할 수 없습니다. |
| `SOLD_OUT` | 잔여 수량이 없어 예약할 수 없습니다(품절). |
| `IDEMPOTENCY_CONFLICT` | 같은 생성 키가 다른 대상의 예약에 이미 사용되었습니다. |
| `PAYLOAD_TOO_LARGE` | 요청 본문이 백엔드 상한(8KiB)을 넘었습니다. |
| `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 Content-Type입니다. |
| `RATE_LIMITED` | 쓰기 요청 제한을 넘었습니다. |
| `DEPENDENCY_UNAVAILABLE` | 백엔드 의존성(DB·Redis) 장애로 처리하지 못했습니다. 변경은 반영되지 않았습니다. |
| `CONSOLE_ROUTE_NOT_ALLOWED` | 콘솔이 허용하지 않는 경로입니다. 백엔드로 전달하지 않았습니다. |
| `CONSOLE_FORBIDDEN_ORIGIN` | 허용되지 않은 출처의 요청입니다. 백엔드로 전달하지 않았습니다. |
| `CONSOLE_PAYLOAD_TOO_LARGE` | 콘솔 전달 상한(1MiB)을 넘었습니다. 백엔드로 전달하지 않았습니다. |
| `CONSOLE_UPSTREAM_UNREACHABLE` | 백엔드에 연결할 수 없습니다. 백엔드 실행 여부를 확인하세요. |
| `CONSOLE_UPSTREAM_TIMEOUT` | 백엔드 응답 시간이 초과되었습니다. |
| `CONSOLE_MISCONFIGURED` | 콘솔의 백엔드 주소 설정이 올바르지 않습니다. |
| `CONSOLE_CLIENT_TIMEOUT` | 콘솔 서버 응답이 없어 요청을 중단했습니다. |
| (그 밖) | 알 수 없는 오류 코드입니다. 원문을 확인하세요. |

## C2.5 최근 기록

- 항목 텍스트: `<종류> · <식별자> · <이름(있으면)> · <계정> · <기록 시각>`.
- 기록이 없으면 `기록 없음`.
- 항목 선택 시 해당 영역의 식별자 입력을 채우고 조회 요청을 보낸다.
