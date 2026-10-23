# 0단계 계약·사례 문서 검토 기록

**0단계 당시 상태:** 0단계 문서·학습 인수 완료, 작업27/27 완료. T025는 질의·보완 설명 뒤 사용자의 명시적 확인/완료 지시로 처리했다. 최초 답변의 오류와 확인 방식의 한계는 맨 아래 T025 기록에 남겼다. 이전 대기/부분 완료 상태는 당시 이력이다. 실제 기능 실행 검증은 여전히 미실행이다.

## 범위와 실행 환경

- 날짜: 2026-10-13. 기준 커밋: `9ecf782`, 작업 브랜치: `feature/define-api-contract`.
- 저장소: `/Users/joypeb/Desktop/projects/limited-booking`. Spec Kit 활성 기능: `specs/001-define-api-contract`.
- 시작 시 작업 트리 깨끗함. 선행 `345b954`·`771c418`과 명세 `9ecf782`가 포함돼 있으며 develop 통합·병합·push는 수행하지 않는다.
- 적용: [명세](../specs/001-define-api-contract/spec.md), [작업 목록](../specs/001-define-api-contract/tasks.md), [계획서](<../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절·0단계, [지식 안내](../knowledge/README.md), 헌법 I~V.
- 앱 골격은 Java 25·Boot 4.1.1·Gradle 9.7.1 설정이며 이번 작업에서 실행 버전 호환성을 검증하지 않았다.
- 이번 검증은 문서 내용·링크·공백·사례 수량식 대조다. 앱 테스트 수: 해당 없음(미실행). seed·스레드·DB 연결 수: 해당 없음(실제 경쟁 미실행).
- API·SQL·Flyway·인증 필터·Redis 기능은 미구현. HTTP·DB·Redis·자동 YAML/OpenAPI validator·Property·PIT·k6·외부 CI는 미실행.

## 수행 기록과 판단

| 작업 | 수행·검증 | 상태 |
| --- | --- | --- |
| T001 | 기준 문서·실제 앱 골격·Git 최상위·HEAD·브랜치·develop 차이·활성 기능 확인 | 완료 |
| T002 | 기존 명세 커밋 보존 확인, 현재 작업 브랜치 유지, 이 증거 파일 작성 | 완료 |

- 판단: 최신 작업 목록에 지정된 기존 feature 브랜치에서 작업한다. 추가 worktree나 develop 병합은 이번 범위에 필요하지 않다. 잘못된 판단이라면 후속 통합 시 브랜치 출처를 다시 검토해야 한다.
- 판단: 문서 작업의 검증은 계획대로 내용·링크·형식 검사로 수행한다. 실행 계획 스킬의 코드용 RED/GREEN·task-start 스크립트 대신 이 문서와 tasks.md에 작업별 검토를 기록한다. 이는 기능 TDD 증거가 아니다.
- 미확정: 이름의 제거할 공백 범위를 사용자에게 질문했다. 답변 전 독립 정책만 작성하며 공백 범위에 의존한 최종 계약 확정은 보류한다.

## 실제 명령

| 명령 | 종료 코드 | 확인 결과 |
| --- | --- | --- |
| `.specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks` | 0 | 지정 기능과 tasks.md 발견 |
| `git rev-parse --show-toplevel` / `git status --short` / `git branch -v` / `git log --oneline --all -8` | 0 | 루트·깨끗한 시작 상태·기존 브랜치/커밋 확인 |
| `git diff develop...HEAD --stat` | 0 | 선행 도구·헌법·명세 변경 확인, 병합 미실행 |
| Python 표준 라이브러리 체크리스트 집계 | 0 | requirements.md 16/16, 미확인 0; 품질 게이트만 판정 |


## 공통 문서 수행 기록

| 작업 | 검토 | 상태 |
| --- | --- | --- |
| T003 | resource 입력·필드·수량 정책 대조, 공백 범위 질문은 보류 기록 유지 | 완료 |
| T004 | reservation 템플릿 9개 절 작성·근거 및 트랜잭션 대조 | 완료 |
| T005 | security 템플릿 9개 절 작성·권한/본문 순서 대조 | 완료 |
| T006 | ratelimit 템플릿 9개 절 작성·고정 창/장애 대조 | 완료 |
| T007 | 도메인 4개·각 9개 절·상대 링크 Python 검사 종료 0, 목록 등록 | 완료 |

## US1: 키·예약·수량의 기대 결과

아래는 실행 결과가 아닌 수동 검토용 기대값이다. A/B는 서로 다른 인증 UUID, R1=`10000000-0000-0000-0000-000000000001`, R2=`10000000-0000-0000-0000-000000000002`, S1=`20000000-0000-0000-0000-000000000001`이다. t0=`2026-10-13T00:00:00.123456Z`, t1=`2026-10-13T00:01:00.123456Z`로 가정한다. 준비 데이터 생성은 실제 실행하지 않았다.

### 수량 1의 핵심 6개 행동

초기 R1 capacity=1, available=1, CONFIRMED=0, A/K 성공 기록 없음. 모든 요청은 유효한 인증·입력이고 제한/의존성 정상이다. 표의 A/K 기록은 취소 후에도 S1로 유지한다.

| ID | 행동·입력 | 초기 available/활성 | 응답·헤더 | 본문 주요 값 | 최종 available/활성 | A/K |
| --- | --- | --- | --- | --- | --- | --- |
| F01 | A, POST 예약 R1, K | 1/0 | 201, Location=/api/reservations/S1, Idempotent-Replayed=false | id=S1, resourceId=R1, status=CONFIRMED, createdAt=t0; cancelledAt 필드 없음 | 0/1 | S1 생성 |
| F02 | 같은 A/K/R1 생성 | 0/1 | 201, 동일 Location, Idempotent-Replayed=true | F01의 JSON 필드 값 동일 | 0/1 | S1 유지 |
| F03 | A, POST S1/cancel, 본문 0바이트 | 0/1 | 200, application/json | id=S1, resourceId=R1, status=CANCELLED, createdAt=t0, cancelledAt=t1 | 1/0 | S1 유지 |
| F04 | 같은 A/K/R1 생성 | 1/0 | 201, F01 Location, Idempotent-Replayed=true | 최초 CONFIRMED·t0 재현, cancelledAt 필드 없음; DB 상태는 CANCELLED | 1/0 | S1 유지 |
| F05 | A, GET S1 | 1/0 | 200, application/json | F03과 동일 현재 CANCELLED·t0·t1 | 1/0 | S1 유지 |
| F06 | A, POST S1/cancel 재전송, 본문 없음 | 1/0 | 200, application/json | F03과 같은 CANCELLED·t1 | 1/0 | S1 유지 |

Location의 S1 표기는 위 UUID 값을 대입하는 약칭이다. 매 행 available+활성=1이며 전체 복구는 한 번이다. 생성 응답과 현재 예약 응답을 혼합하지 않는다. 실제 최초/재요청은 저장 후 반환된 마이크로초 시각을 사용해야 하며 JSON 공백·필드 순서의 동일 바이트 보장은 없다.

### 실패 키의 복구와 응답 유실

각 시나리오는 독립 초기 상태이며 유효한 재시도 전에 원인·제한을 해소한다. 실패 키는 원래 미사용 키다.

| ID | 초기·행동 | 초기 available/활성 | 기대 응답 | 최종 available/활성 | 키 기록 |
| --- | --- | --- | --- | --- | --- |
| F07 | capacity=1, B가 R1 예약, A/F로 생성 | 0/1 | 409 SOLD_OUT, application/problem+json | 0/1 | B 성공 키 유지, A/F 없음 |
| F08 | F07 뒤 B가 본인 예약 취소 | 0/1 | 200 CANCELLED, 취소 시각 저장 | 1/0 | B 키 유지, A/F 없음 |
| F09 | F08 뒤 A/F/R1 생성 | 1/0 | 201 CONFIRMED, Location, Replay=false | 0/1 | A/F에 새 성공 행 |
| F10 | capacity=1 빈 R1, A/E에 잘못된 JSON | 1/0 | 400 INVALID_REQUEST | 1/0 | A/E 없음 |
| F11 | F10의 JSON 수정·동일 A/E | 1/0 | 201 CONFIRMED, Location, Replay=false | 0/1 | A/E 성공 행 |
| F12 | capacity=1 빈 R1, A/D, Redis 장애 또는 DB 차감 뒤 timeout·전체 롤백 | 1/0 | 503 DEPENDENCY_UNAVAILABLE | 1/0 | A/D 없음 |
| F13 | F12의 의존성 복구 후 동일 A/D | 1/0 | 201 CONFIRMED, Location, Replay=false | 0/1 | A/D 성공 행 |
| F14 | capacity=1 빈 R1, A/L 생성 커밋 뒤 응답 유실 | 1/0 | 클라이언트는 성공 응답을 못 받음; 커밋된 행은 CONFIRMED | 0/1 | A/L 성공 행 보존 |
| F15 | F14 뒤 제한 통과·A/L/R1 재요청 | 0/1 | 201, 최초 id/resourceId/t0/CONFIRMED/Location, Replay=true | 0/1 | 동일 A/L 성공 행 |

F12는 실패 트랜잭션이고 F14는 성공 커밋이다. 모든 503이 커밋 미발생을 증명하는 것으로 일반화하지 않고, 커밋 뒤 응답 유실은 성공 키로 확인한다.

### 키 범위와 추가 예약

| ID | 독립 초기 조건·행동 | 초기 available/활성 | 응답 | 최종 available/활성 | 행·키 |
| --- | --- | --- | --- | --- | --- |
| F16 | R1 capacity=1 A/K 성공, R2 capacity=1 비어 있음; A/K/R2 생성 | R1=0/1, R2=1/0 | 409 IDEMPOTENCY_CONFLICT | R1=0/1, R2=1/0 | 기존 R1 행 유지, R2 행 없음 |
| F17 | F16의 R1 취소 후 동일 A/K/R2 | R1=1/0, R2=1/0 | 409 IDEMPOTENCY_CONFLICT | R1=1/0, R2=1/0 | A/K는 취소된 R1 행 유지 |
| F18 | R1 capacity=3 비어 있음; A/K1 성공 뒤 A/K2 성공 | 3/0 | 각각 201, 서로 다른 id·Location, Replay=false | 1/2 | A/K1·A/K2 각각 한 행 |
| F19 | R1 capacity=2 비어 있음; A/K와 B/K 성공 | 2/0 | 각각 201, 서로 다른 id·Location, Replay=false | 0/2 | 사용자별 독립 키 두 행 |

id·resourceId·userId·key·createdAt는 생성 뒤 불변이다. resourceId는 필수 FK이며 사용자·키 전체 범위의 유일 제약이 적용된다. userId는 입력/응답 필드가 아니다.

### 경쟁의 문서상 허용 결과

모든 요청은 인증·제한을 통과하고 timeout/장애가 없다고 가정한다. 실제 요청 병행·DB 잠금·Future 확인은 후속 7단계에서 실행한다.

| ID | 초기 조건·병행 명령 | 허용 응답·최종 상태 |
| --- | --- | --- |
| C01 | capacity=1 빈 R1, A/K/R1 두 생성 | 둘 다 동일 행의 201·CONFIRMED; 최초 하나 Replay=false, 다른 하나 true. available=0·활성=1·A/K 한 행. 패자를 SOLD_OUT으로 처리하지 않는다. |
| C02 | R1/R2 각각 capacity=1 비어 있음, A/K로 서로 다른 대상 생성 | 한 대상만 201, 다른 요청 409 IDEMPOTENCY_CONFLICT. 승자 available=0·활성=1, 패자 available=1·활성=0; 같은 키 행 하나. 패자 차감은 롤백. |
| C03 | capacity=1 R1에 S1 CONFIRMED, A가 S1 두 취소 | 둘 다 200·동일 CANCELLED/취소 시각. available=1·활성=0, S1·성공 키 보존; 수량 복구 1회. |
| C04 | capacity=1 R1에 A의 S1 CONFIRMED; A가 S1 취소와 새 키 N 생성 병행 | 취소는 200. 생성이 먼저 품절 판단하면 409 SOLD_OUT, 최종 available=1·활성=0·N 없음. 취소 복구 뒤 생성하면 201, 최종 available=0·활성=1·새 N 행. 기존 S1는 CANCELLED·키 유지. |
| C05 | capacity=1 빈 R1, available UPDATE 성공 뒤 INSERT에 비멱등 내부 결함 주입 | 전체 롤백: available=1·활성=0·새 키 없음. 내부 결함은 500 탐지, 임의 409/503으로 숨기지 않음. 의존성 timeout을 주입한 별도 경우는 503. |

수동 대조: F01~F19 19행·C01~C05 5행의 기대 수량식·키 유일성·복구 최대 1회를 원 계획 2·5절과 비교했다. 모순 0건. C04의 두 결과는 실행 순서에 따른 허용 결과이며 미정 정책이 아니다. 실제 경쟁 검증 건수는 0건(미실행)이다.

## 작업별 진행 기록

| 작업 | 검토 결과 | 상태 |
| --- | --- | --- |
| T008 | F01~F06에서 최초 응답과 현재 상태·수량·키 대조 | 완료 |
| T009 | F07~F15에서 실패 키 재사용·커밋 후 유실 구분 | 완료 |
| T010 | F16~F19에서 대상 전체 키 범위·다른 키/사용자 구분 | 완료 |
| T011 | F 19행·C 5행의 수량식·상태·유일성 대조, 모순 0 | 완료 |

## US2: 다섯 API와 경계 사례

각 행은 별도 초기 상태이며 R1/R2/S1/t0/t1 약칭은 US1과 같다. 별도 언급이 없으면 Basic 인증·Redis/DB 정상·허용 횟수 이내다. POST JSON의 Content-Type은 application/json이다. 성공은 application/json, 아래 오류는 모두 application/problem+json의 type/title/status/detail/instance/code를 포함하고 SQL·스택·비밀번호를 노출하지 않는다. 응답 Location/Replay의 Replay는 정확한 헤더명 Idempotent-Replayed의 약칭이다. 현재 예약 응답 cancelledAt는 필수이며 null을 허용한다. 읽기·거부는 업무 데이터와 성공 키에 무변경이지만 인증 후 예약 쓰기 진입은 제한 횟수에 포함한다.

| ID | API | 초기 사용자·상태 | 요청 | 기대 응답·헤더·필드 | 최종 업무 상태·키 |
| --- | --- | --- | --- | --- | --- |
| R01 | 대상 생성 | ADMIN, 대상 없음 | JSON name=예약 대상,capacity=0 | 201 ResourceView, Location=/api/resources/R1 | R1 capacity/available=0/0·활성0, 키 해당 없음 |
| R02 | 대상 생성 | USER_A, 대상 없음 | R01 유효 JSON | 403 FORBIDDEN | 대상 없음·키 해당 없음 |
| R03 | 대상 생성 | ADMIN, 대상 없음 | capacity="1" 또는 1.5 | 400 INVALID_REQUEST | 대상 없음·키 해당 없음 |
| R04 | 대상 조회 | A, R1 capacity=1·available=1·활성0 | GET R1, 본문 없음 | 200 ResourceView: id=R1,name,capacity=1,available=1 | R1=1/0, 키 무변경 |
| R05 | 대상 조회 | 미인증, R1=1/0 | GET R1 | 401 UNAUTHENTICATED, WWW-Authenticate Basic | R1=1/0, 키 무변경 |
| R06 | 대상 조회 | A, R1 없음 | GET R1 | 404 NOT_FOUND | 대상 없음·키 무변경 |
| R07 | 예약 생성 | A, R1 capacity=1·available=1·활성0, K 없음 | JSON resourceId=R1, Idempotency-Key=K | 201 CreatedReservationView: S1/R1/CONFIRMED/t0, Location=/api/reservations/S1, Replay=false | R1=0/1·A/K=S1 |
| R08 | 예약 생성 | A, R1 capacity=1·available=0·활성1, F 없음 | JSON resourceId=R1, key=F | 409 SOLD_OUT | R1=0/1·F 없음, 기존 키 유지 |
| R09 | 예약 생성 | A/K=R1의 S1, R1=0/1, 빈 R2 capacity=1 | JSON resourceId=R2, key=K | 409 IDEMPOTENCY_CONFLICT | R1=0/1·R2=1/0·A/K=S1 유지 |
| R10 | 예약 조회 | A 소유 S1 CONFIRMED, R1 capacity=1·available=0·활성1 | GET S1 | 200 ReservationView: S1/R1/CONFIRMED/t0/cancelledAt=null | R1=0/1·S1·A/K 무변경 |
| R11 | 예약 조회 | B 또는 ADMIN, A 소유 S1, R1=0/1 | GET S1 | 404 NOT_FOUND, 없는 UUID 조회와 동일 오류 의미 | R1=0/1·S1·A/K 무변경 |
| R12 | 예약 조회 | A, R1=0/1 | GET id=bad-uuid | 400 INVALID_REQUEST | R1=0/1·행/키 무변경 |
| R13 | 예약 취소 | A 소유 S1 CONFIRMED, R1 capacity=1·available=0·활성1 | POST S1/cancel, 0바이트 본문 | 200 ReservationView: S1/R1/CANCELLED/t0/t1 | R1=1/0·S1 CANCELLED·A/K 유지 |
| R14 | 예약 취소 | B 또는 ADMIN, A 소유 S1, R1=0/1 | POST S1/cancel, 본문 없음 | 404 NOT_FOUND | R1=0/1·CONFIRMED·A/K 유지 |
| R15 | 예약 취소 | A 소유 S1, R1=0/1 | POST S1/cancel, {} 2바이트 | 400 INVALID_REQUEST | R1=0/1·CONFIRMED·키 유지 |
| R16 | 대상 생성 | ADMIN, 대상 없음 | name=😀 100개, capacity=1000 | 201 ResourceView: name 그대로, capacity=available=1000, Location | 대상 생성·활성0·키 해당 없음 |
| R17 | 대상 생성 | ADMIN, 대상 없음 | name=😀 101개, capacity=1 | 400 INVALID_REQUEST | 대상 없음·키 해당 없음 |
| R18 | 대상 생성 | ADMIN, 대상 없음 | ASCII 공백 2개+😀 100개+ASCII 공백 2개,capacity=1 | 201 ResourceView: name=😀 100개, capacity=available=1, Location | R1=1/0·키 해당 없음 |
| R19 | 대상 생성 | ADMIN, 대상 없음 | name이 빈 문자열 또는 ASCII 공백만,capacity=-1 또는 1001의 독립 입력 | 400 INVALID_REQUEST | 대상 없음·키 해당 없음 |
| R20 | 예약 생성 | A, 빈 R1 capacity=1, K 없음 | 동일 resourceId를 중복 JSON 키로 전송 | 400 INVALID_REQUEST | R1=1/0·K 없음 |
| R21 | 예약 생성 | A, 빈 R1 capacity=1, K 없음 | JSON에 quantity=1 또는 userId 추가 | 400 INVALID_REQUEST | R1=1/0·K 없음 |
| R22 | 예약 생성 | A, 빈 R1 capacity=1 | 키 1자 A 또는 64자 A, 각각 독립 초기 상태 | 201, Location·Replay=false·CONFIRMED | 각각 R1=0/1·키별 성공 행1 |
| R23 | 예약 생성 | A, 빈 R1 capacity=1 | 키 누락/빈값/65자/공백/허용 외 문자, 각 독립 상태 | 400 INVALID_REQUEST | R1=1/0·새 키 없음 |
| R24 | 예약 생성 | A, 빈 R1 capacity=1 | Content-Type=text/plain 또는 미지원 유형 | 415 UNSUPPORTED_MEDIA_TYPE | R1=1/0·키 없음 |
| R25 | 예약 생성 | A, 빈 R1 capacity=1, K 없음 | 유효 resourceId JSON 뒤 ASCII 공백 패딩해 UTF-8 총8192바이트 | 201, Location·Replay=false·S1/R1/CONFIRMED/t0 | R1=0/1·A/K=S1 |
| R26 | 예약 생성 | 미인증, 빈 R1 capacity=1 | R25를 총8193바이트로 패딩; Content-Length 있음/없음·chunked 각 독립 상태 | 413 PAYLOAD_TOO_LARGE, 인증 앞에서 거부 | R1=1/0·키 없음, Redis 미진입 |
| R27 | 예약 취소 | 미인증, A 소유 S1, R1=0/1 | S1/cancel에 8193바이트 본문 | 413 PAYLOAD_TOO_LARGE | R1=0/1·CONFIRMED·키 유지 |
| R28 | 예약 취소 | A, A 소유 S1, R1=0/1, 제한 정상 | S1/cancel에 ASCII 공백8192바이트 | 400 INVALID_REQUEST: 상한 이내라도 비어 있지 않은 본문 | R1=0/1·CONFIRMED·키 유지, 제한 횟수 포함 |
| R29 | 예약 생성 | A, 빈 R1=1/0, 창 count20·PTTL=1501ms | 유효 K 생성 또는 기존 키 재요청 | 429 RATE_LIMITED, Retry-After=2 | R1·행·키 무변경, 카운터 증가·TTL 비연장 |
| R30 | 예약 취소 | A, 소유 S1 CONFIRMED, R1=0/1, 창count20·PTTL=1ms | S1/cancel 본문 없음 | 429 RATE_LIMITED, Retry-After=1 | R1=0/1·CONFIRMED·키 유지 |
| R31 | 예약 생성 | A, 빈 R1=1/0, Redis 장애 | 유효 K 생성 | 503 DEPENDENCY_UNAVAILABLE | R1=1/0·키 없음·DB 트랜잭션 시작 안 함 |
| R32 | 예약 취소 | A, 소유 S1 CONFIRMED, R1=0/1, Redis 장애 | S1/cancel | 503 DEPENDENCY_UNAVAILABLE | R1=0/1·CONFIRMED·키 유지 |
| R33 | 대상 생성 | ADMIN, Redis 장애·DB 정상 | 유효 name/수량1 | 201 ResourceView, Location | 새 R1=1/0; Redis 미진입 |
| R34 | 대상 조회 | A, Redis 장애·DB 정상, R1=1/0 | GET R1 | 200 ResourceView | R1=1/0·키 무변경 |
| R35 | 예약 조회 | A 소유 S1, Redis 장애·DB 정상, R1=0/1 | GET S1 | 200 ReservationView | R1=0/1·S1·키 무변경 |
| R36 | 예약 생성 | A, 빈 R1=1/0, K 없음, Redis 정상 | UPDATE 뒤 DB timeout | 503 DEPENDENCY_UNAVAILABLE | 전체 롤백·R1=1/0·K 없음, 제한 카운터 복구 안 함 |
| R37 | 예약 생성 | A, R1 없음, K 없음 | 유효 UUID R1·K | 404 NOT_FOUND | 대상 없음·K 없음 |
| R38 | 예약 취소 | A, S1 이미 CANCELLED/t1, R1=1/0 | S1/cancel 본문 없음 | 200 S1/R1/CANCELLED/t0/t1, 동일 취소 시각 | R1=1/0·S1·A/K 유지 |
| R39 | 예약 생성 | A, 빈 R1=1/0, K 없음 | 유효 JSON에 resourceId=bad 또는 잘못된 JSON 원문 | 400 INVALID_REQUEST | R1=1/0·K 없음 |
| R40 | 대상 조회 | 미인증, R1=1/0 | GET R1에 8193바이트 본문 | 413 PAYLOAD_TOO_LARGE | R1=1/0·키 무변경 |
| R41 | 예약 조회 | 미인증, R1=0/1·S1 | GET S1에 8193바이트 본문 | 413 PAYLOAD_TOO_LARGE | R1=0/1·S1·키 무변경 |

수동 대조 결과: R01~R15는 다섯 API마다 정상1·비정상2의 최소 15개다. 추가 R16~R41로 총 41개 독립 사례를 작성했다. 본문 상한은 문자열 길이가 아닌 UTF-8 실제 바이트이며 8192바이트의 유효 JSON 사례와 취소의 비어 있지 않은 본문 거부를 구분했다. 표의 각 행·분기 기대값을 원 계획 4절·계약 설계·OpenAPI와 대조한다. 이 기록은 실제 HTTP 응답을 받았다는 뜻이 아니다.


## US3: 결정 기록 검토

[결정 기록](decisions.md)의 세 결정은 각각 4개 항목(4줄)으로 선택·이유·대안·영향·미구현 상태를 적었다. JDBC는 SQL·Writer 경계의 이해, 성공 행 키 보존은 성공 응답 재구성, Redis는 업무 트랜잭션 앞의 진입 제한이라는 목적을 조사와 원 계획 3·5절에 대조했다. 실패 키 재사용과 취소 후 최초 CONFIRMED 재현의 설명 모순은 0건이다. 사용자의 설명 능력은 아직 확인하지 않았다.

## 추가 수행 기록

| 작업 | 검토 | 상태 |
| --- | --- | --- |
| T012 | localhost·Basic·다섯 path/메서드·UUID parameter·권한 선언 | 완료 |
| T013 | 요청 2개 스키마 작성; 제거할 공백 범위 답변 대기 | 미완료 |
| T014 | 응답 3개 필드·enum·UTC·필수 nullable cancelledAt 대조 | 완료 |
| T015 | 생성 본문·성공 응답·Location·키·Replay 선언 대조 | 완료 |
| T016 | 오류 10개와 401/429 헤더·default 내부 결함 설명 대조 | 완료 |
| T017 | 실제8192바이트·chunked·처리 순서·Redis 제한/장애 명시 | 완료 |
| T018 | API 최소15+경계26의 독립 사례41행 작성·무변경 조건 대조 | 완료 |
| T019 | 공백 정책 확인 뒤 최종 누락/모순 전수 대조 예정 | 미완료 |
| T020~T022 | JDBC·성공 행 키·Redis 결정 각4줄 작성 | 완료 |
| T023 | 세 결정 내용 대조 완료, T019 의존성 충족 뒤 완료 표시 | 미완료 |
| T025 | 사용자에게 본인 설명 요청, 미확인 | 미완료 |

- 판단: 본문 제한은 모든 요청에 적용하므로 GET 두 작업에도 413을 명시했다. 원 계획 4.1의 표는 주요 실패 목록이며 4.1 공통 문단의 모든 요청 상한을 함께 적용한 것이다. 새 업무 정책은 아니며 후속 계약 검증에서 GET 본문 초과도 확인해야 한다.
- 판단: C04 생성·취소 경쟁은 예약 생성의 품절 판정 순서에 따라 두 결과를 허용한다. 특정 실행 순서를 보장하지 않는다. 잘못된 기대값이면 경쟁 시험에서 순서·최종 상태를 다시 검토해야 한다.

## 문서 동기화 준비와 품질 재검토

- T024 준비: 네 도메인과 knowledge 목록에 실제 작성된 계약·결정·증거 링크를 연결했다. 계획 작성 당시 기록과 현재 작성 상태를 구분했다. T019/T023 완료 전 전체 정리 작업의 완료 표시는 보류한다.
- 체크리스트 16개 항목을 재대조했다. 기존 체크리스트 파일/표식은 변경하지 않았다(speckit-implement의 읽기 전용 게이트). 분석 후 추가 확인한 공백 범위 때문에 “추가 확인 표식 없음·모든 결과 판정 가능” 항목은 최종 재평가가 필요하다. 기존 품질 체크를 최신 완료 증거로 사용하지 않는다.
- 제외 확인: Git은 .env·빌드·class·일반 jar·임시 작업 폴더를 무시한다. Gradle Wrapper jar는 무시하지 않는다(`git check-ignore` 종료1은 이 경우 정상). Docker/Node/Terraform/Helm 파일이나 이번 단계의 해당 빌드 설정이 없어 별도 ignore 파일을 추가하지 않았다.

## 최종 문서 검사 실행 기록 (2026-10-13)

환경: Python 3.12.13, Git 2.54.0 (Apple Git-157), Darwin arm64. 앱/JDK/Docker 버전 실행 확인은 이번 범위 밖이며 위 설정 버전과 구분한다.

| 실제 명령 | 종료 코드 | 결과·한계 |
| --- | --- | --- |
| quickstart.md의 첫 Python heredoc | 0 | 설계 문서7개·상대 링크 |
| quickstart.md의 추가 Python heredoc | 0 | Markdown23개·도메인4개·API5개 필수 표식·사례65행 존재 |
| `test -s docs/openapi.yaml` | 0 | 비어 있지 않은 계약 초안 존재 |
| `test -s docs/decisions.md` | 0 | 결정3개 작성 |
| `test -s docs/test-evidence.md` | 0 | 기대값·실제 검사·미검증 기록 존재 |
| `git diff --check` | 0 | 추적 변경 공백 오류 없음; 새 문서는 추가 Python 형식 검사에 포함 |
| `python3 --version` / `git --version` / `uname -sm` | 0 | 위 문서 검사 환경 |

검사 코드는 [quickstart](../specs/001-define-api-contract/quickstart.md)에 있어 재현할 수 있다. 링크 검사는 파일 존재를 확인하고 앵커·외부 URL·정책의 정답을 자동 검증하지 않는다. YAML 문법·OpenAPI 표준/validator·실제 HTTP 테스트 통과를 주장하지 않는다. 시나리오 수량식과 계약 의미는 별도 수동 대조이며 공백 정책에 의존한 최종 인수는 아직 대기 중이다.

T026: 독립 문서 검사·파일 존재·필수 항목/상대 링크/형식 검사 실행 완료. T024의 링크 연결 준비는 이미 반영됐으나 최종 정책 확인 전 전체 정리 완료 표시는 보류한다.

## 독립 문서 검토와 보완

executing-plans가 요구한 독립 리뷰를 읽기 전용 에이전트가 수행했다. Critical0·Important1·Minor2를 보고했고 순차19/경쟁5행의 수량·키 기대값에 모순은 발견하지 않았다.

- Important: 초기 공통 ProblemDetail만으로는 HTTP상태·본문status/code 대응을 강제하지 못했다. 응답별 allOf에 status singleton과 허용 code enum(409만 두 코드)을 추가했다. 새 code는 추가하지 않았다.
- 보완 전 선언 검사: HTTP400의 상태/코드 제한 누락 AssertionError, 종료1. 보완 후 응답9종·오류코드10개 선언 검사 종료0. 첫 보완 후 검사 자체에서 들여쓰기와 무관한 문자열 분할을 사용해 블록을 잘못 잘라 실패했다. 원인은 검사 코드 경계 탐지이며 정확히 4칸 들여쓴 응답명을 기준으로 검사해 확인했다. 실제 validator의 고의 오류 거부는 후속 단계 미실행이다.
- reservation의 사례 상태는 T024 동기화 범위로 실제 작성·대조됨/HTTP미실행에 맞췄다.
- 403 예시 instance는 대상 생성 경로 /api/resources로 맞췄다. 예시 경로의 계약 의미 오류이므로 단순 문체 개선보다 인수 정확성의 보완으로 판단했다.
- 판단 유보 처리: 공백 범위·본인 설명은 사용자 답변 전 미확정/미검증으로 유지한다. YAML/validator/실제HTTP·DB·Redis는 이번 문서 작업 밖으로 유지하고 검증했다고 주장하지 않는다. default는 내부 결함 설명을 남기되 허용 HTTP상태의 엄격한 후속 검사는 별도로 필요하다. 이 판단이 잘못되면 후속 계약 검증에서 예상 밖 HTTP상태를 놓칠 수 있으므로 검사 항목으로 넘긴다.

## 부분 완료와 다음 작업

- 변경 이유: 구현 전에 재요청·취소·실패 키·수량·권한의 기대값을 고정하고 독립 검토 가능한 계약 초안과 결정 기록을 마련했다.
- T027: 원 계획 0단계에서 실제 충족한 사례 작성·키/형식 선언·세 결정 기록만 표시한다. 공백 문자 범위·본인 설명 대기 상태를 그대로 남겨 전체 완료로 표시하지 않는다. 목적별 로컬 문서 커밋은 검증 후 수행하며 결과는 아래에 기록한다.
- 미완료: T013(공백 정책), T019(최종 계약 인수), T023(T019 이후 결정 검토 종료), T024(최종 정책 상태·링크 정리 종료), T025(본인 설명). 관련 초안·검토·링크 연결 자체는 작성됐지만 선행 조건 충족 전 체크를 하지 않는다.
- 본인 설명 질문과 공백 범위 질문을 제시했으며 답변을 아직 받지 않았다. 사람이 실제 설명한 것으로 대리 기록하지 않는다.
- 핵심 읽을 문서: [고정 계약 초안](openapi.yaml), [65행 기대값·검사 증거](test-evidence.md), [세 결정](decisions.md), [예약 지식](../knowledge/domain/reservation.md).
- 다음 한 작업: 공백 범위 답변을 계약·계획서·지식·사례에 동기화하고 본인 설명을 확인해 남은 0단계 인수를 마친다. 완료 후 1단계 최소 테스트 도구 준비로 넘어간다.
- 유지: feature/define-api-contract 작업 브랜치, 기존 Gradle/Java/패키지. develop병합·push·외부CI·배포 미수행.

## 로컬 커밋과 최종 상태 기록

- `git add -- <검토한14개명시경로>`: 최초 sandbox 실행은 index.lock 권한 오류로 종료128. 같은 명시 경로의 권한 확장 요청이 승인돼 종료0으로 스테이징했다. 파일 삭제·브랜치 이동·병합은 하지 않았다.
- `git diff --cached --check`: 종료0. staged 통계14파일·문서 변경만 확인.
- `git commit -F /private/tmp/limited-booking-contract-commit.txt`: 종료0, `972d6b8` (`docs(api-contract): 0단계 계약 초안과 수량 검토 사례를 작성한다`).
- 커밋 직후 `git status --porcelain`: 종료0·출력 없음. T027의 문서 검사·충족 항목 표시·미완료 기록·목적별 로컬 커밋 수행을 확인했다. 이 결과 기록의 추가 커밋은 후속 상태 기록이며 위 계약 커밋을 수정하지 않는다.
- T027 완료: 부분 완료 기록도 허용하는 작업 정의에 따라 완료한다. 전체 기능/0단계 완료는 아니다. 작업22/27 완료, T013·T019·T023·T024·T025 미완료.
- after_implement 확장 훅: `.specify/extensions.yml` 없음, 실행 대상 없음.

## 2026-10-13 추가 답변 반영: 이름은 Java strip 기준

현재 상태: 사용자가 “Java strip() 기준으로 한다”라고 답변했다. 최초 수행의 공백 확인 대기·22/27 기록은 당시 이력이며, 아래 검증 후 현재 상태는 26/27 완료·T025 본인 설명만 미완료다.

이름 앞뒤는 Java 25 `String.strip()`과 `Character.isWhitespace(int)` 기준으로 제거하고 중간 공백을 유지한다. 제거 후 코드 포인트 1~100개여야 하며 빈 문자열은 400이다. U+00A0(NBSP)·U+2007·U+202F는 제거하지 않고 길이에 포함한다. NBSP만 있는 이름도 별도 거부 규칙 없이 길이 조건에 따라 허용한다. 이름 처리의 기준이며 Idempotency-Key에는 strip을 적용하지 않는다.

아래 입력은 JSON의 name 문자열로 표현했으며 `\t`, `\n`, `\uXXXX`는 JSON escape를 해석한 문자를 뜻한다. 각 행은 ADMIN·DB 정상·새 대상 생성·capacity=1의 독립 상태다. 성공은 application/json ResourceView의 id·name·capacity=1·available=1과 Location, 실패는 application/problem+json의 400 INVALID_REQUEST이며 대상 미생성이다. 성공·실패 모두 기존 예약·성공 키는 무변경이다. 실제 API 요청을 보내지 않았고 후속 Unit·HTTP 테스트의 고정 기대값이다.

| ID | 요청 name | strip 후 저장 이름·코드 포인트 수 | 기대 응답·최종 상태 |
| --- | --- | --- | --- |
| W01 | `"\t  예약 대상 \n"` | `"예약 대상"`, 5자, 중간 공백 유지 | 201·새 대상 available=1·활성0 |
| W02 | `"\t\n "` | 빈 문자열, 0자 | 400 INVALID_REQUEST·대상 미생성 |
| W03 | `"\u00A0예약 대상\u00A0"` | NBSP를 포함한 원문, 7자 | 201·이름 양끝 NBSP 유지·available=1·활성0 |
| W04 | `"\u00A0"` | NBSP 1자 | 201·NBSP를 별도 공백 전용 이름으로 거부하지 않음·available=1·활성0 |
| W05 | `"\u2007대상\u202F"` | 양끝 비줄바꿈 공백 포함, 4자 | 201·원문 유지·available=1·활성0 |
| W06 | 탭 + 😀100개 + 줄바꿈 | 😀100개, 100자 | 201·available=1·활성0 |
| W07 | 줄바꿈 + 😀101개 + 탭 | 😀101개, 101자 | 400 INVALID_REQUEST·대상 미생성 |

### 최종 문서 계약 대조

| 항목 | 대조 기준·검토 대상 | 결과 |
| --- | --- | --- |
| 입력·다섯 API | 원 계획4.1 vs OpenAPI5개 path/메서드·요청객체2개·UUID·필수키·권한 | 누락/모순0 |
| 엄격한 입력 | additionalProperties false·중복키·미디어유형·8KiB·실제바이트·chunked·취소본문·strip | 누락/모순0, 실행 미검증 |
| 응답 | ResourceView4개/CreatedReservationView4개/ReservationView5개 필수필드·현재null·CONFIRMED재현·UTC | 누락/모순0 |
| 오류 | 10개 code·9개 HTTP/code 조합·401/429 필수헤더·필터/인증 동일오류·비밀정보 비노출 | 누락/모순0 |
| 키·취소·수량 | F19행·C5행·R41행과 W7행의 독립 상태·응답·키·수량식 | 기대값 모순0, 실제경쟁/DB 미실행 |
| 결정 | JDBC·성공행키·Redis3개 결정 각4줄·선택/이유/대안/영향·실행상태 | 3/3, 모순0 |
| 도메인 동기화 | 원 계획·명세·모델·계약설계·조사·지식규칙·resource·OpenAPI에 strip 기준 | 누락/모순0 |

이번 확정은 미정이던 정규화 문자 범위를 명확하게 한 것이다. 기존 확정 계약을 테스트 통과를 위해 바꾸지 않았다. NBSP 보존/단독 허용을 명시했으며 후속 규칙·HTTP 시험에서 이 기대값을 검증해야 한다. 순차/경쟁24행+기존API41행+strip7행=총72개 문서 사례다. API별 최소15개는 계속 충족한다.

### 작업 상태와 검증 범위

- T013: 요청 스키마·name strip 정책·미정의 필드 금지를 대조해 완료.
- T019: 원 계획4절·계약설계·OpenAPI·모든 사례72행의 권한/상태/헤더/업무 변경을 수동 대조, 누락·모순0으로 완료.
- T023: T019 이후 세 결정의 각4줄·이유/대안/영향·실패키/취소후 재현을 조사와 다시 대조해 완료.
- T024: 실제 문서 링크·작성 상태를 갱신하고 strip 정책을 기준문서·지식·명세·계약에 동기화해 완료.
- T025: 본인 설명 미확인으로 미완료. 이 공백 정책 답변을 예약/키/세 결정의 설명 완료로 대체하지 않는다.
- 원 계획의 OpenAPI 선언 항목은 문서 인수 후 표시한다. 본인 설명 체크와 0단계 전체 완료는 유지하지 않는다.
- 다음 한 작업: T025에서 사용자의 키 재사용·실패 키·다른 키·취소 후 재요청·세 선택 이유 설명을 확인한다. 이후 1단계 최소 테스트 도구를 준비한다.
- 미검증: Java strip의 기기상 실행·API/HTTP·DB/Redis·YAML/OpenAPI validator·Property/PIT/k6·외부 CI. 앱 코드는 이번 답변 반영에서 변경하지 않았다.

### 추가 답변 반영의 실제 검사

- quickstart.md 첫 Python heredoc: 종료0, 설계문서7개·상대링크 검사.
- quickstart.md 추가 Python heredoc: 종료0, 문서23개·도메인4개·API5개 필수표식·사례72행(F19+C5+R41+W7) 존재·링크·형식 확인. 기존 실행환경 Python3.12.13을 사용했다.
- `git diff --check`: 종료0.
- 작업표식 Python 집계: 총27·완료26·미완료 T025만 확인. 체크리스트 파일/표식은 변경하지 않았다.
- 이 검사는 문서 텍스트·링크·형식이며 Java 실행이나 YAML파싱/OpenAPI검증기가 아니다.


## 2026-10-13 T025: 질의·보완 설명 후 사용자 확인

사용자가 직접 키 재요청·품절 키 재사용·새 키 추가 예약·취소 후 생성 요청·JDBC/Redis 선택 이유에 대한 답변을 작성했다. 최초 답변 전체를 정답으로 판정하지 않았으며 아래 항목을 설명·보완했다.

| 확인 항목 | 최초 사용자 설명 | 대화에서 보완한 기준 |
| --- | --- | --- |
| 같은 키 재요청·다른 대상 | 동일 응답·다른 대상은 예외 | 같은 인증 사용자·키·대상은 최초201/Location/JSON 재현, 다른 대상은409 IDEMPOTENCY_CONFLICT. 인증·제한 통과가 전제 |
| 품절 실패 키 | 다시 사용 불가 | 실패는 키를 보존하지 않으므로 수량 복구·원인 해소 뒤 같은 키로 성공 가능 |
| 새 키 추가 예약 | 가능 | 수량이 있고 인증·제한을 통과하면 요청당1개 추가 가능 |
| 취소 후 같은 키 생성 | 예외 반환 | 최초201·CONFIRMED 생성 응답 재현, 새 예약·차감 없음. GET은 현재CANCELLED, 새 예약 의도에는 새 키 사용 |
| JDBC | 필요한 내용 저장 | NamedParameterJdbcTemplate로 SQL·조건부 수량 갱신·Writer 트랜잭션·롤백을 직접 이해·검증하는 목적 |
| 성공 행의 키 | 최초 설명에 구체적 이유 없음 | 사용자·키로 기존 성공 행을 찾아 중복 예약/차감 방지와 최초 응답 재현, 취소 후도 키 보존 |
| Redis | 캐싱·휘발성 정보 보관 | 인증된 예약 생성/취소 합산20회/60초의 진입 횟수·TTL만 관리, 수량/예약 원본은PostgreSQL |

추가로 사용자는 키의 의미·클라이언트 보관·취소 후 생성 재요청·예약의 의미를 질문했다. 공연 입장권 예시로 resource는 대상/총수량, reservation은 사용자가 수량1개를 확보한 기록임을 설명했다. 클라이언트는 같은 의도의 재전송에 같은 키를 보내고 서버는 성공 예약 행에 사용자·키를 저장한다. 응답 유실·늦은 재전송 때 추가 예약을 막는 이유와 현재 상태 조회/새 키 예약을 구분했다.

그 뒤 사용자는 **“확인했어. T025처리해줘”**라고 명시했다. 최신 지시에 따라 보완 설명 후 사용자의 이해 확인·학습 인수를 완료 근거로 삼아 T025를 완료 처리한다. 최초 답변에 오류가 없었다거나 사용자가 모든 정답을 독립적으로 다시 설명했다고 기록하지 않는다. 보완 후 독립 재설명 시험은 실시하지 않았다. 이는 사용자 확인에 따른 0단계 학습 완료이며 API·DB·Redis·실행 호환성·운영 정확성의 증거가 아니다.

- 변경: T025 체크, 작업27/27 완료, 원 계획0단계 설명 항목 및 현재 상태 동기화. 기존 사례72행·업무 정책·OpenAPI는 변경 없음.
- 다음 한 작업: 1단계 최소 테스트 도구 준비. 기존 Gradle·Java25·패키지를 유지하고 실제 실행으로 도구 발견·호환성을 검증한다.
- 미검증: Java/API/HTTP·DB/Redis·YAML/OpenAPI validator·Property/PIT/k6·외부CI. 새 기능·환경 구현이나 실행 테스트는 이번 작업에서 수행하지 않았다.

검증: quickstart의 Python 검사2개 종료0(설계문서7개·전체문서23개·사례72행), 작업27/27 표식 집계 종료0, `git diff --check` 종료0. 앱 테스트는 미실행이다. after_implement 확장 훅 파일은 없어 실행 대상이 없다.

## 1단계 환경·테스트 도구 착수 (2026-10-13)

- 적용: knowledge/README.md, rules 전체, security 도메인, 원계획 7절 1단계 및 명세/실행 계약. 업무 API·SQL은 이번 검증 범위 밖이다.
- 저장소 최상위 `/Users/joypeb/Desktop/projects/limited-booking`, 착수 HEAD `71b677c`. develop과 문서 브랜치 HEAD 동일. 기존 변경은 미추적 specs/002-setup-test-toolchain/뿐이며 보존했다.
- `git switch -c chore/setup-test-toolchain develop`: 종료0, 실제 브랜치 생성. 별도 저장소·Maven·패키지 교체 없음.
- 체크리스트 게이트: requirements 16/16, toolchain 30/30, 미체크0. CHK022 검토 완료를 포함한다. 이 표시는 요구사항 품질 검토이며 구현 완료가 아니다. implement에서는 체크리스트를 수정하지 않는다.
- `backend/`의 `./gradlew --version`: 종료0, Gradle9.7.1·Corretto25.0.4 확인.
- 기존 골격 기준 `./gradlew test --rerun-tasks`: 종료0, contextLoads 1건·실패0·오류0·skip0. 새 분류 및 DB/Redis 검증의 증거가 아니다.
- `docker info`: sandbox 안에서는 socket 접근 거절; 승인된 로컬 실행에서는 daemon 미기동 확인. `open -a Docker` 종료0 후 데몬29.2.1/arm64/메모리8,216,915,968 bytes 확인(종료0).
- `brew install k6`: 종료0, 누락된 k6만 설치. `k6 version`: 종료0, 2.3.0. 부하 실행은 후속 단계다.
- 두 고정 이미지 docker pull: 각각 종료0. 이미지 버전·digest 및 실제 연동 결과는 versions 기록에 연결한다.
- T001~T003 준비 완료. T004 환경 취득 확인 진행 중. 기능·Compose 기동·데이터 보존·새 도구 조합은 아직 미검증이다.

### US1 로컬 기동·보존

- dependencyInsight의 Testcontainers/Jupiter/Flyway/PostgreSQL 각각 종료0, versions에 해석값 기록. Gradle tasks --all에서 네 분류 태스크를 확인했다. 아직 사례 0건인 태스크를 성공으로 실행하지 않았다.
- Compose 비밀값 누락 config --quiet 종료1(필수 변수 누락). 이번 로컬 시험용 비밀값은 무작위 생성·0600 .env에만 저장했고 git check-ignore로 제외 확인. 기존 .env 없음 확인 후 생성했다.
- compose up -d --wait --wait-timeout120 종료0, 두 서비스 healthy. local bootRun 기동1.865초, health HTTP200/UP(연결3초/요청10초). 첫 sandbox curl은 접속 제한으로 종료7; 승인된 localhost 요청은 성공했다.
- redis stop 후 health HTTP503/DOWN 확인, redis up --wait 종료0으로 복구. 업무 API 장애 계약의 검증은 아니다.
- SIGINT로 이번 앱 PID 종료: Tomcat graceful shutdown complete/Hikari 종료 확인. Java130을 Gradle이 종료1로 전달한다. 이는 정상 인터럽트 종료 기록이며 테스트 성공으로 계산하지 않는다.
- TOOLCHAIN_SMOKE_PASSWORD 누락 local bootRun 종료1. 정상 비밀값 복구 후 UTC 설정 재검증 진행.
- 보존 표식 UUID `fca1c9f8-a068-49d7-b5fc-d433bbab3a0d`, 기대73. reservation_local 확인 후 명시 BEGIN/DDL/INSERT/COMMIT, 새 연결 UUID/73 조회. down(볼륨 유지)→up --wait120 각각 종료0, 새 컨테이너의 새 연결에서 동일 UUID/73 확인. 이번 표식만 성공 후 정리한다. 기존 개발 데이터·schema/volume 삭제 없음.
- 복구/정리 책임은 실행 담당자. 중단·commit 유실은 상태 조회 후 재개하도록 README에 안내했다. 실제 응답 유실 주입은 미실시이며 이를 실행 검증으로 주장하지 않는다.

- UTC 재기동1.535초 및 Z 로그 확인, 복구 health200/UP. 이번 표식만 DELETE1(종료0). T005~T012 실행 준비·US1 검증 완료, Unit/IT·심화는 후속이다.

### US2 순수 프로브 TDD

- 첫 파일 작성 명령의 작업 위치 오류로 파일 생성이 안 되어 NO-SOURCE 실행이 발생했다. 기능 실패 근거가 아니며 T020에서 0건 차단을 보완한다.
- 올바른 위치에 테스트를 작성한 뒤 최소 골격에서 `./gradlew test --tests '*ToolchainSmokeTest' --rerun-tasks`: 종료1, 실행3·실패2·skip0. 입력37/100의 실제 조회0으로 assertion 실패. 컴파일 정상이며 의도한 저장 동작 실패를 확인했다.
- 최소 저장 필드 구현 후 정상 실행 결과는 아래 후속 기록으로 연결한다.

- 정상 최소 구현 후 ./gradlew test --rerun-tasks 및 지정 ToolchainSmokeTest 실행: 각각 종료0·실행3·실패0·skip0. 첫 실행은 새 실행이며 두 번째 지정 실행도 test 태스크가 실제 실행됐다.

- 초기 IT 종료1: Testcontainers의 image compatibility 검사에서 tag+digest 문자열을 postgres 대체 이미지로 인식하지 못했다. DB 연결 전 준비 실패이며 기능 assertion 실패가 아니다. 이미 docker pull/inspect/컨테이너 내부 버전으로 공식 postgres digest를 확인했으므로 asCompatibleSubstituteFor("postgres")를 명시했다. 버전 하향·다른 DB·skip은 사용하지 않았다.

### US2 실제 연동·발견 게이트

- image 호환 선언 후 integrationTest --rerun-tasks 종료0: 일반 IT4건·실패0·skip0. 실제 PostgreSQL metadata/version1·insert/select73, Redis73, RANDOM_PORT health200/UP, 고의 migration 오류·metadata 확인·새 컨테이너/DB 재개를 확인했다.
- 앱 context보다 컨테이너를 먼저 정리하면 Redis 재연결 경고가 발생했다. 수동 AfterEach context.close는 Spring 후처리가 닫힌 context를 재시작하려 해 IT2실패를 발생시켰다(verify 종료1, 결과 게이트도 실패). Spring DirtiesContext AFTER_CLASS 종료 후 order2999 cleanup listener로 의존 컨테이너를 정리하도록 수정했다.
- `./gradlew verify --rerun-tasks` 수정 후 종료0: Unit3·일반IT4, 실패0·skip0, 현재 listener/XML 수 일치. 각 Test의 cache/up-to-date 재사용을 끄고 NO-SOURCE·현재 결과 없음·XML 없음/불일치를 게이트에서 거부한다. configuration cache 지원은 이번 범위 밖이며 Gradle10 API 호환성은 미검증이다.

- 고의 실패(각각 원복, 종료1): Unit 기대값+1(3실패), 지정 이름 없음(0건), 전체 태그 제외(0건), 클래스 Disabled(0건), Redis 기대74(1실패). 별도 Assumptions로 실제 Unit3 중 skip1을 주입해 게이트 비성공을 확인했다.
- 이전 성공 XML이 있는 상태에 Test 입력을 빈 경로로 바꾼 init-script 실행: 종료1, 현재 실행 결과 없음 게이트로 차단. init-script는 시연 명령에서만 사용하며 정상 설정에는 적용하지 않는다.
- 원복 verify 종료0: Unit3/IT4·실패skip0. 지정 `test integrationTest --tests '*ToolchainSmokeIT'` 종료0: Unit3/지정IT3. 마지막 skip/빈 입력 시연 원복 후 test 종료0: Unit3·실패skip0. 고의 변경 잔존 없음.
- T014~T021 완료. Spring 없는 프로브는 도구 확인용이며 실제 업무 불변식·SQL 경쟁 테스트는 아직 미구현이다.

### US3 속성·변이

- QuickTheories0.26/validator core2.46.1 고정 의존성과 PIT Gradle1.19.0/engine1.22.1/Jupiter plugin1.2.3을 취득했다. 심화 지원 클래스 포함 compileTestJava/processTestResources 종료0. test classpath에서 계약 원본을 직접 복사하고 수작업 사본을 만들지 않았다.
- 첫 Property 정상 실행 종료1: 중복 제거로 실제 assertion61회여서 최소100 조건이 실패했다. 도구 Core bytecode의 visitedValues/생성 루프 확인 후 생성 예산1000으로 조정했다. 최소100 기대값은 유지했다.
- 수정 propertyTest 종료0: JUnit3건(경계0/100 각1, 속성1), 속성 실제 assertion101회, seed20261013, 실패skip0.
- 프로브의 `value>50 ? 0 : value` 고의 결함: propertyTest 종료1, 경계100 및 속성 실패. 최초 반례100 → 중간59/58/57/56/54/53 → 최소51. QT_SEED=20261013을 Test JVM에 전달한 재현 실행도 종료1·동일51과 반례 경로. 원복 propertyTest 종료0·3건·실제101회.
- mutationSmoke 종료0: PIT generated1/killed1/survived0/NO_COVERAGE0/invalid0. XML/HTML 보고서 확인. PIT examined1은 도구 내부 수이며 JUnit Unit3건과 구분한다. pure 프로브에서만 확인했고 Spring·SQL·권한·업무 규칙 변이를 검증한 것이 아니다.

### US3 계약 검증

- contractTest --rerun-tasks 종료0: Contract3건·실패skip0. loopback 동적 HTTP 서버의 GET /toolchain/probe 실제 method/path/headers/status/body를 전달해 정상200/UUID 응답 허용, 같은200/{}의 필수id 누락은 validator가 ERROR로 거부했다. 음성 사례는 예상 거부 assertion의 성공이며 태스크 실패와 구분한다.
- 기존 docs/openapi.yaml 원본의 OpenAPI3.0.3 parse/resolve 성공. 업무 API를 기동·검증한 근거는 아니다.
- 정상 fixture의 UUID를 not-a-uuid로 고의 변경: 종료1·Contract3 중 정상 응답1실패. 원복 contractTest 종료0·3건 실패skip0. 계약 리소스의 수작업 중복 사본과 제품 API 음성 옵션 없음.
- T023~T032 완료. 각 후보의 최소 실행을 확인했으며 업무 동작 호환성은 미검증이다.

### US4 실행 조합 대조

최종 조합별 첫 증거 실행에 --rerun-tasks를 사용했다. Test listener 결과와 XML을 대조했으며 심화 미포함 그룹은 해당 명령에서 실행되지 않는다.

| 명령 | 종료 | 실제 그룹별 JUnit 실행 수 |
|---|---|---|
| `./gradlew test --rerun-tasks` | 0 | test=3 |
| `./gradlew verify --rerun-tasks` | 0 | integrationTest=4, test=3 |
| `./gradlew test --tests *ToolchainSmokeTest --rerun-tasks` | 0 | test=3 |
| `./gradlew test integrationTest --tests *ToolchainSmokeIT --rerun-tasks` | 0 | test=3, integrationTest=3 |
| `./gradlew verifyContract --rerun-tasks` | 0 | test=3, integrationTest=4, contractTest=3 |
| `./gradlew verifyProperty --rerun-tasks` | 0 | test=3, integrationTest=4, propertyTest=3 |
| `./gradlew deepTest --rerun-tasks` | 0 | test=3, integrationTest=4, contractTest=3, propertyTest=3 |
| `./gradlew mutationSmoke --rerun-tasks` | 0 | PIT 생성1·탐지1 |

- Contract/Property 각 --tests 불일치·전체 태그 제외는0건 종료1. 각 BeforeEach assumption 중단은3건 중 skip3으로 종료1. 정상 assertion 실패 탐지는 US3의 UUID 훼손/경계 결함 근거에 연결한다. 모든 임시 변경은 finally 원복했다.
- 원복 뒤 ./gradlew deepTest 종료0: Unit3/일반IT4/Contract3/Property3, 총13·실패0·skip0. Property101회, 필수 그룹0건0, 원복 누락0. PIT는 별도 표의 생성1·탐지1을 유지한다.

### 1단계 요구사항·성공 기준 인수

| 기준 | 실제 근거 | 판정 |
|---|---|---|
| FR-001 | 착수 root/HEAD 확인·chore 브랜치·기존 Gradle 보존 | 충족 |
| FR-002 | JDK/Wrapper/Docker/Compose/k6/CPU/메모리·versions | 충족 |
| FR-003 | local health200·종료·down/up 뒤 PostgreSQL 표식73 유지 | 충족 |
| FR-004 | .env 제외·변수명만 example·loopback·UTF8/UTC | 충족 |
| FR-005 | 순수 Unit3·지정3 실제 실행 | 충족 |
| FR-006 | PG/Redis 실제 동적 IT·실패 migration 정리·새 DB 재개 | 충족 |
| FR-007 | PIT 생성1·탐지1·NO_COVERAGE0·생존0 | 충족 |
| FR-008 | 속성 실제101회·100→51·seed20261013 재실패 | 충족 |
| FR-009 | 여덟 실행 조합·이름/태그·실제 수 표 대조 | 충족 |
| FR-010 | 값/0건/skip/빈 입력 비성공·원인 분류 | 충족 |
| FR-011 | Unit/IT/Contract/Property 고의 결함 원복 후 통과 | 충족 |
| FR-012 | 실제 HTTP 정상/id누락 및 업무 OpenAPI parse/ref | 충족 |
| FR-013 | versions·decisions·본 문서에 명령/종료/수/seed/축소/한계 기록 | 충족 |
| FR-014 | 검증 단위 로컬 커밋·프로브 후속 대체·전체 인수 대조 | 충족 |
| SC-001 | 환경 전체 목록 및 PostgreSQL commit/down/up/조회1회 | 충족 |
| SC-002 | Unit3·PG/Redis 각 독립 최소1·실패skip0 | 충족 |
| SC-003 | PIT1생성1탐지·Property탐지/축소/재현/원복 네 근거 | 충족 |
| SC-004 | 정상200 허용·id누락 거부·기존 문서 parse/ref | 충족 |
| SC-005 | 조합별 수/종료와 고의 실패·원복·필수0건0 | 충족 |
| SC-006 | 재현 안내/근거/미검증을 연결·근거 없는 호환 표시0 | 충족 |

**판정:** 1단계 필수 최소 검증 인수 충족. 업무 API·업무 schema·인증 UUID·SQL 경쟁·Redis 요청 제한·부하·외부 CI는 미구현/미검증이다. 다음 한 작업은 2단계 실제 schema 제약의 첫 통합 검증이다. cleanup10초 초과/앱준비60초 초과의 장애 주입은 미실시이며 정상 경로의 상한 설정과 정리 완료만 확인했다. 개발 commit 응답 유실은 복구 안내를 작성했지만 실제 유실 주입은 미실시다.

### 문서 동기화와 최종 범위

- 원계획3·6·7절과 README/knowledge/계획/실행 계약을 실제 Gradle·경로·수·조합으로 맞췄다. 업무2·4·5절은 HEAD 원본과 바이트 동일임을 확인했다. 원계획의 저장소 밖 참고 문서2건은 존재하지 않는 로컬 링크 대신 원래 파일명을 보존한 참고 표기로 바꿨다.
- 고의 결함·skip·임시 태그 설정은 모두 원복했고 .env는 ignored이다. toolchain 체크리스트를 implement에서 수정하지 않았다. 외부 hooks 설정은 없으므로 before/after hook은 해당 없음.
- 최종 코드 검증은 원복 deepTest13건·실패skip0 및 별도 PIT1생성1탐지다. 이후 변경은 문서 동기화·인수 기록이며 추가 업무 동작 변경은 없다. 최종 브랜치 리뷰를 별도 시행한다.

- 최종 환경 재조회: Docker Compose v5.6.0. 착수 CLI 출력v5.1.0과 구분해 versions에 기록했다. 최종 docker ps의 실행 컨테이너0, PostgreSQL named volume은 보존. 최종 XML은 Unit3/일반IT4/Contract3/Property3·실패skip0, PIT read 변이1개 KILLED.

### 2026-10-14 최종 독립 리뷰와 보완

- 읽기 전용 독립 리뷰: range71b677c..ab6a2c1 및 미커밋 버전 기록. 첫 시도는 사용량 제한으로 실패해 리뷰 결과가 없었고, 사용자 “이어서 해줘” 지시 후 같은 리뷰를 재개했다. Critical0, Important1(앱 IT HttpClient.close의 종료 상한 누락), Minor1(버전 문서 준비/완료 상태 혼재).
- Important는 종료10초 계약 누락으로 판단해 보완했다. 실제 동적 HTTP 서버가200 헤더/첫 바이트를 보내고 본문을 끝내지 않는 회귀 사례 HttpClientCleanupIT를 먼저 작성했다. 기존 close 동작의 지정 실행 종료1, 1건 assertion의 Future TimeoutException(12초 관찰 상한)을 확인했다. 컴파일 실패나 Docker 미준비가 아니다. 실패 후에도 본문/클라이언트/서버/실행기 정리를 완료했다.
- HttpClientTestSupport.close를 shutdownNow→awaitTermination(10초)로 바꾸고 앱 IT의 finally에 적용했다. 지정 회귀 IT 실행 종료0·1건 실패skip0. 이후 ./gradlew deepTest 종료0: Unit3·일반IT5·Contract3·Property3, 총14·실패skip0. 미종료 응답 cleanup 회귀도 포함한다. PIT 대상 main/Unit은 변경하지 않았으므로 기존 생성1·탐지1 근거를 유지하며 다시 실행하지 않았다.
- 버전 문서는 미완료 준비 표현을 현재 확인 표로 정리했다. 이 문서 수정은 리뷰 결과 도착 전 진행 중이던 최종 기록 정리이며 별도 prose 테스트 대신 내용·링크·diff 검사로 확인한다. 남은 Minor 없음.
- 제외 범위 판단: 업무 API/UUID 인증/수량·SQL 경쟁/Redis 제한/부하/외부 CI는1단계 범위 밖으로 그대로 미구현/미검증이다. 종료10초 초과 분기 자체와 개발 commit 응답 유실 주입은 여전히 미실시로 표시한다. 실제 미종료 HTTP 응답의 bounded cleanup은 이번 보완에서 실행 확인했다.
- 최초 여덟 조합 표의 일반IT4/총13은2026-10-13 당시 근거다. 현재 모든 일반IT 포함 명령은 새 회귀1건을 포함해 일반IT5, deepTest14다. 이름 지정 ToolchainSmokeIT3 및 Unit/Contract/Property 각3은 유지된다.
- 기존 작업 공간/chore 브랜치에서 순차 실행했고 Spec Kit T번호를 ledger에 직접 추적했다. 자동 brief 추출 스크립트는 Task N 제목만 지원해 사용하지 않았다. 병행 checkout 충돌/수동 추적 누락 가능성은 실제 git 상태·체크42개·실행 근거 대조로 관리했다.
- T042의 지시에 따라 작업 브랜치와 로컬 커밋을 유지한다. develop/main 병합·push·PR·태그는 하지 않는다. 후속 통합은 별도 작업이다.

- 최종 코드 대상 commit: `be96d7c` (앱 IT 종료 상한·회귀 보완 포함). 기본 범위 baseline71b677c에서6개 목적별 로컬 커밋으로 진행했다. 수정 뒤 재리뷰 대신 실제 회귀 RED→GREEN/전체14통과로 보완을 판정했다. 현재 작업 브랜치는 chore/setup-test-toolchain이며 기록 정리 커밋 후 git status를 재확인한다.

### 2026-10-14 GitHub PR 준비·적대적 코드리뷰

- 사용자 지시로 현재 chore/setup-test-toolchain을 origin에 push했다. 원격에는 main만 있었고 로컬 develop과 원격main의 기준71b677c가 같음을 확인해 PR 대상 origin/develop을 생성했다. 이전 “외부 작업 없음”은 그 이전 작업 범위의 이력이며 이번 push/PR는 별도 명시 지시에 따른다.
- PR 대상 diff의 적대적 독립 리뷰(71b677c..41c7847): Critical0·Important1·Minor0. Important는 헤더/첫 바이트 이후 본문 정체가 HttpRequest.timeout(10초)만으로 제한되지 않는 문제다. 리뷰어가 Corretto25에서12,009ms 후 Future pending을 실제 확인했다. 앞선 수렴 검토는 이 실패 경로를 놓쳤다.
- 수정 전 회귀: 실제 서버가200·첫 바이트를 flush한 뒤 본문을 끝내지 않는 HttpClientCleanupIT.responseBodyStallFailsWithinWholeRequestDeadline를 작성했다. 지정 실행 종료1·1건 assertion 실패:12초 caller Future TimeoutException을 받았고 기대한 요청 HttpTimeoutException이 발생하지 않았다. 실패 후 정리 완료. 컴파일/환경 오류가 아니다.
- 공통 HttpClientTestSupport.send를 sendAsync + Future.get(10초)로 변경했다. 전체 본문 완료를 제한하고 Timeout/Interrupted에서 pending.cancel(true), 실행 실패는 원인을 전달한다. BackendApplicationIT와 ToolchainHttpFixture 모두 사용한다. 클라이언트 종료10초 상한은 유지한다.
- 수정 후 지정 회귀 종료0·1건 실패skip0. ./gradlew deepTest 종료0: Unit3·일반IT6·Contract3·Property3, 총15·실패skip0. 본문 정체의 요청 timeout과 취소 후 정리를 실제 확인했다.
- PR 준비 시 ./gradlew deepTest mutationSmoke도 종료0(수정 전14건, PIT 생성1·탐지1·생존0·NO_COVERAGE0). 이후 변경은 테스트 HTTP 지원/IT만이며 main/PIT 대상 Unit·빌드 의존성은 변경하지 않아 PIT를 재실행하지 않았다. 수정 후15건 결과와 구분한다.
- 전체 업무 API·schema·UUID 인증·예약 경쟁·Redis 제한·부하·외부 CI는 후속 범위다. Docker startup/cleanup timeout의 모든 장애 분기와 개발 commit 응답 유실은 미검증이다. 임의 -x/init script로 검증을 고의 제거하는 사용자 우회는 정상 실행 계약 범위 밖이다.

- 같은 적대적 리뷰어의 수정 재평가 PASS: 본문 완료 Future의10초 제한·Timeout/Interrupted 취소·두 호출 경로 적용·실제 회귀와 전체15건 로그를 읽기 전용으로 확인했다. 미해결 Critical/Important0, Minor0. 이 판정 후 수정·기록을 커밋하고 원격 반영한 뒤 develop 대상 PR을 게시한다.

- GitHub 실제 게시 결과: [PR #1](https://github.com/joypeb/limited-booking-test/pull/1), base=develop, head=chore/setup-test-toolchain, OPEN·정식PR(draft 아님)·MERGEABLE. 리뷰/검증된 코드 commit6772606과 원격 head 일치 확인. GitHub statusCheckRollup은 빈 목록이므로 외부 CI 통과를 주장하지 않는다. PR 생성은 완료했고 병합은 수행하지 않았다.

### 2026-10-14 / 2단계 저장 제약·초기화 명세 작성

- 대상 커밋: 기준 HEAD `9eb50af`; 이번 문서 변경은 아직 커밋하지 않았다.
- 작업 브랜치: `develop`의 깨끗한 작업 트리를 확인하고 `docs/define-db-schema`를 실제 생성·전환했다. 병합·태그·외부 push는 수행하지 않았다.
- 환경·기준: 기존 저장소와 Spec Kit 1.1.3 설정을 확인했다. `knowledge/README.md`, 규칙 전체, resource·reservation 도메인, 계획서 2~5절·7절 2단계, 개발 헌법을 대조했다.
- 기대 결과: 수량 범위·사용자/키 유일성·참조·상태/취소 시각·조회·격리된 초기화·수량 보존 검사의 요구사항을 한 기능 명세로 작성한다. 업무 구현이나 테스트 실행을 통과로 표시하지 않는다.
- 변경: [명세](../specs/003-define-db-schema/spec.md), [품질 체크리스트](../specs/003-define-db-schema/checklists/requirements.md)를 작성하고 `.specify/feature.json`의 활성 경로와 knowledge 읽기 안내의 링크를 갱신했다.
- 템플릿 확인 명령: `bash .specify/scripts/bash/resolve-template.sh spec-template --json`, 종료 코드 0. 활성 해석 결과는 기본 spec-template이며 이를 복사한 뒤 같은 절 순서로 한국어 명세를 작성했다.
- 문서 검증 명령: `git diff --check` 및 `python3 - <<'PY'`로 실행한 문서 구조·링크 검사, 종료 코드 각각 0. Python 검사는 활성 경로 일치, 필수 절 존재, FR 11개·SC 5개, 체크리스트 완료 표시 16개, 미해결/템플릿 표시 없음, 명세·체크리스트·knowledge 안내의 상대 링크 27개 존재를 확인했다. 실행 테스트 수는 0건이며 문서 검사 결과만 기록한다.
- 품질 검토: 체크리스트 16개 항목 통과. 각 요구사항과 인수 사례·성공 기준의 대응은 체크리스트 검토 기록에 연결했다.
- 전후 훅: `.specify/extensions.yml`이 없어 before_specify·after_specify 훅 실행 대상 없음. 브랜치는 프로젝트 Git Flow 규칙에 따라 별도로 생성했다.
- 선택 이유: 저장 제약과 입력 규칙을 구분하고, 전체 수량 보존식은 저장 제약만으로 보장되지 않음을 명시했다. 정상 검사와 의도적 불일치 검사를 모두 요구해 검사 도구의 탐지력도 확인하도록 했다.
- 미완료·미검증: 업무 migration·Repository·TestData·DatabaseAssertions·SchemaIT 구현, 실패→통과 확인, 새/기존 DB 실행은 모두 미실시다. 2단계 계획 체크박스는 변경하지 않았다.
- 다음 한 작업: `$speckit-plan`으로 2단계 구현·검증 계획을 작성한다. 실제 조회 인터페이스·마이그레이션·단일 SELECT와 기존 Gradle 발견/실행 게이트를 구체화한다.
- 실제 시간: 미측정.

### 2026-10-14 / 2단계 명세 명확화 검토

- 대상: [003 명세](../specs/003-define-db-schema/spec.md), [품질 체크리스트](../specs/003-define-db-schema/checklists/requirements.md). 기존 문서 작업의 미커밋 변경은 보존했다.
- 기준: knowledge 읽기 안내·규칙 전체·resource/reservation 도메인, 개발 헌법 및 계획서 2·5.1절·7절 2단계.
- 경로 확인: `bash .specify/scripts/bash/check-prerequisites.sh --json --paths-only`를 한 번 실행, 종료 코드 0. FEATURE_DIR·FEATURE_SPEC이 사용자가 지정한 003 명세를 가리킨다. 출력의 BRANCH는 기능 경로에서 파생된 값이며 실제 Git 브랜치 생성·전환 기록으로 해석하지 않는다.
- 검토 결과: 추가 질문·답변 0개. FR-002의 기존 문장이 대상의 이름·수량·생성 시각도 필수 속성에서 제외하는 것으로 읽힐 수 있음을 발견했다. 계획서 5.1의 확정 규칙에 따라 대상의 모든 속성과 예약의 취소 시각 이외 속성이 필수임을 명시했다. 새 정책 결정이나 사용자 답변을 생성하지 않았다.
- 범주별 검토: 기능 범위·사용자 흐름·품질 속성·외부 의존성·실패/경계·제약/절충·용어·완료 신호·미해결 표시는 현재 단계 기준으로 명확하다. 데이터 모델은 위 문장 교정으로 해소했다. 상세 SQL·테스트 구성·실행 순서는 계획 단계 대상이다.
- 체크리스트 재검토: 16/16 → 16/16. 새 통과·퇴행·남은 미체크 항목 모두 0개. 체크리스트 표시와 그 외 내용은 변경하지 않았다.
- 실제 검증: `python3 - <<'PY'` 문서 검사와 `git diff --check`, 종료 코드 각각 0. 필수 속성 문장 교정, FR 11개·SC 5개 유지, 미해결/템플릿 표시 없음, 체크리스트 16개 표시, 명세·체크리스트 상대 링크 존재를 확인했다.
- 전후 훅: `.specify/extensions.yml` 없음. before_clarify·after_clarify 실행 대상 없음.
- 미검증: 업무 코드·SQL·DB·HTTP 테스트는 실행하지 않았다. 실행 테스트 수 0건이며 문서 검증을 기능 검증으로 취급하지 않는다. 커밋·병합·외부 push는 수행하지 않았다.
- 다음 한 작업: `$speckit-plan`으로 2단계 구현·검증 계획 작성. 실제 시간은 미측정.

### 2026-10-14 / 2단계 구현 계획·설계 작성

- 대상 커밋·브랜치: 기준 `9eb50af`, 실제 `docs/define-db-schema`; 이번 문서 변경은 미커밋이다. 이전 specify/clarify의 변경을 보존했다. 브랜치 전환·병합·태그·외부 push는 이번 작업에서 수행하지 않았다.
- 기준·환경: knowledge 읽기 안내와 목록·규칙 전체·resource/reservation, 개발 헌법·003 명세, 기존 build.gradle·ContainerTestSupport·ToolchainSmokeIT·BackendApplicationIT·버전 기록을 대조했다. 기술 버전은 기존 채택/검증 기록값이며 이번에 재실행 검증한 값은 아니다.
- 작성물: [plan](../specs/003-define-db-schema/plan.md), [research](../specs/003-define-db-schema/research.md), [data-model](../specs/003-define-db-schema/data-model.md), [저장 계층 계약](../specs/003-define-db-schema/contracts/persistence-contract.md), [quickstart](../specs/003-define-db-schema/quickstart.md). knowledge 읽기 안내에 계획 링크를 등록했다. tasks.md는 후속 작업으로 남겼다.
- setup 명령: `bash /Users/joypeb/Desktop/projects/limited-booking/.specify/scripts/bash/setup-plan.sh --json`, 종료 코드 0. 명세·계획·기능 경로를 확인했다. JSON BRANCH `003-define-db-schema`는 기능 경로 파생값이며 실제 Git 브랜치 기록으로 사용하지 않았다.
- 조사: speckit-plan Phase 0 지시에 따라 읽기 전용 조사 에이전트가 Gradle 선택 필터·게이트·컨테이너 호환·Flyway 프로브 위치를 검토했다. 공식 PostgreSQL18 제약, pgJDBC 시각 매핑, Flyway migrate, Gradle 명령행/필터 문서를 확인하고 research.md에 직접 링크를 기록했다. 코드 읽기·문서 확인과 실제 실행은 구분했다.
- 결정: 기존 도구 유지, 업무 V1과 프로브 경로 분리, PostgreSQL-only 선택 경로 추가 시 무인자 지원 계약 유지, 전체 저장 컬럼 조회, 한 SELECT 수량 검사, 동일 제약 사례의 fresh/reapplied 조건을 계획했다.
- 실패 확인 계획: 임시 테스트 전용 무제약 구조에서 같은 거부 assertion의 실패를 확인하고 이를 테스트 탐지력 증거로 기록한다. 실제 업무 V1의 RED라고 표현하지 않는다. 임시 구조를 제거한 뒤 새 전용 DB에서 실제 업무 V1을 최종 검증한다. 적용된 V1 수정·clean/repair 우회는 계획하지 않았다.
- 헌법 검토: 설계 전·후 게이트 모두 통과, 규칙 변경·정당화가 필요한 위반·미해결 설계 질문 없음. 과거 헌법의 구현 상태/CI/채택일 기록은 해당 저장 기능 설계의 새 정책으로 해석하지 않았다.
- 실제 문서 검증: `python3 - <<'PY'` 구조·링크 검사와 `git diff --check`, 종료 코드 각각 0. 산출물5개, 상대 링크44개, 필수 절·인터페이스·필수 SchemaIT 테스트명, 미결정/템플릿 표시 없음, Markdown fence·공백 정상, tasks 미생성을 확인했다.
- 전후 훅: `.specify/extensions.yml` 없음. before_plan·after_plan 실행 대상 없음.
- 미완료·미검증: 업무 코드·V1·IT 작성, Gradle 실행, fresh/reapplied 실제 DB·정리 결과, 테스트 실행 수·RED/GREEN 결과는 모두 미실시다. 실행 테스트 수0건이며 문서 검사 통과를 기능 통과로 보고하지 않는다. 계획서 단계 체크박스는 변경하지 않았다.
- 다음 한 작업: `$speckit-tasks`로 의존성 순서의 작업 목록 작성. 실제 시간은 미측정.

### 2026-10-14 / 2단계 맞춤 요구사항 체크리스트 생성

- 대상·범위: [database.md](../specs/003-define-db-schema/checklists/database.md)를 새로 생성했다. 저장 제약·조회와 테스트 격리·초기화·재적용·수량 관측의 요구사항 품질을 다룬다. 표준 깊이, 작업 목록 작성·구현 전 검토자용으로 현재 단계 맥락에서 도출했다. 별도 필수 강조 항목은 사용자 입력에 없었다.
- 기준: knowledge 읽기 안내·Markdown 목록·규칙 전체·resource/reservation, 개발 헌법, 003 명세·계획·데이터 모델·저장 계약 및 계획서 5.1절·7절 2단계를 대조했다. tasks.md는 아직 없다.
- setup 명령: `bash /Users/joypeb/Desktop/projects/limited-booking/.specify/scripts/bash/check-prerequisites.sh --json --template checklist-template`, 종료 코드0. FEATURE_DIR·AVAILABLE_DOCS·TEMPLATE_CONTENT를 확인하고 활성 체크리스트 템플릿 구조를 사용했다.
- 작성 결과: CHK001~CHK034, 34개 전부 미체크. 기존 맞춤 파일은 없었으므로 추가 생성이며, requirements.md와 기존 다른 문서는 대체하지 않았다. knowledge 읽기 안내에 목록 링크를 등록했다.
- 내용 범위: 완전성·명확성·일관성·인수 기준·주요/대체/예외/복구 시나리오·경계·비기능·의존성·단계 경계다. 길이 보장 범위, 새/재적용 검증 범위, 마이그레이션/초기화 실패 요구사항은 검토 질문으로 남겼으며 결함으로 확정하거나 정책을 바꾸지 않았다.
- 실제 검사: `python3 - <<'PY'`와 `git diff --check`, 종료 코드 각각0. 34개 미체크·연속 번호·질문 형식, 명세/누락/모호함 등의 추적 표기33/34(97%, 기준80% 이상), 상대 링크·공백, 기존 기본 체크리스트16개 표시 유지, tasks 미생성을 확인했다. 나머지1개도 계획·검증 안내 절을 참조한다.
- 전후 훅: `.specify/extensions.yml` 없음. before_checklist·after_checklist 실행 대상 없음.
- 미평가·미검증: 이 명령은 항목별 충족 판단을 수행하지 않는다. 체크리스트의 모든 표시는 검토자 소유로 남겼다. 업무 구현·DB/HTTP 테스트 실행·커밋·병합·외부 push는 수행하지 않았다. 실행 테스트 수0건이며 형식 검사를 요구사항 인수나 기능 검증으로 보고하지 않는다.
- 다음 한 작업: 검토자가 맞춤 목록의 각 요구사항을 평가하고 필요한 문서 보완을 반영한다. 이후 `$speckit-tasks`로 작업 목록을 작성한다. 실제 시간은 미측정.

### 2026-10-14 / 2단계 사용자 이야기별 작업 목록 생성

- 대상: [tasks.md](../specs/003-define-db-schema/tasks.md)를 새로 작성했다. plan.md의 tasks 존재/다음 단계 안내와 knowledge 읽기 안내 링크를 현재 상태에 맞췄다. 다른 기존 미커밋 변경은 보존했다.
- 기준·입력: 003 명세·계획·데이터 모델·저장 계약·조사·검증 안내, knowledge 읽기 안내·규칙 전체·resource/reservation과 개발 헌법을 대조했다. 업무 기준은 계획서 2~5절·7절 2단계다.
- setup 명령: `bash /Users/joypeb/Desktop/projects/limited-booking/.specify/scripts/bash/setup-tasks.sh --json`, 종료 코드0. FEATURE_DIR·TASKS_TEMPLATE 절대경로·TASKS_TEMPLATE_CONTENT·AVAILABLE_DOCS를 확인했다.
- 작성 결과: 총45개 미수행 작업. 준비3·공통기반2·US1(P1)10·US2(P1)6·US3(P2)9·US4(P2)9·정리6이며 공통 합계11개다. 네 이야기의 독립 판정, 전체 의존 그래프, 이야기별 병행/순차 예시, FR/SC 추적 및 US1 최초 MVP를 기록했다.
- 병행 후보: 8개 표시. US3의 서로 다른 테스트2·모델2·Repository2와 정리의 도메인 문서2다. 의존 검증 완료 후 후보일 뿐 자동 병행 실행 또는 JUnit 병렬 실행 지시가 아니다.
- 주요 배치: 하나의 공유 업무 V1은 최초 적용 전에 두 테이블·제약·인덱스를 완성한다. 예약 제약 테스트의 RED는 임시 테스트 전용 누락 구조에서의 탐지 증거로 구분하고 실제 업무 V1 최종 검증을 연결했다. 적용된 V1 수정·실패 은폐·0건 허용은 작업에 포함하지 않았다.
- 체크리스트 상태: 기본 requirements16개 표시와 맞춤 database34개 미체크를 유지했다. 작업 목록 생성은 맞춤 요구사항 검토자 평가를 대신하지 않는다. 구현 진입 상태 확인을 T001에 둔다.
- 실제 문서 검사: `python3 - <<'PY'` 형식·구조·상대링크 검사와 `git diff --check`, 종료 코드 각각0. 작업45개, T001~T045 연속, 단계별 US라벨, 전 작업 파일경로, 모델제약·제공인터페이스, placeholder 없음, Markdown fence/공백, 체크리스트 유지 상태를 확인했다.
- 전후 훅: `.specify/extensions.yml` 없음. before_tasks·after_tasks 실행 대상 없음.
- 미완료·미검증: 모든 작업은 미수행이며 코드·SQL·테스트·Gradle 검증을 실행하지 않았다. 실행 테스트 수0건이다. 커밋·브랜치 전환·병합·외부 push/CI도 이번 명령에서는 수행하지 않았다.
- 다음 한 작업: `$speckit-analyze`로 spec/plan/tasks의 일관성과 누락을 검토한다. 맞춤 목록의 검토자 평가 및 필요한 문서 보완도 구현 전에 처리한다. 실제 시간은 미측정.

### 2026-10-14 / 분석 C1·I1 수정 승인 반영

- 승인: 사용자가 제안한 수정안 적용 후 speckit-analyze 재실행을 지시했다. 수정 단계와 이후 읽기 전용 분석 단계를 분리한다.
- 대상: 003 spec/plan/tasks·data-model·research·저장 계약·quickstart, 계획서 5.1·2단계, 도메인/검증 규칙 및 resource/reservation 지식 문서를 동기화했다. 기존 타입·외부 API·OpenAPI·코드·SQL은 변경하지 않았다.
- C1 수정: 각 제약을 테스트→후보 구조 RED→현재 CREATE DDL 최소 추가→새 소유 DB의 같은 테스트 GREEN으로 순서화했다. resources 후보 인수는 T006~T015, reservations 후보 인수는 T016~T018이다. 전체 후보 내용을 변경 없이 V1로 옮기는 T019와 실제 Flyway 적용 T020을 그 뒤에 둔다. 후보는 테스트 전용 schema-candidate/schema.sql이며 이번에는 예정 경로만 문서화했다. 최종 복사 동일성·후보/분기 제거·적용 V1 불변을 명시했다.
- I1 수정: 문자 수 기준 저장 상한과 비공백 길이 초과 거부를 명시했다. 비공백 이름100자/키64자 뒤 일반공백1자의 절단 저장을 별도 정상 사례로 요구하고 입력 전처리/문자 규칙은 기존 후속 범위를 유지한다. 명시적 cast의 절단과 NBSP를 공백 예외로 확대하지 않도록 구분했다. SC-001의 판정 범위를 명시된 제약 위반 사례로 정리했다.
- 근거: PostgreSQL18 공식 문자열 타입 문서를 다시 확인했다. 직접 DB 재현은 미실시이며 출처는 003 research.md에 연결했다. 이름·키의 기존 API 입력 규칙을 완화하거나 새 DB 제약을 추가하는 수정이 아니다.
- 실제 문서 검사: python3 here-document 검사는 최종 종료코드0, 문서14개·상대링크65개·작업45개·연속ID/US라벨/파일경로·FR11/SC5 유지·Markdown/템플릿을 확인했다. 최초 검사에서는 기존 계획서의 Markdown 두 칸 hard break를 불필요한 공백으로 오탐했으므로 검사 기준만 바로잡고 기존 문서 줄바꿈은 보존했다. git diff --check는 종료코드0이다.
- 체크리스트: 수정 전 SHA-256과 비교해 requirements.md·database.md 두 파일의 내용·표시 전체가 동일함을 확인했다. 맞춤34개는 미평가 상태를 유지한다.
- 실제 실행 범위: 문서 수정·검사만 수행했다. 업무 구현·Gradle/DB/HTTP 테스트·커밋·브랜치 전환·외부 작업은 미실시이며 실행 테스트 수0건이다.
- 다음: 수정 완료 상태를 기준으로 speckit-analyze를 읽기 전용으로 실행하고 분석 결과는 대화에만 보고한다. 이 절은 재분석 통과 기록이 아니다. 실제 시간은 미측정.

### 2026-10-14 / 맞춤 요구사항 체크리스트 사용자 요청 평가

- 요청: 사용자가 speckit-implement의 미체크 우회 질문에 「아니 체크리스트 미 체크인 부분은 체크먼저 해」라고 답해, 구현 단계와 별도로 체크리스트 품질 판단 지원을 명시적으로 요청했다.
- 대상: [database.md](../specs/003-define-db-schema/checklists/database.md)의 CHK001~034를 현재 명세·계획·모델·계약·조사·검증 안내·작업 목록과 대조했다. 기본 requirements.md의 내용·표시는 유지했다.
- 판단: 33개는 문서 근거가 충족돼 [x], CHK022는 보완 필요로 [ ]를 유지했다. 항목별 근거34행과 남은 보완 범위를 목록에 기록하고 tasks.md T001의 과거 미평가 상태 안내만 현재33/1로 동기화했다. 구현 작업 체크박스45개는 모두 미수행으로 남겼다.
- CHK022 근거: 정상 최초/재적용·한 트랜잭션 초기화는 설계돼 있지만 업무 migration 실패의 부분 구조/적용이력/다음 준비와 초기화 중간 실패의 잔류/재시도 인수 기준 또는 구체적 제외 이유가 명시돼 있지 않다. 도구 프로브의 장애 검증은 업무 V1·TestData 복구의 실행 증거가 아니다. 기존 산출물 분석의 요구사항별 작업 연결 확인과 이번 상세 복구 범위 검토를 구분한다.
- 실제 문서 검사: python3 here-document 종료0, 항목34개·체크33개·미체크CHK022만1개·평가근거34행·ID/상대링크, 기본requirements SHA-256 불변, 작업45개 미체크를 확인했다. git diff --check 종료0.
- 미완료·미검증: CHK022 문서 보완 및 전체 체크리스트 통과는 미완료다. 요구사항 품질 평가만 수행했으며 업무 구현·DB/HTTP/Gradle 테스트·커밋·외부 작업은 미실시다. 실행 테스트 수0건.
- 다음 한 작업: CHK022의 migration/초기화 실패 기대값 또는 제외 이유를 문서화하고 재평가한다. 구현은 아직 시작하지 않았다. 실제 시간은 미측정.

### 2026-10-14 / CHK022 실패 처리 요구사항 보완·재평가

- 요청: 사용자가 CHK022 실패 처리 요구사항 보완을 지시했다. 기존 격리·한 트랜잭션·부분 반영 금지·실패 기록 규칙과 1단계 도구 프로브의 새 DB 재개 방식을 기준으로 업무 인수 계약을 명시했다.
- 변경: 003 spec FR-007~008·SC-003·이야기4.7~4.8, plan의 실패 처리 절, 계약·모델·조사·검증 안내7절, tasks T020/T031~034·추적표, 계획서2단계·검증 규칙·resource/reservation·knowledge 안내를 동기화했다. 외부 API/OpenAPI·의존성·업무 코드·SQL은 변경하지 않았다.
- 마이그레이션 기대: 원본 V1의 테스트 전용 사본 전체 DDL 뒤22012 오류를 붙여 Flyway 트랜잭션 롤백을 관측한다. 업무 테이블/인덱스 부재·V1 성공 이력0건을 요구하며 이력 테이블 자체는 존재할 수 있다. 실패 DB 증거/자원 종료 뒤 다른 실행소유 DB에 변경하지 않은 V1을1건 적용하고 제약을 검증한다. 원본 V1 변경·clean/repair·무한 재시도는 하지 않는다.
- 초기화 기대: 예약 삭제 뒤 대상 삭제의 테스트 전용 SQL 장애에서도 두 DELETE 전체 롤백·기존 양쪽 행/값·구조·이력 불변을 요구한다. 예외 후 별도 연결에서 스냅샷을 비교하고 장애 제거 뒤 같은 DB의 새 트랜잭션으로 명시적 초기화를 재요청해 두 테이블0건·구조/이력 유지·장애 객체 정리를 확인한다.
- 한계: 정상 연결의 고의 SQL 오류에 한정한다. 상태 조회가 불가능하면 불명/검증 실패로 기록하고 해당 환경 후속 사례를 중단한다. 연결 단절/프로세스 중단은11단계이며 롤백 성공을 추정하지 않는다. 생산 코드/마이그레이션에 고의 장애를 추가하지 않는다.
- 근거: Flyway 공식 migration transaction handling·executeInTransaction 문서를 확인하고 research.md에 링크했다. 기존 ToolchainSmokeIT의 고의 migration 실패/새 DB 재개 코드를 읽었지만 이를 업무 V1/TestData 검증 성공으로 간주하지 않았다.
- 체크리스트: CHK022를 보완된 문서·예정 시험과 대조해 요구사항 품질 충족으로 재평가했다. 맞춤33/34→34/34, 새 체크1개·퇴행0개·남은 미체크0개. 기본requirements.md는 SHA-256 불변이며 작업45개는 모두 미수행이다.
- 실제 문서 검사: python3 here-document 종료0, 문서14개·상대링크95개·맞춤34개체크/근거34행·FR11/SC5·작업45개ID·실패시험2개 계약/작업/안내 연결을 확인했다. git diff --check 종료0.
- 미완료·미검증: 업무 구현·고의 실패 주입·실제 롤백/복구·Gradle/DB/HTTP 테스트·커밋·외부 작업은 미실시다. 실행 테스트 수0건이며 체크는 요구사항 품질 인수만 뜻한다.
- 다음 한 작업: speckit-implement에서 T001의 문서/체크리스트 상태를 재확인한 뒤 단계별 구현을 진행한다. 실제 시간은 미측정.

## 2단계 구현 진입 (2026-10-14)

requirements16/16·database34/34 검토 완료로 진입 가능. 맞춤 표시는 구현 완료가 아니다. Gradle --version/Docker info exit0: Gradle9.7.1, Corretto25.0.4+7-LTS, MacOS27.0.1 aarch64, Docker29.8.2. `./gradlew test` exit0, Unit3·실패0·skip0. 최초 sandbox 캐시/socket 접근 거부는 환경 권한 문제이며 기능 RED가 아니다.

Git root limited-booking, HEAD9eb50af, develop과 docs/define-db-schema의 시작점 동일. 기존 문서 변경을 보존해 로컬 커밋 후 기능 브랜치에서 이어간다. 별도 worktree를 생성하지 않는다. 외부 push/병합은 수행하지 않는다.

T003: b3f78eb에 이전 문서 변경 보존 후 feature/db-schema 생성. 시작 develop9eb50af의 직계 문서 커밋이며 develop 병합은 수행하지 않았다.
T004~005: PostgreSQL-only 접속/current_database·동적 포트·미기동 Redis 제외·종료 검증 및 기존 ToolchainSmokeIT/BackendApplicationIT, `./gradlew test integrationTest --tests '*DatabaseSupportIT' --tests '*ToolchainSmokeIT' --tests '*BackendApplicationIT'` exit0. Unit3/IT5·실패0·skip0. 환경 추가는 최소 실행으로 확인.

### 대상 후보 DDL 인수

업무 V1 적용 전 JDBC 후보 CREATE를 새 실행 소유 DB마다 준비했다. 각 RED의 실제 INSERT가 허용돼 SQLException 기대 assertion이 실패했다(exit1, IT1/실패1/skip0); 최소 제약 추가 후 같은 필터 GREEN(exit0, IT1/실패0/skip0), 매번 Unit3 통과.

| 대상 | 필터 (`*SchemaIT.` 뒤) | RED → GREEN |
|---|---|---|
| quantity | *SchemaIT.rejectsInvalidAvailability | exit1 → exit0; GREEN IT1 |
| resource-null-id | *SchemaIT.rejectsResourceNull_id | exit1 → exit0; GREEN IT1 |
| resource-null-name | *SchemaIT.rejectsResourceNull_name | exit1 → exit0; GREEN IT1 |
| resource-null-capacity | *SchemaIT.rejectsResourceNull_capacity | exit1 → exit0; GREEN IT1 |
| resource-null-available | *SchemaIT.rejectsResourceNull_available | exit1 → exit0; GREEN IT1 |
| resource-null-created_at | *SchemaIT.rejectsResourceNull_created_at | exit1 → exit0; GREEN IT1 |
| resource-pk | *SchemaIT.enforcesResourcePrimaryKey | exit1 → exit0; GREEN IT1 |
| resource-length | *SchemaIT.enforcesResourceNameLength | exit1 → exit0; GREEN IT1 |
| us1 | *SchemaIT.rejectsResourceNull_*, *SchemaIT.enforcesResourcePrimaryKey, *SchemaIT.enforcesResourceNameLength, *SchemaIT.rejectsInvalidAvailability, *SchemaIT.acceptsAvailabilityBoundaries | exit1 → exit0; GREEN IT9 |

NOT NULL은 id/name/capacity/available/created_at 각각 검증, PK23505/resources_pkey·이름 ASCII/😀100허용101거부22001·100자+일반공백 절단 저장을 확인했다. 수량 비정상4종/경계4종을 확인했다. 전체 대상 선택은 Unit3/IT9·실패0·skip0. 초기 `*Resource*` 필터가 아직 구현 전인 예약 FK/인덱스까지 포함해 IT11중2실패한 것은 선택 범위 오류이며 명시된 대상 필터로 수정했다. 드라이버 컴파일 오류도 제약 RED로 계산하지 않았다. 후보는 Flyway가 아니고 업무 마이그레이션 완료 증거가 아니다.

### 예약 후보 DDL 및 V1 복사

- user-key: `*SchemaIT.enforcesUserKeyUniqueness`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- foreign-key: `*SchemaIT.rejectsUnknownResource`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- status-combination: `*SchemaIT.enforcesStatusAndCancellationTime`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-null-id: `*SchemaIT.rejectsReservationNull_id`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-null-resource_id: `*SchemaIT.rejectsReservationNull_resource_id`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-null-user_id: `*SchemaIT.rejectsReservationNull_user_id`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-null-idempotency_key: `*SchemaIT.rejectsReservationNull_idempotency_key`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-null-status: `*SchemaIT.rejectsReservationNull_status`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-null-created_at: `*SchemaIT.rejectsReservationNull_created_at`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- reservation-pk: `*SchemaIT.enforcesReservationPrimaryKey`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- key-length: `*SchemaIT.enforcesKeyLength`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- status-type: `*SchemaIT.exposesStatusStorageType`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- resource-index: `*SchemaIT.exposesResourceLookupIndex`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT1·실패0·skip0.
- candidate-all: `*SchemaIT`; 제약 부재 RED exit1/IT1실패1 이후 GREEN exit0/IT22·실패0·skip0.

모든 예약 제약·상태타입·인덱스 제약별 RED/GREEN 후 최종 후보 CREATE를 V1로 byte-identical 복사. SHA256 `d4fff8c4a6cc64c618c07f538af8f9dd4ef1ffd66c4754074eb0d02a7b270844`. 후보·선택 분기를 제거했다. 고의 실패는 원본 사본 마지막에 SELECT1/0을 추가한 JUnit @TempDir의 V1 파일이며 원본은 변경하지 않는다. SQLSTATE22012/DDL롤백/새DB복구 검증은 아래 실행 결과로 연결한다.

실제 V1 `./gradlew test integrationTest --tests '*SchemaIT'` exit0: Unit3/IT23·실패0·skip0. SchemaIT23은 기존 제약22+실패복구1이다. Flyway 최초 적용1건·실패사본22012 뒤 업무 테이블/인덱스0·성공V1이력0·실패컨테이너 종료·다른DB 원본1건 적용 및 비정상 수량 거부를 확인했다. 생산 V1을 적용한 후 편집하지 않았다.

### 읽기 Repository 인수

`./gradlew test integrationTest --tests '*ResourceRepositoryIT' --tests '*ReservationRepositoryIT'`: 최소 empty 구현에서 실제 존재 행/SQL 오류 전달 assertion RED exit1, Unit3통과·IT4실패4/skip0. 구현 후 GREEN exit0, Unit3/IT4·실패0·skip0. 대상4필드, 예약7필드, 사용자·원문키 정확분리/대소문자/공백·현재 CANCELLED·UTC마이크로초·null 취소시각·없는행empty·SQL오류전달 및 조회 전후 전체 데이터 불변을 확인했다.

### TestData 인수

`./gradlew test integrationTest --tests '*DatabaseSupportIT'`: no-op stub에서 준비 행 수·초기화 후0행·고의 오류전달 assertion RED exit1/IT4실패3·skip0. 트랜잭션 구현 후 GREEN exit0/Unit3·IT4/실패0·skip0. 별도 연결 준비 데이터/시각 가시성, 초기화2회0행·스키마/이력 보존, 예약 DELETE 뒤 대상 DELETE22012 트리거로 양쪽 전체 행/값 롤백, 장애 제거 뒤 같은DB 새트랜잭션 명시 재요청·장애 객체0을 확인했다. 최초 시각 조회 assertion의 타입추론 컴파일 오류는 명시적 Instant 타입으로 교정했으며 RED에 포함하지 않았다.

### 수량 검사 인수

`./gradlew test integrationTest --tests '*DatabaseSupportIT'`: no-op 검사에서 불일치/대상부재 assertion RED exit1·IT9실패2/skip0. 단일SELECT 구현 후 GREEN exit0·Unit3/IT9·실패0·skip0. 10/8/확정2/취소1·타대상제외·0/0·전부취소 정상 통과, 10/9/확정2와 없는대상 AssertionError 탐지. 코드에서 SELECT1회·LEFT JOIN ON상태조건·COUNT(id)·두 불변식 확인.

### fresh/reapplied 전체 인수 (2026-10-14~15)

네 클래스마다 두 독립 실행 소유 컨테이너를 순차 준비했다. FRESH 원본V1 1건, REAPPLIED 원본1건→양쪽표식/이력/구조 스냅샷→validate→migrate0건→동일 스냅샷을 확인했다. 이름은 실행마다 toolchain_랜덤UUID이며 각 클래스 로그에 모드를 남긴다. 준비 접속 current_database는 컨테이너 소유 이름과 같다. 개발 DB URL/fallback·local 프로필·후보 SQL/분기는0이다.

명령: `./gradlew test integrationTest --tests '*SchemaIT' --tests '*ResourceRepositoryIT' --tests '*ReservationRepositoryIT' --tests '*DatabaseSupportIT'` exit0. Unit3, 업무IT72·실패0·skip0. SchemaIT45(모드별22+실패복구1), ResourceRepositoryIT4(모드별2), ReservationRepositoryIT4(모드별2), DatabaseSupportIT19(모드별9+환경1). 합계 모드별35씩+독립실패복구1+환경1. 소유 자원 종료까지 테스트 실행 범위다.

추가 커밋 경계 검증: 준비가 호출자 트랜잭션에 합류하면 별도 연결에서0행인 RED를 두 모드에서 확인했다(`*DatabaseSupportIT.commitsPreparationEvenWhenCallerTransactionRollsBack`, exit1/IT2실패2). REQUIRES_NEW로 독립 커밋을 보장한 뒤 위 전체72건이 통과했다. 호출자 롤백 뒤에도 준비 행을 읽는다.

### 기존 일반 IT 영향 검증

`./gradlew verify` exit0, Unit3/일반IT78(업무72+기존6)·실패0·skip0. 현재 listener/XML 게이트 일치, 기존 도구 V1 위치 및 HTTP 클라이언트 종료 회귀 유지. Property/PIT/부하·외부 CI는 이번 단계 재실행하지 않았다. XML 사례 표시를 메서드+모드로 명확히 하고 DB 식별을 로그에 추가한 뒤 최종 verify를 다시 실행한다.

### 요구사항·인수 기준 실행 연결

| 요구사항/기준 | 실제 검증 | 결과 |
|---|---|---|
| FR-001·SC-001 | SchemaIT.rejectsInvalidAvailability / acceptsAvailabilityBoundaries | 비정상4종23514/잔류0·경계4종저장값일치 |
| FR-002·SC-001 | rejectsResourceNull_* / rejectsReservationNull_*·두 enforces*PrimaryKey·enforcesResourceNameLength/enforcesKeyLength·exposesStatusStorageType | 필수값23502·PK23505·ASCII/😀상한·22001·일반공백절단·status폭16 |
| FR-003~004·SC-001 | enforcesUserKeyUniqueness / rejectsUnknownResource / enforcesStatusAndCancellationTime / exposesResourceLookupIndex | uq이름/23505·FK23503·상태23514·정상허용·인덱스유효비부분 |
| FR-005~006·SC-002 | ResourceRepositoryIT / ReservationRepositoryIT | 4/7컬럼·정확사용자키·현재취소·UTC마이크로초·empty·SQL오류전달·전체행불변 |
| FR-007·SC-003 | SchemaDatabase(REAPPLIED)·네클래스Mode·SchemaIT.rollsBackFailedMigrationAndRecoversInNewDatabase | 최초1/재적용0·validate·표식/구조/이력보존·22012잔류0·종료후다른DB복구 |
| FR-008~009·SC-003 | DatabaseSupportIT.commitsPreparedDataVisibleToAnotherConnection / commitsPreparationEvenWhenCallerTransactionRollsBack / resetsBothTablesRepeatedlyPreservingHistoryAndStructure / rollsBackFailedResetAndRetriesAfterFaultRemoval | 명시소유DS·독립커밋·초기화2회0·실패전체스냅샷불변·장애제거같은DB재요청 |
| FR-010·SC-004 | DatabaseSupportIT.acceptsMixedStatesAndIgnoresOtherResources / acceptsZeroCapacityWithoutReservations / acceptsAllCancelledReservations / rejectsInventoryMismatch / rejectsMissingResourceInInventoryAssertion | 단일SELECT·정상통과·2종AssertionError탐지 |
| FR-011·SC-005 | 단계별 위 명령·RED/GREEN 수·환경·최종verify 및 listener/XML 게이트 | 0건/실패/skip 성공처리없음·미실행 구분 |

이번 인수 범위에 미실행 요구사항은 없다. API·입력규칙·생성취소 Writer·권한·경쟁·멱등 응답·요청 제한·연결 단절/프로세스 중단·local DB V1 전환·부하·외부 CI는 범위 밖/미검증이다. 테스트 외 DB에 연결하는 코드 경로를 사용하지 않았으며 개발·부하 DB의 상태를 확인하려고 접속하지도 않았다. 핵심 읽기 파일은 업무 V1/두 Repository, SchemaIT/DatabaseSupportIT, TestData/DatabaseAssertions다. 다음 작업은 3단계 순수 입력 규칙이다.

### 최종 verify와 실제 환경 식별

메서드+모드 사례명과 실행 소유 DB 식별 로그를 반영한 뒤 `./gradlew verify` exit0, Unit3/일반IT78·실패0·skip0. listener와 XML의 수가 같다. 아래는 최종 XML system-out의 실행 소유 식별이며 비밀번호/URL 자격증명은 기록하지 않는다.

- com.limitedbooking.backend.reservation.ReservationRepositoryIT: 업무 DB 준비 database=toolchain_73eb427eea414002b47ab643c14fa48b mode=FRESH 최초 migration=1
- com.limitedbooking.backend.reservation.ReservationRepositoryIT: 업무 DB 준비 database=toolchain_453e0a10db714ab68694c056718d9b1a mode=REAPPLIED 최초 migration=1 validate=성공 재적용=0 표식/이력/구조=보존
- com.limitedbooking.backend.common.SchemaIT: 업무 DB 준비 database=toolchain_747c014f30014c2bbbc6588e59aace8f mode=FRESH 최초 migration=1
- com.limitedbooking.backend.common.SchemaIT: 업무 DB 준비 database=toolchain_bc402f0e6e36432faaab6d434e71f074 mode=REAPPLIED 최초 migration=1 validate=성공 재적용=0 표식/이력/구조=보존
- com.limitedbooking.backend.common.SchemaIT: 마이그레이션 실패사본 DB=toolchain_579803d37e40405aabef528bb2851496 SQLSTATE 기대=22012
- com.limitedbooking.backend.common.SchemaIT: 마이그레이션 복구 원본 DB=toolchain_408abb7f1f614d96a6ab339aec8e3da3 실패DB종료=true
- com.limitedbooking.backend.resource.ResourceRepositoryIT: 업무 DB 준비 database=toolchain_d001610ebb1a473582c2672f3ba42b58 mode=FRESH 최초 migration=1
- com.limitedbooking.backend.resource.ResourceRepositoryIT: 업무 DB 준비 database=toolchain_c28e0efe60cc4774a3abc2377047473c mode=REAPPLIED 최초 migration=1 validate=성공 재적용=0 표식/이력/구조=보존
- com.limitedbooking.backend.support.DatabaseSupportIT: 업무 DB 준비 database=toolchain_41dbeaaa429148a2b66059e6be2f8ec3 mode=FRESH 최초 migration=1
- com.limitedbooking.backend.support.DatabaseSupportIT: 업무 DB 준비 database=toolchain_5d288ef7700144f29d11e3744a429d03 mode=REAPPLIED 최초 migration=1 validate=성공 재적용=0 표식/이력/구조=보존

문서의 실제 파일 링크 검사와 git diff --check 통과. docs/openapi.yaml 변경0, 체크리스트 표시는 변경0, 임시 후보 경로/분기0. 적용 V1 SHA256은 d4fff8c4a6cc64c618c07f538af8f9dd4ef1ffd66c4754074eb0d02a7b270844로 복사 시 값과 동일하다.

### 최종 독립 리뷰와 완료

executing-plans의 독립 리뷰어가 9eb50af..924e0ba 생산 DDL·Repository·지원 코드·4개IT를 읽고 XML Unit3/일반IT78·실패/오류/skip0을 확인했다. 생산 코드 Critical/Important0. 문서의 구현 전 표현 1건(명세·개발검증규칙·원계획의 요약)이 발견됐다. 다음 작업자의 완료 범위 판단과 T043 동기화 기준에 영향을 주므로 상태 정합성 수정 대상으로 재평가하고, 기존 실행 증거와 충돌하는 문구 검증 실패 후 저장 기반 완료/Writer·HTTP 후속으로 교정했다. 내용·링크·공백 검사로 확인했으며 코드 변경은 없다.

리뷰에서 제외한 Writer·HTTP/권한/입력·멱등응답·경쟁·Redis제한·연결단절/프로세스중단·개발DB전환·부하는 기존 계획의 명시 범위 밖이므로 후속으로 유지한다. DB CHECK는 전이 전체나 테이블 간 보존식을 강제하지 않으며 이 경계도 유지한다. 미수정 리뷰 항목은 없다.

T001~T045 실제 수행·검증 완료. 맞춤 체크리스트는 읽기 전용으로 유지했다. after_implement 확장 파일/후크는 없어 건너뛰었다. feature/db-schema와 로컬 커밋을 보존하고 외부 push·PR·병합·태그는 수행하지 않았다. 핵심 파일/다음 한 작업은 위 추적 표와 3단계 입력 규칙을 따른다.

## develop 대상 GitHub PR과 적대적 리뷰 (2026-10-15)

사용자 요청으로 feature/db-schema를 origin에 게시하고 [PR #2](https://github.com/joypeb/limited-booking-test/pull/2)를 생성했다. 대상 develop9eb50af, 리뷰 HEAD25b8541618d0c9d4349289f6d875617dc35d456a이며 GitHub와 로컬의 35개 변경 파일·HEAD가 일치한다. 원격 develop 추가 변경0·로컬8커밋 앞섬. GitHub OPEN/MERGEABLE, statusCheckRollup은 빈 목록으로 외부 CI 통과 기록은 없다. merge·태그는 수행하지 않았다.

첫 push는 자동 승인 검토에서 목적지·페이로드 승인 근거 부족으로 거부됐다. 동일 origin의 기존 develop PR#1·소유 저장소 joypeb/limited-booking-test·ADMIN 권한과 35개 코드/테스트/문서 범위를 확인하고 비밀 파일/키 패턴 검사0을 제시한 뒤 같은 push가 승인됐다. 우회 실행은 하지 않았다.

Codex CLI0.145.0 challenge는 기본 모델의 ChatGPT 계정 미지원으로 실패했다. 사용자 설정을 제외한 기본 설정에서는 읽기 전용 검토를 시작했으나 계정 사용량 제한으로 최종 응답 없이 중단됐다. 두 실패를 리뷰 통과로 취급하지 않았다. requesting-code-review 절차의 새 독립 컨텍스트 에이전트로 적대적 리뷰를 완료했다. 원격 PR diff와 로컬 diff 공백 검사는 exit0이며 기존 XML 수를 다시 집계했다. 코드 변경이 없어 테스트를 추가 반복하지 않았다.

아래는 독립 리뷰 최종 판정 전문이다.

develop 병합 게이트: **PASS**. `9eb50af...25b8541`의 저장 기반 변경에서 확정할 P1/P2/P3 결함을 찾지 못했습니다.

- V1의 수량·필수값·PK·FK·사용자/키 유일성·상태/취소 시각 제약을 계약과 대조했습니다.
- 두 Repository의 바인딩, 사용자·원문 키 분리, 현재 취소 상태, UTC 시각 매핑, SQL 오류 전파 및 복수 행 거부를 확인했습니다.
- 테스트 지원의 독립 커밋, 초기화 전체 롤백, 재적용 표식/구조/이력 보존, 단일 SELECT 수량 검사와 테스트 발견 게이트를 검사했습니다.
- 기존 XML은 Unit 3건·일반 IT 78건, 실패·오류·skip 각각 0건입니다. 이번 리뷰에서 테스트를 재실행하거나 파일·Git 상태를 변경하지 않았습니다.

남은 실험 한계는 컨테이너 시작/종료 장애, 실제 연결 단절, 비UTC 세션·나노초 입력의 추가 재현입니다. 코드에서 확정 결함을 찾은 항목은 아닙니다. HTTP·권한·Writer 경쟁·Redis·개발 DB 전환은 합의된 후속 범위로 평가에서 제외했습니다.

PR 본문에 이 판정·검사 범위·실험 한계를 연결한다. 이 기록 커밋은 문서만 변경하며 검토한 코드/SQL/테스트와 기존 실행 증거는 같다.

## 3단계 순수 규칙 명세 작성·품질 검토 (2026-10-15)

사용자가 선택한 계획서 7절 3단계를 `speckit-specify`로 명세화했다. 저장소 최상위와 HEAD 존재, 작업 트리 변경0을 확인한 뒤 로컬 develop90a4085에서 `docs/validate-domain-rules`를 생성했다. 최초 생성은 샌드박스의 Git 참조 쓰기 제한으로 실패했고, 같은 `git switch -c docs/validate-domain-rules`를 승인된 권한으로 실행해 exit0을 확인했다. 커밋·push·PR·병합·태그는 수행하지 않았다.

`bash .specify/scripts/bash/resolve-template.sh spec-template --json` exit0으로 활성 템플릿을 해석하고 순차 번호004에 해당하는 [명세](../specs/004-validate-domain-rules/spec.md)를 작성했다. `.specify/feature.json`의 활성 경로는 `specs/004-validate-domain-rules`다. 사전·사후 모두 `.specify/extensions.yml`이 없어 실행할 확장 훅이 없었다.

계획서 2~5절·3단계와 knowledge 규칙 전체·resource/reservation 지식을 기존 V1·읽기 Repository·ReservationStatus·도구 Unit과 대조했다. 이름의 공백·코드 포인트, 수량0 포함 경계, 키 원문 보존·끝 줄바꿈 거부, 두 상태의 취소 판단을 정의했다. 실제 요청 경로 사용은 4~6단계 연동 조건으로 남겼으며 순수 규칙 검증만으로 실제 취소·수량 복구·경쟁 보장을 주장하지 않는다.

내용 검토는 1회, [명세 품질 항목](../specs/004-validate-domain-rules/checklists/requirements.md)16개 충족·미해결 질문0개다. `python3` 문서 검사 exit0: 사용자 이야기3·FR8·SC5·완료 체크16, 명세/검토목록/knowledge 안내의 로컬 링크32개 실재, 활성 경로 일치, 미치환 자리표시자·줄 끝 공백0을 확인했다. `git diff --check` exit0이다. 문서 검사 결과이며 기능 테스트는 실행하지 않았다. 개발자 설명 인수도 미실시다. 다음 작업은 `$speckit-plan`으로 제공 인터페이스·경계 테스트·실패 확인 절차와 후속 연동 추적을 구체화하는 것이다.

## 3단계 명세 모호성 검토 (2026-10-15)

`speckit-clarify`로 활성 [004 명세](../specs/004-validate-domain-rules/spec.md)를 검토했다. `.specify/scripts/bash/check-prerequisites.sh --json --paths-only`를1회 실행해 exit0·FEATURE_DIR/FEATURE_SPEC를 확인했다. 출력의 BRANCH는 활성 기능 식별값004-validate-domain-rules이며 실제 Git 브랜치는 docs/validate-domain-rules다. 사전·사후 `.specify/extensions.yml` 부재로 실행할 확장 훅은 없다.

계획서 2~5절·7절 3단계, 지식 규칙 전체·resource/reservation 문서와 기존 상태 enum·V1·OpenAPI를 대조했다. 기능 범위·데이터·사용 흐름·비기능 품질·외부 의존성·경계/실패·제약/절충·용어·완료 신호·자리표시자의10개 범주에서 이번 순수 규칙 범위의 중대한 모호성은 발견하지 못했다. 외부 운영·인증·경쟁은 후속 범위로 명시돼 있으며 실제 요청 경로 사용 조건도 유지된다. 예외 표현·프로브 정리·실행 절차는 기존 명세대로 구현 계획에서 정할 사항이므로 사용자 정책 질문으로 올리지 않았다.

질문0·답변0, 명세와 검토목록은 변경하지 않았다. 요구사항·인수 사례·성공 기준을 다시 대조한 품질 재검토는16/16→16/16, 신규 충족0·회귀0·미충족0이다. Python 문서 검사 exit0으로 두 문서의 로컬 링크4개·활성 경로 일치·미치환 표시 없음·사후 훅 부재를 확인했다. 기능 테스트·개발자 설명 인수는 미실시다. 다음 작업은 `$speckit-plan`이다.

## 3단계 구현 계획·설계 문서 작성 (2026-10-15)

사용자 `$speckit-plan` 지시로 `docs/validate-domain-rules`에서 [004 계획](../specs/004-validate-domain-rules/plan.md)·[조사](../specs/004-validate-domain-rules/research.md)·[값 모델](../specs/004-validate-domain-rules/data-model.md)·[호출 계약](../specs/004-validate-domain-rules/contracts/rules-contract.md)·[검증 안내](../specs/004-validate-domain-rules/quickstart.md)를 작성했다. 기존 명세/clarify 변경을 보존했으며 tasks.md·생산 Java·테스트·빌드·SQL·OpenAPI는 변경하지 않았다. 브랜치 생성·커밋·병합·외부 게시를 이번에 수행하지 않았다.

`bash .specify/scripts/bash/setup-plan.sh --json` exit0으로 활성 경로와 템플릿 복사를 확인했다. BRANCH 출력004-validate-domain-rules는 기능 식별값이고 실제 Git 브랜치는 docs/validate-domain-rules다. 사전·사후 `.specify/extensions.yml` 부재로 실행할 훅은 없다. `java --version` exit0: Corretto25.0.4+7-LTS다. Gradle9.7.1·Boot4.1.1은 기존 파일/기록 대조이며 이번에 Gradle 실행·의존성 해석을 다시 하지 않았다.

읽기 전용 조사 에이전트2개가 Java25 공식 문서와 Gradle/프로브 참조를 조사했다. strip·코드 포인트·키 전체 일치·record 검증 생성자·기존 상태 재사용을 선택하고 내부 예외 계약·후속 HTTP 변환 경계를 문서화했다. Unit 프로브는 PIT/Property 의존으로 보존하며 업무 심화 검증의 완료로 보지 않는다. 컴파일 가능한 골격에서 실제 assertion RED→GREEN·최종 전체Unit·별도 설명 확인을 계획했다. 실제 요청 경로 사용은4~6단계 미완료 조건으로 유지한다.

Python 문서 검사 exit0: 설계문서5개(계획149행·조사45행·모델33행·안내57행·계약82행), 로컬 링크25개 존재·미치환 자리표시자0·줄끝공백0·활성경로 일치·tasks 미생성·사후훅 없음·기존 품질 체크16/16 유지를 확인했다. `git diff --check` exit0이며 헌법의 설계 전/후 게이트 위반0·미해결 설계 질문0이다. FR-001~008·SC-001~005 담당 검증과 인터페이스·행렬을 자체 대조했다. 이번 결과는 문서 설계 검토이며 신규 규칙 테스트 수0(미구현·미실행), 개발자 설명/HTTP/DB/Redis/경쟁/심화 검증은 미실시다. 다음 작업은 `$speckit-tasks`로 상세 작업 목록을 생성하는 것이다.

## 3단계 맞춤 체크리스트·작업 목록 생성 (2026-10-15)

사용자가 이어서 요청한 `$speckit-checklist`와 `$speckit-tasks`를 모두 적용했다. 실제 Git 브랜치는 docs/validate-domain-rules이며 기존 명세/계획 변경을 보존했다. `check-prerequisites.sh --json --template checklist-template`와 `setup-tasks.sh --json`은 각각 exit0, FEATURE_DIR는 specs/004-validate-domain-rules이고 활성 템플릿과 research/data-model/contracts/quickstart 목록을 확인했다. 사전·사후 `.specify/extensions.yml`이 없어 두 명령 모두 실행할 훅이 없었다.

[맞춤 목록](../specs/004-validate-domain-rules/checklists/domain-rules.md)은 새 파일로 CHK001~CHK034 총34개를 생성했다. 범위 선택 질문1개에 답변은 아직 없어 권장안인 규칙·경계값+인수·증거 기준, 구현 전 검토자·표준 깊이를 적용했다. 모든 항목은 요구사항 문서의 완결성·명확성·일관성·측정 가능성·범위를 묻고 근거 참조34/34를 포함한다. 전 항목 미체크·미판정이며 생성 과정에서 요구사항 충족을 평가하지 않았다. 기존 requirements.md의16개 체크는 변경하지 않았고 구현 통과를 뜻하지 않는다.

[작업 목록](../specs/004-validate-domain-rules/tasks.md)은 T001~T033 총33개를 생성했다. US1 10개·US2 6개·US3 6개·공통11개이고 모두 미실행이다. 각 이야기의 독립 선택 Unit·컴파일 골격 이후 실제 assertion RED→최소 구현→GREEN·목적 커밋을 배치했다. T005/T015/T021의 서로 다른 테스트 파일 작성만 [P]이며 같은 build 디렉터리의 Gradle 실행·공통 증거/문서·Git 작업은 직렬화한다. US1을 MVP로 제안하고 전체 Unit·설명 인수·후속4~6단계 실제 사용 미완료 조건도 유지한다. 신규 Controller/Writer·스키마·PIT/Property 교체·외부 작업은 추가하지 않았다.

Python 형식 검사 exit0으로33개 연속 작업 ID·체크/이야기 라벨/파일 경로·단계별 개수·병행 표식3개,34개 연속 CHK ID·전 항목 미체크·질문 형식·근거 참조100%, 두 신규 문서 로컬 링크17개·활성경로 일치·기존 품질16개 유지·후크 부재를 확인했다. 계획의 문서 트리와 다음 작업, knowledge/README.md 목록도 갱신했다. 이는 생성 산출물의 형식·연결 검사이며 맞춤 요구사항 품질 판정·코드 리뷰·기능 테스트·개발자 설명 인수는 수행하지 않았다. 다음은 `$speckit-analyze`와 맞춤 목록의 검토자 판정이다. 이번에 커밋·브랜치 생성·병합·외부 게시를 수행하지 않았다.

## 3단계 작업 목록 재생성 요청 반영 (2026-10-15)

사용자가 `$speckit-tasks`를 다시 요청해 기존 [작업 목록](../specs/004-validate-domain-rules/tasks.md)을 현재 명세·계획·모델·계약·조사·검증 안내와 재대조했다. `setup-tasks.sh --json` exit0으로 같은 FEATURE_DIR·활성 작업 템플릿·설계 문서 목록을 확인했다. 사전·사후 `.specify/extensions.yml`이 없어 실행할 훅이 없었다. 현재 docs/validate-domain-rules와 기존 미커밋 변경을 보존했다.

범위·작업33개·T001~T033 ID·전 항목 미체크·이야기 라벨·병행 표식은 유지했다. T001/T008/T014/T020/T022/T024/T026/T028/T030/T031의 축약 문서/Java 경로를 저장소 루트 기준 전체 경로로 구체화했다. 계획 첫 안내도 후속 생성 예정 표현 대신 이미 존재하는 tasks.md와 미실행 상태를 가리키도록 갱신했다. US1 10개·US2 6개·US3 6개·공통11개, 독립 검증과 US1 MVP·후속 연동 경계는 유지한다.

Python 검사 exit0: 연속ID·체크·라벨·파일 경로·분포·병행 후보T005/T015/T021 보존, 두 문서 로컬 링크20개 정상, 활성 경로 일치·사후 훅 없음. 맞춤/내장 체크리스트는 수정 전후 SHA256 동일이며 판정하지 않았다. `git diff --check` exit0이다. 이번 변경은 작업 지시의 경로 명확화이며 신규 기능 테스트·실제 작업 수행·Git 커밋/병합/게시를 하지 않았다. 다음은 `$speckit-analyze`로 산출물 일관성을 검토하는 것이다.

## 3단계 맞춤 체크리스트 검토 인수 (2026-10-15)

사용자가 미판정 우회 진행에는 아니요라고 답하고 “체크리스트 전체 체크해”라고 별도로 요청했다. 명세·계획·모델·계약·작업 목록의 요구사항 근거를 CHK001~CHK034 각각 대조해34/34 충족으로 판정했다. 항목별 근거는 [맞춤 목록](../specs/004-validate-domain-rules/checklists/domain-rules.md)의 판정 표에 기록했다. 내장 명세 품질16/16도 유지한다. 이 체크는 요구사항 품질이며 기능 구현/테스트/설명 인수의 완료가 아니다. speckit-implement의 체크리스트 게이트는50/50 체크·미체크0으로 통과하며 원래 구현 요청을 계속한다.

## 3단계 구현 착수와 공통 선행 조건 (2026-10-15)

T001: root·HEAD 90a4085·docs/validate-domain-rules·기존 변경5개와004 문서만 대조했다. 이번 명세/계획에 해당하는14개 문서만 검토·명시 경로로 보존 커밋174da7d를 작성했다. `git switch -c feature/validate-domain-rules develop` 뒤 `git cherry-pick 174da7d` exit0으로e3de6ab에 반영했다. 사용자 변경 삭제·stash·reset·외부 게시·develop 병합은 하지 않았다. 첫 Git 쓰기는 샌드박스 권한 실패, 권한 확장 후 성공했다.

T002: requirements16/16·domain-rules34/34, 미판정0이며 기존 검토자 판정표를 읽고 구현 게이트를 통과했다. 두 체크리스트는 수정하지 않는다. `.specify/extensions.yml` 부재로 사전 훅 없음.

T003: build.gradle의 *Test 및 contract/property 제외, 현재 listener/XML 합계·실행≥1·실패/오류/skip0, 캐시 금지·UTF-8/UTC·단일 fork와 junit-platform.properties 병렬 비활성을 확인했다. 기존 두 ReservationStatus와 프로브/PIT/Property 참조를 유지하며 빌드·SQL·OpenAPI는 변경하지 않는다. ignore는 root/backend의 기존 Java 산출물·환경·IDE 제외와 .dockerignore를 확인했으며 현재 구성에 필수 누락 없음.

T004: `java --version` exit0은 Corretto25.0.4+7-LTS, `./gradlew --version` exit0은9.7.1·Mac OS X27.0.1 aarch64다. Gradle 최초 샌드박스 캐시 잠금 접근 실패(exit1)는 권한 확장으로 해결했으며 기능 RED가 아니다. 읽기 명령의 일시적인 backend/backend 경로 오류도 기능 검증과 무관하다. RED/GREEN은 아래 실제 assertion·종료 코드·XML 실행/실패/오류/skip으로만 기록한다. 고정 Unit이라 seed·경쟁 조건은 해당 없다.

- T006-capacity-RED: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest` (backend/) exit1; XML 실행/실패/오류/skip: com.limitedbooking.backend.resource.ResourceRulesTest: 5/2/0/0.

- T007-capacity-GREEN: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest` (backend/) exit0; XML 실행/실패/오류/skip: com.limitedbooking.backend.resource.ResourceRulesTest: 5/0/0/0.

- T008-name-RED: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest` (backend/) exit1; XML 실행/실패/오류/skip: com.limitedbooking.backend.resource.ResourceRulesTest: 14/9/0/0.

- T009-name-GREEN: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest` (backend/) exit0; XML 실행/실패/오류/skip: com.limitedbooking.backend.resource.ResourceRulesTest: 14/0/0/0.

- T010-codepoints-RED: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest` (backend/) exit1; XML 실행/실패/오류/skip: com.limitedbooking.backend.resource.ResourceRulesTest: 52/5/0/0.

- T011-resource-GREEN: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest` (backend/) exit0; XML 실행/실패/오류/skip: com.limitedbooking.backend.resource.ResourceRulesTest: 52/0/0/0.

T005~T013: 수량 no-op에서 -1·1001의 IAE 기대2건 실패→5건 통과. 원문 이름 반환에서 공백 반환값3건과 null/빈/공백 거부6건 실패→14건 통과. 길이 검사 없이 일반/😀/NBSP3종101개가 모두 수용돼5건 실패→52건 통과했다. 정확한 오류 문구에 의존하지 않았다. R-CAPACITY/R-NAME 전체와 대조해 일반/😀/혼합/정리 전100초과·NBSP 각1/양끝/100/101·정상값 재정리를 포함한다. 기대값은 고정 입력과 명시 문자열이며 mock/구현 호출로 계산하지 않는다. 반복 GREEN 없이 직전 XML52/0/0/0을 최종 US1 인수로 사용한다. 사용자 설명·HTTP·IT·PIT는 미실시다.

- T016-key-RED: `./gradlew test --tests com.limitedbooking.backend.reservation.IdempotencyKeyTest` (backend/) exit1; XML 실행/실패/오류/skip: com.limitedbooking.backend.reservation.IdempotencyKeyTest: 56/48/0/0.

- T017-key-GREEN: `./gradlew test --tests com.limitedbooking.backend.reservation.IdempotencyKeyTest` (backend/) exit0; XML 실행/실패/오류/skip: com.limitedbooking.backend.reservation.IdempotencyKeyTest: 56/0/0/0.

T015~T019: 무검증 record에서 parse24건·직접 생성24건의 무효 키 거부가 모두 실패했다. 정상7건·값 동등성1건은 기존 record 동작을 보장하며 억지 RED를 만들지 않았다. compact constructor 최소 구현 후56/0/0/0을 확인했다. K-KEY 전체의 null/빈/65·허용 문자별1/64·공백의 앞/뒤/내부·끝LF/CRLF·비ASCII/구분문자를 양 생성 경로로 대조했다. 같은 원문 equals/hashCode·대소문자 비동등을 확인했으며 다른 값의 hashCode 차이나 참조 동일성을 요구하지 않는다. 이 실행을 최종 US2 인수로 사용하며 HTTP·DB·경쟁·PIT는 미실시다.

- T022-decision-RED: `./gradlew test --tests com.limitedbooking.backend.reservation.CancellationDecisionTest` (backend/) exit1; XML 실행/실패/오류/skip: com.limitedbooking.backend.reservation.CancellationDecisionTest: 5/5/0/0.

- T023-decision-GREEN: `./gradlew test --tests com.limitedbooking.backend.reservation.CancellationDecisionTest` (backend/) exit0; XML 실행/실패/오류/skip: com.limitedbooking.backend.reservation.CancellationDecisionTest: 5/0/0/0.

T021~T025: null 반환 골격에서 두 상태/반복4건이 값 불일치, 내부 null1건이 예외 미발생으로 실패했다. 두 분기와 명시 null 검사 구현 후5/0/0/0이다. C-DECISION 전체와 대조했고 가변 필드·I/O·Clock·저장 의존성·예약 mock이 없음을 코드 검토했다. 반복 호출은 같은 기대 enum과 대조한다. 이 실행을 US3 최종 인수로 사용하며 실제 상태/수량/시각 갱신·취소 원자성은 검증하지 않았다.

- T027-all-Unit-GREEN: `./gradlew test` (backend/) exit0; XML 실행/실패/오류/skip: com.limitedbooking.backend.common.ToolchainSmokeTest: 3/0/0/0; com.limitedbooking.backend.reservation.CancellationDecisionTest: 5/0/0/0; com.limitedbooking.backend.reservation.IdempotencyKeyTest: 56/0/0/0; com.limitedbooking.backend.resource.ResourceRulesTest: 52/0/0/0.

### 전체 인수·assertion 추적

T027: 전체 Unit116=ResourceRules52+IdempotencyKey56+CancellationDecision5+기존 프로브3이며 현재 listener와4개 XML의 합계가 일치한다. 실패/오류/skip은 전부0, IT·contract/property는 발견되지 않았다. Spring·DB·Redis·Docker 기동 없이 실행했다. 최초 기준 Unit도3/0/0/0이었다. 기존 Gradle deprecated 경고는 남으며 Gradle10/configuration cache는 미검증이다.

T028의 계약·quickstart 대조:

| 계약 | assertion·탐지할 실패 조건 | 실제 RED→GREEN |
| --- | --- | --- |
| R-CAPACITY | acceptsCapacityBoundary/rejectsCapacityOutsideRange: 양끝 포함 범위와 보정 없는 거부 | T006→T007 |
| R-NAME 공백 | normalizesName/rejectsInvalidName: 정확한 반환·내부 공백 유지, null/정리 후 빈 값 거부 | T008→T009 |
| R-NAME 길이·NBSP | countsCodePoints/rejectsNamesBeyondCodePointLimit/preservesNonBreakingSpaces: UTF-16/정리 전 길이 제한·101 허용·절단·NBSP 제거/금지 거부 | T010→T011, 기존 strip에서 만족한 정상 사례는 회귀 보장 |
| R-NAME 재정리 | normalizesAgainToSameValue: 각 정상 결과의 재호출도 명시한 기대값과 일치 | T011의52건 포함, 재정리 회귀 보장 |
| K-KEY | preservesValidKey/rejectsInvalidKey/rejectsWhitespaceAnywhere/rejectsNonAsciiOrSeparators/rejectsInvalidDirectConstruction: 원문 변형·부분 일치·생성자 우회 탐지 | T016→T017 |
| K-KEY 동등성 | preservesCaseSensitiveEquality: 같은 원문의 equals/hashCode와 대소문자 비동등 | record 기존 동작 회귀 보장 |
| C-DECISION | mapsReservationStatus/repeatsDecisionWithoutStateChange/rejectsMissingInternalStatus: 매핑·반복·내부 null 오류 | T022→T023 |

T029: 사용자에게 경계값 한 사례의 이유와 전체 assertion 탐지 대상의 검토 여부를 요청했다. 사용자 답변/확인 전으로 미완료다. 위 설명 표와 예시는 사용자 설명을 대신하지 않는다.

T030~T031: README·versions·decisions·knowledge와004 명세/모델/계약/안내/계획 상태를 실제 규칙·Unit 수에 맞췄다. 원계획3단계의 코드/실행5항목만 체크하고 설명 및 실제 요청 경로 사용은 미완료로 남겼다. 버전·빌드·OpenAPI·SQL·기존 프로브는 유지했다. 이번 IT/HTTP/DB/Redis/경쟁/Contract/Property/PIT/부하·외부 CI는 미실행이다.

T032 사전 검사: Python 문서 검사 exit0으로 변경 Markdown16개·로컬 링크191개 실재·줄끝공백0을 확인했다. XML4개 합계116/0/0/0과 신규 클래스별 실행>0을 다시 읽어 listener와 일치함을 확인했다. `git diff --check` exit0, 체크리스트는e3de6ab 대비 차이0이다. build.gradle·OpenAPI·업무 migration·기존 ReservationStatus는90a4085 대비 변경0이다. 최종 생산 코드에 수량 no-op·이름 원문 반환·무검증 키·null 판단 골격은 남아 있지 않다. `.specify/extensions.yml` 부재로 사후 훅도 없다. 독립 리뷰 결과는 아래에 추가한다.

### 최종 독립 리뷰·인수 결과

T032: 읽기 전용 독립 리뷰어가90a4085..903fce9의 생산3개/Unit3개와 현재 문서를 검토했다. Critical0·Important0, 인터페이스·순수성·원문 비노출과 계약 전체/다섯 핵심 실패 조건의 assertion 및 XML116/0/0/0을 확인했다. 테스트 재실행은 하지 않았다. 리뷰 시 T032/T033 미체크와32/33 표현의 일시 차이(Minor1)는 최종 기록·커밋 절차에 따라 두 체크를 반영해 일치시켰다. 이월 Minor는0이다.

리뷰 판단 제외 항목의 처리는 기존 명세 범위를 유지한다: T029는 사용자 설명 대기이며 완료로 세지 않는다(전체 인수 지연). HTTP·입력 형식·인증/오류 변환은4~6단계 후속이다(업무 API 사용 불가). Writer·멱등 응답·취소 복구·경쟁·장애 보장은 후속이다(실제 쓰기 정합성 미보장). 업무 PIT/Property·프로브 교체는 후속이다(변이 탐지율/생성 사례 보장 없음). 이번 미실행 IT/Contract/부하/외부CI의 결과를 새 증거로 주장하지 않는다(해당 경로·성능 회귀 미검증).

T033: 명시 경로의 문서/최종 체크만 목적 커밋으로 보존한다. 구현 브랜치는 feature/validate-domain-rules, 기능 커밋은1f38027·b809877·903fce9이다. 최종 커밋 ID와 작업 트리 상태는 커밋 후 git log/status로 확인해 완료 보고에 남긴다. push·PR·develop 병합·태그는 하지 않았다. T029는33개 중 유일한 미완료이며 설명 답변 후 해당 기록을 인수해야 전체 완료다. 사용자가 읽을 핵심은 ResourceRules/IdempotencyKey/CancellationDecision 및 대응 세 Unit·규칙 계약·위 RED/GREEN 표다. 다음 구현은4단계 로컬 인증과 대상 API의 ResourceRules 연동이다.

## 3단계 T029 설명·사용자 완료 지시 반영 (2026-10-16)

**현재 상태: 004 작업33/33 완료.** 2026-10-15의 T029 대기·32/33 기록은 당시 이력이다. 사용자가 T029의 목적을 물은 뒤 “경계값 하나의 이유를 설명해봐”, “테스트가 어떤 오류를 잡아”를 요청했고, 안내를 받은 뒤 “이제 T029처리해”라고 명시적으로 완료를 지시했다. 이 최신 지시를 설명 내용 확인·인수 처리의 근거로 삼았다. 사용자가 아래 내용을 독립적으로 재설명했다고 기록하지 않는다.

- 경계값 설명: capacity=0은 정수0~1000의 하한 포함 규칙에 따라 허용한다. 대상 생성은 가능하지만 예약 가능한 수량은0이므로 예약은 품절이어야 한다. -1은 거부,0·1은 허용이며 `capacity <= 0`으로 거부하는 오류를 해당 Unit이 탐지한다. 품절 HTTP 동작은 후속 기대값이고 이 Unit의 실행 범위가 아니다.
- 전체 assertion 설명: 수량의 하한/상한 제외·범위 밖 수용, 이름 공백 정리/내부 공백 훼손·null/빈 값·코드 포인트 길이·strip 전 길이 검사·NBSP 제거/금지, 키 구문/길이·공백/끝LF/CRLF·원문/대소문자 변형·직접 생성자 우회·값 동등성 오류, 취소 상태 매핑·반복 판단·내부 null 오류를 안내했다. 실제 재고 음수나 중복 취소의 이중 복구는 후속 DB/트랜잭션 검증 대상이라고 구분했다. 구체적인 assertion 대응은 위 T028 표와 세 Unit을 따른다.
- 확인 방식과 한계: 설명은 어시스턴트가 제공했고 사용자는 설명 뒤 완료를 지시했다. 사용자 자신의 설명 답변·독립 재설명 시험·추가 질의로 설명 능력을 검증한 증거는 없다. 최신 사용자 지시를 근거로 T029를 인수한 것이며 독립 설명 능력 검증과 구분한다. 기존 FR-008/SC-005의 학습 목적과 규칙은 바꾸지 않았다.
- 반영: T029·원계획의 설명 체크와 README/knowledge/004 상태를 동기화했다. 과거 실행/대기 기록과 요구사항 품질 체크리스트는 보존했다. 코드·SQL·OpenAPI·테스트·버전 변경은 없다. 기존 전체 Unit116건 통과는 2026-10-15 기록이며 이번 문서 처리에서는 테스트를 재실행하지 않는다.
- 남은 범위: 실제 요청 경로 사용은4~6단계에서 검증한다. HTTP·Writer·경쟁·업무PIT/Property·부하·외부CI를 이번 설명 인수로 완료 처리하지 않는다. 다음 한 작업은4단계 로컬 인증·대상 API의 ResourceRules 연동이다.

문서 검증(완료 커밋44f6c90 대상): Python 검사 exit0으로 Markdown9개·로컬 링크147개 실재·작업33/33·체크리스트 변경0을 확인했다. `git diff --check HEAD~1 HEAD` exit0이다. 최초 두 검사 스크립트는 각각 Git 한글 경로의 따옴표 출력과 기존 Markdown 줄바꿈용 끝 공백을 처리하지 못해 실패했다. NUL 구분 경로를 사용하고 공백은 실제 변경 줄에 대해 검사하도록 수정한 뒤 통과했다. 테스트/기능 실패는 아니며 이번 기능 테스트는 재실행하지 않았다.

## 3단계 GitHub PR·병합 전 검증 (2026-10-16)

사용자가 develop 대상 GitHub PR 생성, 적대적 코드 리뷰, 이상이 없을 때 병합을 명시적으로 지시했다. `git fetch origin develop` exit0, origin/develop90a4085 대비 feature HEADfec4f75는 뒤처짐0·추가7개 커밋이며 작업 트리는 깨끗했다. 기존 PR은0개였고 merge commit 방식 허용을 확인했다. 별도 release/버전 변경·배포는 이번 범위가 아니다.

`backend/`의 `./gradlew build` exit0으로 jar/bootJar와 전체 Unit116건을 실행했다. XML4개의 ResourceRules52·IdempotencyKey56·CancellationDecision5·프로브3을 현재 listener와 대조해 실패/오류/skip0·합계116 일치를 확인했다. T001~T033 체크33/33과 `git diff --check origin/develop...HEAD` exit0도 확인했다. 기존 Gradle deprecated 경고는 남아 있다. IT/Contract/Property/PIT/부하는 이번에 실행하지 않았다.

`git push -u origin feature/validate-domain-rules`와 `gh pr create --base develop --head feature/validate-domain-rules` exit0으로 [PR #3](https://github.com/joypeb/limited-booking-test/pull/3)을 게시했다. GitHub head는fec4f75, base는develop이며 MERGEABLE/CLEAN을 확인했다. statusCheckRollup·feature workflow 실행은 모두0건이고 저장소에 .github/workflows도 없다. 외부 CI 성공을 주장하지 않으며 로컬 실행 검증과 구분한다.

적대적 리뷰의 첫 Codex CLI 프로세스는 read-only/high로 실행했지만 ChatGPT 계정에서 기본 모델 gpt-6.1-sol을 지원하지 않는400 오류로 exit1 종료했다. 별도 설치 skill YAML 오류 로그도 있었으며 실제 모델 리뷰는 시작되지 않았다. 이 실패를 코드 결함이나 리뷰 통과로 세지 않는다. 읽기 전용의 새 컨텍스트 리뷰 에이전트로 대체했으며 결과는 아래에 기록한다.

### PR #3 적대적 리뷰 결과와 병합 판정

새 컨텍스트의 읽기 전용 리뷰어가 origin/develop..fec4f75의 전체 변경과 새 생산3개/Unit3개를 계약·원계획에 대조했다. 유효한 신규 코드 결함0·머지 차단0으로 판정했다. 실제 생산 코드를 /private/tmp에서 컴파일한 공격 harness41건이 exit0으로 통과했으며 😀100/101·strip 전100초과·NBSP3종·int MIN/MAX·키 끝 LF/CRLF/CR/Unicode 줄바꿈·parse/new·대소문자 동등성·취소 두 상태/null을 포함한다. 리뷰어도 기존 XML116/0/0/0을 읽었고 저장소 변경이나 Gradle 재실행은 하지 않았다.

주 작업자는 세 생산 클래스와 대응 assertion·현재 XML을 독립 대조했고 분류할 유효 지적은 없었다. 빌드·의존성·OpenAPI·SQL·프로브는 기준 대비 변경되지 않았다. T029 사용자 완료 지시와 독립 재설명 미실시를 구분하며 실제 HTTP/Writer/경쟁/업무 심화 검증은 후속으로 유지한다. 이 판정 이후의 변경은 본 PR/리뷰 결과를 남기는 문서뿐이다.

병합 조건: 최신 원격 head가 검토 코드와 동일하고 base가develop이며 충돌·실패/미해결 체크·변경 요청이 없으면 사용자 지시에 따라 merge commit으로 병합한다. PR 설명에 로컬 빌드·별도 CLI 리뷰 실패와 독립 대체 리뷰 결과·외부 CI 미등록을 반영한다. 실제 merge SHA/최종 상태는 GitHub PR과 완료 보고에서 확인하며 병합 전 기록에서 성공을 선포하지 않는다.

## 4단계 구현 착수 및 공통 기반 준비 (2026-10-17)

- T001: `develop`(`8366995`)에서 `git switch -c feature/auth-resource-api`로 작업 브랜치를 생성하고, 선행 Spec Kit 단계 산출물을 구간별로 보존 커밋했다(`39bf36b` 명세·요구사항 체크리스트, `6ece577` 구현 계획·연구·데이터 모델·인터페이스 계약·빠른 시작, `642d4ee` 맞춤 체크리스트·작업 분해·지식 색인). 사용자 변경 삭제·reset·원격 push·develop 병합은 수행하지 않았다.
- T002: `specs/005-auth-resource-api/checklists/requirements.md`(16/16 충족, 미체크 0)와 `specs/005-auth-resource-api/checklists/auth-resource-api.md`(34/34 충족, 미체크 0) 검토 완료 상태를 확인해 구현 게이트를 통과했다. 두 체크리스트 파일은 구현 중 변경하지 않는다.
- T003~T006: `.env.example`과 `backend/src/main/resources/application-local.yaml`을 `ADMIN_PASSWORD`·`USER_A_PASSWORD`·`USER_B_PASSWORD` 구조로 갱신하고, `ClockConfig`(`Clock.systemUTC()`), `ApiExceptionHandler` 공통 `application/problem+json` 직렬화 골격, `TestData`·`ContainerTestSupport`·`HttpClientTestSupport`·`BackendApplicationIT`의 고정 UUID(`ADMIN_ID`, `USER_A_ID`, `USER_B_ID`)·테스트 전용 가짜 비밀번호·Basic 인증/raw HTTP 전송 헬퍼를 구성했다.
- 기반 확인 실행: `backend/`에서 `./gradlew test integrationTest --tests com.limitedbooking.backend.BackendApplicationIT` 종료 코드 `0`; `test: tests=116, failed=0, skipped=0`, `integrationTest: tests=1, failed=0, skipped=0`.

### User Story 1·2 (로컬 인증·권한 및 예약 대상 생성·단건 조회) TDD 기록

- T007~T008 (US1 RED): `SecurityIT` 작성 후 최소 골격 상태에서 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT` 실행 -> 종료 코드 `1`; `integrationTest: tests=4, failed=4, skipped=0` (기능 미구현으로 4건 모두 의도된 실패 확인).
- T009~T011 (US1 구현 및 US2 RED): `LocalIdentityResolver`·`SecurityConfig` 구현 및 `ResourceApiIT`(US2 범위) 작성 후 `ResourceController` 최소 골격 상태에서 `./gradlew integrationTest --tests com.limitedbooking.backend.resource.ResourceApiIT` 실행 -> 종료 코드 `1`; `integrationTest: tests=4, failed=4, skipped=0` (대상 생성/조회 엔드포인트 미구현으로 4건 모두 의도된 실패 확인).
- T012~T015 (US1·US2 GREEN): `ResourceRepository.insert`, `ResourceService.create/get`, `ResourceController`, `ApiExceptionHandler`(`404 NOT_FOUND`) 구현 후 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT` 실행 -> 종료 코드 `0`; `integrationTest: tests=8, failed=0, skipped=0` (`SecurityIT` 4건, `ResourceApiIT` 4건 모두 통과).

### User Story 3 (8KiB 본문 상한·엄격한 입력 검증·공통 오류 응답) TDD 기록

- T016~T018 (US3 RED): `SecurityIT`(미인증 8,193바이트 일반/chunked/raw `413` 우선 반환, 8,192바이트 재사용 래퍼 전달)와 `ResourceApiIT`(이름/수량 경계 위반, 미정의 `userId`/`quantity`, 중복 JSON 키, 부동소수점 `1.5`/`1.0`, 문자열 `"10"`, 깨진 JSON, 잘못된 UUID 경로 `400`, `text/plain` `415`, 취소 경로 비어 있지 않은 본문 `400` 및 8KiB 초과 `413`) 추가 후 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT` 실행 -> 종료 코드 `1`; `integrationTest: tests=13, failed=4, skipped=0` (신규 US3 검증 4건 의도된 실패 확인).
- T019~T022 (US3 GREEN): `RequestBodyLimitFilter` 구현 및 `BasicAuthenticationFilter` 앞 등록, `ApiExceptionHandler`의 Jackson 3 엄격 역직렬화(`FAIL_ON_UNKNOWN_PROPERTIES`, `STRICT_DUPLICATE_DETECTION`, `ALLOW_COERCION_OF_SCALARS` 비활성화, `ACCEPT_FLOAT_AS_INT` 비활성화, `LogicalType.Integer` 스칼라 강제 변환 거부) 및 `400`/`415`/`503` 매핑(미분류 SQL 결함 `500` 분리 포함), `SecurityConfig` 내 취소 경로 본문/UUID 검증 구현 후 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT` 실행 -> 종료 코드 `0`; `integrationTest: tests=14, failed=0, skipped=0` (`SecurityIT` 6건, `ResourceApiIT` 8건 통과).

### Phase 6 (`./gradlew verify` 전체 회귀 게이트·문서 동기화·최종 점검) 기록

- T023 (`./gradlew verify` 전체 회귀 검증):
  - 실행 명령: `backend/`에서 `./gradlew verify` -> 종료 코드 `0`, `BUILD SUCCESSFUL`
  - JUnit XML 집계 (`backend/build/test-results/test/TEST-*.xml` 4개, 합계 `116/0/0/0`):
    - `com.limitedbooking.backend.common.ToolchainSmokeTest`: `3/0/0/0`
    - `com.limitedbooking.backend.reservation.CancellationDecisionTest`: `5/0/0/0`
    - `com.limitedbooking.backend.reservation.IdempotencyKeyTest`: `56/0/0/0`
    - `com.limitedbooking.backend.resource.ResourceRulesTest`: `52/0/0/0`
  - JUnit XML 집계 (`backend/build/test-results/integrationTest/TEST-*.xml` 9개, 합계 `92/0/0/0`):
    - `com.limitedbooking.backend.BackendApplicationIT`: `1/0/0/0`
    - `com.limitedbooking.backend.common.HttpClientCleanupIT`: `2/0/0/0`
    - `com.limitedbooking.backend.common.SchemaIT`: `45/0/0/0`
    - `com.limitedbooking.backend.common.ToolchainSmokeIT`: `3/0/0/0`
    - `com.limitedbooking.backend.reservation.ReservationRepositoryIT`: `4/0/0/0`
    - `com.limitedbooking.backend.resource.ResourceApiIT`: `8/0/0/0`
    - `com.limitedbooking.backend.resource.ResourceRepositoryIT`: `4/0/0/0`
    - `com.limitedbooking.backend.security.SecurityIT`: `6/0/0/0`
    - `com.limitedbooking.backend.support.DatabaseSupportIT`: `19/0/0/0`
- T024~T025: `knowledge/domain/security.md`, `knowledge/domain/resource.md`, `knowledge/README.md`, `README.md`, `docs/decisions.md`, `한정 수량 예약 백엔드 로컬 개발 계획.md`(4단계 체크박스 8/8 완료), `specs/005-auth-resource-api/spec.md`·`plan.md`·`tasks.md`(`27/27` 완료)를 실제 구현·검증 상태와 동기화했다 (`checklists/requirements.md` 및 `checklists/auth-resource-api.md`는 수정하지 않음).
- T026~T027: 저장소 루트에서 `git diff --check` 종료 코드 `0`(서식·공백 오류 0건)을 확인하고, `feature/auth-resource-api` 브랜치에서 구간별 선행 문서 커밋(`39bf36b`, `6ece577`, `642d4ee`)과 구현 커밋(`954f172`, `a2985a3`, `f4e0d3f`)에 이어 최종 검증·문서 동기화 커밋으로 마무리했다. 원격 저장소 `push` 및 `develop` 병합은 수행하지 않는다.

## 4단계 GitHub PR·적대적 코드 리뷰 및 병합 전 검증 (2026-10-17)

- 사용자가 `develop` 대상 GitHub PR 생성, 적대적 코드 리뷰 실행, 병합 적합성 확인 후 병합을 명시적으로 지시했다.
- `git push -u origin feature/auth-resource-api` 및 `gh pr create --base develop --head feature/auth-resource-api` 종료 코드 `0`으로 [PR #4](https://github.com/joypeb/limited-booking-test/pull/4)를 게시했다.
- 독립된 읽기 전용 서브에이전트(`5e14debf6eb098b267e1e97b6ad16d50`)가 `origin/develop...HEAD`(`8366995..36adffd`) 전체 프로덕션·테스트·설정·문서·JUnit XML 결과를 계획서 2~5절·7절 4단계, `docs/openapi.yaml`, `specs/005-auth-resource-api/*` 및 헌법 원칙 I~V에 대조해 적대적 코드 리뷰를 수행했다:
  - **판정**: **PASS** (`Critical 0`, `Major 0`, `Minor 0`, 머지 차단 사유 0건)
  - 검증 항목: 고정 UUID 3계정(`ADMIN`·`USER_A`·`USER_B`) 및 BCrypt 보관, `LocalIdentityResolver.userId(Authentication)`의 `Principal` 단일 신뢰(`X-User-Id`/본문 `userId` 불신뢰), 무상태 Basic 인증(`JSESSIONID` 미생성)·CSRF 비활성화 사유 명시·CORS 와일드카드 미추가, 인증 앞단 `RequestBodyLimitFilter`의 8,192바이트 초과(일반/`chunked`/길이 헤더 누락) `413` 우선 차단 및 상한 이하 재사용 래퍼 전달, Jackson 3 엄격 설정(미정의 필드·중복 키·부동소수점 `1.5`/`1.0`·문자열 `"10"`·불리언 강제 변환 거부 `400`), `415`, 취소 비어 있지 않은 본문/잘못된 UUID `400`, 일시적 DB 장애 `503` 및 미분류 SQL 결함 `500` 분리, 전 구간 `application/problem+json` 6개 필수 속성 및 민감정보 비노출, `./gradlew verify` JUnit XML(`test` 116/0/0/0, `integrationTest` 92/0/0/0) 일치 확인.
- 저장소에 `.github/workflows` 외부 CI는 등록되어 있지 않으므로(12단계 예정) 외부 CI 성공을 주장하지 않으며 로컬 `./gradlew verify` 통과와 구분한다.
- 병합 조건: 최신 원격 head가 검토된 커밋과 일치하고 `MERGEABLE`/`CLEAN` 상태임을 확인한 뒤 사용자 지시에 따라 merge commit 방식으로 `develop`에 병합하고 로컬 `develop`을 동기화한다.

## 2026-10-19 — 5단계 명세 품질 검토

- 기준 커밋: `58f4106`(명세 변경 전). 실제 생성·전환한 로컬 작업 브랜치: `docs/reservation-create-spec`. develop 병합·커밋·push·외부 CI 실행은 수행하지 않았다.
- 대상: [예약 생성 명세](../specs/006-reservation-create/spec.md), [품질 검토](../specs/006-reservation-create/checklists/requirements.md), 활성 기능 경로 `.specify/feature.json`, 지식 문서 목록. 기존 동작·외부 계약 변경은 없다.
- 근거: 계획서 2~5절·7절 5단계, knowledge 규칙 전체와 reservation/resource/security/ratelimit 도메인, 헌법. 실제 예약 읽기 저장소·키 규칙·V1 SQL·Gradle 발견 규칙과 대조했다. 생성 처리 및 선택된 세 통합 테스트는 아직 없다.
- 템플릿: `bash .specify/scripts/bash/resolve-template.sh spec-template --json`으로 해석하고 `.specify/templates/spec-template.md`를 복사해 작성했다. `feature_numbering=sequential`에 따라 `006-reservation-create`를 생성했다. 사전·사후 `.specify/extensions.yml`은 존재하지 않아 실행할 훅이 없다.
- 검증: `python3` 문서 검사(종료 코드 0)로 사용자 이야기 4개·FR 12개·SC 5개·품질 체크 16/16·미해결 표식 없음·활성 경로 일치·명세/체크리스트/지식 목록의 로컬 링크 55개 존재·훅 파일 부재를 확인했다. 요구사항과 인수 시나리오의 의미 대조는 품질 검토 목록에 기록했다.
- `git diff --check`: 종료 코드 0. 문서 검증이며 앱 테스트 수·seed·경쟁 스레드·DB 연결 수는 해당 없음이다. Java·DB·Redis 버전 호환성은 이번 작업에서 재검증하지 않았다.
- 미완료·미검증: 5단계 구현 계획·작업 목록·코드·Unit/IT 실행, 실제 경쟁·장애·취소·Redis 제한. 계획서의 구현 완료 체크는 변경하지 않았다.
- 다음 한 작업: `$speckit-plan`으로 5.2절의 트랜잭션 경계·제공 인터페이스·오류 분류·테스트 우선 검증 순서를 구체화한다.

## 2026-10-19 — 5단계 명세 명확화 검토

- 대상: [006 명세](../specs/006-reservation-create/spec.md). `check-prerequisites.sh --json --paths-only`를 1회 실행해 활성 기능을 확인했다. 작업 브랜치는 기존 `docs/reservation-create-spec`을 유지했다.
- 검토 근거: 계획서 2~5절·7절 5단계, 헌법, knowledge 규칙과 관련 도메인, 실제 예약 읽기 저장소·키 값 객체·V1 제약·OpenAPI 및 현재 테스트 목록. 명세의 기대 결과와 실제 구현 완료 여부를 구분했다.
- 질문·응답: 0개. 기존 확정 정책으로 판단할 수 없는 핵심 모호성이 발견되지 않아 명세·품질 체크리스트 본문은 변경하지 않았다. 실제 사용자 답변이 없으므로 명확화 질문·응답 절을 생성하지 않았다.
- 범주별 판정: 기능 범위·도메인/데이터·사용자 흐름·비기능 품질·외부 의존성·경계/실패·제약/절충·용어·완료 신호·미해결 표식의 10개 범주 모두 이번 5단계 범위에서 명확하다. 비기능 기준은 기존 로컬 환경·대기 상한·실패 복구·보안 규칙을 적용한다. 성능 부하·관측 확장·실제 경쟁 반복 검증은 기존 후속 단계 경계를 유지한다.
- 수동 의미 검토로 품질 16개 항목을 재평가했다. 결과 16/16 → 16/16, 신규 충족 0·퇴행 0·미충족 0. Python 문서 검사 종료 코드 0: 활성 기능 경로·FR12개·SC5개·미해결 표식 없음·체크 상태·로컬 링크 9개를 확인했다.
- 사전·사후 `.specify/extensions.yml`은 없어 실행할 훅이 없다. `git diff --check` 종료 코드 0. 애플리케이션·DB·Redis·지정 IT·일반 verify는 미실행이며 테스트 수·seed·경쟁 조건은 해당 없음이다.
- 다음 한 작업: `$speckit-plan`으로 기존 제공 인터페이스·별도 쓰기 트랜잭션·중복 오류 분류·TDD 순서를 구체화한다. 미해결 업무 정책 또는 명확화 재실행이 필요한 항목은 없다.

## 2026-10-19 — 5단계 구현 계획·Phase 1 설계 문서 검토

- 기준 커밋: `58f4106`, 실제 Git 브랜치 `docs/reservation-create-spec`. 기존 명세·명확화의 미커밋 변경을 보존했다. 브랜치 추가 생성·병합·커밋·push·외부 CI는 수행하지 않았다.
- `bash .specify/scripts/bash/setup-plan.sh --json`: 종료 코드0, FEATURE_SPEC/IMPL_PLAN/FEATURE_DIR는 `specs/006-reservation-create`다. 출력 BRANCH=`006-reservation-create`는 스크립트의 기능 식별값이며 실제 Git 전환 결과가 아니다. 이후 실제 브랜치를 별도로 확인했다.
- [구현 계획](../specs/006-reservation-create/plan.md)·[조사](../specs/006-reservation-create/research.md)·[데이터 모델](../specs/006-reservation-create/data-model.md)·[생성 계약](../specs/006-reservation-create/contracts/reservation-create-contract.md)·[검증 안내](../specs/006-reservation-create/quickstart.md) 5개 산출물을 작성했다. 읽기 전용 조사 에이전트가 실제 저장소와 Spring/PostgreSQL/PGJDBC 공식 자료를 대조했다. 신규 스키마·업무 정책 변경은 없다.
- 헌법 I~V 조사 전/설계 후 관문을 모두 설계 기준으로 충족 판정했다. 신규 업무 정책 질문은 없으며 향후 실제 드라이버·프록시·테스트 주입·timeout 매핑의 실행 확인과 구분했다. 과거 헌법의 채택일/CI TODO를 완료로 바꾸지 않았다.
- Python 문서 검사: 종료 코드0. 설계 산출물5개·지식 문서2개의 로컬 링크90개·미해결 템플릿 표식 없음·코드펜스 짝·행 끝 공백·활성 경로 일치·tasks.md 미생성을 확인했다. 생성 계약의 FR001~012 연결·내부 인터페이스·201/Location/Replay·오류 및 트랜잭션 분기는 수동 대조했다.
- `git diff --check`: 종료 코드0. `.specify/extensions.yml`은 사전/사후 모두 없어 실행할 훅이 없다. 지식 목록·예약 설계 연결과 결정 기록을 갱신했다.
- 미실행: 앱·Unit·지정 세 IT·verify·DB/Redis·실제 경쟁·장애·부하·외부 CI. 테스트 수·seed·경쟁 스레드 수는 해당 없음이다. 버전 실행 호환성도 이번 문서 작업에서 재검증하지 않았다. 계획서 구현 체크박스는 변경하지 않았다.
- 다음 한 작업: `$speckit-tasks`로 네 사용자 이야기의 RED→GREEN·관련 검증·기록을 의존성 순서로 분해한다. 구현 시 먼저 capacity1 최초 생성 HTTP 검증 하나부터 진행한다.

## 2026-10-19 — 5단계 맞춤 요구사항 체크리스트 생성

- 실제 브랜치: `docs/reservation-create-spec`, 기준 커밋 `58f4106`. 기존 명세·계획·기록 변경을 보존했다. 새 커밋·병합·외부 작업은 수행하지 않았다.
- `bash .specify/scripts/bash/check-prerequisites.sh --json --template checklist-template`: 종료 코드0. 활성 디렉터리 `specs/006-reservation-create`, 조사·데이터 모델·계약·검증 안내와 해석된 체크리스트 템플릿을 확인했다. tasks.md는 아직 없다.
- 검토 깊이/용도 선택 질문1개를 제시했고 응답 없이 기본값(구현 전 검토자용·표준 깊이)을 적용했다. 기능 범위는 기존 명세·계획에 따라 생성·멱등 재현·원자적 실패 복구 중심이며 취소·실제 경쟁·Redis는 후속 경계만 다룬다.
- [맞춤 목록](../specs/006-reservation-create/checklists/reservation-create.md)을 신규 생성했다. CHK001~CHK034 총34개이며 요구사항 완전성·명확성·일관성·측정 가능성·시나리오·경계·비기능·전제·모호성을 묻는다. 모두 미체크이고 품질 충족 판정은 수행하지 않았다. 내장 requirements.md의16개 항목 상태도 변경하지 않았다.
- Python 형식 검사 1차: 종료 코드1, CHK032의 직접 명세 참조 누락을 발견했다. 기존 계획/계약/검증 안내 참조를 보존하며 Spec 전제 참조를 보완한 뒤 재검사 종료 코드0: 연속·중복 없는34 ID, 전체 미체크·질문형·명세 참조100%, 로컬 링크61개 정상, 템플릿 표식 없음.
- `git diff --check`: 종료 코드0. `.specify/extensions.yml`은 사전·사후 모두 없어서 실행할 훅이 없다. 지식 문서 목록에 새 맞춤 목록을 등록했다.
- 이번 검증은 생성물 형식·참조 검사다. 요구사항34개 충족 평가·기능 구현·앱/DB/Redis/테스트/CI 실행은 미실시다. 다음 한 작업은 검토자가 목록의 요구사항 근거를 평가하고 보완할 항목을 정하는 것이다.

## 2026-10-19 — 5단계 의존성 순서 작업 목록 생성

- 실제 브랜치 `docs/reservation-create-spec`, 기준 커밋 `58f4106`. 앞선 명세·계획·체크리스트의 미커밋 변경을 보존했다. 새 브랜치·커밋·병합·push·외부 작업은 수행하지 않았다.
- `bash .specify/scripts/bash/setup-tasks.sh --json`: 종료 코드0. FEATURE_DIR=`specs/006-reservation-create`, 절대 TASKS_TEMPLATE 경로와 해석된 템플릿, 조사·데이터 모델·계약·검증 안내의 존재를 확인했다.
- [tasks.md](../specs/006-reservation-create/tasks.md) 신규 생성: 총43개, 준비2·공통 선행2·US1 10·US2 7·US3 11·US4 6·전체 인수5. 모두 미완료다. 명세의 네 사용자 이야기·FR001~012·SC001~005와 제공 인터페이스·실패/복구 검증을 연결했다.
- 의존성은 준비→선행→US1→US2→US3→US4→전체 인수다. 각 이야기는 커밋된 준비 데이터로 독립 인수 가능하지만 공유 운영 파일 수정은 순차다. 병행 후보는 T015/T016, T024/T025의 서로 다른 테스트 파일 작성뿐이다. Gradle·공통 기록·커밋은 순차다. 실제 병행 구현을 수행한 기록이 아니다.
- 최초 MVP는 US1의 한 개 생성·품절/대상 없음·다른 키 추가 예약이다. US2 멱등 재현·US3 원자적 실패 복구·US4 입력/오류 인수와 최종 회귀 전에는5단계 전체 완료가 아니다. 구현 전에 테스트를 먼저 작성하며 기존 동작이 이미 충족하는 항목은 인위적인 RED 없이 회귀로 기록한다.
- Python 정적 검사: 종료 코드0. 43개 전부 체크박스·연속T001~T043·해당 이야기 라벨·파일 경로 포함, [P]4개·작업분류 일치·코드펜스 짝·로컬 링크68개 정상·맞춤 체크34개 미체크 보존·사전/사후 훅 파일 없음 확인. 내용 대조로 데이터 제약·TDD·실패 주입·실제 DB와 순수 분기 구분·후속 단계 경계를 확인했다.
- `git diff --check`: 종료 코드0. plan.md의 후속 작업 문서 존재 설명과 knowledge 목록을 갱신했다. 내장 품질16개·맞춤34개 체크 상태는 바꾸지 않았으며 맞춤 목록의 미검토를 승인으로 간주하지 않았다.
- 미실행: 기능 구현·Unit/IT·verify·실제 DB/Redis/경쟁/장애/부하·외부CI. 이번 검증은 작업 목록의 형식·추적·문서 링크 검증이다. 다음 한 작업은 `$speckit-analyze`로 명세·계획·작업의 일관성/누락을 교차 검토하는 것이다. 맞춤 요구사항 목록의 검토자 평가는 여전히 별개로 남아 있다.

## 2026-10-19 — 분석 C1·G1·G2 수정안 반영

- 사용자 지시: 수정안 제안 후 “수정 진행해”로 문서 편집을 승인했다. 앞선 analyze의 읽기 전용 실행과 구분되는 후속 수정이다. 실제 브랜치 `docs/reservation-create-spec`, 기준 커밋 `58f4106`의 기존 미커밋 변경을 보존했다.
- C1: tasks.md의 T019를 Writer UPDATE0 분기 직접 테스트·기능 RED로, T020을 최소 구현·GREEN으로 재구성했다. plan.md·생성 계약·quickstart의 순서와 의존성 설명을 함께 맞췄다. T019 앞선 Service 선조회 인수로 Writer 분기 테스트를 대신하지 않는다.
- G1: T004와 신규 IT 작성 T005/T015/T022에 동적 테스트 설정 `spring.datasource.hikari.maximum-pool-size=10`, `spring.datasource.hikari.connection-timeout=3000`을 명시했다. T030에는 실제 DataSource 적용값 확인·유한 종료·503·무변경·복구 후201/false를 요구했다. local 프로필을 통한 개발 DB 설정 사용과 정확히3초 응답 단정은 하지 않는다. 관련 plan/contract/quickstart를 동기화했다. 실제 Java/YAML 설정을 구현한 것은 아니다.
- G2: T012/T014에 유효 키K의 품절A409→같은K의 예약가능B201/false를, T033/T038에 유효 키K의 잘못된 본문400→본문만 수정한 동일K201/false를 추가했다. 실패 직후 예약/키 없음·수량 불변과 재시도 후 예약1개·보존식을 구분했다. 기존 계약의 실패 키 비보존 요구를 명시적으로 작업에 연결한 것이며 정책 변경은 아니다.
- 검증: Python 문서 검사 종료 코드0. 작업43개·연속ID·기존[P]4개 유지, T019/T020 및 의존성 일치, 세 IT 풀 설정/실제 적용값 확인과 두 재시도 사례/인수 연결, 로컬 링크21개·코드펜스 짝을 확인했다. 맞춤34개 미체크·내장16개 체크 유지. `git diff --check` 종료 코드0.
- 변경 범위: plan.md·tasks.md·quickstart.md·contracts/reservation-create-contract.md와 knowledge 목록·이 기록. spec.md·업무 코드·SQL·OpenAPI·체크 마커는 변경하지 않았다. 작업 완료 체크와 계획서 단계 체크도 하지 않았다.
- 미실행: 기능 구현·Unit/IT/verify·DB/Redis/경쟁/장애·전체 analyze 재실행·외부CI. 이번에는 승인된 세 지적의 문서 보완과 해당 일관성만 검증했다. 다음 한 작업은 `$speckit-analyze`로 전체 명세·계획·작업을 다시 교차 검토하는 것이다.

## 2026-10-19 — 분석 I1 검증 위치 표기 수정

- 사용자 지시 “반영 후 다시 speckit-analyze”에 따라 분석 시작 전에 승인된 표기 수정만 반영했다.
- specs/006-reservation-create/contracts/reservation-create-contract.md의 FR-010 검증 연결을 ReservationRollbackIT(T022~T023·T030)의 저장/의존성 실패 복구와 ReservationCreateIT(T012·T014·T033·T038)의 입력 오류/품절 후 키 재사용으로 나눴다. 실패 직후 무변경·같은 키201/false·예약1개·보존식 기준은 유지했다.
- 요구사항·작업 수·ID·체크 상태·업무 코드·SQL·외부 계약은 변경하지 않았다. `git diff --check` 종료 코드0. 기능 테스트는 미실행이다.
- 뒤따르는 speckit-analyze는 읽기 전용으로 수행하며 결과는 대화에 보고한다. 이 수정 기록은 분석의 파일 쓰기가 아니다.

## 2026-10-19 — 5단계 맞춤 요구사항 품질 34개 평가

- 사용자 지시: 마지막 안내의 “맞춤 체크리스트34개 항목의 요구사항 품질 평가”에 대해 “진행해”라고 명시해 평가를 수행했다. 체크리스트 신규 생성이나 기능 구현 요청으로 확대하지 않았다.
- 실제 브랜치 `docs/reservation-create-spec`, 기준 커밋 `58f4106`. 기존 명세·계획·분석 보완·기록의 미커밋 변경을 보존했다. 새 브랜치·커밋·병합·외부 작업은 수행하지 않았다.
- 대상 [맞춤 체크리스트](../specs/006-reservation-create/checklists/reservation-create.md)의 CHK001~034를 각 질문에 따라 spec·plan·data-model·contracts·research·quickstart·tasks 및 docs/openapi.yaml·기준 계획서와 대조했다. C1/G1/G2/I1이 보완된 최신 문서가 평가 기준이다.
- 결과: 0/34 → 34/34 충족, 미충족/판정 보류0. 각 항목별 근거와 판단 이유34개를 체크리스트 검토표에 작성하고 [x]로 갱신했다. 체크는 요구사항 문서 품질 판정이며 구현·기능 실행 완료가 아니다.
- CHK013은 실제 고정 OpenAPI의201/Location/Replay·생성 네 필드·CONFIRMED·오류 분류와 대조했다. CHK033은 본문상한→인증→입력→Service 키 비교의 유효 입력 전제를 확인했다. CHK034는 기준 계획서2절 실패조건표·7절11단계의 커밋 후 응답 폐기·DB커밋 확인·동일id재시도 요구를 근거로 상세 장애 검증의 후속 경계를 확인했다.
- tasks.md의 검토 대기 설명과 T002의 최신 결과 확인 문구, knowledge/README.md의 상태를 실제34/34 평가 결과로 맞췄다. 작업43개는 전부 미완료이며 내장 품질16개 마커는 변경하지 않았다. 업무 규칙·spec/plan·SQL·운영 코드·OpenAPI는 변경하지 않았다.
- 검증: Python 문서 검사 종료 코드0. 맞춤34개 체크·34개 개별 근거·연속ID·누락/중복 없음, 작업43개 미완료·내장16개 유지, 코드펜스 짝·로컬 링크70개 정상 확인. `git diff --check` 종료 코드0.
- 미실행: 앱·Unit/IT·verify·DB/Redis·실제 경쟁·장애·부하·CI. 버전 호환성/seed/실제 테스트 수의 새 실행 증거는 없다. 다음 한 작업은 `$speckit-implement`로 최신 게이트와 작업 브랜치를 확인한 뒤 T001부터 순서대로 진행하는 것이다.


## 2026-10-19 / 5단계 구현 시작 — T001~T004

- 기준: knowledge/README.md·rules 전체, reservation/resource/security 도메인, 계획서2~5절과006 spec/plan/contracts를 대조했다. 기존 V1·저장 지원을 유지한다.
- 시작 HEAD: 58f41066786d10c1cb483422827617ae4c7fed19. docs/reservation-create-spec의 기존 문서4개 수정과 specs/006 미추적 파일을 보존하고 develop 기준 feature/reservation-create를 생성했다. 별도 worktree 생성 없이 사용자 지정 작업 루트에서 작업한다. 병합·push 없음.
- 구현 게이트: requirements.md 16/16, reservation-create.md 34/34 충족, 미완료0. 검토자 마커 변경 없음.
- 환경: Corretto25.0.4.7.1, Docker29.8.2. Docker Desktop 시작 후 연결 확인. 최초 sandbox의 소켓/Gradle 캐시 제한은 승인된 실행으로 해결했다.
- baseline: backend/에서 ./gradlew test, 종료0, Unit116·실패0·skip0(listener/XML).
- 테스트는 postgresOnly의 고정18.6 이미지·동적 포트·전용 DB를 사용한다. local 프로필은 사용하지 않는다. 신규3IT의 풀10/획득3000ms를 명시한다. V1 변경 없음.

T003: ./gradlew dependencies --configuration compileClasspath 종료0, PostgreSQL42.7.13(BOM). T006: 최초 IT1건 실패(201 기대/404 실제), 종료1·skip0. 앞선 테스트 타입 추론 컴파일 오류는 수정했으며 기능 RED에 포함하지 않는다.

### US1 RED→GREEN (T005~T014)

- 최초 선택 실행: integrationTest --tests '*ReservationCreateIT.createsFirstReservation' 종료1,1건 중1실패(404/201). 최소 생성 구현 후 같은 명령 종료0,1건 통과.
- 추가 기대값: integrationTest --tests '*ReservationCreateIT' 종료1,4건 중2실패(품절/없는 대상이500),2건 회귀 통과. UPDATE0 키 재조회 후 대상 없음/품절 구분·SOLD_OUT409를 추가했다.
- 각 HTTP 종료 후 별도 연결로 available·행 수·인증UUID·키 미보존·단일SELECT 보존식을 확인한다. 품절 A 실패 키로 B 생성201/false를 검사한다. US2 재현·US3 복구·US4 추가 경계는 아직 미완료다.

US1 인수: ./gradlew integrationTest --tests '*ReservationCreateIT' --tests '*ResourceRepositoryIT' --tests '*ReservationRepositoryIT', 종료0·12건·실패0·오류0·skip0.

### US2 조정 분기 (T015~T020)

- ./gradlew test --tests '*ReservationServiceTest' integrationTest --tests '*IdempotencyIT': 종료1, Unit3건 모두실패(키 선조회 미호출·재현/충돌 미구현). Unit 실패로 IT는 실행 안 됨.
- IdempotencyIT 독립 실행은 Clock Bean 이름 중복으로 기동 실패했다. systematic-debugging으로 BeanDefinitionOverrideException을 확인하고 테스트 전용 fixedClock 이름으로 수정했다. 기능 RED로 세지 않는다. Service는 이미 확보한 Unit RED에 따라 구현했다.
- ./gradlew test --tests '*ReservationWriterTest': 종료1,4건 중2실패(UPDATE0 재현/충돌이404),없음/품절2건 회귀통과. 이후 새 SELECT 결과의 재현/충돌 분기를 추가했다. 실제 경쟁 관측은7단계다.

US2 인수: ./gradlew test --tests '*ReservationServiceTest' --tests '*ReservationWriterTest' integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT', 종료0·Unit7·IT9·실패/오류/skip0. 마지막 수량 재현, 충분/품절/없는 다른 대상 충돌, 사용자/키 격리, UUID값 비교, 저장/최초/재현 ...123456Z 일치, CANCELLED 행 불변을 확인했다.

T022~T023: ./gradlew integrationTest --tests '*ReservationRollbackIT', 종료0·IT1·실패/오류/skip0. 실제 같은 트랜잭션에서 available0 관측→INSERT 전 실패500→종료 후 available1/예약0/키 없음→주입 제거 후 동일 키201/false. 기존 Writer가 이미 만족한 회귀이며 가짜 RED 없음. 주입은 finally에서 원복한다.

T026 RED: ./gradlew test --tests '*UniqueViolationClassifierTest' --tests '*ReservationServiceTest', 종료1·17건 중5실패(분류2·복구3),skip0. 대상 메타데이터 탐색과 Writer 롤백 뒤 재조회만 최소 구현한다.

T027~T028: 같은 선택 Unit 명령 종료0·17건·실패/오류/skip0. SQLSTATE/정확한 제약명이 동일 PSQLException일 때만 분류하며 순환 체인 종료·비대상 전파·새 조회1회·성공 누락 내부 오류를 검사했다.

T029~T030: ./gradlew integrationTest --tests '*ReservationRollbackIT', 종료1·6건 중2실패·skip0. 실제 Writer 유일 위반(23505/uq_reservations_user_key) 후 차감 롤백·상위 트랜잭션 거부·statement timeout은통과. 풀 소진과 lock timeout은503 기대/500 실제. 로그의 원인은 각각 CannotCreateTransactionException→SQLTransientConnectionException(3005ms,active10)과 UncategorizedSQLException SQLSTATE55P03이다. T031에서 실제 연결 획득 원인과 정확한55P03만503으로 보완한다. 미분류SQL/트랜잭션 내부결함은500 유지 회귀를 추가한다.

US3 인수: ./gradlew test --tests '*UniqueViolationClassifierTest' --tests '*ReservationServiceTest' --tests '*ReservationWriterTest' integrationTest --tests '*ReservationRollbackIT' --tests '*ReservationCreateIT' --tests '*IdempotencyIT', 종료0·Unit21·IT16·실패/오류/skip0. RollbackIT7건. 풀 실제10/3000ms, Writer 실제read committed/lock2s/statement5s 확인. 잠금/문장/풀503 뒤 수량1·예약0·키없음 및 같은 키201/false, 미분류SQL/Transaction500를 확인했다. 주입/잠금/연결을 finally 정리한다.

T033~T035: ./gradlew integrationTest --tests '*ReservationCreateIT', 종료0·8건·실패/오류/skip0. 기존 키 검증·필터·엄격JSON·인증UUID 처리를 재사용해 모든 추가 입력/인증 사례가 첫 실행 회귀GREEN이었다. 유효 키·잘못된 본문400 직후 키/예약 없음·수량불변 및 동일 키 정상 본문201/false를 확인했다. 인위적 RED/불필요한 운영 변경 없음.

US4/T036~T038: ./gradlew integrationTest --tests '*ReservationCreateIT' --tests '*SecurityIT' --tests '*ResourceApiIT', 종료0·25건(11+6+8)·실패/오류/skip0. 8192통과,8193인증전413, Content-Length 없는 chunked(HTTP client/raw) 실제8193거부, 미디어415,기존성공키의 잘못된입력400/미인증401 우선, 인증3계정/위조헤더 무시·오류필드/WWW-Authenticate/민감정보 비노출을 확인했다. 기존 공통 처리와 T011 입력 검증이 모두 충족해 운영 수정은 불필요했다.

T039: ./gradlew test integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT' --tests '*ReservationRollbackIT', 종료0·Unit137·지정IT23(Create11/Idempotency5/Rollback7),실패/오류/skip0. listener와 현재XML 합계를 대조했다.

### 5단계 최종 전체 인수 — T040~T043

- backend/ ./gradlew verify: 종료0, 1분7초. 현재 listener/XML: Unit137·일반IT115, 실패0·오류0·skip0. 신규23IT 외 기존92IT도 통과했다. 심화 태스크/부하/외부CI는 실행하지 않았다.
- 검증한 코드: ReservationController(인증UUID/입력), Service(트랜잭션밖 선조회/롤백후 복구), Writer(READ COMMITTED/SET LOCAL/조건차감·저장), UniqueViolationClassifier(정확한 구조 메타데이터). SQL은 ResourceRepository.decrementIfAvailable·ReservationRepository.insert에 있다.
- 핵심 테스트: ReservationCreateIT11·IdempotencyIT5·ReservationRollbackIT7, Service7·Writer4·Classifier10 Unit. 실제 PostgreSQL18.6·동적 포트/전용DB이며 개발DB·volume은 변경하지 않았다.
- 기준 정책·V1·고정OpenAPI 변경 없음. POST/201/Location/Replay/4필드 및 오류code를 고정계약과 대조했다. 자동 업무 OpenAPI validator는9단계 후속이다.
- README·versions·decisions·지식3도메인/목록·006문서·계획서5단계 체크를 실행 근거에 맞췄다. 기존 미커밋006 설계·분석 기록을 검토·보존한 후 같은 기능 문서 묶음에 포함한다. checklists 마커는 변경하지 않았다.
- 실제 경쟁 반복·응답 유실·소유자GET/취소·Redis·업무계약/Property/PIT·부하·외부CI 미검증. 다음 한 작업:6단계 소유자의 예약 단건 조회·취소.
- 로컬 feature/reservation-create에서 검증 대상별 커밋만 작성한다. develop병합·push·공개 없음. 최종 독립 리뷰 결과는 후속 기록한다.

문서 검증: 변경/006 Markdown 로컬 링크264개, 누락0. git diff --check 종료0. 운영 src/main의 pg_sleep/Mockito/실패주입 플래그 검색0건. 체크리스트 requirements16/16·custom34/34 유지. T001~T043 수행 완료 후 독립 최종 리뷰를 진행한다.

### 독립 최종 리뷰 및 단일 수정 — UUID 입력

- 독립 리뷰 범위58f4106..9cdb39f: Critical0·Important1·Minor1. 수량/트랜잭션/재현 경계는 적합했다. Important는 Jackson 기본 UUID 바인딩이 Base64 22/24자 및 UUID앞뒤공백을 허용하는 입력 결함이다.
- 실제 RED: ./gradlew integrationTest --tests '*ReservationCreateIT.rejectsNonUuidWireRepresentationsWithoutConsumingKey', 종료1·5건 중4실패(400 기대/201 실제),축약UUID1건은 기존400 회귀였다. 새 예약 대상 UUID...0001을 준비해 잘못된 표기가 실제 수량 변경까지 진입함을 재현했다.
- 최소 수정: CreateReservationRequest의 명시적 JsonCreator에서 원문36자 UUID 패턴 확인 후 UUID로 변환한다. Base64/공백제거를 허용하지 않고 대소문자는 모두 허용한다. 기존 UUID record와 외부 계약은 유지한다.

- 문서 현재표의 생성 미구현 표시는 knowledge를 먼저 읽는 작업자에게 이미 완료한 생성 구현을 다시 지시하므로 T041 현재 상태 동기화의 누락으로 평가했다. 같은 수정 과정에서 resource.md의 현재표/다음 작업을 취소·실제 경쟁 후속으로 정정했다. 동작 변경 없는 문서 정정은 링크·diff 검사로 확인한다.
- 판단 보류에 대한 최종 판정: 실제 경쟁 반복은7단계, GET/소유권·취소는6단계, Redis/429/Redis장애는8단계, 응답유실·전체연결/프로세스중단은11단계, 업무자동계약·Property·PIT·부하·외부CI는9~12단계다. 현재 생성 기능/SQL·timeout의 인수를 대신하지 않는 별도 후속 범위로 유지한다. 잘못된 범위 판단이면 최종 예약 시스템의 정합성·장애 보장을 과대평가할 수 있으므로 전체 버전 완료로 보고하지 않는다.

UUID 수정 관련 GREEN: ./gradlew integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT', 종료0·21건(Create16+Idempotency5)·실패/오류/skip0. 비표준5건·엄격JSON과 정상UUID 대소문자재현 모두 통과했다.

수정 후 지정 인수: ./gradlew test integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT' --tests '*ReservationRollbackIT', 종료0·Unit137·지정IT28(Create16/Idempotency5/Rollback7),실패/오류/skip0. listener/XML 대조 일치.

수정 후 최종 전체verify: ./gradlew verify, 종료0·1분9초·Unit137/일반IT120,실패0·오류0·skip0. listener/XML 합계를 대조했다. 독립 리뷰의 UUID 입력 결함은4건 기능RED→5건GREEN 및 관련/전체 회귀로 해결했으며 T041 문서 현재표도 정정했다. 남은 리뷰 지적/유예Minor는0건이다. 재리뷰 대신 단일 수정 후 전체 검증으로 확인했다.

최종 수행 판단과 한계:
- 기존 사용자 지정 경로에서 별도 feature 브랜치로 작업했다. 기존 미커밋 명세를 보존했으며 별도 worktree 수준의 변경 격리는 없다.
- 관측한 SQLSTATE55P03만503으로 추가 연결했다. 같은 상태의 다른 잠금 획득 실패도 의존성 이용불가503에 포함되지만 다른SQL/내부결함은 숨기지 않는다.
- 6~12단계의 소유자GET/취소·실제경쟁·Redis·전체응답유실/연결중단·심화/부하/CI는 후속이다. 이 판단이 잘못되면 전체 예약 시스템의 정합성·장애 보장을 과대평가하므로5단계 인수 범위를 유지한다.

수정 후 문서 링크264개/누락0·diff-check0·운영 실패주입 검색0건을 재확인했다. 전후 확장 훅 파일은 없으며 생략했다. feature/reservation-create를 유지하고 merge/push/공개/외부CI는 수행하지 않는다.

최종 경로 감사: git diff --name-only -z 58f4106 HEAD를 사용해 한글 계획서 경로까지 포함한 전체 변경 Markdown 링크269개·누락0을 확인했다. 앞선264개 검사는 Git의 한글 경로 인용 출력 때문에 계획서의5개 링크를 제외한 부분 집계였다. NUL 구분 재검사로 보완했으며 문서/기능 결함이나 테스트 실패는 아니다. 최종43/43·체크리스트16/16·34/34·확장훅없음을 재확인했다.


## 2026-10-20 6단계 예약 조회·취소 — 구현 중간 증거

- 기준: specs/007-reservation-cancel의 spec/plan/tasks, 계획서 5.3·7절 6단계, knowledge의 도메인·트랜잭션/API·보안 규칙. 계약·스키마·의존성 변경 없음.
- Git: 설계 `63dd7e4`를 사용자 `진행해` 승인 후 develop에 `282295b`로 --no-ff 병합하고 `feature/reservation-cancel` 분기. 구현 결과는 로컬 커밋하며 외부 push/병합은 하지 않는다.
- 사전 확인: requirements 체크리스트16/16, Markdown8개 로컬 링크 오류0, 공백 검사 종료0. SecurityIT 기존6건은 일반 본문 상한 증거만 있고 취소 경로400/413 증거는 없어 신규 IT로 보완.
- 환경: Corretto25.0.4+7-LTS, Gradle9.7.1, Boot4.1.1, Docker daemon29.8.2, PostgreSQL18.6 고정 digest(기존 ContainerTestSupport), RANDOM_PORT/격리 DB. 신규 두 IT의 Hikari 풀10·획득3000ms는 실제 적용값 assertion 포함. JUnit 클래스 순차, 테스트 전체 트랜잭션 없음.
- 조회 RED: `cd backend && ./gradlew integrationTest --tests '*ReservationOwnershipIT'` 종료1, 3건 모두 HTTP 기능 부재로 실패. GET200/404/400 기대와 기존 미구현 응답 불일치(컴파일 성공).
- 조회 GREEN: 같은 명령 종료0, 3건 실패/오류/skip0. null cancelledAt 필수 직렬화, 타인/관리자/없는 UUID404, 인증401+헤더, 비표준 UUID400.
- 취소 RED: `./gradlew integrationTest --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT'` 종료1, 7건 중 최초 취소/타인 취소2건 실패. 기존 취소 본문400·상한413은 이미 통과.
- 취소 GREEN: 위 명령에 `--tests '*SecurityIT'` 추가, 종료0, 13건 실패/오류/skip0. capacity1 상태/수량 복구, 저장 UTC 시각과 GET 동일, 소유권/본문400·413 무변경.
- US3 추가: `./gradlew integrationTest --tests '*ReservationCancelIT'` 종료0, 7건 실패/오류/skip0. 반복 취소(2·3번째) 응답 전체 값 불변. 복구 예외/0건/QueryTimeoutException을 테스트 Spy에만 주입하고 실제 상태 UPDATE 후 같은 트랜잭션의 CANCELLED 및 lock_timeout2s/statement_timeout5s/read committed를 관측. 500/500/503과 롤백(CONFIRMED/null/available0), finally reset 후200 재시도 확인. 활성 상위 트랜잭션 거부도 확인.
- T020은 US2에서 방어를 이미 구현하여 추가 당시 GREEN이다. 일반 중복 취소만으로 조기 반환·상태 조건을 각각 제거하면 두 방어가 서로를 가려 각1건 종료0이었다. 탐지 실패를 숨기지 않고 별도 사례 보강을 진행했다. 복구0건 검사 제거는 `zeroRecoveryIsInternalDefectAndRollsBack`에서 기대500/실제200으로 종료1(1건 실패), 원복했다.
- 보강 원본 실행: CancelIT9건 종료0. 이미 취소된 상태에서 별도 연결의 대상 잠금을 유지해도 재취소200이며, 오래된 CONFIRMED 첫 조회만 Spy로 전달하고 실제 조건UPDATE가0건이면 기존 취소 응답과 수량을 보존한다. 후자는 경쟁 중 낡은 조회의 경로 검증이며 동시성 반복 증거가 아니다.

실제 경쟁 반복/실제 잠금 timeout 경합 전체/Redis 제한/업무 자동 계약·속성·PIT·부하·외부 CI는 이번 범위 밖이다. 대상 잠금 단일 사례와 QueryTimeoutException 주입을 실제 경쟁·DB 중단 검증으로 일반화하지 않는다.

- T021 보강 고의 변경: 조기 반환 제거→`alreadyCancelledDoesNotWaitForResourceLock` 기대200/실제503로1건 실패·종료1. status 조건 제거→`staleConfirmedReadCannotRepeatStatusUpdateOrRecovery` 기대200/실제500로1건 실패·종료1. 변경을 각각 따로 적용하고 finally에서 원복했다. 첫 시험의 탐지 실패는 위 기록을 유지하며 보강 시험으로 각 방어를 실제 탐지했다.

- 원복 및 US4 지정 검증: `cd backend && ./gradlew test integrationTest --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT'` 종료0, Unit137·OwnershipIT5·CancelIT11(지정IT16) 실패/오류/skip0. 취소 후 원 키의201/Replay=true/CONFIRMED·Location·JSON 최초 값, GET CANCELLED 불변과 복구 후 USER_B 품절 키K2의 새201/false를 실제 HTTP·DB로 확인했다. US4는 기존 생성 재현 규칙이 이미 구현되어 추가 당시 GREEN이며 RED를 새로 주장하지 않는다.


### 6단계 최종 인수 (2026-10-20, 구현 커밋 5b4019c)

- `cd backend && ./gradlew verify`: 종료0, Unit137·일반IT136(기존120+Ownership5+Cancel11), 실패/오류/skip0. 실제 실행66초. test/integrationTest XML의 합계도 대조했다. 기존 Gradle10 비호환 deprecated 경고와 Mockito bootstrap classpath JVM 경고는 유지되며 새 버전 변경은 없다.
- 독립 읽기 전용 리뷰: `282295b..5b4019c`를 실행 계획 스킬의 별도 리뷰 에이전트가 검토했다. 중요/경미 기능 결함 없음. 소유권404·본문/UUID·잠금 순서·트랜잭션·UPDATE0 재조회·롤백·정밀도·멱등 재현과 테스트 탐지력을 확인했다. 리뷰는 테스트를 독립 재실행한 증거가 아니다.
- `docs/openapi.yaml`의 getReservation/cancelReservation과 수동 대조: 현재 응답의 id/resourceId/status/createdAt/cancelledAt 다섯 필드·CONFIRMED null·CANCELLED UTC timestamp,200 JSON,400/401/404/413/503 problem+json·code를 신규 IT/CLI 결과와 대조했다. 정상 생성 재현은201/Location/Idempotent-Replayed를 유지한다. 계약 변경 없음. 500은 기존 안전한 내부 오류 경로이며 정의되지 않은 오류 code를 추가하지 않았다. 자동 업무 계약 검증·429는 후속 단계다.
- CLI 착수 시 `.env`에 ADMIN_PASSWORD 등 인증 변수가 없어 사전검사가 종료1로 중단했다(앱 기동/데이터 변경 전). 이후 이번 앱 프로세스에만 임시 난수 비밀번호를 환경 변수로 주입하고 `.env`는 수정하지 않았다. 실제 자격증명은 출력·기록하지 않았다.
- `docker compose up -d postgres redis` 종료0. `.env`의 DB 접속값은 호스트 JVM에 별도 주입하고 `cd backend && ./gradlew bootRun --args=--spring.profiles.active=local`로127.0.0.1:8080에서 실행했다. health200 후 실제 curl을 호출했다. 요청마다 연결3초/전체10초, 비밀번호는 curl stdin config로 전달해 프로세스 인수/로그에 남기지 않았다. UTF-8·UTC이며 local 프로필 설정·실제 UTC 응답을 확인했다.

| CLI 순서 | 실제 요청 | 응답 |
| --- | --- | --- |
| 1 | ADMIN POST /api/resources, capacity1 | 201 + Location |
| 2 | USER_A GET /api/resources/{resourceId} | 200 |
| 3 | USER_A POST /api/reservations, 새 key | 201 + Location + replayed=false |
| 4 | 같은 key/대상 POST | 201 + 같은 Location/JSON + true |
| 5 | USER_A GET 예약 | 200 CONFIRMED/cancelledAt null |
| 6 | USER_A POST 예약/cancel, 본문 없음 | 200 CANCELLED/UTC 시각 |
| 7 | 동일 취소 POST | 200, 첫 취소 JSON 동일 |
| 8 | USER_B GET 예약 | 404 NOT_FOUND |
| 9 | 취소 뒤 원 key POST | 201, 최초 CONFIRMED/Location/JSON + true |
| 10 | USER_A GET 예약 | 200, 첫 취소 JSON 동일 |
| 11 | USER_A GET 대상 | 200 available1 |

CLI 대상=`fa3fb767-67f9-417c-9488-308559421629`. 개발 DB는 초기화·삭제하지 않았으며 이번 대상만 새로 생성하고 최종 상태를 SELECT했다. 결과 `capacity=1|available=1|CONFIRMED=0|total=1`이며 보존식을 만족한다. 앱 프로세스 그룹을 종료하고 이번 기동한 Compose 서비스를 `docker compose stop postgres redis`(종료0)로 정지했다. 8080 LISTEN 없음도 확인했다. PostgreSQL named volume 및 이번 CLI 데이터는 보존했다. IT는 계속 별도의 격리 DB를 사용했다.

재현 명령은 README/007 quickstart의 local 기동 후 다음 형태다(인증 환경 변수의 실제 값은 기록하지 않음).

```sh
curl --connect-timeout 3 --max-time 10 --user "ADMIN:$ADMIN_PASSWORD" \
  -H 'Content-Type: application/json' -d '{"name":"CLI 검증","capacity":1}' \
  http://127.0.0.1:8080/api/resources
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  http://127.0.0.1:8080/api/resources/<대상-UUID>
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: cli-새키' \
  -d '{"resourceId":"<대상-UUID>"}' http://127.0.0.1:8080/api/reservations
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  http://127.0.0.1:8080/api/reservations/<예약-UUID>
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  -X POST http://127.0.0.1:8080/api/reservations/<예약-UUID>/cancel
```

4·9는 같은 생성 명령,7은 같은 취소 명령을 반복하고8은 USER_B로 조회한다. 최종 DB 검사는 `docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d reservation_local'`에서 이번 대상만 대상으로 resources와 reservations를 LEFT JOIN해 capacity/available/count(CONFIRMED)/count(전체)를 GROUP BY 단일 SELECT로 확인했다.

미완료 범위: 이번 명세의 업무 구현·일반검증·CLI 인수는 완료했다. 실제 동시성 반복(7단계), Redis20/60초 및 장애(8단계), 자동 업무 계약·속성·PIT/부하/외부CI는 후속이다. 실제 취소 QueryTimeoutException은 테스트 주입으로만 검증했으며 DB 중단·실제 timeout 반복과 구분한다. 다음 한 작업은 7단계 경쟁 시나리오 명세·기대 결과 확정이다.

최종 문서 검증: 변경 Markdown13개(공백·한글 경로 포함)의 로컬 링크 오류0, 작업32/32·미완료0, test/integrationTest XML 합계137/136·실패/오류/skip0을 대조했다. `git diff --check` 종료0. 최종 문서 수정 이후 backend 소스는 검증한 구현 커밋5b4019c와 동일하다. 사전·사후 `.specify/extensions.yml` 부재를 확인하여 확장 훅은 없다.


## 2026-10-20 PR #6 적대적 리뷰와 계약 회귀 보완

- 사용자 지시: `develop` 대상 GitHub PR 생성 → 적대적 코드 리뷰 → 문제 없을 때 결과 코멘트·병합. `git fetch origin`으로 대상 d5c05ee96859e773d040909f10a96b74696bf536을 확인하고 `git push -u origin feature/reservation-cancel`, `gh pr create --base develop --head feature/reservation-cancel --body-file ...`를 실행했다. [PR #6](https://github.com/joypeb/limited-booking-test/pull/6), 최초 head bf27a30a3de0b3b9276399f9aa4bf55bf58ed4d0. 작업 트리 clean, 초기 GitHub 체크 등록0, mergeState CLEAN이다. 원격 작업은 이번 최신 지시로 승인되었다.
- 리뷰 절차: `review` 스킬의 SQL/수량·경쟁/소유권·타입/시각·계약 점검, 독립 적대적 검토 및 testing/maintainability·security/API/performance 전문 검토를 수행했다. Codex CLI0.163.0의 읽기 전용 적대적 exec와 `codex review --base origin/develop` 구조화 검토도 각각 종료0. 외부 리뷰 댓글은0이었다. 새 코드의 내부 사용자 영향과 실제 재현으로 판정했고 후속7·8단계 부재는 결함으로 세지 않았다.
- 발견 P2/confidence9: 원시 `getRequestURI()` 접두사·접미사로 취소 본문을 검사하는 필터와 MVC 경로 디코딩 사이의 차이. `/api/reservations/{id}/%63ancel`, `/api/%72eservations/{id}/cancel`에 인증 소유자와 `{}` 본문을 보내면 필터를 우회하여 Controller가 취소를 실행했다. 독립 적대적·API/보안 리뷰와 Codex CLI가 같은 결함을 지적했다.
- RED: `cd backend && ./gradlew integrationTest --tests '*ReservationCancelIT.encodedCancelPathsCannotBypassEmptyBodyContract'` 종료1, 파라미터2건 모두 실패. 두 경로 모두 기대400/실제200, 기대CONFIRMED/실제CANCELLED, cancelledAt null 위반, 기대available0/실제1을 실제 HTTP·PostgreSQL로 관측했다. 컴파일 성공 후 기능 실패이며 미인가 사용자 권한우회라고 과장하지 않는다. 소유자의 잘못된 요청을 잘못 처리한 계약 결함이다.
- 수정: `ReservationController.cancel`이 Handler 선택 후 인증/상한 필터에서 전달한 캐시 본문을 직접 검사하고 비어 있지 않으면 Service 호출 전에 IllegalArgumentException→400 INVALID_REQUEST로 거부한다. 기존 raw 필터를 유지해 일반 요청 동작·검증 순서에 회귀를 만들지 않는다. 경로 자체를 금지하거나 OpenAPI/도메인 정책을 바꾸지 않는다.
- GREEN: `./gradlew integrationTest --tests '*ReservationCancelIT' --tests '*ReservationOwnershipIT' --tests '*SecurityIT'` 종료0, 24건 실패/오류/skip0. 이후 같은 인코딩 경로의 빈 본문 정상200/CANCELLED/available1 회귀도 추가했다.
- 최종 `./gradlew verify` 종료0, Unit137·일반IT138(기존120+Ownership5+Cancel13), 실패/오류/skip0. 실제70초. XML 합계도137/138로 대조했다. 추가된 파라미터2건은 본문{}400·CONFIRMED/null/available0 불변과 동일 경로 빈 본문200·정상복구를 함께 검증한다.
- 수정 이후 최신 작업 트리의 Codex 구조화 리뷰는 수정이 필요한 추가 결함 없음으로 종료했다. 초기 적대적 결과의 P2는 위 RED→수정→GREEN/전체verify로 해소했다. 테스트/유지보수 전문 검토는 추가 발견 없음이며 소유권·잠금·롤백·시각·최초 응답 재현에서도 추가 확정 결함이 없다.
- 계약 연결: 007 FR-003·FR-004와 계획서4.1·5.3의 기존 본문 없음/오류 무변경 요구를 확장 경로에도 적용한다. 기준 계획서·도메인 지식·계약 보조 문서·현재 테스트 수를 동기화했다. OpenAPI 정본·스키마·의존성 변경 없음.

리뷰 후 미해결 발견0. 최종 커밋 push 후 PR head SHA를 재확인하고 결과 코멘트를 남겨 merge commit 방식으로 병합한다. 이 문단은 병합 전 판정·절차이며 실제 병합 완료 증거는 GitHub PR의 merge 메타데이터/리뷰 코멘트를 따른다. 외부 CI는 등록된 체크가 없어 성공으로 주장하지 않는다. 실제 경쟁 반복·Redis·업무 자동 계약/속성/PIT·부하는 여전히 후속이다.

## 7단계 준비 (2026-10-20)

- worktree limited-booking-concurrency-plan, branch docs/reservation-concurrency-spec, 시작6316e19. requirements16/16. 확장 훅 없음.
- knowledge 규칙 전체·reservation/resource/security·계획서2~5절/7단계·고정OpenAPI와 C01~C11을 대조했다. 업무 계약/스키마/후속Redis는 변경하지 않는다.
- `cd backend && ./gradlew test`: 종료0, Unit137 실패/오류/skip0. 지원 Unit/다중snapshot을 먼저 검증하며 실제 경쟁110회는 아직 미실행.

### 공통 기반 T004~T010

- `./gradlew test --tests '*ConcurrencyTestSupportTest'`: 종료1, 신규8/8 실패(빈 목록 반환·장벽 무동작·최초 원인 미보존 등 assertion 실패). 컴파일 성공 후 동작 실패를 확인했다.
- 실행/공통deadline/예외수집/장벽/원인보존/종료 실패 차단 구현 후 `./gradlew test`: 종료0,145건(기존137+지원8), 실패/오류/skip0.
- `./gradlew integrationTest --tests '*DatabaseSupportIT'`: 빈 snapshot 골격에서23건 중 신규4건 실패·종료1. 실제 DB에서 결과 집합 누락과 오류 미거부를 탐지했다. 단일LEFT JOIN 집계 구현 후23건·종료0.
- `./gradlew test integrationTest --tests '*ReservationConcurrencyIT'`: Unit145·부모setup fixture1·종료0. 보호 검사 생략 고의 변경은 fixture1건 실패·종료1로 탐지 후 원복했다. 실제 DataSource 무호출(reset0), 준비0, 제출0, 최초 원인 보존과 null-client cleanup을 검증했다.
- 최종 기반 게이트 `./gradlew test integrationTest --tests '*DatabaseSupportIT' --tests '*ReservationConcurrencyIT' --tests '*ReservationCreateIT'`: 종료0,Unit145·IT40(23+1+16), 실패/오류/skip0. 실행 지원은 최대10작업자, DB풀10/3000ms이며 10ms 연결 표본을 사용한다. 아직 업무 경쟁은 미실행.

### US1 T011~T014: C01

- `./gradlew integrationTest --tests '*ReservationConcurrencyIT.C01*'`: 종료0, 반복10/10. 매회 capacity10/요청100/작업자10,201×10·409/SOLD_OUT×90,total/confirmed10·available0. 모든 Future 수집/Executor 종료/서버0/HTTP 종료를 검사했다.
- 기존 제품 구현은 통과했다. ResourceRepository의 `available > 0`만 고의 제거: 동일 명령 종료1,10/10 실패. DB CHECK가 음수 차감을 거부하여 예상 품절90 대신500이 발생하는 것을 HTTP 분포가 탐지했다. 즉시 원복했고 제품 코드는 변경하지 않았다.
- 원복 후 `./gradlew integrationTest --tests '*ReservationConcurrencyIT.C01*' --tests '*ReservationCreateIT'`: 종료0,IT26(C01반복10+기존16), 실패/오류/skip0. 22초. 각 회차의 수량과 연결 표본은 최종 전체 인수 기록에 합친다. 표본은 정확한 연결 피크/운영 성능 증거가 아니다.

### US2 T015~T021: C02/C03/C04/C07/C08

- 각10회(총50회) 실제 HTTP·DB 초기 실행: 종료0. C02/03 동일값10건·false1/true9·1행·잔여0/9, C04 대상별 차감1·실제23505/uq_reservations_user_key·승자1행/잔여0·패자0행/잔여1, C07 차감0 뒤 활성 트랜잭션 성공 재조회, C08 두 차감1 뒤 UNIQUE·롤백 후 비트랜잭션 성공 재조회를 모두 관측했다. 최초 실제 빈 선조회 두 개만 트랜잭션 밖에서 기다린다.
- Service의 대상 UUID 비교만 고의 제거한 C04 실행: 종료1,10/10 실패(201이2개여서 단일 성공/충돌 계약 위반). 즉시 원복했다. 기존 제품 결함은 발견하지 않아 제품 변경 없음.
- 원복 후 위5종 각10회 + `IdempotencyIT`5 + `ReservationRollbackIT`7: `./gradlew integrationTest --tests '*ReservationConcurrencyIT.C02*' --tests '*ReservationConcurrencyIT.C03*' --tests '*ReservationConcurrencyIT.C04*' --tests '*ReservationConcurrencyIT.C07*' --tests '*ReservationConcurrencyIT.C08*' --tests '*IdempotencyIT' --tests '*ReservationRollbackIT'`, 종료0/IT62/실패·오류·skip0,27초. 저장UTC마이크로초·Location·JSON필드 일치와 실패키 복구 회귀 유지.

### US3 T022~T028: C05/C06/C09/C10

- 4종各10회 초기 실행 종료0/40건. C05 동일취소시각·복구호출1회·행유지, C06 두 허용결과와 품절시 같은 키 재시도, C09/C10 각10회는 두 요청 진입·비트랜잭션 대기·선행완료·해제·후행차감/복구 사건 순번을 단언했다. 순서 관측100%, 수량 불일치0.
- Writer의 `changed == 1` 복구 방어만 `changed >= 0`으로 고의 변경한 C05: 종료1/10회 중9실패·1통과. 이미취소됨 조기 반환만 실행된 회차는 이 고의 결함을 통과할 수 있다. 실패9회에서는 중복 복구가0건을 반환하여500 또는 복구호출 수 위반을 탐지했다. 성공 회차로 실패를 덮지 않았고 원복했다. 제품의 실제 결함은 발견하지 않았다.
- 원복 후 `./gradlew integrationTest --tests '*ReservationConcurrencyIT.C05*' --tests '*ReservationConcurrencyIT.C06*' --tests '*ReservationConcurrencyIT.C09*' --tests '*ReservationConcurrencyIT.C10*' --tests '*ReservationCancelIT' --tests '*ReservationOwnershipIT'`: 종료0/IT58(경쟁40+기존취소13/소유권5), 실패/오류/skip0,27초. 취소 후 생성재현·타인/ADMIN404도 기존회귀에서 유지했다.

### US4 T029~T034: C11·실패 증거

- 장애 주입 전 `./gradlew integrationTest --tests '*ReservationConcurrencyIT.C11*'`: 종료1/10회 모두 실패(예상500 대신201). 테스트/관측 지원 준비의 RED이며 제품 결함이라고 주장하지 않는다.
- 지정 키 첫 INSERT에만 IllegalStateException을 주입하고 실제 차감1·활성트랜잭션을 확인했다. 모든 요청 종료 후 실패키없음·정상키1·잔여1, 같은 실패키 재시도201 후confirmed2/잔여0을 검사했다. 기존 롤백 제품 코드는 그대로다.
- 추가 지원Unit은 작업 오류+다른작업 종료 실패가 겹칠 때 정리 예외가 원래 오류를 대체하는 문제를 탐지했다: `./gradlew test --tests '*ConcurrencyTestSupportTest'` 종료1/10중1실패. 최초 작업 오류에 종료 오류를 suppressed로 보존하도록 테스트 지원만 수정했다. 외부장벽 해제→취소/종료 순서 Unit도 추가했다.
- `./gradlew test integrationTest --tests '*ReservationConcurrencyIT.C11*' --tests '*ReservationConcurrencyIT.parentSetup*'`: 종료0/Unit147(기존137+지원10)/IT11(C11반복10+fixture1), 실패/오류/skip0,10초. 미관측 경로·timeout·종료 실패를 성공으로 누락하지 않는다. 회차 증거는 최종 인수 JSON에 연결한다.

### 지정 전체 경쟁 인수 (T035 일부)

`cd backend && ./gradlew test integrationTest --tests '*ReservationConcurrencyIT'`: 종료0,Unit147·일반IT111(경쟁110회+setup차단fixture1), 실패/오류/skip0,37초. 실제실행 listener와 XML 일치. 코드 기준373f95e, 날짜2026-10-20. 캐시통과 대신 test/integrationTest 모두 실행했다.

[전체 회차 증거](concurrency-runs.json)는 XML system-out에서110회 전부 추출했다. 요청/결과 수·상태분포·소요시간·작업자·풀/표본·최종snapshot·경로횟수·강제경로사건·정리결과를 포함한다. 원본 전체요청 경로는 재실행 XML에서 확인한다. 준비요청은 C05/06/09/10 각1건(기존확정 생성), 재시도는 C06 품절회차·C10·C11 각1건이며 경쟁결과 수와 별개다. C05 GET, 혼합사례 GET과 DB 조회도 준비/사후 확인이다. seed는 사용하지 않았다.

| 사례 | 반복 | 회차 시간 범위(ms) | active 연결 최대 표본 | 결과 |
|---|---:|---:|---:|---|
| C01 | 10 | 1001~1221 | 8 | 통과 |
| C02 | 10 | 111~366 | 10 | 통과 |
| C03 | 10 | 99~133 | 8 | 통과 |
| C04 | 10 | 71~80 | 2 | 통과 |
| C05 | 10 | 231~256 | 10 | 통과 |
| C06 | 10 | 282~290 | 2 | 통과 |
| C07 | 10 | 76~86 | 2 | 통과 |
| C08 | 10 | 73~82 | 2 | 통과 |
| C09 | 10 | 276~290 | 1 | 통과 |
| C10 | 10 | 350~365 | 1 | 통과 |
| C11 | 10 | 153~976 | 2 | 통과 |

시간은 준비·사후검증 포함 회차시간이며 성능 SLA가 아니다. 표본 간격10ms, 풀10/획득3000ms; 표본 사이의 진짜피크는 미관측이다. 실제 DB18.6(Debian18.6-1.pgdg12+2), READ COMMITTED/lock2s/statement5s/UTC를 검사했다. PostgreSQL 고정digest/Java/Gradle/Docker는 버전문서와 JSON에 기록했다. 동적DB·port와 localhost HTTP만 사용했고 Redis는 시작하지 않았다. Executor종료·서버Service in-flight0·HTTP종료는110/110 확인했다. 정상 사례의401/429/503/500·결과누락은0이며 C11의 주입500만 지정예상결과다.

### 최종 일반 회귀·문서 판정 (T035~T038)

`cd backend && ./gradlew verify`: 종료0,1분40초,Unit147·일반IT253, 실패/오류/skip0. listener/XML 일치. IT253은 기존138+DatabaseSupportIT추가4+경쟁110+fixture1이며 Unit147은 기존137+경쟁지원10이다. 기존 SQL·소유권·엄격입력·인증·순차장애·시각 회귀도 실행됐다. Contract/Property/PIT심화·k6·Redis요청제한·외부CI는 실행하지 않았다. Gradle10/configuration cache 및 ByteBuddy Unsafe 경고의 후속 호환성은 미검증이며 버전을 변경해 숨기지 않았다.

업무 제품코드·SQL·V1·OpenAPI는 시작6316e19와 동일하다. 본문/소유권/오류/성공재현 계약 보존을 대조했다. 테스트지원/경쟁IT·README/지식/실행안내를 연결하고 실제 통과 근거로만 작업/7단계 체크를 갱신했다. 확장훅 파일이 없어 before/after implement 등록훅은 없다. 외부병합·push·배포는 수행하지 않는다. 다음 한 작업은8단계 Redis 제한이다.


## 2026-10-21 최종 독립 리뷰 보완·완료

이전 검증147/253은2026-10-20 실행 기록이다. 아래 결과가 최종 수정본이며, 현재 [110회 JSON](concurrency-runs.json)은2026-10-21 재검증으로 갱신했다. 이전 JSON은2131539 커밋에 보존돼 있다. 테스트 소스 SHA-256도 현재 JSON에 기록했다.

### 발견과 RED→GREEN

- 종료 격리 Important: 실제 cleanup을 사용하는 별도 상태 fixture로 HTTP종료 전 reset, 이미 설정된 Executor종료 실패 무시, HTTP종료 실패 후 reset을 재현했다. `./gradlew integrationTest --tests '*ReservationConcurrencyIT.cleanupWaits*' --tests '*ReservationConcurrencyIT.executorTermination*' --tests '*ReservationConcurrencyIT.httpTermination*'`: 종료1/3건 모두 실패. Service진입 전 timeout 결과에서도 같은 문제를 확인한 `./gradlew integrationTest --tests '*ReservationConcurrencyIT.transportFailure*'`: 종료1/1실패.
- 다중 오류 보존 Important: A/B가 각각 실패하고 C가 종료되지 않는 지원Unit에서 두 번째 오류 유실을 확인했다. `./gradlew test --tests '*ConcurrencyTestSupportTest.retainsAll*'`: 종료1/1실패. 최초 오류를 유지하고 나머지 오류·timeout·정리오류·전체 결과를 suppressed/CollectedOutcomes로 보존한다. IT는 종료 예외에서도 전체 결과를 회수해 실패 회차에 기록한다.
- 경로 증거의 요청ID 겹침은 정확한 요청별 입증에 영향을 주므로 필수 수정으로 재분류했다. 다른 요청 또는 잘못된 트랜잭션의 lookup이 있으면 경로 미관측으로 실패해야 한다. `./gradlew test --tests '*ConcurrencyTestSupportTest.pathWitness*'`: 종료1/1실패 후 고유 호출ID/정확한 요청·순번·트랜잭션 매칭으로 수정했다. C03의 호출ID10개가 모두 고유함을 반복마다 검사한다.
- C07 보존 증거 누락도 회차 추적 요구에 따라 필수 보완했다. JSON에 실제 차감0 사건과 같은 요청ID·더 큰 순번의 활성 트랜잭션 성공lookup을 함께 저장하며10회 모두 추출 검사를 통과해야 한다.
- 첫 보완 후 `./gradlew test integrationTest --tests '*ReservationConcurrencyIT.cleanupWaits*' --tests '*ReservationConcurrencyIT.executorTermination*' --tests '*ReservationConcurrencyIT.httpTermination*' --tests '*ReservationConcurrencyIT.transportFailure*'`: 종료0/Unit148·지원IT4. 이후 경로 보완을 포함한 `./gradlew test integrationTest --tests '*ReservationConcurrencyIT'`: 종료0/Unit149·IT115(경쟁110+지원fixture5),40초.

### 1차 리뷰 보완 전체 회귀

`cd backend && ./gradlew verify`: 종료0,1분53초. XML/listener 모두 Unit149·일반IT257, 실패/오류/skip0. Unit149=기존137+경쟁지원12, IT257=기존138+DB지원추가4+경쟁110+setup/정리지원fixture5. 요청 오류가 있어도 다음 회차를 reset하는 대신 같은 최초원인으로 실패시킨다. 기존 부모setup의 첫 보호 검사와 DataSource무호출 검증으로 DB reset0을 확인한다. 준비/제출0과 null-client 정리는 기존fixture를 유지한다.

정리 순서는 장벽해제→작업종료→Service in-flight0/표본수집종료/HTTP종료→차단상태없음확인→Spy초기화다. Service 이전 서버 처리는 계수 범위 밖이므로 HTTP전송 실패·timeout 등 결과예외는 보수적으로 차단한다. 운영 서버 전체 요청 추적을 구현했다고 주장하지 않는다. 불확실한 요청이 없는 정상110회는 결과수집·종료·상태·수량 검사가 모두 통과했다.

아래 표와 현재 JSON은 checked예외 보완 후 최종 재실행의110회다.

| 사례 | 반복 | 회차 시간 범위(ms) | active 최대 표본 | 판정 |
|---|---:|---:|---:|---|
| C01 | 10 | 956~1518 | 9 | 통과 |
| C02 | 10 | 106~275 | 8 | 통과 |
| C03 | 10 | 98~119 | 8 | 통과 |
| C04 | 10 | 77~80 | 2 | 통과 |
| C05 | 10 | 237~296 | 9 | 통과 |
| C06 | 10 | 279~294 | 2 | 통과 |
| C07 | 10 | 73~80 | 2 | 통과 |
| C08 | 10 | 78~82 | 2 | 통과 |
| C09 | 10 | 284~342 | 1 | 통과 |
| C10 | 10 | 350~387 | 1 | 통과 |
| C11 | 10 | 160~171 | 2 | 통과 |

전체 리뷰는 최초 사용량 제한 뒤 같은 리뷰어가 이어서 완료했다. 알려진4개 지적을 한 수정 pass로 처리했고 재리뷰는 실행하지 않았다. 미룬Minor는 없다. 업무제품·스키마·OpenAPI 변경0이며 외부병합·push·배포도 없다. 판단 유보인 별도제품보안감사·Redis·자동계약·Property/PIT/k6·외부CI/배포·전체스케줄·정확한피크·운영성능·JUnit내부예외결합표현은 이번 완료주장에 포함하지 않는다. 다음 한 작업은8단계 Redis 요청 제한이다.


### checked 예외 보완 후 최종 완료 검증

같은 다중 오류 보존 지적의 checked예외 경계를 추가 점검했다. 최초 작업이IOException으로 실패하고 종료 실패가 겹치면 원인은 래퍼의 cause에 보존되지만, 호출자가 받은 래퍼의 직접suppressed에 결과 묶음이 없었다. `./gradlew test --tests '*ConcurrencyTestSupportTest.checkedTask*'`: 종료1/1실패. 원인 체인을 유지하고 래퍼에도 CollectedOutcomes를 연결하여 IT의 결과 회수가 가능하도록 보완했다.

최종 `cd backend && ./gradlew verify`: 종료0,1분48초,Unit150·일반IT257, 실패/오류/skip0. Unit150=기존137+경쟁지원13이며 IT257=기존138+DB지원추가4+경쟁110+지원fixture5다. 현재 JSON은 이 실행의110회와 테스트 소스SHA를 담고 있다. 38/38작업·링크·코드블록·C07 같은요청 차감0/tx재조회10회·민감헤더 미포함·제품/계약diff0·after_implement 등록훅없음을 확인했다. 이후 변경은 문서·기록 동기화뿐이다.


## 2026-10-21 develop 대상 PR 게시 전 검증

- 사용자 지시: 현재 개발 내용을develop에 병합하기 위한PR 생성. 대상은origin/develop(3cfabe7)이며 기본브랜치main을 대상으로 삼지 않았다.
- `git fetch origin` 뒤 작업브랜치에 `git merge origin/develop --no-edit`를 수행했다. 충돌 없이52ff9e2를 생성했으며 유입된 차이는qodana.yaml1개다. 별도fix/qodana-findings의 미커밋/미병합 변경은 포함하지 않았다.
- 병합본에서 `cd backend && ./gradlew verify build`: 종료0,2분7초. Unit150·일반IT257, 실패/오류/skip0을listener/XML로 대조했다. bootJar/assemble/check/build도통과했다. 테스트소스SHA·110회기록·38작업완료는유지된다.
- 새읽기전용pre-landing/적대적리뷰는52ff9e2 기준 새P1차단문제없음. STOP·HTTP종료후reset·다중오류/checked래퍼·고유ID·C07기록을재확인했다. 비차단후속: 전송오류차단은경쟁Future결과에적용되며, 준비/사후재시도의직접HTTP호출까지공통경계보호를넓히는것은추가보완여지다. 현재실행에서해당문제가관측됐다는증거는없다.
- 제품코드·V1·OpenAPI·의존성·앱버전의origin/develop대비변경0. 이작업은테스트/문서PR이며별도릴리스VERSION/CHANGELOG체계를새로도입하지않았다. 외부CI성공이나develop병합/배포성공을주장하지않는다.

## 2026-10-20 / Qodana 지적 수정

- 기준 HEAD `3cfabe7`, 보고 revision `3b26a2517e8111ee28d3af0e9fd90873d1e81b89`, 실제 생성 브랜치 `fix/qodana-findings`. 기존 사용자 Wrapper 변경(9.8.0)은 보존·커밋 제외한다.
- 환경: Corretto 25.0.4+7-LTS, Gradle 9.8.0(사용자 기존 변경 적용), Docker Engine 29.8.2. 정확한 작업 시간은 미계측. 분류·근거·담당은 [Qodana 검토](qodana-review.md).
- SQL null RED: `./gradlew test --tests com.limitedbooking.backend.common.ApiExceptionHandlerTest` 종료1, 6건 중 null SQLException 보존 사례 1실패, skip0. 기존 코드는 원래 예외 대신 NPE를 던졌다. 최소 null guard 후 GREEN 확인.
- 최초 통합 명령: `./gradlew test integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT' --tests '*ResourceRepositoryIT' --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT' --tests '*ApiExceptionHandlerTest'`. 종료1. test 태스크는 Unit143건 전체 통과. integrationTest 36건 중23통과/13실패/skip0. Security6·ResourceApi8·ResourceRepository4·Ownership5 통과. Cancel13은 Spring 컨텍스트 기동의 Flyway PostgreSQL 연결에서 SocketTimeoutException(Read timed out) 발생. 후속 메서드들은 컨텍스트 실패 임계값으로 실패했다. 기능 assertion 실패와 구분하며 초기 실패를 숨기지 않는다.
- 의존성 해석: testRuntimeClasspath/runtimeClasspath 전체 dependencies 및 jackson-databind/rhino dependencyInsight 각 종료0. 경고5좌표는 테스트 경로에만 존재한다. 변경 후 Jackson2 core/databind/dataformat/datatype2.21.8·annotations2.21, Rhino1.7.15.1 선택 확인. runtimeClasspath는 기존 tools.jackson3.1.5 유지.
- `./gradlew contractTest bootJar` 종료0. Contract 도구 프로브3건 실패/오류/skip0, bootJar 진입점 자동 탐지·패키징 성공. 업무 API 전체 계약 검사나 CVE 공격 재현은 아니다.
- 의존성 변경 후 관련 재검증: `./gradlew test integrationTest --tests '*ReservationCancelIT'` 종료0, Unit143·CancelIT13건 실패/오류/skip0. 초기 연결 실패 뒤 의존성 변경이 있어 재검증했고, timeout/기대값/테스트 코드는 완화하지 않았다. 재실행 통과가 최초 Docker/JDBC 연결 실패의 원인 규명이나 재발 방지를 뜻하지 않는다. 다른23 IT는 의존성 변경 전 통과 기록이다.
- 추가 설정 메타데이터 JSON: String 프로퍼티3개 확인. 실제 비밀번호·기본값 없음. 소스 독립 리뷰: 요구 적합성/코드 품질에서 수정 필수 결함 없음. 최종 결과는 루트 실행 증거로 보완했다.
- 미검증/미해결: Qodana CLI가 설치되지 않아 재검사 미실행, 경고 소멸 확정 불가. 전체 verify/Property/PIT/부하/외부CI 미실행. json-patch1.13·jackson-coreutils2.0은 공식 수정 버전·간접 취약 함수 도달성 미확정. main public은 Boot 탐지 호환성 때문에 유지. 다음 한 작업: Qodana 재검사로 잔여 지적을 확인하고 미해결 계약 검증기 의존성 대체를 별도 검증 대상으로 정한다.


## 2026-10-20 / 사용자 제공 Qodana 재검사 후속

- 사용자 보고 revision80e1fa0: 이전 수정 대상15건은 problems 목록에서 사라지고 기존 검토3건 및 새 LocalIdentityResolver 상수 조건1건이 보고됐다. 에이전트 직접 Qodana 실행과 구분한다.
- LocalUserPrincipal의 null 불허 계약에 맞춰 resolver의 userId null 중복 조건만 제거했다. 인증 객체 null·미인증·Principal 타입 거부와 UUID 추출은 보존했다. 동작 보존 정리이므로 신규 기능 RED 테스트 대신 기존 보안·소유권 통합 검증을 사용했다.
- backend에서 `./gradlew integrationTest --tests '*SecurityIT' --tests '*ReservationOwnershipIT'` 종료0, 총11건(Security6·Ownership5), 실패/오류/skip0, 11초. 고정 UUID 추출·null/외부 Principal 거부·실제 HTTP 인증/권한·소유권을 검증했다.
- 기존 fix/qodana-findings에서 수행, 사용자 Wrapper 변경 보존. 관련 변경 diff 검토·공백 검사 수행. 전체 verify/Unit/부하/CVE 재현/Qodana 직접 실행은 이번 후속에서 미실행. 남은 main public 유지와 의존성2개 미해결은 [Qodana 검토](qodana-review.md)를 따른다. 다음 한 작업은 후속 커밋 Qodana 재검사다.


## 2026-10-20 / 잔여 Qodana3건 보안 수정 검증

- 기준b4a40fd, 기존 fix/qodana-findings 브랜치. 사용자 Wrapper 변경 보존·제외. 기존 Corretto25.0.4/사용자 Gradle9.8.0 환경.
- 사용자 보고의 json-patch1.13 및 jackson-coreutils2.0은 기존 테스트 의존성 그래프에 존재했다. 고정 명세/classpath YAML과 테스트 localhost HTTP 응답이 검증기 입력이다. 운영 공격 가능성을 입증한 것은 아니며 취약 모듈 제거를 보안 불변식으로 선택했다.
- RED: `./gradlew verifyContractDependencies` 종료1, testCompileClasspath에서 두 취약 좌표를 실제 검출했다. 컴파일 오류나 문자열 소스 검사에 의한 실패가 아니다.
- 공식 검색·배포 확인 후 core3.0.0/networknt로 전환 및 Swagger2 변환기 제외. `./gradlew verifyContractDependencies contractTest bootJar test` 종료0, 기존 Contract3·Unit143 실패/오류/skip0, bootJar main 자동 탐지·패키징 성공. 모든 Test는 동일 테스트 runtime을 사용한다.
- 엔진 교체 호환성 보강: 실제 HTTP 응답의 잘못된 UUID/숫자 id/추가필드3건 추가. `./gradlew contractTest verifyContractDependencies` 종료0, Contract6건 실패/오류/skip0(정상·필수 id 누락·UUID 형식·타입·미정의필드·고정 업무 명세 참조 해석). 제품 API에 테스트 후크를 추가하지 않았다.
- testRuntimeClasspath/runtimeClasspath 전체 dependencies 명령 각 종료0. graph 검사에서 json-patch/jackson-coreutils/Rhino 없음 확인, core3.0.0/networknt2.0.1/swagger-parser-v32.1.41 확인. verifyContractDependencies가 testCompileClasspath도 함께 검사한다. 원래 CVE의 strongest substitute는 취약 구현이 해석된 구성에 존재하지 않음을 확인하는 이 검사다. 공격성 무한자원 payload 실행은 불필요하여 수행하지 않았다.
- main은 public 유지·단일 UnnecessaryModifier만 억제하여 실행 호환성 보존. Qodana 보안 경고 제외/기준선/품질 게이트 완화 없음. 이 문법 지적을 보안 수정으로 보고하지 않는다.
- 미실행: Qodana 자체 재검사, 전체 verify/DB·Redis IT/Property/PIT/부하/외부CI. 업무 자동 HTTP 계약 전체로 확대하지 않는다. 최종 독립 후보 리뷰 결과는 아래 후속 기록을 따른다. 다음 한 작업은 이번 커밋 Qodana 재검사다.

최종 독립 후보 리뷰: 읽기 전용 에이전트가 배포 POM/JAR·실제 호출·계약 테스트·Gradle 연결을 검토했고 구체적인 surviving bypass/회귀 결함을 발견하지 못했다. 리뷰어는 Gradle/Qodana를 실행하지 않았으며 테스트 결과는 위 루트 실행 기록을 근거로 한다. git diff --check 통과.


## 2026-10-21 / PR #8 적대적 리뷰·병합 전 검증

- PR https://github.com/joypeb/limited-booking-test/pull/8, base develop3cfabe7, 최초 head3421ea3. 사용자가 Qodana 재검사 완료와 PR 생성·리뷰·수정 후 병합을 지시했다. 자체 Qodana 실행 결과로 바꾸어 기록하지 않는다.
- 기존 Wrapper9.8.0 변경을 제외하고 별도 detached worktree에서 커밋된 Gradle9.7.1/Corretto25.0.4로 검증했다. `./gradlew verifyContract bootJar` 종료0, Unit143·일반IT138·Contract6 실패/오류/skip0, bootJar 성공(1분36초). 개발 DB가 아닌 기존 Testcontainers 격리 환경을 사용했다.
- 독립 적대적 서브에이전트 및 별도 Codex CLI 읽기전용 리뷰에서 코드 호출·null계약·SQL오류·검증기 교체의 재현 가능한 제품 회귀는 발견하지 못했다. 서브에이전트가 제시한 미해결 의존성 그래프 가설은 루트가 추가 실험했다.
- 발견P2: allComponents만 검사하는 gate는 unresolved edge를 놓쳐 단독 실행에서 성공할 수 있었다. 임시 Gradle init script로 testImplementation에 `com.github.java-json-tools:json-patch:0.0.0-qodana-missing`을 afterEvaluate에서 주입하고 `./gradlew --offline -I <임시init파일> verifyContractDependencies` 실행: 수정 전 종료0으로 잘못 통과했다. contractTest 자체의 classpath 해석은 별도로 실패하므로 실제 계약검사 우회와 구분한다.
- 최소 수정:3개 구성의 resolution.allDependencies에서 UnresolvedDependencyResult를 검사하고 하나라도 있으면 실패한다. 동일 주입 재실행은 종료1 및 `의존성 해석 실패`로 기대대로 거부했다. 새 보안 검사는 발견된 상태를 fail closed 처리하며 정상 버전을 임의 낮추거나 테스트 skip하지 않았다.
- 다른 실패 경로: 동일 init script의 버전을1.13으로 바꾸어 재유입시키면 종료1 및 두 취약 모듈 좌표가 포함된 `취약 계약 검증 의존성 재유입`으로 거부했다. 모든 장애 주입은 /tmp init script에만 두었고 저장소 의존성이나 최종 작업트리에 남기지 않았다.
- 정상 대조: `./gradlew verifyContractDependencies contractTest bootJar` 종료0, Contract6건 실패/오류/skip0. 애플리케이션 코드 변경이 없는 gate 수정이므로 통과한 DB138건/Unit143건은 불필요하게 다시 실행하지 않았다. 이후 수정 범위 독립 재리뷰 결과는 PR 최종 댓글을 따른다.
- 미검증: Property/PIT/부하/외부CI. 저장소에는 외부 status check가 구성되지 않았으며 로컬 검증과 혼동하지 않는다. 사용자 기존 Wrapper는 계속 별도로 보존한다.


## 2026-10-21 PR 게시 직전 develop 추가 갱신과 미해결 실행 실패

게시 직전origin/develop이3cfabe7에서312b2a7(PR8,Qodana보완)로전진했다. 그대로게시하지않고51aa964로추가병합했다. README·decisions·test-evidence·versions·security의충돌5개는양쪽독립추가절을모두보존했다. 경쟁테스트/지원코드와그소스SHA는변경하지않았다. 제품/스키마/의존성은새develop과같으며, 이PR이그변경을새로도입한것은아니다.

- 최신통합본 `cd backend && ./gradlew verifyContract build`: 종료1,1분49초. Unit156/156통과,일반IT257중256통과/1실패,오류/skip0. 실패는기존BackendApplicationIT.contextLoadsAndHealthRespondsOverHttp의Flyway DB연결/앱기동이며 HTTP기능assertion에진입하지못했다. 경쟁115건(110회+fixture5)은모두통과했다.
- 확인한경계:컨테이너기동로그뒤Hikari/Flyway의pgJDBC42.7.13 ConnectionFactoryImpl.enableSSL:717에서SQLSTATE08P01과“SSL 연결을 설정하는 동안 오류가 발생했습니다.”가발생했다. 로컬캐시sources.jar를대조하면SSL협상응답이N/E/S가아닌분기다. 실제응답바이트·전달경로및근본원인은관측되지않아Docker문제나코드회귀로단정하지않는다. 과거별도Qodana실행의Read timed out과도같은원인이라고주장하지않는다.
- BackendApplicationIT/ContainerTestSupport/application.yaml은직전통과본과동일하며, 실패를skip·기대값완화·timeout/버전변경·반복통과로숨기지않았다. 최초실패는이기록에유지한다.
- 실패로미실행된계약/보안/패키징범위를별도수행했다: `./backend/gradlew -p backend verifyContractDependencies contractTest bootJar` 종료0,Contract6/6실패오류skip0·보안의존성게이트통과·패키징성공. 이것을전체verifyContract/build성공으로표현하지않는다.
- PR은이상태를명시한draft로게시하고develop병합은수행하지않는다. 최신기준의앱기동실패는미해결이며후속조사대상이다. 이전3cfabe7대상의verify build150/257통과와현재312b2a7대상의실패를구분한다.


### PR 게시 결과

사용자 지시로 [PR9](https://github.com/joypeb/limited-booking-test/pull/9)를 develop 대상으로 draft 게시했다. head는 docs/reservation-concurrency-spec이며 최신기준의 기존앱기동IT1실패·근본원인미확정과 Unit156/Contract6/경쟁115통과를 본문에 구분했다. develop 병합·배포·외부workflow수동실행은 수행하지 않았다.


## 2026-10-21 / 기존 앱 기동 실패의 제한된 진단

- 대상은 PR9 head `df683d4`이며 작업 소스·의존성·SSL 설정·timeout은 변경하지 않았다. 개발/검증 및 환경/기록 규칙, 기존 기동 IT와 컨테이너 지원 코드를 대조했다. 이전 전체 검증의 `08P01` 실패는 위 기록대로 미해결이다.
- pgJDBC42.7.13의 `ConnectionFactoryImpl.enableSSL`은 응답 N/E/S 외 바이트에서 해당 오류를 던진다. `PGStream.receiveChar`는 EOF를 별도 예외로 처리하므로 이 기록을 단순 EOF/연결 timeout이라고 해석할 근거도 없다. 공식 소스: https://github.com/pgjdbc/pgjdbc/blob/REL42.7.13/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java
- 임시 `/tmp/limited-booking-ssl-diagnostic/SslProbeSocketFactory.java`는 표준 Socket을 사용하면서 각 연결의 첫 서버 바이트만 출력한다. 비밀번호·후속 인증 payload는 기록하지 않았다. 외부 Gradle init script로 진단 클래스와 Hikari `data-source-properties.socketFactory`만 주입했다. 첫 시도는 프로젝트 설정에 classpath 추가가 덮여 진단 클래스 ClassNotFound/08006으로 종료1이었다. 원래08P01 재현으로 세지 않는다. 주입 시점을 projectsEvaluated로 바로잡고 한 번 진단 실행했다.
- `cd backend && ./gradlew -I /tmp/limited-booking-ssl-diagnostic/init.gradle integrationTest --tests com.limitedbooking.backend.BackendApplicationIT`: 종료0, 6초, IT1건 실패/오류/skip0. 첫 서버 바이트 관측은 모두78(N)이었고 앱 기동과 HTTP health assertion을 통과했다. 로그와 관측 XML은 같은 임시 디렉터리에 보관한다. 진단 Socket 사용은 관측 조건 차이이므로 원래 환경의 무조건적인 정상성을 증명하지 않는다.
- 한계: 원래 실패 당시 바이트·서버 로그를 소급 확보하지 못했다. 기존 integrationTest XML 출력은 지정 실행 결과로 갱신되었으며, 전체257건 실행 증거로 재사용하지 않는다. 최초 전체 실패 명령 로그 `/tmp/limited-booking-pr-final-verify.log`와 위 실행 기록은 유지한다. 임시 경로는 장기 보관 아티팩트가 아니다.
- 결론: 지정 진단에서는 재현되지 않았으며 근본 원인은 여전히 미확정이다. SSL 비활성화·재시도 추가·timeout 증가는 적용하지 않았다. 전체 verifyContract/build를 다시 통과했다고 주장하지 않으며 PR9는 draft를 유지한다. 다음 조사는 실패 시점 SSL 응답 바이트와 해당 컨테이너 PostgreSQL 로그의 동시 확보다.


## 2026-10-21 / 테스트 소유 PostgreSQL의 SSL 협상 경로 제거

- 작업 기준 `a50ffc6`(develop312b2a7 통합). 사용자 요청에 따라 연결 실패를 추가 조사하고 테스트 환경에 한정한 대응을 적용했다. Java25.0.4·Gradle9.7.1·Boot4.1.1·pgJDBC42.7.13·Testcontainers2.0.5·PostgreSQL18.6 고정digest·Docker29.8.2를 유지했다. 개발 Compose DB/volume과 사용자 Wrapper 변경은 건드리지 않았다.
- 실행 계획: (1) 협상 바이트·새 컨테이너 초기 응답 관측, (2) 테스트 fixture의 비TLS 모드 명시와 실제 DB 회귀 확인, (3) 관측 도구 없는 전체 검증·독립 리뷰·기록·PR 반영. 제품 계약을 바꾸지 않으며 이번 대응과 원인 규명을 구분한다.
- 수정 전 진단: `cd backend && ./gradlew -I /tmp/limited-booking-ssl-diagnostic/init.gradle verifyContract build` 종료0,1분45초. Unit156·일반IT257·Contract6 실패/오류/skip0, 보안 의존성 게이트·build 통과. 관측 SocketFactory는 외부 init script로 Hikari에만 주입했고 BackendApplicationIT의 각 연결 첫서버바이트10건은 모두78(N)이었다. 정상 실행은 최초08P01 원인 해결의 근거가 아니다.
- 초기 연결 분리: 임시 ColdStartProbe로 동일 고정 이미지의 독립 Testcontainers5개를 순차 생성하고 각50연결/작업자10개에서 SSLRequest(길이8/코드80877103)를 전송했다. 요청250개 전부78(N), 각 `SHOW ssl`은 off였다. Socket 연결/읽기3초·Future10초 상한을 사용하고 각 시험의 컨테이너만 종료했다. 컨테이너 로그는 `/tmp/limited-booking-ssl-diagnostic/cold-postgres-0.log`~`cold-postgres-4.log`에 보관했다. 실패나 비정상 바이트는 재현되지 않았다.
- 확인된 구성: 서버는 TLS 인증서 없는 ssl=off fixture인데 JDBC URL에는 sslmode가 없어 드라이버 기본 prefer의 SSLRequest→N을 거쳐 평문으로 연결했다. 최초 비정상 바이트 발생원은 여전히 미확정이다. 실패 감추기용 재시도·skip·timeout 증가·버전 변경은 적용하지 않았다.
- RED: DatabaseSupportIT.connectsToOwnedPostgresWithoutStartingRedis에 URL의 명시disable과 실제 DB의 서버ssl=off/연결ssl=false 검사를 추가했다. `./backend/gradlew -p backend integrationTest --tests 'com.limitedbooking.backend.support.DatabaseSupportIT.connectsToOwnedPostgresWithoutStartingRedis'` 종료1,7초,1건실패/오류skip0. 실패는 `expected disable but was null`(기존 URL에 모드 없음)이다. 원래08P01을 재현한 테스트로 해석하지 않는다. 최초 루트의 잘못된 `./gradlew` 명령은 파일 없음/종료127이었으며 RED로 세지 않았다.
- 최소 변경: ContainerTestSupport의 PostgreSQL URL에 withUrlParam(sslmode,disable)만 추가했다. 컨테이너 준비 확인·직접 JDBC·Flyway·Spring/Hikari가 동일 URL을 쓴다. 기존 앱 기본/local 설정·제품 코드·스키마·OpenAPI는 유지했다. TLS가 원래 없던 로컬 테스트에만 적용하며 외부 DB에 이 설정을 일반화하지 않는다.
- GREEN: 같은 외부 관측 init script로 위 DB지원1건과 BackendApplicationIT1건을 지정 실행했다. 종료0,10초,2건실패/오류/skip0. 실제 current_database는 소유DB, current_setting(ssl)=off, pg_stat_ssl의 해당pid ssl=false(non-null)를 확인했다. 앱 기동/health200·UP·details비노출도 통과했다. 앱 연결의 첫서버바이트10건은 모두82(R,Authentication)로 바뀌어 SSLRequest 경로를 건너뛰는 것을 확인했다.
- 진단 소스·로그·원래 결과 사본은 `/tmp/limited-booking-ssl-diagnostic/` 아래이며 장기 보관은 보장하지 않는다. 최종 전체 검증은 외부 init script와 관측 SocketFactory 없이 실행한다.
- 최종 정상 경로: `cd backend && ./gradlew verifyContract build`(실제 실행은 동등한 루트의 `./backend/gradlew -p backend verifyContract build`), 종료0,1분45초. Unit156·일반IT257·Contract6 전부 실패/오류/skip0, listener/XML 수 일치, verifyContractDependencies와build 통과. BackendApplicationIT XML에 관측 SocketFactory 출력이 없어 외부 도구 없이 실행한 결과임을 대조했다. 경쟁115건을 포함하며 Property/PIT/k6/외부CI는 이번에 실행하지 않았다. 소유 컨테이너 종료 후 원래 개발 Compose2개만 남았다.
- 결론: 테스트 환경의 SSL 협상 경로 제거와 현재 전체 검증은 확인했다. 최초 비정상 응답의 발생원은 미확정으로 보존하며 근본 원인 해결·운영 TLS 검증으로 주장하지 않는다. PR9에 대응·검증·한계를 반영하고 draft는 유지한다.
- 최종 독립 리뷰: 읽기 전용 리뷰어가 a50ffc6 대비 최종 diff·고정 Testcontainers/pgJDBC 소스·저장된 N→R 관측·현재 XML을 대조했고 수정이 필요한 결함을 발견하지 못했다. 리뷰어는 파일 수정·Gradle 실행을 하지 않았다. 적용 판단은 테스트 소유 비TLS fixture의 연결 설정 명시뿐이며, 최초 비정상 응답 발생원이 다른 경로에도 영향을 주면 향후 연결 실패가 계속 발생할 수 있다. 미룬 경미한 지적은 없다. git diff --check 통과.


## 2026-10-22 8단계 구현 시작 — T001~T003

- 공식 speckit-implement 사전조건 FEATURE_DIR=specs/009-redis-rate-limit; requirements16/16 PASS, 확장 hooks 파일 없음.
- root=/Users/joypeb/.paseo/worktrees/3968wubl/lucky-meerkat, branch=feature/redis-rate-limit, 시작HEAD=3ca6f82, 기존 변경 없음.
- AGENTS·knowledge README/rules전체·관련4도메인·계획2~5/8단계·헌법·009설계와 실제 SecurityConfig/ContainerTestSupport/예약fixture/Gradle 대조. 기존 업무SQL/Writer·본문필터·공통오류·경쟁실행기 재사용.
- java -version: Corretto25.0.4+7-LTS, ./gradlew --version:9.7.1, docker version:29.8.2(권한상승 실행), 종료0. Boot4.1.1·고정PG18.6/Redis8.10.2 digest·BOM Testcontainers2.0.5 유지, 실제 기능실행은 후속.
- qodana.yaml JVM2026.2/Java25/recommended/any0/라이선스 기준 유지. CLI·이미지·QODANA_TOKEN 미확보(인계 정보 및 CLI 미발견), 실제 정적검사 미실행. T037 필수 미완료, 오케스트레이터 확인 대기. 토큰값 출력 안 함.
- .gitignore/.dockerignore 기존 Java/build/cache/비밀정보 제외 확인; 신규 의존성·기술별 ignore 불필요.

### US1 중간 인수 — T007~T013 (2026-10-22)

최신 오케스트레이터 지시: US1까지 검증·커밋 후 중간검토 대기. T004~T006은 필요한 최소 환경만 준비했다. ContainerTestSupport 고정 PG/Redis의 동적 포트·소유 컨테이너를 재사용했고 RateLimitTestSupport는 ADMIN/A/B 키만 DEL/count/PTTL한다. FLUSHALL 없음. RedisRateLimitIT는 앱 YAML을 읽는 최소 실제 Boot Redis 컨텍스트이며 PG 업무/Basic HTTP 검증이 아니다. HTTP·Controller/Service/TX 관측은 T006 후속으로 남긴다.

| 순서 | backend/ 실제 명령 | 종료 | XML/listener 결과·의도 |
| --- | --- | --- | --- |
| 판정 RED | `./gradlew test --tests '*RateLimitDecisionTest'` | 1 | 9건/실패8/오류0/skip0. 상수 허용 골격이 TTL 올림7경계와 깨진응답 거부 assertion 실패 |
| 판정 GREEN | 같은 명령 | 0 | 9건/실패·오류·skip0 |
| 설정 RED | `./gradlew test --tests '*RateLimitPropertiesTest'` | 1 | 3건/실패1/오류0/skip0. 검증 없는 record가 비양수·불가능한 창을 거부하지 않음 |
| 설정 GREEN | `./gradlew test --tests '*RateLimit*Test'` | 0 | 12건/실패·오류·skip0 |
| 실제 Redis RED | `./gradlew integrationTest --tests '*RedisRateLimitIT'` | 1 | 3건/실패3/오류0/skip0. 상수 허용 limiter로 정확20 대신40허용, PTTL -2, 격리카운트0 확인 |
| Lua GREEN+만료 확장 | 같은 Redis 지정명령 | 0 | 4건/실패·오류·skip0 |
| 컨텍스트/기존 경쟁지원 정리 | `./gradlew test integrationTest --tests '*RedisRateLimitIT'` | 0 | 전체 일반 Unit168 + 지정 RedisIT4, 실패·오류·skip0 |
| 최종 US1 인수 | `./gradlew test integrationTest --tests '*RedisRateLimitIT'` | 0 | 전체 일반 Unit169 + 지정 RedisIT5, 실패·오류·skip0. 신규 판정10·설정3/기존Unit156. RedisIT에 시작시 잘못된 설정 거부 추가 |

- 최종 XML: `backend/build/test-results/test/TEST-*.xml` 합계169/0/0/0, `integrationTest/TEST-com.limitedbooking.backend.ratelimit.RedisRateLimitIT.xml` 5/0/0/0. listener와 일치. 이전 기록 Unit150/IT257을 복사하지 않았다.
- 기본20/60000ms 실제 count22(21·22거부), 최초PTTL59999→59925ms(40ms 간격 후 후속20/21/22호출), 판정 Retry-After60초. 실제 후속 TTL이 짧아졌으며 요청마다 연장하지 않는다.
- 기존 ConcurrencyTestSupport 사용: 40요청·작업자10·전체30초·종료10초, 허용20/거부20/count40/PTTL59985ms, 결과40·예외0·terminated=true. StopState 첫 검사 후에만 다음 소유키 reset 가능. HTTP 경쟁40/기존110회는 이번에 실행하지 않았다.
- 만료 전용 독립 ApplicationContext:3/1000ms, 최초PTTL1000ms,60ms 간격 뒤 허용/거부 모두 최초TTL보다30ms 이상 감소 assertion 통과;20ms polling/최대5초로 만료 확인 뒤 count1·PTTL993ms. 기본 컨텍스트20/60000ms는 별도로 유지했다. 판정PTTL과 후속측정의 경과를 구분하며 정밀한 벽시계 만료 시각을 보장한다고 해석하지 않는다.
- 실제 YAML 기본값 바인딩20/60000ms·Lettuce factory 명령/SocketOptions 연결 timeout 각각200ms 확인. 설정0한도/0ms/1ns는 컨텍스트 refresh에서 거부된다. 단위 경계는 Long.MAX_VALUE·음수·null·밀리초 소수/overflow를 포함한다.
- Lua 연결 과정의 DefaultRedisScript 생성자 시도2회는 컴파일 실패였다. 설치 API의 setLocation/setResultType로 수정했으며 컴파일 실패를 기능 RED로 세지 않았다. 기능 RED는 그 전에 실제 Redis assertion3건으로 확인했다.
- 최종 RedisIT 실행 시간3.834초, Gradle7초. Spring Data Redis4.1.1·Lettuce7.5.2.RELEASE/5728917·실제 Redis서버8.10.2 출력 확인. `./gradlew dependencies --configuration runtimeClasspath` 종료0으로 Framework7.0.9/Security7.1.1/SDR4.1.1/Lettuce7.5.2.RELEASE 해석 확인.
- afterAll은 컨텍스트 close와 컨테이너 close를 모두 시도하고 정리오류를 보존한다. 이 실행 정리오류0.
- 업무 SQL/스키마·SecurityConfig·고정 OpenAPI429/503/Retry-After는 변경하지 않았다. 필터 미연결이라 실제 예약 HTTP에는 아직 제한이 적용되지 않는다.
- 미완료: T004~T006 전체기반 인수, T014~T038. Qodana T037 인증/정식 실행환경 미확보. build+verify+verifyContractDependencies 최종게이트·실제 장애·HTTP순서·기존110회·전체회귀는 후속이며 이번 통과로 표시하지 않는다. docs/concurrency-runs.json은 기존 실행기록을 유지했다.
- 다음 한 작업: 오케스트레이터의 US1 독립검토 후 지시를 받아 US2 실제 HTTP RED(T014~T016)를 작성한다.


### 2026-10-22 US2 T014~T016 RED

US1 100d392 오케스트레이터 독립검토 통과 후 동일 담당이 재개했다. 공식 사전조건 재확인·checklist16/16·작업트리깨끗함·hooks없음. 승인설계/knowledge/기존SecurityConfig·Controller·SQL과 대조해 실제RANDOM_PORT/Basic·PG/Redis·요청별ID·실제limiter/Controller/Service spy·transaction listener·전체행 단일SELECT snapshot 지원을 작성했다. 설정기본20/60000ms·명령/연결200ms/풀10·획득3000ms를 검사한다.

`./gradlew integrationTest --tests '*RateLimitApiIT'` 종료1, 31건/실패21/오류0/skip0. 필터부재로 limiter-start 기대1/실제0 및 count0을 실제HTTP에서 확인했다. 미인증·413/독립API 사례10건은 기존동작으로 통과. 앞선 관측필터 FilterRegistrationBean 제네릭 컴파일실패1회는 기능RED로 세지 않는다. US3장애·US4기존시험전환은 수행하지 않는다.

### US2 T004~T006/T014~T020 GREEN·중간 인수 (2026-10-22)

- 순서: 필터부재31건/실패21 RED→Security 체인 최소구현→같은 명령31건 GREEN→재전송/중복취소/취소후재현·실제HTTP40건·체인소유/순서·후속500 추가35건 GREEN→실제ERROR dispatch/framing/스크립트관측·userId 입력거부·UTF-8 바이트 상한 보강37건 GREEN. 각 GREEN 명령은 `backend/`의 `./gradlew integrationTest --tests '*RateLimitApiIT'`, 종료0·오류/skip0이다. 31/35건의 실행은 후속 테스트 추가 전 기록이며 최종수는37건이다.
- 최종 XML `backend/build/test-results/integrationTest/TEST-com.limitedbooking.backend.ratelimit.RateLimitApiIT.xml`: tests37/failures0/errors0/skipped0, listener37/0/0 일치. 실행시각2026-10-22T09:37:34.199Z, XML11.67초·Gradle13초. 다른 일반Unit/IT·verify/build는 이번 US2에서 실행하지 않았다. US1의 일반Unit169/RedisIT5 기록과 구분한다.
- 실제 소유 PG/Redis 동적접속·RANDOM_PORT·클래스별 랜덤 Basic 비밀번호·UTF-8/UTC·풀10/획득3000ms·명령/연결200ms·기본20/60000ms를 사용했다. ResourceRepository 실제 SQL 진입에서 READ COMMITTED/SET LOCAL lock2s·statement5s/DB UTC를 검사했다.
- 요청별 고유ID의 테스트 전용 servlet 관측→실제 limiter/Redis script 응답·Controller/Service spy·실제 transaction manager listener→종료 사건을 연결했다. limiter 시작·판정은 TX 비활성, 허용 뒤 Controller/Service/tx-before/tx-start/SQL 순서, tx-start/SQL 활성 상태를 확인했다. 초과 때 Controller/Service/TX0이며 전체 대상·예약의 모든 컬럼을 한 SELECT의 jsonb snapshot으로 비교해 동일함을 확인했다.
- 생성10+동일 소유예약 취소10(직접커밋 준비, 중복취소 포함)에서 요청별count1~20,21번째429/count21; B/ADMIN 독립1, X-User-Id는 소유자·카운터에 영향 없음. 잘못된JSON/UUID/키·키누락·quantity/userId 미정의·중복필드·415, 취소본문/잘못된UUID400, 타인/없는예약404·품절/충돌409는 각각집계되며 소진후429가 먼저다.
- 일반 Content-Length/길이미정 publisher 자동chunked/원시chunked × 인증유무 ×8192/8193의12사례를 실행했다. 관측Content-Length는 fixed=size, 나머지-1·Transfer-Encoding=chunked다.8193은413·limiter/script0·업무진입0·전체snapshot동일,8192는401또는인증후201/count1이다. UTF-8 `가`2731자=8193byte의 길이미정 요청도 인증유무 모두413·미집계다.
- 정상/`%63ancel`/`%72eservations` 각각 빈본문·비빈본문·잘못된UUID의9사례와 소진뒤429를 확인했다. StrictHttpFirewall 완화 없음. PathPatternRequestMatcher는 MVC기본 parsed path 규칙을 사용한다.
- 필터는 Bean/서블릿에 자동등록하지 않고 SecurityConfig의 체인 생성에서만 소유한다. 실제 체인에서 RequestBodyLimit→Basic→Authorization→RateLimit→CancelValidation 순서와 각1개, Servlet/Bean RateLimitFilter 등록0을 확인했다. 기존 Controller의 취소본문 방어는 유지했다.
- 실제 후속 Service 내부결함은500으로 남고 실제ERROR dispatch1회에도 limiter/script/count는1회였다. 새500업무code·광범위Redis catch·후속chain catch 없음. Redis 의존성503 분류/실제장애는 US3 미구현으로 명시한다.
- 재전송201/Location/Replay·최초JSON/UTC시각, 중복취소200/동일cancelledAt, 취소후 생성재현CONFIRMED와 GET현재CANCELLED를 검증했다. 각5회집계 후 소진한 같은 생성/취소는429·업무진입0·전체snapshot동일이다.
- 기본정책 실제HTTP40건/수량40/서로다른키·작업자10·수집30초/HTTP9초/종료10초:201정확20·429정확20/count40/PTTL59652ms. 결과40/예외0, 서버in-flight0·executorTerminated=true·httpClosed=true 뒤 단일SELECT inventory capacity40/available20/total20/confirmed20. 기존 ConcurrencyTestSupport를 재사용했고 다음reset의 첫STOP검사·모든 종료 관측을 유지한다. US4의 기존부모fixture/110회는 아직 실행하지 않았다.
- 독립GET resource200·소유예약200·ADMIN resourcePOST201·미인증/틀린비밀번호401+WWW-Authenticate는 limiter/script0이었다.429는 공통writeProblem의6필드/code·민감정보 비노출이며 실제Lua반환PTTL로 계산한 Retry-After와 정확히 비교했다.
- T004~T006의 US2 필요 기반을 공통 RateLimitHttpTestSupport에 완성했다. RedisFailureIT는 T023 후속에서 이 기반을 재사용하며 현재 파일/실행이 존재한다고 표시하지 않는다. 업무SQL/스키마/OpenAPI 정상·오류 기대값은 변경하지 않았다. 기존 postgresOnly HTTP 전환·전체verify를 미리 실행하지 않았다.
- Qodana 범위 조정 문서커밋4895488: 사용자2026-10-22 “나중에 할거야” 지시로 T037은 미완료 후속이며 이번 완료/PR/병합 게이트에서 제외한다. 미실행은 통과가 아니다. 앞선 US1 기록의 필수blocker 표시는 당시 지시이며 현재 범위는 이 지시로 대체한다.
- 다음 한 작업: US2 커밋의 오케스트레이터 독립 중간검토 후 후속 지시를 받는다. T021~T038(Qodana만 별도후속), 실제장애·기존회귀110회·최종build/verify/의존성·전체독립리뷰는 미완료다.


### 2026-10-22 US3 T021 분류RED

US2 84b3ef7의 오케스트레이터 독립검토 통과 후 공식 사전조건·작업트리·승인된009/knowledge/설치SDR4.1.1 LettuceExceptionConverter·Lettuce7.5.2 예외를 대조했다. `./gradlew test --tests '*RateLimitFilterTest'` 종료1·16건/실패6/오류0/skip0. 확인된연결/명령timeout/WRONGTYPE5종은 기존필터가503으로변환하지못하고, 인터럽트1종은플래그미복원으로실패했다. 내부결함9종·후속chain예외1종은기존전파로통과했다.

T022 최초GREEN: 같은분류Unit명령 종료0·16건/실패·오류·skip0. T023~T026 최초 실제인수: `./gradlew test --tests '*RateLimitFilterTest' integrationTest --tests '*RedisFailureIT' --tests '*RateLimitApiIT'` 종료0·Unit16/IT46(실제장애9+US2회귀37)·실패/오류/skip0,1분33초. 실제예외·경과는 후속 최종기록과 구분한다.

분류보강RED: 직렬화예외가 전송원인을 포함해도 내부결함으로 전파해야 하는 사례를 추가했다. `./gradlew test --tests '*RateLimitFilterTest' --tests '*RateLimitHttpLifecycleTest'` 종료1·19건/실패1/오류0/skip0. 원인만 보는 초기분류가 SerializationException(ConnectException)을503으로감춰 실패했다. 지원fixture2건은 모든정리오류보존·전송불확실뒤Redis/DBreset·준비·제출0을 통과했다.

### US3 T022~T028 최종 인수와 한계 (2026-10-22)

| 실제 명령(backend/) | 종료 | 실제 발견·실패/오류/skip | 조건·범위 |
| --- | --- | --- | --- |
| `./gradlew test --tests '*RateLimit*Test' integrationTest --tests '*RedisFailureIT' --tests '*RateLimitApiIT'` | 0 | Unit32/IT46, 모두0/0/0 | 직렬화결함 우선전파 수정 뒤 전체US3 9건+US2회귀37건,1분27초 |
| `./gradlew test --tests '*RateLimit*Test' integrationTest --tests '*RedisFailureIT.actualWritePauseTimesOutEachOperationWithoutChangingBusinessState' --tests '*RedisFailureIT.independentApisRemainAvailableDuringWritePause' --tests '*RateLimitApiIT'` | 0 | Unit32/IT41, 모두0/0/0 | 관리연결close/shutdown각10초 future 상한·복구HTTP201 보강 영향4건+US2회귀37건,18초 |
| `./gradlew test --tests '*RateLimit*Test'` | 0 | Unit33,0/0/0 | 실제관측 RedisSystemException→RedisException→SocketException 원인을 분류표에 추가;분류18+종료fixture2+기존순수13 |
| `./gradlew integrationTest --tests '*RedisFailureIT.actualWrongTypeRejectsAllWritesWithoutChangingBusinessState'` | 0 | IT1,0/0/0 | WRONGTYPE finally의 원래/정리오류 보존·종료실패뒤키삭제차단 보강 영향 사례만 재검증 |

- 전체46건 당시 XML: ExistingConnectionRedisFailureIT1(6.045초),FirstConnectionRedisFailureIT1(2.011초),RedisFailureIT7(68.317초),RateLimitApiIT37(8.278초), 실패/오류/skip0. 해당XML들은 후속지정실행 덮어쓰기 전 `/private/tmp/009-us3-full46-xml/`에 실행자료로 보관했고 요약은 이 문서에 보존한다. build 산출물/임시XML은커밋하지 않는다. 이후영향시험41건의 XML은 RedisFailureIT4(2.97초)+RateLimitApiIT37(11.891초), 마지막지정XML은WRONGTYPE1건이다. 서로다른명령의 발견수를 합쳐 한 번의 실행수로 표시하지 않는다.
- 실제 CLIENT PAUSE: 앱200ms와 독립적인3초관리연결에서, 각HTTP의 인증완료뒤실제limiter진입 시 `CLIENT PAUSE 1000 WRITE` OK를 확인해 인증지연이pause를소진하지않도록 했다. 제품후크는없고 실제Bean spy의 테스트전용관측이다. 생성/취소/성공키재전송 각각 QueryTimeoutException→RedisCommandTimeoutException·503을 확인했다. 보강실행 limiter경과207/203/206ms(HTTP전체200ms보장은아님). Controller/Service/TX0·전체대상/예약모든컬럼snapshot동일이다.
- finally의별도관리연결CLIENT UNPAUSE·PING최대5초,관리명령/접속3초,관리connection.closeAsync와client.shutdownAsync의get각10초를 적용했다. 모든정리오류를preserve하고 정리실패는StopState에보존해다음초기화를차단한다. 복구PONG 뒤앱HTTP생성201도3사례모두확인했다. HTTP9초/전체10초·서버in-flight10초·HTTPclient종료10초를유지했고추가작업executor는만들지않았다. queued명령의실행은불확실하며 실제복구뒤count0/1/1을관측했다. 이를0으로고정하거나DECR보상하지않는다.
- 기존연결은실제PING완료후소유Redis를stop했다. 관측원인은첫인수의 RedisSystemException→RedisException→SocketException(60/1/1ms) 또는최종전체인수의 QueryTimeoutException→RedisCommandTimeoutException(223/206/206ms)이었다. 재연결/큐상태에따라다르며한형태를보장하지않는다. 별도첫접속컨텍스트는소유포트를확보하고Redis를중단한뒤앱컨텍스트를생성했고resetRedis를하지않았다. 실제 RedisConnectionFailureException→RedisConnectionException→AnnotatedConnectException→ConnectException(1ms수준),3쓰기모두503이었다. 죽은소유동적포트를명시하며개발Redis로fallback하지않는다. 재기동포트를이전ApplicationContext에재사용하지않았다.
- 실제WRONGTYPE는소유사용자제한키를리스트로준비해Redis가반환한 RedisSystemException→RedisCommandExecutionException의WRONGTYPE접두사를검증했다. 생성/취소/성공키재전송503·후속진입/TX0·전체snapshot동일,키정리후새생성201을확인했다. Lua문법/없는명령·직렬화·깨진응답·일반RuntimeException·auth/config결함은Unit에서원래예외전파를고정하고 US2후속내부500/ERROR dispatch회귀를유지했다.
- 지연과두중단컨텍스트의정상PG에서GET대상200·GET소유예약200·ADMIN대상생성201을확인했고limiter/Redis script-attempt0이었다. 503은공통writeProblem의6필드/code·민감정보비노출이다. 정상503은HTTP전송예외가아니며STOP는false임을검사했다.
- DB실패는테스트전용ReservationRepository spy에서실제수량차감0을관측한뒤 `SELECT pg_sleep(6)`을실행했다. 기존SET LOCAL statement_timeout5초가실제로발생해DB503·tx-rollback1·전체행snapshot동일·Rediscount1을확인했다. 실패주입은한번만하며같은생성키재요청201/count2·성공키1행·최종수량보존을확인했다. Writer/Service/업무SQL/스키마는변경하지않았다.
- 성공키TTL독립은기본20/60000ms를유지해최대65초/200ms polling으로실제제한키만료를기다렸다.60197ms뒤에도예약전체snapshot동일,같은키201/Replay=true/Location/JSON동일·새창count1이었다. 짧은창이나키수명조작으로대체하지않았다.
- 순수지원fixture2건은정리서버오류+HTTP종료오류를모두보존하고첫StopState원인유지·다음Redis/DBreset/준비/제출0을mock호출0으로확인했다. 별도전송불확실IOException fixture도원인보존·다음동작0을확인했다. 이는지원코드탐지시험이며실제Redis장애9건을mock으로대체한것이아니다. 인터럽트Unit은플래그복원·원래예외전파후시험스레드상태를정리했다.
- 새정책·버전/제한값변경·skip·무한재시도없음. 분류호출범위는limiter.check만이며후속chain예외는catch밖이다. 역직렬화/스크립트/내부결함은503으로일괄숨기지않는다.
- 미완료: US4 T029~T034 기존postgresOnly HTTP전환·110회/부모fixture, T035~T036/T038 최종문서/전체build+verify+의존성·독립리뷰. 이번명령은일반전체Unit/IT나최종전체빌드가아니다. Qodana T037은사용자지정미완료후속(4895488)이며이번gate제외를유지한다. docs/concurrency-runs.json은변경하지않았다. 다음한작업은US3커밋의오케스트레이터중간검토다.

### US3 중간 코드검토 보완 — 실제 noeviction OOM RED

오케스트레이터가현재WRONGTYPE이외의모든RedisCommandExecutionException을내부결함으로전파하는분류에서메모리부족거부가빠졌음을지적했다. 코드리뷰수용절차로공식[Lua저메모리동작](https://redis.io/docs/latest/develop/programmability/eval-intro/#execution-under-low-memory-conditions)·[noeviction](https://redis.io/docs/latest/develop/reference/eviction/)과설치Lettuce예외를대조했다. 실제`./gradlew integrationTest --tests '*RedisFailureIT.actualNoEvictionOomRejectsWritesAndRestoresOriginalConfiguration'` 종료1·3건/실패3/오류0/skip0. 소유Redis의원래maxmemory=0/policy=noeviction을보존한뒤maxmemory1/noeviction에서생성·취소·성공키재전송은모두HTTP500이었다. 실제원인은RedisSystemException→RedisCommandExecutionException의 `OOM command not allowed when used memory > 'maxmemory'. script: 218b0e0a17530b3260b642d96184f36dbbfacc98, on @user_script:1.`이었다. 별도OOM타입은아니었다. RED에서도각finally가원래0/noeviction원복·조회확인을완료했다. DB전체snapshot은동일했다.

### US3 OOM 보완 최종 GREEN — 현재 구현 인수

- OOM분류Unit RED: `./gradlew test --tests '*RateLimitFilterTest'` 종료1·22건/실패2/오류0/skip0. 알려진bare/script-suffix OOM만503으로추가하며임의OOM문구와직렬화가감싼OOM은원래예외로전파하는경계를고정했다.
- 최종명령: `./gradlew test --tests '*RateLimit*Test' integrationTest --tests '*RedisFailureIT' --tests '*RateLimitApiIT'` 종료0·관련Unit37/지정IT49,실패/오류/skip0,1분30초. 현재XML은분류22+종료fixture2+기존순수13=Unit37,기존연결중단1(2.949초)+첫접속전중단1(2.023초)+RedisFailureIT10(73.771초)+US2회귀37(8.539초)=IT49이며listener와일치한다. 앞선46/41/1건은보강전단계별실행이며최종전체지정인수수와혼동하지않는다.
- 실제OOM3사례(생성/취소/성공키재전송)는각503·Controller/Service/TX0·전체DBsnapshot동일이었다. RedisSystemException→RedisCommandExecutionException의확인된정형OOM응답만503이며본문은OOM/maxmemory를노출하지않는다. 각OOM중정상PG의독립GET2종200·ADMIN resourcePOST201은limiter/script-attempt0이었다. 각finally가원래maxmemory=0/policy=noeviction을정확히원복·재조회확인했고,원복후앱HTTP생성201·최종수량보존을확인했다. 원복/정리오류는원래오류와함께보존하고StopState로다음reset을차단한다. 메모리/정책CONFIG는소유시험Redis만변경하며재현을위해전역캐시초기화나제품정책변경을하지않았다.
- 최종전체인수의기본TTL실제만료60141ms·성공키재현201/count1,실제pause·두중단·WRONGTYPE·실제DBtimeout/복구·US2HTTP500보호도함께통과했다. 전체stage8완료가아니며 US4 기존110회·최종일반build/verify/의존성·독립리뷰는미완료다. Qodana사용자지정후속을유지한다.

## 8단계 US4 — 기존 HTTP/110회 Redis 연결 회귀 (2026-10-22)

US3 `2adadb2`의 오케스트레이터 독립검토 통과 후 T029~T034를 수행했다. 제품/업무 SQL·스키마·원래 HTTP/DB 기대값은 변경하지 않았다. `ReservationApiTestSupport.setup`은 `beforeDatabaseReset()`을 여전히 첫 동작으로 호출하고 그 뒤 ADMIN/A/B 세 소유 키만 DEL·count0 확인·DB reset을 한다. 종료 fixture는 mock DataSource와 mock StringRedisTemplate의 접근0·redisResets0·준비/제출0을 검사한다. 실제 장애 통합을 mock으로 대체하지 않았다.

| 검증 | 실제 명령(backend/) | 결과 |
| --- | --- | --- |
| 고의 순서 변경 RED | `./gradlew integrationTest --tests '*ReservationConcurrencyIT.parentSetupStopsBeforeResetPreparationAndSubmissionAndNullCleanupIsSafe' --tests '*ReservationConcurrencyIT.executorTerminationFailurePreventsSpyResetEvenAfterServerBecomesIdle' --tests '*ReservationConcurrencyIT.httpTerminationFailurePreventsSpyResetAndBlocksNextSetup' --tests '*ReservationConcurrencyIT.transportFailureBeforeServiceEntryBlocksResetAndNextSetup'` | 종료1·4건/실패4/오류0/skip0, 실제 Mockito NoInteractionsWanted로 보호검사 전 Redis delete를 탐지 |
| 원복 GREEN | 위 4 fixture + `--tests '*ReservationConcurrencyIT.cleanupWaitsForHttpTerminationBeforeResettingSpies'`, 앞에 `test --tests '*ConcurrencyTestSupportTest'` | 종료0·Unit13/IT5, 실패/오류/skip0 |
| 기존 회귀/경쟁 | `./gradlew test --tests '*ConcurrencyTestSupportTest' integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT' --tests '*ReservationRollbackIT' --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT' --tests '*SecurityIT' --tests '*ReservationConcurrencyIT'` | 종료0·Unit13/IT167, 실패/오류/skip0, 1분13초 |
| 취소 입력 검증 위치 정리 | `./gradlew integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT'` | 종료0·Security7/Resource8=IT15, 실패/오류/skip0, 12초 |

- RED는 기존 안전 분기를 재구현한 것이 아니라, Redis 초기화를 보호검사 앞으로 고의 이동한 탐지력 시험이다. 실제 Redis delete 1회(부모 setup 반복 fixture는2회)가 발견됐다. 원복 후 네 실패 상태 모두 Redis/DB 접근0이며 기존 최초 원인과 HTTP/executor/서버 종료 보호가 유지됐다. 최초 준비 시 잘못된 설정 접두사로 실제값 assertion이 실패한 실행은 환경 구성 오류로 구분하고 RED로 세지 않았다. 설정을 `reservation.rate-limit.limit`로 정정한 뒤 위 고의 변경 RED를 실행했다.
- 회귀167건의 XML은 Create16/Idempotency5/Rollback7/Ownership5/Cancel13/Security6/Concurrency115(110회+지원fixture5)다. 이 XML은 `/private/tmp/009-us4-regression-xml/`에 보존했다. 취소 입력의 위치 정리 후 Security7/Resource8은 별도 실행15건이며 167과 합산해 단일 실행으로 표시하지 않는다.
- 기존 ResourceApiIT 안에도 인증된 예약 취소의 본문/UUID/8KiB 검증이 있었다. 실제 제한 경로를 통과하므로 해당 취소 부분만 실제 Redis SecurityIT로 옮겼다. 원래 400/413·민감정보/필드 assertion을 유지하며 ResourceApiIT는 순수 대상 API만 남겨 postgresOnly를 유지했다. 순수 DB/Repository/Schema도 Redis를 강제하지 않는다.
- 예약 HTTP6종과 Security는 소유 PostgreSQL+Redis 동적 포트를 쓴다. 기본20/60000ms·명령/접속200ms 실제값을 확인하고 사례마다 소유 키를 초기화했다. 경쟁에만10000/60000ms를 명시·실제값 검사하며 각110회는 ownedKeyResets=1·키3개 count0을 확인한다. FLUSHALL/영구 제한해제/429·503로 기대값 대체는 없다.
- C01~C11 각10회는 기존 실제 HTTP·Basic·제한필터·업무서비스·DB 경로를 통과했다. 요청수=결과수, 예상밖429/503 0, 종료 뒤 단일SQL InventorySnapshot과 중복키0, 모든수량보존을 확인했다. 모든110회 CLEANUP은 server0/http=true/failure=none이다. C07의 정확한 요청ID별 UPDATE0→조회present와 UNIQUE 롤백 경로·고유ID·관측을 유지했다. 기존100요청120초/그외30초·gate5초·HTTP9초·종료10초·풀10/획득3000ms와10ms표본을 변경하지 않았다.
- `docs/concurrency-runs.json`은 이번 지정 실행의 실제110회 RUN/phaseCounts/정확한경로증인/경로hash/CLEANUP·실행소스SHA256으로 재생성했다. 7단계 기록은 Git 이력과 과거 실행 증거로 보존한다. 최종 단일 그래프가 끝나면 그 실행의 실제110회 기록으로 갱신한다.
- 로그: `/private/tmp/009-us4-order-red.log`, `/private/tmp/009-us4-order-green.log`, `/private/tmp/009-us4-regression.log`, `/private/tmp/009-us4-input-regression.log`. Qodana T037은 미실행 사용자후속이고 이번 게이트 제외다. 다음은 T035/T036/T038 문서·최종단일그래프 인수다.


## 8단계 최종 구현·실행 인수 (2026-10-22)

**현재 상태:** T001~T036/T038 37개 완료, Qodana T037만 사용자지정 미실행 후속이다. US1~US3는 오케스트레이터 중간검토를 통과했고 US4 및 최종 커밋의 독립검토·PR·병합은 후속 오케스트레이터가 수행한다. 아래 실행 인수는 그 검토/병합을 수행했다는 뜻이 아니다. 이번8단계 절차는 최신 사용자 지시의 Spec Kit(specify/plan/tasks/analyze/implement)를 적용했으며 계획서/헌법의 역사적 superpowers 기준과 과거날짜 실행증거는 보존한다.

- 명령: `cd backend && ./gradlew build verify verifyContractDependencies`, 검증 코드 기준 `45c0db0`. 종료0, `BUILD SUCCESSFUL in 3m 12s`, 12 actionable tasks(9 executed/3 up-to-date). 최종 test/integrationTest는 현재 실행했으며 공유 Unit test 태스크는 그래프에서1회다. 도구 경고는 기존 Gradle10 비호환 deprecated 기능 및 ByteBuddy Unsafe 경고이며 실패·skip·continue-on-error로 감추지 않았다.
- listener/XML 일치: Unit193(13 XML)·일반IT312(20 XML), 각각 실패0/오류0/skip0. IT는 기존 업무/DB/지원258 + RedisRateLimitIT5 + RateLimitApiIT37 + 실제장애12(기존연결1/첫연결1/RedisFailureIT10)다. 기존 업무/지원에는 경쟁115(반복110+종료fixture5), Security7·Resource8, Create16·Idempotency5·Rollback7·Ownership5·Cancel13 등이 포함된다. 발견0·실패·skip을 거부하는 기존 Gradle listener/XML 게이트를 통과했다.
- 실제장애12건에서 pause1000 WRITE·명령200ms/연결200ms·기존연결중단·첫연결전중단·WRONGTYPE·정형OOM3·실제DB timeout/롤백·성공키TTL독립을 재검증했다. 정상PG의 독립API·거부시업무진입0/전체snapshot동일·원복finally/유한복구·모든종료·정리오류보존·다음reset차단을 유지했다. 최종 RedisFailureIT10은69.782초였다.
- 최종 경쟁110회(클래스115건,31.811초)는 매회 소유키reset1·10000/60000ms·접속/명령200ms, HTTP 요청수=결과수·예상밖429/503 0·모든종료뒤 단일SQL 수량snapshot·고유ID별C07 UPDATE0/UNIQUE witness를 유지했다. 110 CLEANUP 모두 server0/http=true/failure=none이다. 기본제한 HTTP37은8.760초였으며 기본20/60초 정책을 올리지 않았다.
- `docs/concurrency-runs.json`을 최종 실행 XML의 실제110회로 갱신했다. sourceSha256 5개를 현재 파일과 모두 대조해 일치했다. 앞선 US4 지정167건은 당시 실행소스/archivedXML 기록이며 커밋전 fixture 선언 정리 이후의 동일소스 실행으로 주장하지 않는다. 이번 최종 실행이 정리된 현재 소스를 검증했다. 이전7단계/지정 회차 기록은 Git 이력과 위 날짜별 증거로 구분한다.
- 빌드 산출물: `backend/build/libs/backend-0.0.1-SNAPSHOT.jar` 44,156,613 bytes, SHA256 `ccfd488d3c081238d99c2709a65d687dba9b927f2c073ebc030b6599d9c08fa9`; plain JAR65,335 bytes, SHA256 `90eda6530127dcdd280341d34a7f9860c61c2b073e338cd1479845cb846e6724`. 실행JAR에 RateLimitDecision/Properties/Filter/RedisRateLimiter와 `redis/rate-limit.lua` 포함을 ZIP목록으로 확인했다. 산출물은 커밋하지 않는다.
- `verifyContractDependencies` 실행·종료0: testCompileClasspath/testRuntimeClasspath/runtimeClasspath의 해석실패0 및 금지 `com.github.java-json-tools:json-patch/jackson-coreutils` 재유입0을 기존 게이트로 확인했다. 이는9단계 업무전체 자동OpenAPI검증을 실행했다는 뜻이 아니다.
- 고정 OpenAPI3.0.3의 생성/취소429·503, Retry-After와 RATE_LIMITED/DEPENDENCY_UNAVAILABLE, 성공201/200·Location·Replay·소유권404를 실제제품/HTTP시험과 대조했다. 계약·업무SQL/스키마를 바꾸지 않았다. 최신 계획서 상단 현재요약과8단계10개체크, README/knowledge/009설계·작업·버전·결정을 실제결과에 맞췄다. 과거 날짜의 Unit137/150/169 및 IT138/257/지정수는 해당 시점 증거로 보존한다.
- 로그: `/private/tmp/009-final-build-verify.log`; XML: `backend/build/test-results/test/`·`backend/build/test-results/integrationTest/`, 임시보존 `/private/tmp/009-final-xml/`. JSON은 커밋된 재현가능 요약이며 임시로그/XML은 로컬 실행자료다. seed없음(지정사례+실제스케줄링), workers최대10·풀10/3000ms·10ms연결표본·UTF-8/UTC·동적PG/Redis를 유지했다. 운영규모 성능이나 모든경쟁순서의 증명으로 일반화하지 않는다.
- 남은 범위: Qodana 미실행(사용자2026-10-22 “나중에 할거야”, 범위조정4895488)·오케스트레이터 최종독립리뷰/PR/merge. Qodana JVM2026.2/Java25/recommended/any0/라이선스 설정을 유지하며 통과·지적0으로 기록하지 않는다. 업무전체 자동계약/Property/PIT심화·k6부하·외부CI는 다음계획단계다. push/PR/merge·외부workflow는 수행하지 않았다.
- 최종 문서검사: 변경문서의 로컬링크439개 존재, 009 작업37개 완료/T037만미완료, 계획서8단계10/10, 회차JSON110개·현재소스SHA2565개 일치, `git diff --check` 통과. 내장 명세품질 체크리스트는 읽기전용으로16/16 PASS를 확인했다. `.specify/extensions.yml`이 없어 implement 전후 훅 대상없음. 최종 실행 뒤 변경은 이 문서동기화/회차요약뿐이며 제품/시험/Gradle 코드는 바꾸지 않았다.


## Qodana 지적 해결 검증 (2026-10-22, revision 782dd03 후속)

- 목적: Qodana 정적 분석 결과(revision `782dd03c2650d10a4c7feece1bf95d6c209db3e0`)에서 보고된 `RateLimitFilter.java`의 중복 예외 조건 1건(`ConditionCoveredByFurtherCondition`) 및 `RedisRateLimiter.java`의 raw type `List` 사용 3건(`RawUseOfParameterizedType`) 해결.
- 조치:
  - `RateLimitFilter.java`: `root instanceof java.net.ConnectException` 제거 (`SocketException` 분기로 포섭).
  - `RedisRateLimiter.java`: `DefaultRedisScript<List<?>>` 타입 지정 및 unchecked 경고 억제 추가.
- 실행 검증:
  - `cd backend && ./gradlew test`: 종료 0 (Unit 193건, 실패 0, 오류 0, skip 0).
  - `cd backend && ./gradlew verify verifyContractDependencies`: 종료 0 (Unit 193건, 일반 IT 312건, 실패 0, 오류 0, skip 0, 계약 의존성 게이트 통과).
- 문서: `docs/qodana-review.md` 갱신.


## 2026-10-23 9단계 공통 선행 T001~T006

T001: prerequisite 종료0, FEATURE_DIR=specs/010-openapi-contract, requirements 체크16/16 PASS. extensions.yml 없음(전후 훅 없음). AGENTS·knowledge README/rules 전체·4 domain·헌법·계획2~5/7절9단계 및 승인 spec/plan/tasks/research/data-model/contracts/quickstart 대조. HEAD e098af6, feature/010-openapi-contract, 독립 worktree 루트 확인, 초기 사용자 diff 없음. backend `./gradlew --version` 종료0: Wrapper9.7.1, Corretto25.0.4+7-LTS, macOS27.0.1 aarch64. 원본9.8 변경 무수정.

T002: build.gradle은 docs/openapi.yaml→business.openapi.yaml 복사, Unit *Test(contract/property 제외), contractTest *IT/contract 태그, maxParallelForks1, 현재 listener/XML 수 일치·0/실패/skip 거부, verifyContractDependencies 재유입 게이트를 제공한다. 기본 Gradle 변경 없음. 기존 ToolchainContractValidator는 본문없는 GET 프로브만 검증하고 ToolchainContractIT 업무 검사는 parser/ref뿐이다. ContainerTestSupport 소유 동적PG/Redis·종료10초, HttpClientTestSupport connect/request/종료 패턴, TestData 사전커밋/reset 순서, DatabaseAssertions 단일 snapshot, RateLimitTestSupport 소유3키, ConcurrencyTestSupport StopState/최초원인/suppressed를 재사용한다. 기존 회귀 대상: ReservationConcurrencyIT110회/종료fixture, ReservationRollbackIT/CancelIT, RedisFailureIT, RateLimitApiIT, SecurityIT/ResourceApiIT. 이번 공통선행은 이 전체 회귀 인수와 구분한다. .gitignore 및 backend/.gitignore의 Java/Gradle 산출물·비밀정보 제외, .dockerignore의 .git/산출물/.env 제외 확인.

T003 RED: `./gradlew test --tests '*ApiContractValidatorTest'` 1차 종료1/6건 중 assertion실패5, 2차 파싱경계 추가 후 종료1/7건 중 assertion실패6, 오류/skip0. 컴파일 및 testClasses 성공. 빈 void 골격의 미검증을 실제 assertion으로 확인했다. 최초 잘못된 cwd 파일쓰기와 sandbox 캐시 잠금 실패는 기능 RED에서 제외하고 올바른 경로/정상 권한 상승으로 실행했다.

T004 GREEN: 동일 Unit명령 종료0, 7건 실패/오류/skip0. 고정 3.0.3 parser/ref·정확5operation·명시status·원문 request/response 전달 구현. docs/openapi.yaml 단일 원본에 default 비허용·UTC 정밀도 설명·Problem 추가필드거부를 반영했다. US1 이후 의미/오류/탐지력은 미구현.

T005: 동일 Unit명령 RED 종료1/11건 중 중복 singleton assertion실패2, GREEN 종료0/11건 실패·오류·skip0. HttpExchange 원문/null/빈문자열/raw path/헤더 보존, Map 이전 중복·case변형 singleton 거부, 불변 예상위반 multiset/시나리오 경계만 구현. 무효요청 허용/의미검사는 후속이며 아직 연결하지 않았다.

T006 RED: `./gradlew contractTest --tests '*ApiContractIT'` 종료1/3건 실패3·skip0. 실제 앱201 후 requestBody=null assertion 및 실제 truncated HTTP IOException 뒤 StopState=false assertion을 확인했다. 서버 지연fixture의 최초 chunked EOF는 기능 RED로 세지 않았다. 응답 stream을 완결한 뒤 `./gradlew contractTest --tests '*ApiContractIT.responseReceivedButServerStillRunningBlocksNextStages'` 종료1/1건 assertion실패1: 실제404 수신 후 close가 in-flight를 검사하지 않아 예외가 없었다. 소유PG/Redis·Spring RANDOM_PORT/실제 Basic 인증 기동 성공.

T006 첫 GREEN: `./gradlew contractTest --tests '*ApiContractIT'` 종료0/3건 실패·오류·skip0, 6초. 원문 String과 실제 HttpRequest raw path/headers·HttpResponse status/headers/body를 전달한다. singleton 중복은 List→Map 이전 검사한다. 실제 Authorization으로 ADMIN 생성201/사전커밋된 USER 조회200을 확인한다. 기본 정책20/60000ms·DB풀10/획득3000ms·Redis200ms 및 기존 이미지/digest는 변경하지 않았다.

최종 관련 회귀(순차):
- `./gradlew test --tests '*ApiContractValidatorTest' --tests '*ConcurrencyTestSupportTest'`: 종료0/24건(신규11+기존13), 실패·오류·skip0, 2초.
- `./gradlew contractTest --tests '*ApiContractIT' --tests '*ToolchainContractIT'`: 종료0/9건(신규3+기존6), 실패·오류·skip0, 11초. verifyContractDependencies도 종료0. 실제 percent-encoded raw path 유지, 본문 null/빈문자열 구별, 실제201 requestBody 공백/필드순서 보존을 확인했다.

종료 fixture: 실제404 본문을 완결한 뒤 서버 filter 종료만 latch로 보류하고 100ms 시험용 deadline에 close 실패를 확인한다. release는 finally에서 수행하고 서버 idle을10초 내 확인한다. truncated Content-Length 실제HTTP는 IOException 뒤 StopState를 설정하며 서버종료 미확인 cleanup을 원래 IOException의 suppressed로 보존한다(50ms fixture deadline). 두 경우 모두 **새** HTTP 지원 인스턴스에 같은 StopState를 전달해 reset/준비 callback0·submittedCount0·최초원인 보존을 확인했다. 정상 지원은 connect3초/request9초/외부HTTP전체10초/client await10초/server await10초, ContainerTestSupport는 종료10초다. 기존 raw socket read10초는 변경하지 않았고 이번 최소 지원에서 raw socket/chunked/조기응답 전송바이트 관측은 구현·실행하지 않았다(T016 후속).

미완료: T007 이후/US1~US4·전체 적용표·정확한 expected-invalid 요청 판정·schema 밖 의미검사·실제DTO변이·전체 verifyContract 인수. OpenAPI info 첫설명과 나머지 시각필드의 오래된 상태 설명은 T035에서 정리한다. Qodana는 최신 사용자지정 미실행 후속이며 이번 게이트 제외. Property/PIT/부하/외부CI/push/PR 없음.

T006 최종 snapshot 보완 후 `./gradlew contractTest --tests '*ApiContractIT'` 종료0/3건 실패·오류·skip0, 12초. 정상 시험의 모든 HTTP/서버 종료 후 준비 대상과 HTTP 생성 대상을 **한 SELECT snapshot**으로 함께 검사: 2행 각각capacity1/available1/예약0, 보존식 적합. 최종 XML의3건 및 앞선Unit XML24건을 대조했다. `git diff --check` 종료0. after_implement 훅은 .specify/extensions.yml 부재로 없음. T001~T006만 완료하며 승인 spec/plan·Gradle·Wrapper·제품코드/SQL은 무수정이다.

T006 커밋 전 수명주기 보완 RED: `./gradlew contractTest --tests '*ApiContractIT.actualTruncatedHttpTransportStopsAndPreservesOriginalFailure'` 종료1/1건 assertion실패1, 6초. 실제 truncated HTTP를 try-with-resources로 실행하자 send IOException을 close가 동일 객체로 다시 던져 Self-suppression IllegalArgumentException이 주예외를 가렸다. send 원인은 StopState에 유지하고 close는 새로 발생한 cleanup만 던지며 기존 원인에 붙인 오류와 독립 wrapper를 보존하도록 최소 수정했다. 범위는 T006이며 기존 정상 GREEN 뒤 발견된 새 결함이다.

수명주기 보완 GREEN: `./gradlew contractTest --tests '*ApiContractIT'` 종료0/3건 실패·오류·skip0, 11초. 동일 truncated HTTP의 try-with-resources 결과는 최초 IOException이며 cleanup suppressed와 다음 인스턴스 StopState의 동일 cause를 확인했다. 나머지 실제 원문·2대상 단일snapshot·서버 in-flight 종료차단 시험도 함께 통과했다. 최종 stage에는 이 최신 수정까지 포함한다.


## 2026-10-23 9단계 US1 T007~T011

메인 승인에 따라 깨끗한 dee212d에서 지정 두 worktree의 ApiContractValidatorTest.java/ApiContractIT.java만 정확히 복사했다. 작성 담당은 Gradle/커밋 미실행이며 통합 실행은 여기서 순차 수행한다. prerequisite 종료0·requirements16/16 PASS·extensions.yml 없음·기존 ignore 규칙 유지. 승인 spec/plan/data-model/contracts/quickstart 및 knowledge 규칙과 계획2~5절의 기대값을 유지한다. T007/T008은 작성만 완료 표시했다.

T009 RED: `./gradlew test --tests '*ApiContractValidatorTest'` 종료1, 87건 중 assertion실패18·오류/skip0, 2초. 기존11+신규76 발견. 실패는 available>capacity, 비정규화 이름, 상태/cancelledAt 두 관계, createdAt/cancelledAt의 비UTC·+00:00·마이크로초초과 각3, 필수Location2/Replay/401challenge/429retry 누락, Location다른id2, 비Basic challenge다. 전부 정상 대조군 통과 뒤 결함 사본이 거부되지 않은 assertion이며 컴파일/기동 실패가 아니다. Replay대문자·RetryAfter0/소수/문자열은 이미 library response-schema가 거부했고 이를 semantic 탐지로 세지 않는다.

T009 실제HTTP: `./gradlew contractTest --tests '*ApiContractIT'` 종료0, 6건 실패·오류·skip0, 12초. 기존3+신규3 발견. 정상5operation·capacity0/1000·strip전102→100·😀100·NBSP·헤더case/JSON순서·DBsnapshot을 현재 schema만으로 통과했다. 실제HTTP 실패를 꾸미지 않았으며 누락 탐지 RED는 위 독립 Unit의18건이다. 제품/SQL/기본정책/Wrapper 변경 없음.

T010/T011 GREEN: `./gradlew test --tests '*ApiContractValidatorTest'` 종료0/87건 실패·오류·skip0, 2초. 스키마와 분리한 semantic 단계에 UTC Z/offset0/nano%1000, 이름 strip·코드포인트, available≤capacity, 현재상태/cancelledAt 관계, Location-id·소문자Replay·401Basic·429양의정수와 Content-Type 본래 미디어타입을 검사한다. charset 파라미터·추가 전송헤더·필드순서·0/3/6/9자리 중 값이 마이크로초로 표현되는 시각을 허용한다. 기존 Jackson2 테스트 라이브러리를 사용하며 제품 ResourceRules를 기대값으로 호출하지 않는다. 기존 요청/응답 schema 보고서는 계속 분리 수집하고 semantic도 합산한다.

T011 실제HTTP/프로브 회귀: `./gradlew contractTest --tests '*ApiContractIT' --tests '*ToolchainContractIT'` 종료0/12건(업무6+프로브6) 실패·오류·skip0, 12초. listener와 XML(Unit87/업무6/프로브6) 일치 및 verifyContractDependencies 종료0 확인. 업무는 다섯operation의 원문교환/실제Basic/응답필드·헤더·UTC, 대상0/1000·strip전102→100·😀100·NBSP 및 JSON순서/헤더case 동등성을 검사했다. 신규 정상시나리오는 모든 HTTP/서버 종료 후 단일 DBsnapshot에서 대상수량식과 생성/취소 후 예약1·CONFIRMED0·복구수량1을 확인한다. 기존 미종료/전송불확실/try-with-resources 최초원인 보호3건도 포함한다. 원문 헤더/본문을 정규화 DTO로 재생성하지 않았다.

환경은 선행 확인 Corretto25.0.4+7-LTS/Wrapper9.7.1/Boot4.1.1·기존 고정 PostgreSQL18.6/Redis8.10.2 digest·동적 소유 포트·UTF-8/UTC다. 업무/SQL/Gradle/Wrapper/OpenAPI·기본20회/60000ms·Redis200ms·풀10/획득3000ms는 변경하지 않았다. 정상 기대값 변경·제품변이/원복 실험 없음. 통합된 기존 정상Unit fixture에 필수Location을 명시한 작성 담당 변경은 기존 정책을 충족하는 수정이며 입력/응답의 규칙 완화가 아니다.

US1 MVP 지정 인수만 완료한다. T012 이후 정확한 무효요청 multiset/전체오류·필터/의존성/시퀀스·실제DTO변이/전체verifyContract는 미구현·미검증이며 현재 정상 HTTP 통과를 전체계약 인수로 세지 않는다. T035 OpenAPI 오래된 설명 정리·Qodana 사용자 후속을 유지한다. push/PR/외부CI 없음. 전후 extension 훅 없음, ignore 파일 변경 불필요.
