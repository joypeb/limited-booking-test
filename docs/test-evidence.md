# 0단계 계약·사례 문서 검토 기록

**현재 상태:** 0단계 문서·학습 인수 완료, 작업27/27 완료. T025는 질의·보완 설명 뒤 사용자의 명시적 확인/완료 지시로 처리했다. 최초 답변의 오류와 확인 방식의 한계는 맨 아래 T025 기록에 남겼다. 이전 대기/부분 완료 상태는 당시 이력이다. 실제 기능 실행 검증은 여전히 미실행이다.

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
