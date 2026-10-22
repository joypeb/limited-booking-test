# 구현 계획: Redis 원자적 예약 요청 제한

**Branch**: `feature/redis-rate-limit` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: 승인된 009 명세. 공식 `setup-plan.sh --json`을 실행해 이 파일을 생성했다. 스크립트의 BRANCH 값 `009-redis-rate-limit`은 feature 선택용 논리 이름이며 실제 Git 브랜치는 `feature/redis-rate-limit`이다. 브랜치를 생성하거나 전환하지 않았다.

## Summary

8단계의 한 검증 대상은 인증된 예약 쓰기의 Redis 원자적 고정 창 제한과 기존 예약 정합성 보존이다. Lua의 한 실행으로 INCR·최초 PEXPIRE·PTTL을 처리하고 기본 20회/60000ms, 명령 timeout 200ms를 유지한다. 보안 체인에서 인증/인가 뒤·취소 입력 검증 앞에 제한을 배치한다. PostgreSQL 업무 SQL·스키마·트랜잭션은 변경하지 않는다.

이 문서는 설계 산출물이다. 신규 제품 코드·시험·tasks·커밋·push는 이번 단계에 포함하지 않는다. [조사](research.md), [데이터 모델](data-model.md), [계약](contracts/rate-limit-contract.md), [검증 안내](quickstart.md)를 함께 읽는다.

## Technical Context

**Language/Version**: Java25, preview 없음; Wrapper9.7.1 현재 파일 확인. Boot4.1.1 유지.

**Primary Dependencies**: 기존 MVC/Security/JDBC/Redis starter와 BOM 유지. 로컬 캐시 source/API 확인: Spring Framework7.0.9, Security7.1.1, Data Redis4.1.1, Lettuce7.5.2.RELEASE. 구현 시 실제 해석 결과와 실행 버전을 docs/versions.md에 기록한다.

**Storage**: 기존 PostgreSQL18.6의 업무 V1 유지; Redis8.10.2의 만료 가능한 사용자 카운터만 추가. 기존 ContainerTestSupport의 태그·digest 유지.

**Testing**: Spring 없는 *Test, 실제 PG/Redis Testcontainers2.0.5와 RANDOM_PORT/Basic HTTP *IT, 기존 Gradle test/integrationTest/verify 발견·XML 게이트 유지. 기존008 최종 기록은 Unit150·IT257, 경쟁110회와 지원fixture5 포함이다. 이번 실행 결과가 아니다.

**Target Platform**: localhost CLI/API, UTF-8/UTC; Docker Desktop29.8.2 기동은 오케스트레이터 확인 정보다.

**Project Type**: 단일 Spring Boot 웹 백엔드.

**Performance Goals**: 처리량 목표 추가 없음. 명령 timeout200ms는 HTTP 전체 지연 보장이 아니다. 모든 장애·경쟁 검증은 유한 상한을 둔다.

**Constraints**: 8KiB→인증→제한→입력검증→서비스; Redis 장애는 쓰기503, 독립 GET/resource POST 유지; 미분류 결함500; 기본 정책 영구 비활성화 금지.

**Scale/Scope**: 사용자별20/60초, 경쟁 시험만10000/60초·11종 각10회. 9단계 전체 계약 검증·속성·변이·부하·배포는 후속.

## Constitution Check

*Phase0 이전 및 Phase1 설계 이후 재평가 결과: 설계 PASS. 구현 완료 판정과 별개다.*

| 원칙/게이트 | 사전 판단 | 설계 후 근거 |
| --- | --- | --- |
| I 경계/API 호환 | PASS | ratelimit만 지원 기능으로 추가; 기존 Writer/SQL 및 성공 응답 보존; 기존429/503 계약 구현 |
| II 근거 확인 | PASS | AGENTS, knowledge/README·rules전체·4도메인, 기준계획2~5/7절8단계, 실제 코드·빌드·008완료 기록 대조 |
| III 검증/증거 | PASS | TDD·실제 의존성·HTTP·모든 작업 종료·단일SQL; 미실행은 예정으로 표시 |
| IV 문서 동기화 | PASS | 신규 설계는 이 폴더; 구현 시 README/knowledge 목록·도메인·docs/계획 체크를 실제 상태에 맞춰 갱신. OpenAPI 기존 계약 대조 |
| V 미확정 정책 | PASS | 새 업무 정책 없음. 아래 실행 환경 의존성과 헌법 역사 TODO를 완료로 오인하지 않음 |
| 필수 정적검사 | 구현 완료 전 필수 | 루트 qodana.yaml, qodana-jvm:2026.2·any0 기준 유지. CLI/이미지/토큰 부재로 현재 미실행; 오케스트레이터가 실행/인증 경로 확인 중 |

헌법의 채택일 TODO와 과거 CI_CONTEXT는 이 작업이 임의 확정하지 않는다. 현재 외부 Actions 실행/보호규칙 최종 재확인은 오케스트레이터 담당이며 계획 작성의 업무 정책 미확정이 아니다. Qodana 인증·실행 환경 미해결은 구현 완료 게이트의 실제 blocker로 유지한다. 필수 정적검사 미실행을 통과로 표시하지 않는다.

## Project Structure

### Documentation (this feature)

```text
specs/009-redis-rate-limit/
  spec.md
  checklists/requirements.md
  plan.md
  research.md
  data-model.md
  contracts/rate-limit-contract.md
  quickstart.md
```

`tasks.md`는 후속 speckit-tasks 산출물이며 이번에는 생성하지 않는다.

### Source Code (repository root)

아래 신규 파일명은 구현 예정이다. 기존 경로는 유지한다.

```text
backend/src/main/java/com/limitedbooking/backend/
  ratelimit/RateLimitFilter.java                 # 신규
  ratelimit/RedisRateLimiter.java                # 신규
  ratelimit/RateLimitDecision.java               # 신규 record
  ratelimit/RateLimitProperties.java             # 신규 검증된 설정
  security/SecurityConfig.java                   # 체인 순서 연결
backend/src/main/resources/
  redis/rate-limit.lua                           # 신규
  application.yaml / application-local.yaml      # 기본 설정 명시
backend/src/test/java/com/limitedbooking/backend/
  ratelimit/RedisRateLimitIT.java                 # 신규 실제 Redis
  ratelimit/RateLimitApiIT.java                   # 신규 실제 HTTP/PG/Redis
  ratelimit/RedisFailureIT.java                   # 신규 장애/순서/독립 API
  ratelimit/RateLimitDecisionTest.java            # 신규 순수 경계
  support/RateLimitTestSupport.java               # 신규 소유 키 정리
  support/ContainerTestSupport.java               # 실제 의존성/설정 지원
  reservation/ReservationApiTestSupport.java      # 보호 검사 뒤 키 초기화
  reservation/ReservationConcurrencyIT.java      # 실제 Redis·높은 한도
  reservation/*IT.java / security/SecurityIT.java # 영향받는 HTTP fixture 전환
qodana.yaml                                     # 기존 정적검사 게이트 보존
```

**Structure Decision**: 단일 backend, 기능별 패키지와 기존 테스트 지원을 확장한다. Filter Bean이면 비활성 FilterRegistrationBean으로 서블릿 자동 등록을 막고 Security 체인만 소유한다. 취소 검증은 명명된 필터로 분리하거나 제한 필터 뒤에 명시적으로 등록해 익명 필터의 같은 order 의존을 없앤다. Controller의 디코딩 경로 본문 검증은 방어로 유지한다.

## Phase 0: 조사와 확정 설계

[research.md](research.md)에 공식 문서·설치 source 기반 선택, 대안 및 검증 한계를 기록했다. 스크립트 결과 직렬화, 경로 매칭, 예외 경계, 실제 장애 수단과 기존 fixture 영향에 대한 기술적 미확정은 해소했다. 실행 호환성은 구현 단계 검증 대상이다.

## Phase 1: 설계와 검증 순서

1. 설정/판정 경계 Unit과 실제 Redis IT부터 RED→최소 Lua/limiter→GREEN. API 골격 부재의 컴파일 오류만 RED로 인정하지 않는다.
2. HTTP 합산·인증·413·잘못된 입력·인코딩·재요청·중복 적용 RED→필터 체인 연결→GREEN. 기본 정책 시험의 한도는 올리지 않는다.
3. 실제 명령 지연/중단·접속 장애, 독립 API와 DB 실패 카운터 유지·TX 순서 검증을 추가한다. 테스트 Spy는 실제 호출을 관측하며 장애 통합을 mock으로 대체하지 않는다.
4. 영향받는 기존 HTTP fixture를 실제 Redis로 전환하고 원래 응답/DB 기대값을 유지한다. 경쟁 설정10000/60000와 회차별 키 초기화를 적용하며 부모 setup의 첫 종료실패 검사보다 앞에서 Redis/DB reset하지 않는다.
5. 기존110회·종료보호fixture·관련회귀·일반verify·`./gradlew build`·기존 `./gradlew verifyContractDependencies` 및 Qodana 필수 게이트를 확인한다. docs/test-evidence.md·versions·decisions, README·knowledge·기준 계획서 체크는 실제 근거로 갱신한다. 실제 실행 명령·종료 코드·빌드/의존성 검사 결과를 docs/test-evidence.md에 기록한다. 최종 Gradle 실행은 `./gradlew build verify verifyContractDependencies`로 합쳐 공유 test 태스크의 불필요한 중복 실행을 피할 수 있다. Qodana·빌드·의존성 검사 또는 기능 검증이 막히면 미완료로 보고한다.

FR001~004/011→RedisRateLimitIT·판정Unit, FR005~006/010/012→RateLimitApiIT, FR007~009/012→RedisFailureIT, FR013~014→기존HTTP/ReservationConcurrencyIT·종료fixture, FR015→일반verify·build·verifyContractDependencies·Qodana·docs 기록으로 연결한다.

## Complexity Tracking

헌법 위반이나 예외 승인 없음. 신규 저장 테이블·분산 락·장애 공개 API·영구 우회 플래그를 만들지 않는다.
