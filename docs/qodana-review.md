# Qodana 지적 분류와 수정 계획 (2026-10-20)

입력 보고서 revision: `3b26a2517e8111ee28d3af0e9fd90873d1e81b89`.
작업 시작 HEAD: `3cfabe7`, 기준 브랜치 `develop`, 생성한 작업 브랜치 `fix/qodana-findings`.
보고서 행 번호 대신 현재 클래스·메서드를 대조했다. 기존 Wrapper 9.8.0 변경은 사용자 변경으로 보존한다.

## 코드·설정 수정 대상: 12건

| 지적 | 건수 | 원인과 조치 | 검증 |
|---|---:|---|---|
| SecurityConfig username null 상수 조건 | 1 | UserDetailsService의 null 불허 계약에 맞춰 불가능한 분기 제거 | 실제 Basic 인증 HTTP 테스트 |
| SecurityConfig null 계약 재정의 | 4 | LocalUserPrincipal과 익명 필터 메서드에 좁은 범위의 JSpecify NullMarked 적용 | 컴파일·인증·취소 HTTP 테스트 |
| RequestBodyLimitFilter null 계약 재정의 | 2 | doFilterInternal에 NullMarked 적용 | 컴파일·본문 상한 HTTP 테스트 |
| ApiExceptionHandler SQL 예외 null 역참조 | 1 | nullable SQLException을 한 번 조회하고 null이면 원래 예외 재throw. 55P03만 기존 503 유지 | 신규 Unit 6건, RED→GREEN |
| local YAML 사용자 비밀번호 프로퍼티 미해결 | 3 | 추가 Spring 설정 메타데이터로 타입·설명 제공. 기존 Value 바인딩과 환경 변수 유지 | JSON·리소스 포함 검사, 기존 인증 HTTP 테스트 |
| ResourceRepository 불필요한 inserted | 1 | requiredSingleResult의 반환값 직접 반환. SQL·RETURNING 유지 | 저장소·대상 API IT |

## 의존성 조사 후 수정 대상으로 전환: 3건

- Jackson 2 core·databind: 테스트 구성에서 BOM `2.21.8`로 정렬했다. annotations는 공식 BOM의 `2.21`을 유지한다. 애플리케이션 Jackson 3 BOM에는 적용하지 않는다. [databind 공식 릴리스](https://raw.githubusercontent.com/FasterXML/jackson-databind/2.21/release-notes/VERSION-2.x), [core 공식 릴리스](https://raw.githubusercontent.com/FasterXML/jackson-core/2.21/release-notes/VERSION-2.x), [공식 BOM](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/jackson-bom/2.21.8/jackson-bom-2.21.8.pom)을 근거로 한다.
- Rhino: 테스트 구성에서 `1.7.15.1`로 변경했다. [Mozilla 보안 권고](https://github.com/mozilla/rhino/security/advisories/GHSA-3w8q-xq97-5j7x)의 수정 버전이며 1.7 계열을 유지한다.
- 실제 Gradle dependencyInsight에서 위 버전 선택을 확인했고 계약 도구 프로브 3건이 통과했다. 취약 입력을 직접 공격·재현한 검증은 아니다.

## 최종 추가 검토·유지 대상: 3건

| 지적 | 건수 | 판단 기준 |
|---|---:|---|
| BackendApplication.main public 불필요 | 1 | Java 언어 허용 범위와 Spring Boot 실행 진입점 탐지는 별개다. [Boot MainClassFinder](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/loader/tools/MainClassFinder.html)의 public static main 탐지 계약을 고려해 유지한다. 검사 억제로 숨기지 않는다. |
| json-patch 1.13·jackson-coreutils 2.0 취약점 | 2 | 공식 수정 버전을 확인하지 못했다. 계약 검증기를 손상할 수 있는 임의 제외·버전 추정은 하지 않았다. 유지보수된 검증기 대체·포크 패치를 별도 검증 대상으로 검토해야 한다. |

의존성은 `swagger-request-validator-core:2.46.1`에서 유입된다. 애플리케이션은 Jackson 3 (`tools.jackson`)을 사용하며 경고의 Jackson 2 (`com.fasterxml.jackson`)와 구분한다.

미해결 취약점의 근거는 json-patch [Copy/Move](https://github.com/java-json-tools/json-patch/issues/170)·[apply 자원 소모](https://github.com/java-json-tools/json-patch/issues/167)·[merge 재귀](https://github.com/java-json-tools/json-patch/issues/168), coreutils [URL SSRF](https://github.com/java-json-tools/jackson-coreutils/issues/64)·[pointer 자원 소모](https://github.com/java-json-tools/jackson-coreutils/issues/66)·[BigDecimal 자원 소모](https://github.com/java-json-tools/jackson-coreutils/issues/65)다. [CVEProject 원문](https://raw.githubusercontent.com/CVEProject/cvelistV5/main/cves/2026/86xxx/CVE-2026-86512.json)도 조사했다. 테스트에서 저장소의 고정 OpenAPI를 읽고 실제 HTTP 응답을 검증하지만 취약 함수의 간접 도달 가능성은 미확정이다. 테스트 전용이라는 이유로 오탐 판정하지 않는다.

## 파일 책임과 실행 순서

사용자 지시에 따라 독립 파일을 병행 배정했다. `subagent-driven-development`의 파일 책임·검증·리뷰 절차를 적용하되, 병렬 구현 금지 지침보다 사용자의 명시적 병행 지시를 우선한다.

1. 보안 담당: SecurityConfig·RequestBodyLimitFilter만 수정.
2. SQL 담당: ApiExceptionHandler·회귀 Unit·ResourceRepository만 수정.
3. 조사 담당: 의존성·main 호환성 읽기 전용 조사.
4. 루트: 메타데이터·문서·순차 Gradle 실행·최종 diff 검토. 공유 빌드·Git 작업은 병행하지 않는다.

API·수량·SQL·인증 계약 변경이 없으므로 계획서·OpenAPI 기대값을 변경하지 않는다. Qodana 재실행 전에는 지적 소멸을 확정하지 않는다. 실제 실행 결과는 [검증 기록](test-evidence.md)에 기록한다.
