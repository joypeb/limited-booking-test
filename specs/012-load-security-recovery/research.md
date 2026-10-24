# Research: 11단계 로컬 부하·보안·장애 복구

근거: Explorer 갭 분석(2026-10-24), Advisor 설계 의견(2026-10-24), 실제 코드 확인. 미실행 추정은 “추정”으로 표시한다.

## R1. 기존 보안 커버리지
- 결정: 충족 항목은 재작성하지 않고 연결한다. 추가는 예약 경로 `X-User-Id` 위조와 로그 비노출 2건.
- 근거: `ReservationOwnershipIT.otherUserAndAdminCannotDistinguishMissingReservation`·`cancelProtectsOwnershipAndLeavesInventoryUnchanged`·`getRequiresAuthenticationAndValidUuid`·`oversizedCancelIsRejectedBeforeAuthentication`, `ReservationCreateIT.rejectsInvalidBodiesAndDoesNotReserveValidKey`, `ApiContractIT.invalidInputMatchesExactContract`, `SecurityIT`의 401/403/413/UUID 사례. 위조 헤더는 `/api/resources`에서만 검증됨.
- 대안: SecurityIT에 전 항목 재작성 → 중복·유지 비용 증가로 기각.

## R2. DB 연결 중단 재현
- 결정: 관리 연결로 `ALTER DATABASE <db> ALLOW_CONNECTIONS false` + `pg_terminate_backend`. 복구는 `ALLOW_CONNECTIONS true`.
- 근거: 컨테이너 재시작은 매핑 포트가 바뀌어 같은 컨텍스트 재연결 검증 불가. NOLOGIN은 기존 연결을 끊지 못함.
- 위험(추정): 실행 중 쿼리가 종료되면 `UncategorizedSQLException`(08006/57P01)이 500으로 새어 나갈 수 있음 → 재현 시 결함으로 기록하고 분류 최소 확장.

## R3. 응답 정지와 소켓 읽기 상한
- 결정: `docker pause`로 재현. 무한 대기가 재현되면 pgjdbc `socketTimeout=7`(초)을 공통 설정에 추가.
- 근거: statement_timeout은 서버 타이머라 서버 정지 시 동작하지 않음. lock 2초 < statement 5초 < socket 7초 계층이면 정상 서버 timeout을 앞지르지 않음. Hikari 연결 획득 3초가 획득 단계를 제한.
- 위험: 7초보다 긴 정상 비트랜잭션 조회가 끊길 수 있음(현재 업무 조회는 단건 PK 조회로 해당 없음). 5초 이하로 두면 statement timeout보다 먼저 소켓이 끊김.

## R4. 응답 시간 상한
- 결정: 잠금 4초, 연결 거부 6초, 응답 정지 12초.
- 근거: 각 설정값 + macOS Docker Desktop 지연 여유. 기존 생성 잠금 9초는 유지.

## R5. 응답 유실 재현
- 결정: raw Socket 전송 → DB 커밋 폴링(100ms·최대 5초) → 응답 미수신 close → 같은 키 재시도.
- 근거: 제품 코드 후크 불필요(FR-011), 커밋을 DB로 확인(계획서 11단계).
- 대안: 테스트 전용 지연 필터 → 웹 계층 침투로 기각.

## R6. 로그 캡처
- 결정: Logback `ListAppender`를 루트 로거에 붙여 formatted message·arguments·throwable proxy 문자열 검사.
- 위험: Tomcat이 System.err로 직접 쓰는 출력은 미포함 → 한계로 기록.

## R7. load DB·제한값
- 결정: `prepare-db.sh`가 `reservation_load` 부재 시 생성, Flyway는 앱 기동 시 적용. 제한값은 `LOAD_RATE_LIMIT` 필수 환경 변수.
- 근거: Compose 초기화 스크립트는 기존 volume에 미적용. 기본값 없는 필수 값으로 변경값 명시 보장.

## R8. k6
- 결정: 설치된 k6 v2.3.0(darwin/arm64) 사용, 버전 기록. Counter threshold `count==N`으로 정확한 건수 판정, `--summary-export`로 결과 저장.
- 확인 필요: v2.3.0의 threshold·summary-export·expectedStatuses 동작은 첫 실행에서 확인(미실행).
