# 프로젝트 지식 문서

이 프로젝트는 Java/Spring 개발 감각을 복구하고 AI가 작성한 코드를 직접 이해·검증하기 위한 localhost 전용 한정 수량 예약 백엔드다. PostgreSQL이 수량·예약·성공한 생성 키의 원본이며 Redis는 인증된 사용자의 예약 생성·취소 요청 제한만 담당한다. 핵심은 기능 수보다 수량 보존, 멱등성, 취소의 원자성과 재현 가능한 검증이다.

설계 기준은 [로컬 개발 계획](<../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절이다. 이 폴더는 그 기준을 도메인 지식과 개발 규칙으로 정리한다. 작업 절차와 저장소 주의사항은 [AGENTS.md](../AGENTS.md)도 따른다. 최신 사용자 지시를 우선하고, 기준 간 차이는 확인·기록한 뒤 관련 문서를 함께 갱신한다.

## 작업 전 읽는 순서

1. 이 안내를 읽고 `knowledge/` 하위 Markdown 목록을 확인한다.
2. 아래 개발 규칙을 모두 읽는다.
3. 작업 대상과 연관 도메인의 문서를 읽고, 연결된 계획서 절에서 기대값을 확인한다.
4. 실제 코드·SQL·테스트·설정을 확인해 구현 여부와 차이를 구분한다.
5. 한 단계의 한 검증 대상을 정해 개발하고 관련 지식 문서와 실행 기록을 갱신한다.

## 개발 규칙 목록

| 문서 | 내용 | 계획서 근거 |
|---|---|---|
| [구조와 범위](rules/architecture.md) | 프로젝트 범위, 기술, 패키지·계층 책임 | 1~3절 |
| [도메인과 트랜잭션](rules/domain-and-transactions.md) | 수량 보존, 멱등성, SQL, 잠금·롤백 | 2·5절 |
| [API와 보안](rules/api-and-security.md) | 엔드포인트, 엄격한 입력, 인증·권한, 오류 | 2·4절, 7절 4·6단계 |
| [Redis 요청 제한](rules/rate-limit.md) | 고정 창, Lua·TTL, 처리 순서·장애 | 2절, 7절 8단계 |
| [개발과 검증](rules/development-and-testing.md) | 단계별 개발, 테스트 분류·경쟁·계약·속성·변이 | 1.3·6~9절 |
| [환경과 기록](rules/environment-and-records.md) | 로컬 설정, 버전·비밀정보, 증거·CI·부하 | 3절, 7절 1·11·12단계, 8.3·13절 |
| [커밋](rules/commit.md) | 커밋 단위·메시지·스테이징·검증 | 1.3·8.3절, 사용자 요청으로 추가한 규약 |
| [Git Flow](rules/git-flow.md) | 브랜치 역할·작업·병합·로컬 버전 관리 | 단계별 검증·로컬 범위, 사용자 요청으로 추가한 규약 |

## 도메인 문서 목록과 작성 방법

- [_template.md](domain/_template.md): 새 도메인 문서의 공통 형식. 템플릿 자체는 특정 도메인의 확정 계약이 아니다.
- [resource.md](domain/resource.md): 예약 대상의 책임·입력·수량 불변식·권한과 코드 포인트 기준 이름 길이. 설계 지식이며 구현·실행 검증은 미완료다.
- [reservation.md](domain/reservation.md): 예약·멱등 재현·취소·트랜잭션·경쟁의 설계 지식.
- [security.md](domain/security.md): 로컬 인증·권한·소유권·본문 필터의 설계 지식.
- [ratelimit.md](domain/ratelimit.md): Redis 고정 창·처리 순서·장애의 설계 지식.
- 위 업무 기능은 미구현이다. 1단계 도구 프로브의 실제 HTTP·DB·Redis 최소 검증과 업무 기능 검증을 구분한다.
- 첫 버전의 업무 도메인은 예약 대상(`resource`)과 예약(`reservation`)이다. `security`·`ratelimit`은 이를 지원하는 기능이며, 필요할 때 같은 형식으로 책임과 계약을 문서화한다. `common`을 독립 업무 도메인으로 가정하지 않는다.
- 파일 이름은 `<domain-name>.md`를 사용한다. 근거·경계·기대 결과를 먼저 적고, 코드·SQL·테스트·명령은 실제 존재 여부를 확인해 연결한다.
- 템플릿의 모든 절을 유지한다. 해당 없는 항목은 이유를 적고, 미확정 사항은 질문과 영향 범위를 적는다. 다른 도메인과 공유하는 규칙은 관련 문서로 연결한다.

## 문서와 구현 상태

2026-10-13 1단계 수행 후 애플리케이션은 backend/, 기본 패키지는 com.limitedbooking.backend이며 Java25·Boot4.1.1·Gradle9.7.1을 유지했다. MVC·Security·JDBC·Redis·Flyway·Actuator 및 검증 의존성을 추가했다. [실행 안내](../README.md), [버전](../docs/versions.md), [실행 증거](../docs/test-evidence.md)에 실제 상태를 연결한다.

Gradle test/verify/verifyContract/verifyProperty/deepTest/mutationSmoke의 도구 최소 실행을 확인했다. Unit3·일반IT6·Contract3·Property3(내부101회), PIT 생성1·탐지1이다. 프로브는 도구 확인용이며 후속 ResourceRules/IdempotencyKey/CancellationDecision으로 대체할 예정이다. 업무 API·SQL·인증 UUID·요청 제한·경쟁 검증은 아직 미구현이다. 업무 스키마는 2단계에서 만들며 시험 migration은 test resources에만 있다.

`knowledge/`는 지속적인 도메인 지식·규칙을 보관한다. 현재 [OpenAPI 초안](../docs/openapi.yaml), [결정 기록](../docs/decisions.md), [문서 검토 증거](../docs/test-evidence.md)를 작성했다. 이름 공백 범위는 Java strip으로 확정해 계약 문서 인수를 마쳤고, 본인 설명은 질의·보완 뒤 사용자 확인으로 T025를 처리해 0단계 문서·학습 인수를 마쳤다. 독립 재설명 시험과 기능 실행 검증은 미실시다. [버전 기록](../docs/versions.md)은 작성·실행 대조했고, docs/performance.md는 후속 부하 단계에서 작성할 예정이다. 문서 작성과 실제 기능·HTTP·DB·Redis 검증을 구분한다.
