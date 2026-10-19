# 5단계 검증 안내

작성일: 2026-10-19. **2026-10-19 구현 후 지정 인수(Unit137/IT28)를 실행했다. 명령 안내와 실제 결과는 [실행 증거](../../docs/test-evidence.md)로 구분한다.**

## 전제

- Java 25와 기존 Gradle Wrapper, Testcontainers가 사용할 Docker가 필요하다. 버전·이미지는 [versions](../../docs/versions.md)·기존 ContainerTestSupport의 고정값을 유지한다.
- 테스트는 격리된 PostgreSQL 동적 포트와 테스트 전용 자격 증명을 사용한다. Compose의 reservation_local/load DB에 연결하지 않는다. Redis 제한은 아직 후속이며 이번 생성 IT는 기존 postgresOnly 지원을 사용할 수 있다.
- 신규 세 IT는 동적 접속 정보와 함께 `spring.datasource.hikari.maximum-pool-size=10`, `spring.datasource.hikari.connection-timeout=3000`을 각각 명시한다. local 프로필 활성화로 개발 DB 설정을 가져오지 않는다. 장애 사례 전에 실제 DataSource의 설정값을 확인하며 정확히3초에 응답해야 한다는 판정 대신 설정값·유한 종료·503·무변경을 확인한다.
- [계약](contracts/reservation-create-contract.md)·[데이터 모델](data-model.md)의 신규 생성 코드와 지정 세 IT·분류기 Unit이 작성돼 있어야 한다.
- 구현 브랜치는 `feature/reservation-create`다. 기존 `docs/reservation-create-spec`의 미커밋 명세 문서를 보존한 채 develop에서 생성했다. 임의 reset·clean·stash 또는 문서 덮어쓰기를 하지 않는다.

## 한 검증 대상씩 실행

저장소 루트에서 아래 작업 위치로 이동한다. 기존 Wrapper를 사용한다.

```sh
cd backend
```

첫 단계는 새 최초 생성 테스트 하나를 실행해 기능 미구현의 실패를 확인하는 것이다. 예시 메서드명은 실제 작성명에 맞춘다. 컴파일 오류는 기능 RED가 아니다.

```sh
./gradlew integrationTest --tests '*ReservationCreateIT.createsFirstReservation'
```

최초 성공·재현·실패 복구를 각각 RED→GREEN으로 개발한다. UPDATE0의 Writer 재현/충돌 분기는 T019에서 직접 호출하는 순수 테스트·기능 RED를 먼저 확보한 뒤 T020에서 최소 구현·GREEN을 확인한다. 기존 없음/품절 분기는 회귀로 기록한다. 순수 분류기 테스트 명령은 다음과 같다.

```sh
./gradlew test --tests '*UniqueViolationClassifierTest'
```

선택된 세 통합 테스트와 전체 Unit을 함께 실행한다. `--tests`는 바로 앞 integrationTest의 옵션이며 test는 전체 Unit이다. 아래 명령을 실제로 실행했을 때 각 클래스가 발견됐는지 XML도 확인한다.

```sh
./gradlew test integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT' --tests '*ReservationRollbackIT'
```

기능 정리 후 일반 회귀를 실행한다.

```sh
./gradlew verify
```

기존 게이트는 listener/XML의 현재 실행 수가 양수이고 실패·오류·skip이 0이어야 한다. 캐시 결과나 없는 테스트로 통과를 주장하지 않는다. test/build/verify는 자동 계약·속성·변이·부하 전체를 뜻하지 않는다.

## 기대 관측

| 검증 | 통과 기준 |
| --- | --- |
| 최초 생성 | 201, Location, false, CONFIRMED 한 행, capacity1/available0 |
| 같은 요청 2회 | 201/같은 Location·id·resourceId·status·createdAt, 두 번째 true, 총 한 행 |
| 충돌·격리 | 같은 사용자·키 다른 대상409, 두 번째 수량 불변; 다른 사용자 동일 키/같은 사용자 다른 키는 별도 성공 |
| 입력·실패 키 | 무효 키/본문400, 없는 대상404, 품절409, 잘못된 형식415, 초과413, 미인증401; 실패가 키를 예약하지 않음 |
| 실패 키 재사용 | 유효 키 K의 잘못된 본문400 후 본문만 바로잡은 동일 키201/false; 품절 대상 A의409 후 같은 K로 예약 가능한 B의201/false. 실패 직후 예약/키 없음·수량 불변, 재시도 후 고유 예약1개·보존식 확인 |
| 시각·취소된 준비 상태 | 나노초 Clock 입력에도 저장/최초/재현 마이크로초 값 동일; CANCELLED 행의 현재 상태 불변·생성 결과 CONFIRMED |
| INSERT 직전 실패 | 같은 트랜잭션 내부에서는 차감 관측, 종료 후 별도 연결에서는 available1·행0·키 없음; 실패 제거 후 같은 키201/false |
| 실제 유일 위반 | 충분한 수량에서 직접 Writer의 UPDATE 후 대상 제약 위반, 전체 수량 복구; 분류기 대상만 true |
| timeout·연결 | 정의된 의존성 장애503·수량/키 부분 반영 없음, 원인 제거 후 재시도 성공 |
| 전체 상태 | 매 사례 종료 뒤 DatabaseAssertions 단일 SELECT 보존식과 예약 수·키 조회 일치 |

## 실패 주입·정리

실패와 지연은 테스트 전용 spy/래퍼로만 주입한다. 실제 HTTP·Writer·UPDATE·트랜잭션을 유지한다. lock timeout 사례의 독립 잠금 연결은 finally에서 rollback/close하고, spy는 해제 후 재시도한다. 무한 대기·클래스 병렬·실제 개발 DB 초기화는 금지한다. 기존 테스트 종료 리스너/컨테이너 close를 재사용해 실행이 소유한 자원만 정리한다.

실패 시 종료 코드·SQLSTATE·원인·최소 재현 조건을 기록한다. 같은 실패를 사라질 때까지 반복하거나 skip·mock DB·도구 하향으로 숨기지 않는다.

## 기록과 완료

docs/test-evidence.md에 RED/GREEN 각각의 명령·종료 코드·테스트 수·환경·실패/skip·실패 주입 조건·원복·미검증 범위를 기록한다. 새로운 의존성 해석/환경 검증은 docs/versions.md, 선택 변경은 docs/decisions.md에 남긴다. 관련 knowledge·README·OpenAPI와 코드 계약을 대조하고 실제 통과 후 검증 대상별 커밋한다.

이번 단계의 성공으로 실제 경쟁 반복·취소·Redis·외부 CI·부하까지 통과했다고 기록하지 않는다. 작업 목록은 [tasks.md](tasks.md)에 생성돼 있으며, 5단계 구현 인수 뒤 다음 기능은 6단계 소유자 조회·취소다.
