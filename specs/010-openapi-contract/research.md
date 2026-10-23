# 조사: 9단계 계약 검증

**상태**: 2026-10-23 저장소 읽기·독립 조사에 근거한 설계 기록(historical record)이다. 작성 당시에는 신규 업무 테스트 실행·호환성 재검증을 하지 않았다. 이후 R1~R6 결정은 구현·실행 인수됐으며 현재 결과는 [실행 증거](../../docs/test-evidence.md)와 [작업 목록](tasks.md)을 따른다. 아래 "구현 시 검증할" 표현은 작성 당시 기준이다.

## R1. 고정 명세와 기존 검증기

- **Decision**: docs/openapi.yaml → processTestResources의 business.openapi.yaml을 그대로 읽는다. OpenAPI3.0.3 확인, parser 메시지/해결 불가 ref는 오류다. 기존 ToolchainContractValidator의 파싱 패턴을 재사용하되 본문 있는 업무용 검사기는 별도로 만든다.
- **Rationale**: 기존 프로브는 본문 없는 GET만 받으며 업무 명세 파싱까지 확인한다. 이를 확장 완료로 오인하면 FR-002를 만족하지 못한다.
- **Alternatives considered**: 자동 생성 문서를 기대값으로 사용, Swagger2 변환, 새 라이브러리/Pact 도입은 고정 계약·범위·기존 보안 조치와 맞지 않아 제외한다.
- **근거**: backend/build.gradle, support/ToolchainContractValidator.java, common/ToolchainContractIT.java, docs/versions.md·decisions.md의 기존 채택 기록. core3.0.0/networknt2.0.1을 유지하며 전이 Jackson2의 테스트 한정 BOM 및 Swagger2 converter 제외를 유지한다.

## R2. void와 의도적으로 무효인 요청

- **Decision**: 제공 assertValid의 인자·반환형은 유지한다. 기본 인스턴스는 요청 위반0을 요구하고, 별도 불변 팩터리/설정으로 정확한 예상 요청 위반을 가진 인스턴스를 만든다. 요청 보고서와 응답 보고서를 별도로 수집해 예상과 비교한 뒤 모든 실패를 합쳐 AssertionError로 반환한다. 응답 검사를 요청 실패 때문에 건너뛰지 않는다.
- **Rationale**: 오류 응답을 검증하면서 원래 무효인 요청의 스키마 위반을 명시해야 한다. 상태400만 보고 request 오류를 모두 무시하면 정상 요청 회귀/무관한 오류가 묻힌다.
- **Alternatives considered**: 반환형을 ValidationReport로 변경, ThreadLocal 전역 허용, request 전체 OFF, interaction report를 response 전용으로 위장하는 방식은 제외한다.
- **구현 확인 항목**: 독립 조사에서 로컬 공식 sources.jar의 `validateRequest(Request)`·`validateResponse(String path, Request.Method method, Response)`와 `ValidationReport.Message.getKey/getContext/getNestedMessages`를 확인했다. 고정 버전의 개별 검증 호출과 메시지 key/context/중첩 원인을 사용한다. key+location 기반 정규화된 정확한 multiset을 fixture로 고정하고 실제 출력으로 최초 확인한다. 메시지 문장 전문/부분문자열에 허용 여부를 맡기지 않는다. 알 수 없는 메시지/context는 실패다. 예외를 빈 보고서로 치환하지 않는다.

## R3. 스키마의 표현 한계

- **Decision**: 스키마 구조와 별도로 테스트 전용 raw JSON 토큰 검사·입력 의미 검사·HTTP 기대 결과를 결합한다. 중복 키는 tree 변환 전 탐지, capacity는 정수 토큰만, 이름은 독립 명시 기대값으로 strip 후 코드포인트 길이 검사, UUID는 표준 문자열 형식 확인. 8KiB는 실제 UTF-8 바이트/전송 관측과 응답으로 증명한다.
- **Rationale**: 수학적 integer 스키마가 1.0을 허용하거나 parser가 중복 키를 덮어쓸 수 있다. 요청 이름 원문 maxLength100은 strip 전 유효 입력을 잘못 거부한다.
- **Alternatives considered**: 스키마만으로 strict JSON·우선순위·권한·수량 보존 증명, 제품 ResourceRules를 그대로 호출해 독립 기대값 구성은 제외한다.
- **실패 분리**: library가 중복 키 등을 먼저 거부하면 그 정확한 key/location을 함께 예상한다. schema report가 비어도 raw violation이 반드시 있어야 한다. 정책상 유효한 입력이 library에 거부되면 schema/adapter 원인을 조사하며 정상 요청 위반을 허용하지 않는다.

## R4. 의미·연결 검증

- **Decision**: 개별 교환은 명시 상태 allowlist, UUID/타입/필드, 미디어 타입, 필수 헤더, UTC·마이크로초·취소 관계·Location-id·오류 status/code/instance를 검사한다. Replay 최초/재요청, Retry-After와 판정PTTL, 생성 최초값·현재 GET 관계는 실제 시나리오가 추가 검사한다.
- **Rationale**: response header 선언은 필수 존재를 자동 보장하지 않으며 default는 500을 잘못 허용할 수 있다. 단일 교환만으로 과거 최초 값/PTTL을 알 수 없다.
- **Alternatives considered**: 일반 date-time만 사용, 헤더 설명만 추가, default 통과, 관측 후 응답값으로 기대값 계산은 제외한다.
- **근거**: 실제 DTO 3종·ResourceController·ReservationController·ApiExceptionHandler, RateLimitApiIT의 test-only 판정PTTL 관측. type은 about:blank를 유효하게 유지하고 예시 문구를 정책으로 올리지 않는다.

## R5. 실제 의존성·종료·비용

- **Decision**: ContainerTestSupport의 소유 DB/Redis/동적 포트·RANDOM_PORT와 기존 인증·키/DB 지원을 재사용한다. DB503은 소유 DB 연결 불가 상태를 별도 fixture에서 만들고 다섯 작업을 검사한다. Redis503은 실제 소유 Redis 중단 fixture로 생성/취소를 검사하며 독립 API 정상도 확인한다. 쓰기 후 DB timeout/롤백은 기존 ReservationRollbackIT·RedisFailureIT 회귀로 유지한다.
- **Rationale**: mock 예외만으로 실제 의존성·필터 응답 계약을 증명하지 않는다. 파괴적 장애와 정상 시퀀스를 분리하면 복구/시간 비용과 오염이 줄어든다.
- **Alternatives considered**: 개발 DB 중단, Redis flushAll, 제품 장애 파라미터, 전체 경쟁 시험 복제, 무한 polling은 제외한다.
- **경계**: 클라이언트 connect3초/request9초, raw socket read10초, client 종료10초·container 종료10초의 기존 상한 패턴. 서버 in-flight 종료 확인도 유한 deadline으로 한다. 미종료/전송 불확실은 최초 StopState를 유지해 이후 reset/준비/제출을 차단하며 suppressed 오류를 보존한다. DB 준비는 요청 전에 커밋, 클래스 병렬 실행 없음.

## R6. 탐지력과 실제 DTO 타입변이

- **Decision**: 정상 실제 교환을 baseline으로 응답 사본의 한 요소만 훼손해 독립 거부를 확인한다. 이와 별도로 ResourceView의 available record component를 int→String으로 임시 변경하고 기존 (UUID,String,int,int) 호출을 받는 보조생성자에서 숫자를 문자열로 바꾸는 실험을 설계한다. 실제 DTO 파일의 타입과 직렬화 결과가 바뀌며 서비스/SQL·고정 명세·테스트 기대값은 보존한다.
- **Rationale**: 현재 호출은 int 생성자이고 ResourceView available 산술 사용은 발견하지 않았다. 보조생성자는 호출부와 test compile을 유지하기 위한 최소 보완이며 실제 숫자 필드가 문자열이 되어 HTTP schema type 위반을 만들 수 있다. 실제 컴파일과 HTTP 실패로 최종 입증해야 한다.
- **Alternatives considered**: 컴파일만 깨뜨리는 record 변경, DTO 아닌 응답 사본 변경으로 대체, schema도 함께 문자열로 변경, serializer 설정만 변경은 완료 기준이 아니다.
- **인수**: compileJava·compileTestJava 성공 → 실제 ApiContractIT HTTP schema 타입 실패 → 정확한 원복 → 동일 시험 통과 → 최종 전체 게이트. 실험 중 다른 작업 금지, 원래 사용자 변경을 보존하고 git checkout/reset으로 파일을 덮어쓰지 않는다.

## 해소 상태

NEEDS CLARIFICATION 없음. 업무 정책은 승인 명세를 따른다. 라이브러리 보고서 세부 key/context·실제 DTO 변이 컴파일·정확한 실행 수는 구현 시 검증할 기술 증거이며 이미 실행됐다고 주장하지 않는다. Qodana는 사용자 추후 직접 실행·이번 게이트 제외다. Wrapper는 이 worktree 9.7.1을 유지한다.
