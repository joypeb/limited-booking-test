# 개발과 검증 규칙

근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 1.3·6~9절. 실제 실행 기록은 [환경과 기록](environment-and-records.md)을 따른다.

## 한 검증 대상씩 개발

1. 계획서·지식 문서에서 규칙과 기대 결과를 확인한다. 한 단계의 한 검증 대상만 정한다.
2. 테스트 이름·준비 데이터·기대값을 정하고 테스트를 작성한다.
3. 실행해 의도한 동작의 실패를 확인한다. 컴파일 오류만으로 기능 결함을 검증했다고 하지 않는다.
4. 최소 구현 후 해당 테스트와 영향받는 기존 테스트를 실행한다.
5. 코드·SQL·assertion·초기화·인증 경로·skip 설정을 읽고 정리·재검증한다.
6. 변경 이유·실행 결과·미검증 범위·사용자가 읽을 부분·다음 한 작업을 기록한다.

- 실제 구현에는 계획서의 `superpowers:executing-plans` 또는 `superpowers:subagent-driven-development` 절차를 적용한다. 병행 작업을 수행한다면 파일 책임을 나누고 예약·키·트랜잭션 공유 부분은 순서대로 처리한다.
- 환경 설정은 기능 TDD 대신 최소 실행으로 확인한다.
- 실패 원인과 최소 재현 조건부터 찾는다. 반복 실행·skip·mock·임의 버전 하향·continue-on-error로 실패를 숨기지 않는다.
- 사람이 정한 규칙을 테스트 통과 목적으로 바꾸지 않는다. 필요한 설계 변경은 이유·영향을 설명하고 계획서·지식 문서·계약·테스트를 함께 갱신한다.

## 테스트 분류와 발견 계약

| 종류 | 대상·방식 | 실행 시 확인 |
|---|---|---|
| Unit | Spring 없는 `*Test`, 순수 규칙·고정 Clock | 경계값·실제 요청 경로에서 규칙 사용 |
| Integration | 실제 PostgreSQL/Redis Testcontainers의 `*IT` | 제약·SQL·원자성·HTTP·의존성 |
| Boundary/Negative | 해당 Unit·IT에 포함 | 입력 경계·권한·실패 |
| Concurrency | 하나의 IT에서 독립 요청·트랜잭션 병행 | HTTP 결과와 최종 DB 불변식 |
| Contract | 고정 OpenAPI 3.0.3, 실제 HTTP, `contract` 태그 | 요청·응답·헤더·고의 훼손 탐지 |
| Property | 독립 모델, `property` 태그 | 시도 수·seed 재현·shrinking |
| Mutation | PIT로 순수 규칙 검증 | 변이 생성·탐지·생존 변이 설명 |
| Security | 실제 Authorization 헤더·보안 필터 | 소유자·관리자·위조·정보 노출 |
| Performance | k6와 종료 후 DB 검사 | threshold 종료 코드·상태 코드별 결과 |

- Gradle 태스크에서 이름·태그·엔진의 발견 규칙을 명시한다. Unit, 일반 IT, Contract 포함, Property 포함, 전체 심화, PIT의 실행 대상을 구분하고 실제 명령을 기록한다.
- 계획서 6.2절의 Gradle 계약을 검증 범위의 기준으로 사용한다. 1단계에서 test/verify/verifyContract/verifyProperty/deepTest/mutationSmoke를 구현·실행했다. 아직 없는 업무 테스트를 실행 안내나 통과 결과로 기록하지 않는다. 일반 `test`·`build`가 모든 심화 테스트를 포함한다고 가정하지 않는다.
- 테스트 0건·skip·변이 0건·NO_COVERAGE를 통과로 취급하지 않는다. 계획된 태스크가 대상 테스트를 실제 발견하고 고의 실패를 종료 코드에 반영하는지 확인한다.

## 실제 데이터와 경쟁

- PostgreSQL을 H2로 대체하지 않는다. Testcontainers는 동적 포트, 실제 앱 HTTP 테스트는 RANDOM_PORT를 사용한다.
- 테스트 전용 DB에서 reservations → resources 순으로 초기화하고 요청 전에 준비 데이터를 커밋한다. 로컬 개발 DB·실제 개인정보에 연결하지 않는다.
- JUnit 클래스는 순차 실행하고 한 경쟁 테스트 안에서 요청을 병행한다. 경쟁 테스트 전체에 `@Transactional`을 붙이지 않는다.
- 장벽은 요청 시작 전이나 최초 키 조회 뒤의 안전한 지점에 둔다. 연결·DB 잠금을 가진 모든 작업이 장벽에서 기다리는 교착 구조를 만들지 않는다.
- Future 예외·상한 시간·모든 작업 종료를 확인한다. 모든 결과가 모인 뒤 SELECT 하나로 capacity·available·CONFIRMED 수를 집계한다.
- 실패 주입 Bean·후크는 테스트 코드에만 둔다. 공개 API에 장애 주입 파라미터를 추가하지 않는다.
- 우선 사례는 마지막 1개·동일 키, 같은 키·다른 대상, 중복 취소, 생성·취소 경쟁, UPDATE 후 INSERT 실패다. 마지막 수량의 UPDATE 0 재조회와 충분한 수량의 UNIQUE 롤백을 각각 실행한다.
- 계획서 7절 7단계의 100요청/수량10, 동일 키 10요청/수량1, 중복 취소 10요청 등의 사례를 10회 검증하고 조건·반복 수·스레드·연결 수를 기록한다. 반복 실행은 모든 경쟁 순서의 증명이 아니다.
- DB 중간 실패·잠금 timeout·의존성 중단 후 부분 반영 없음과 같은 키 재시도 복구를 확인한다. 응답 유실은 실제 커밋을 DB로 확인하고 동일 키 결과를 비교한다.

## 계약·속성·변이의 탐지력

- 계약은 계획서 4절에서 해당 API에 정의된 정상·실패 응답을 실제 HTTP로 검사한다. UUID·UTC 시각·enum·필수/미정의 필드·Location·Replay·Retry-After·Content-Type을 확인한다.
- 검증기에 전달하는 응답의 필수 id 제거·알 수 없는 status 등 고의 훼손을 거부하는지 확인한다. 취소 후 생성 재요청과 GET의 다른 상태를 고정한다.
- Property 모델은 SQL·실제 구현을 호출하지 않는 Map·Set 기반이다. 명령 논리 ID와 실제 UUID 대응표를 분리하고 매 시도·shrinking 재실행마다 DB·대응표를 초기화한다.
- 초기 Property 조건은 대상 2개·capacity 0~5·명령 1~30건·최소 100회다. 실패 seed·축소 명령을 저장해 일반 회귀 IT로 추가한다. 내부 서비스의 순차 검증과 HTTP·경쟁 검증의 한계를 구분한다.
- PIT 대상은 ResourceRules·IdempotencyKey·CancellationDecision과 대응 Unit으로 한정한다. 초기 탐지율 80% 이상, 중요한 규칙의 설명되지 않은 생존 변이 0건을 기준으로 한다. 동등 변이·도구 한계는 이유를 남긴다.
- SQL available 조건, 취소 status 조건, 소유자 조건, 키 비교를 하나씩 고의 제거해 관련 테스트가 실패하는지 확인하고 반드시 원복한다. PIT 점수로 SQL·권한·경쟁의 안전성을 대신 주장하지 않는다.
- 변경·실패·미해결 문제가 없으면 전체 테스트나 부하를 불필요하게 반복하지 않는다. 별도 AI 리뷰는 실행 증거와 직접 설명을 돕는 보조 수단이다.

1단계 실행 확인은 도구 프로브 범위다. 현재 실행 수/XML 게이트와 Test 캐시 비활성화를 사용한다. Property 생성 예산1000과 실제 assertion101회를 구분하고 실제 최소100회를 검사했다. 클래스·이름·태그·skip·빈 입력 실패 및 원복 근거는 docs/test-evidence.md에 있다.

### 2026-10-14 2단계 스키마의 테스트 우선 순서

003 계획의 각 저장 제약은 테스트→제약 없는 후보 SQL에서 RED→현재 CREATE DDL 최소 추가→새 소유 DB에서 같은 테스트 GREEN 순서로 진행한다. 후보 파일은 테스트 전용 schema-candidate/schema.sql이며 Flyway migration이 아니다. 이미 인수한 제약을 유지하고 다음 제약의 RED 전 구현을 하지 않는다. 전체 후보 CREATE DDL을 완성한 뒤 수정 없이 업무 V1로 복사해 동일성을 확인하고 후보 파일·분기를 제거한다. 이후 실제 Flyway 최초 적용·재적용과 전체 동일 사례를 검증한다. 후보 SQL 인수와 업무 V1 적용 인수는 구분하고 적용된 V1은 편집하지 않는다.

### CHK022: 2단계 준비·초기화의 SQL 실패 인수

정상 연결에서의 고의 SQL 실패는 업무 V1·초기화의 롤백 요구사항으로 검증한다. 실제 V1의 테스트 전용 실패 사본은 업무 DDL 전체 롤백·V1 성공 이력0건을 요구하고 실패 DB 증거/종료 후 새 소유 DB에서 원본으로 재개한다. 초기화의 두 DELETE는 중간 실패에도 전체 롤백해 기존 양쪽 행/값·구조·이력을 보존하고, 장애 제거 후 같은 DB의 새 트랜잭션으로 명시적 재요청한다. 실패 주입은 테스트에만 둔다. 관측 불가능한 상태를 롤백 통과로 추정하지 않는다. 연결 단절/프로세스 중단은11단계다. 상세 계약·시험 이름은 [003 저장 계약](../../specs/003-define-db-schema/contracts/persistence-contract.md)의 CHK022 절을 따른다. 2단계 SchemaIT·DatabaseSupportIT에서 실행 검증했으며 [증거](../../docs/test-evidence.md)를 따른다. 업무 Writer·HTTP·경쟁 인수는 후속이다.


### 계약 도구 보안 의존성 (2026-10-20)

현재 계약 도구는 openapi-request-validator-core3.0.0/networknt이며 고정 OpenAPI3.0.3을 직접 읽는다. Swagger2 변환 경로는 지원 범위 밖으로 제외했다. verifyContractDependencies가 취약 json-patch/jackson-coreutils의 compile·test runtime·app runtime 재유입을 거부하며 contractTest 전에 실행된다. 도구 교체 시 실제 HTTP 정상·필수값 누락·UUID·타입·미정의필드 거부와 업무 명세 참조 해석을 보존한다. 상세 실행은 docs/test-evidence.md를 따른다.

verifyContractDependencies는 금지 모듈 검출에 앞서 미해결 의존성 edge도 거부한다. 해석 실패를 취약 모듈 부재로 판정하지 않으며, 정상·금지 모듈 재유입·존재하지 않는 의존성의 세 경로를 검증한다. 장애 주입은 테스트용 임시 init script로만 수행한다.


### 9단계 계약 탐지력 적용 (2026-10-23)

계약 검사는 schema·raw JSON·semantic·operation status·scenario 단계로 나누어 진단을 `단계:key@위치`로 남긴다. 무효 요청은 정확한 예상 요청 위반 multiset만 허용하고 응답 검사는 생략하지 않는다. 탐지력은 정상 대조 뒤 응답 사본 한 요소만 바꾸는 44종 훼손과, 실제 DTO의 컴파일 가능한 타입 변이(compile 성공→HTTP schema RED→정확 원복→같은 명령 GREEN)를 따로 기록한다. verifyContract는 업무 계약96(T042, T032 당시 90)과 프로브6을 구분해 발견>0·실패/skip0을 확인한다. 상세는 [010 작업](../../specs/010-openapi-contract/tasks.md)과 [실행 근거](../../docs/test-evidence.md)를 따른다.


### 10단계 속성·변이 적용 (2026-10-23)

- 속성 검증은 `ReservationPropertyIT`(property 태그, `propertyTest`가 `*IT`도 발견)에서 실제 PostgreSQL의 서비스 경로를 순차 호출하고 테스트 전용 `ReferenceReservationModel`(java.util만 사용)과 비교한다. 실행기(`ReservationCommandRunner`)는 업무 예외 4종만 결과로 바꾸고 그 외 예외는 전파한다. 실행마다(축소 포함) DB와 대응표를 새로 준비한다. 실제 실행 수·seed·생성 digest를 출력하고 실제 100회 이상을 검사한다.
- 축소: QuickTheories 0.26은 precursor 값을 하나씩 줄이므로 리스트 길이 축소가 드물고 중간 원소를 삭제하지 못한다. `withShrinkCycles(2000)`을 사용하며, 남은 상태 무변경 명령은 도구 한계로 기록한다. 축소 최소 사례는 출처(고의/실제 결함·seed)와 함께 일반 회귀 IT로 고정한다.
- PIT: 기본 변이군은 `RecordFilter`로 레코드 canonical 생성자(키 검증)를 제외해 핵심 규칙 변이가 0이었다. 점수만 보지 말고 생성된 변이 목록이 규칙 분기를 덮는지 확인한다. 현재 `STRONGER`·`-FRECORD`·컴파일러 생성 record 메서드 제외를 쓰며, 연산자가 없는 enum 매핑 교체는 수동 고의 변경으로 보완한다.
- 고의 변경은 한 번에 하나, 커밋 금지, 원복 후 `git diff --exit-code -- backend/src/main`으로 확인한다. 순차 경로에서 관측 불가능한 변경(취소 status 조건)은 경쟁·후크 테스트의 책임으로 분류한다. 상세는 [011 작업](../../specs/011-property-mutation/tasks.md)과 [실행 근거](../../docs/test-evidence.md).
