# 조사: 예약 생성·성공 키·롤백

작성일: 2026-10-19. 조사 상태는 설계 근거 확인이며 현재 환경에서 새 기능을 실행한 증거가 아니다. 읽기 전용 조사 에이전트가 실제 저장소와 공식 자료를 대조했고 주 에이전트가 설계에 통합했다. 업무 정책은 [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절을 따른다.

## R1. 트랜잭션 밖 조정과 별도 Writer

- **결정**: Service에는 `@Transactional`을 붙이지 않는다. 진입 시 `TransactionSynchronizationManager.isActualTransactionActive()`로 상위 활성 트랜잭션을 거부하는 fail-fast 검사를 둔다. 별도 public Writer 메서드에 `@Transactional(isolation = READ_COMMITTED)`를 적용한다. RuntimeException은 프록시 밖으로 전파하고 Service에서만 대상 유일 위반을 처리한다.
- **이유**: 실패한 Writer의 롤백이 끝난 뒤 새 조회가 가능해야 한다. UPDATE 0건은 SQL 오류가 없는 정상 트랜잭션이므로 Writer 안의 새 SELECT로 기존 키를 재확인할 수 있다. READ COMMITTED는 문장별 새 관측과 경쟁 UPDATE의 조건 재평가를 제공한다.
- **대안**: Service 전체 트랜잭션·자기 호출은 롤백 뒤 조회 경계를 깨므로 제외한다. REQUIRES_NEW는 상위 트랜잭션 허용 정책을 새로 만들고 연결을 더 요구하므로 채택하지 않는다. 전역 SERIALIZABLE·Redis 락도 기존 설계와 다르다.
- **근거**: [Spring 선언적 트랜잭션](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html), [PostgreSQL 18 READ COMMITTED](https://www.postgresql.org/docs/18/transaction-iso.html). 상위 트랜잭션 검사와 검증 배치는 이 프로젝트의 계획서 금지 규칙을 강제하기 위한 설계 선택이다.

## R2. 유일 위반 분류의 정확성

- **결정**: `UniqueViolationClassifier.isReservationUserKeyViolation(Throwable)`는 cause 및 SQLException nextException 연결을 순환 방지 identity 집합으로 탐색한다. 같은 PSQLException 객체에서 `getSQLState() == 23505`와 `getServerErrorMessage().getConstraint() == uq_reservations_user_key`가 모두 맞아야 true다. null 또는 메타데이터가 없으면 false다.
- **이유**: 일반 DuplicateKeyException만 보면 예약 PK 충돌까지 성공 재현으로 숨길 수 있다. SQLSTATE와 제약명이 서로 다른 예외에서 각각 발견된 것을 합쳐 판별해서도 안 된다.
- **대안**: 메시지 문자열 파싱·constraint 부분 문자열·모든 무결성 위반 처리·무제한 재시도는 제외한다.
- **의존성 영향**: 현재 Gradle의 PostgreSQL `runtimeOnly`를 동일 BOM의 `implementation`으로 옮긴다. 중복 testImplementation은 구현 시 정리 가능하다. 새 버전·라이브러리 추가가 아니다.
- **검증**: 대상 23505/정확한 이름만 true, 다른 PK 23505·FK 23503·연결 08 계열·정확한 이름과 다른 SQLSTATE·null·순환 체인 false. 일반 SQLException 메시지에 이름만 들어간 경우도 false. 실제 DB 유일 위반을 별도 Writer 호출에서 유발해 메타데이터와 전체 롤백을 대조한다.
- **근거**: [PostgreSQL 오류 코드와 구조화된 객체명](https://www.postgresql.org/docs/18/errcodes-appendix.html), [PGJDBC ServerErrorMessage](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/util/ServerErrorMessage.html).

## R3. 최초 응답과 저장 시각

- **결정**: Writer는 주입된 Clock에서 Instant를 얻어 MICROS로 절삭하고 UUID를 생성한다. 예약 INSERT RETURNING의 저장된 행으로 최초 응답을 만든다. 모든 재현은 저장된 id/resourceId/createdAt과 고정 CONFIRMED를 사용한다.
- **이유**: 최초의 나노초 값과 재현 시 마이크로초 값이 달라지면 성공 결과 재현 계약을 어긴다. 기존 ResourceService도 MICROS 절삭을 사용한다.
- **대안**: 요청 시각으로 즉시 응답 생성·DB 저장값 대신 재요청 시 Clock 사용·현재 CANCELLED 상태를 생성 응답에 사용은 제외한다.
- **검증**: 고정 Clock `2026-10-19T00:00:00.123456789Z`를 넣으면 저장·최초·재현은 `2026-10-19T00:00:00.123456Z`로 같다. 시간대·JSON 문자열을 Instant로 읽은 값과 저장값을 함께 비교한다.

## R4. 실패 주입과 실제 저장 원자성

- **결정**: 기존 실제 PostgreSQL/HTTP 지원을 재사용하고 테스트 전용 Repository spy 또는 래퍼에서 실제 UPDATE 이후 INSERT 직전에 한 번 실패시킨다. 주입 지점은 같은 트랜잭션의 JDBC로 차감 상태를 확인한 후 RuntimeException을 던진다. 주입 해제 후 같은 키 요청을 성공시킨다.
- **이유**: Writer나 UPDATE 자체를 mock하면 실제 트랜잭션의 부분 반영 방지를 검증할 수 없다. HTTP 종료 후 별도 연결의 행·수량·키 조회가 롤백의 증거다.
- **대안**: 운영 실패 플래그·공개 장애 파라미터·전체 DB mock은 제외한다. Spy 설정 시 실제 SQL을 호출할 수 있는 `when` 대신 `doAnswer` 계열을 사용한다. AOP 프록시와 spy의 상호작용은 실행 시 확인하며 실패하면 테스트 전용 위임 래퍼로 전환한다.
- **근거**: [Spring 테스트 Bean spy](https://docs.spring.io/spring/reference/7.1/testing/annotations/integration-spring/annotation-mockitobean.html). 문서 확인은 현행 프로젝트 조합의 실행 호환성 확인을 대신하지 않는다.

## R5. HTTP 오류와 트랜잭션 시작 장애

- **결정**: 기존 ApiExceptionHandler에 SOLD_OUT·IDEMPOTENCY_CONFLICT 전용 409를 추가한다. ResourceNotFoundException의 404·입력 400·공통 413/415·인증 401을 재사용한다. JDBC 연결/timeout의 기존 503 매핑을 유지하되 `CannotCreateTransactionException` 안의 실제 연결 획득 장애도 좁게 분류한다. 모든 TransactionException·무결성 위반을 503으로 묶지 않는다.
- **이유**: Writer 진입의 연결 획득 실패는 기존 DataAccessException 핸들러만으로 포괄되지 않을 수 있다. 알 수 없는 내부 오류는 500으로 드러나야 한다.
- **대안**: 전체 Exception→503/409 변환과 새로운 내부 오류 code 추가는 제외한다. 500은 상태 및 민감정보 비노출만 확인하고 고정 OpenAPI에 없는 오류 code를 발명하지 않는다.
- **근거**: 실제 `ApiExceptionHandler.java`, [Spring CannotCreateTransactionException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/CannotCreateTransactionException.html).

## R6. 실행 범위와 단계 경계

- **결정**: 일반 test는 Unit, integrationTest는 일반 IT, verify는 두 그룹이다. 선택 명령의 `--tests`는 앞에 지정된 integrationTest에 붙여 세 IT를 선택하고 Unit은 별도로 전체 실행한다. 현재 XML/listener 0건·skip 거부를 유지한다.
- **이유**: 없는 테스트는 계획 명령이며 현재 통과 근거가 아니다. 실제 경쟁 반복·취소·Redis·자동 계약·Property/PIT·부하는 후속 단계다.
- **대안**: 검증 편의를 위한 skip·mock DB·버전 하향·전 태스크 반복은 채택하지 않는다.

## 미해결 항목

미확정 업무 정책과 미해결 설계 질문은 없다. 실제 Spring 프록시·spy·드라이버 메타데이터·트랜잭션 timeout 매핑의 실행 검증은 구현 단계의 필수 확인이며 이미 통과했다고 가정하지 않는다.
