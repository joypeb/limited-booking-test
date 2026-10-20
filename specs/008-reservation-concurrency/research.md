# 조사: 동시 요청 검증

작성: 2026-10-20. 로컬 코드·기존 실행 기록을 근거로 한 설계 조사다. 이번에 새 의존성·실행 호환성을 검증한 것은 아니다.

## 1. 실제 서비스와 HTTP 경로

- 결정: 신규 `ReservationConcurrencyIT`는 RANDOM_PORT·실제 Basic Authorization·실제 Service/Writer·격리 PostgreSQL을 사용한다. 내부 호출로 경로를 관측하는 시험을 추가하더라도 HTTP 사례를 대체하지 않는다.
- 근거: 기존 [Service](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationService.java)는 생성 선조회·Writer 호출·롤백 후 재조회를 조정하고 [Writer](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationWriter.java)는 READ COMMITTED/SET LOCAL을 적용한다. 기존 [HTTP 지원](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationApiTestSupport.java)을 재사용할 수 있다.
- 대안: 서비스 호출만 반복하면 인증·필터·오류 변환·헤더 계약을 검증하지 못한다. 가짜 저장소는 실제 경쟁과 롤백의 근거가 될 수 없다.

## 2. 경로 관측과 안전한 장벽

- 결정: 테스트의 `@MockitoSpyBean`으로 실제 Repository 메서드를 호출한 뒤 결과·예외·현재 트랜잭션 여부를 기록한다. 성공값·SQL 결과를 가짜로 반환하지 않는다. 지정 키에 대한 첫 두 `findByUserAndKey` 호출이 실제 빈 결과로 끝난 뒤, 트랜잭션 밖임을 확인하고 장벽을 적용한다. 재조회는 장벽 대상에서 제외한다.
- 근거: 동일 대상의 조건 UPDATE가 행 잠금을 보유하므로 INSERT 직전 양측을 기다리게 하면 두 번째 요청은 그 지점까지 올 수 없다. 선조회 이후에는 연결이 반환되어 두 요청이 안전하게 Writer로 진입할 수 있다.
- 관측: `decrementIfAvailable` 반환0 + 트랜잭션 안 성공 키 재조회, 또는 실제 INSERT의23505/정확한 제약명 + 롤백 완료 뒤 트랜잭션 밖 성공 키 재조회를 확인한다. 충분한 수량10 경쟁에서도 적어도 첫 두 선조회를 맞춰 UNIQUE 경로를 보장한다. 최종 수량만으로 경로 실행을 추정하지 않는다.
- 대안: sleep·우연한 스케줄·로그 문구는 경로 증거가 아니다. 제품 코드에 후크나 장애 파라미터를 추가하지 않는다.

## 3. 생성·취소의 두 순서

- 결정: 생성 성공 순서는 생성의 트랜잭션 밖 첫 키 조회를 마친 후 대기시키고, 취소 HTTP 응답200과 별도 연결의 복구 확정을 확인한 뒤 해제한다. 품절 순서는 취소의 실제 Service 호출을 테스트 Spy에서 진입 관측 후 트랜잭션 밖에서 대기시키고, 생성409/SOLD_OUT 완료를 확인한 뒤 해제한다. 대기 대상 요청은 실제로 시작되어 있어야 한다.
- 근거: 두 요청은 모두 실행 중이되 한 요청은 연결·잠금을 가지기 전에 기다리므로 잠금 timeout을 만들지 않는다. 단순히 순차 HTTP 두 개를 보내는 시험과 다르다. 완료 응답과 사건 순번으로 의도한 순서를 검증한다.
- 대안: Writer 내부 UPDATE 후 대기는 잠금 대기·2초 timeout을 유발할 수 있다. Writer Spy로 프록시 경계를 모호하게 만들기보다 Service 진입·Repository 선조회 지점을 사용한다.

## 4. 실행 자원과 정리

- 결정: 요청 작업자 최대10, Hikari 최대10·획득3000ms. 100건은 첫 실행 작업자10명의 준비 확인 뒤 시작 장벽을 열고 나머지는 큐로 처리한다. 전체100건이 준비 장벽에 도달하기를 기다리지 않는다. JUnit 클래스와 Gradle fork는 기존 순차 설정 유지.
- 제한: 장벽 준비·대기5초, HTTP 요청9초/지원 함수 전체10초, 소규모 회차 전체30초·100건 회차120초, Executor 종료10초, HttpClient 종료10초. 종료 제한을 성공 응답의 지연 목표로 해석하지 않는다.
- 근거: [HttpClientTestSupport](../../backend/src/test/java/com/limitedbooking/backend/support/HttpClientTestSupport.java)의 현재 상한과 [Gradle](../../backend/build.gradle)의 maxParallelForks1/0건 거부를 유지한다. DB lock2초·statement5초는 제품 계약 그대로다.
- 대안: 무제한 스레드·상한 없는 Future.get·성공할 때까지 재실행·DB timeout 확대는 채택하지 않는다.

## 5. 환경 차이

- 결정: 현재 Wrapper 파일의9.8.0은 기존 사용자 변경으로 보존한다. docs/versions.md의 마지막 실행9.7.1과 다르므로 새 실행 단계에서 실제 버전을 확인·기록한다. 별도 worktree에는 추적 Wrapper9.7.1이 존재한다. 사용자 변경을 복사하지 않았으며 계획 작성 중 버전 변경이나 호환 완료를 주장하지 않는다.
- 근거: [현재 Wrapper](../../backend/gradle/wrapper/gradle-wrapper.properties)와 [기존 버전 증거](../../docs/versions.md). Java25·Boot4.1.1 및 기존 PostgreSQL 고정 이미지/digest는 유지한다.
- 대안: 실행 기록에 맞추려 Wrapper를 되돌리거나 버전만 보고 호환 완료로 기록하지 않는다.

## 6. 실패 주입

- 결정: 수량2에서 서로 다른 키 두 요청을 함께 출발시키고 한 키의 실제 차감 후 INSERT 진입에서만 IllegalStateException을1회 발생시킨다. 상대 요청은 정상 저장한다. 종료 후 실패키 없음·정상키1행·available1, 같은 실패키 재시도 후2행·available0을 확인한다.
- 근거: 기존 [ReservationRollbackIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationRollbackIT.java)는 차감 후 테스트 전용 실패와500/전체 롤백을 검증한다. 이번에는 다른 쓰기 요청과 겹친 별도 증거를 남긴다.
- 대안: 모든 저장을 mock으로 막거나 정상키까지 실패시키면 실제 경쟁과 부분 반영 여부를 판정할 수 없다.

미해결 설계 질문 없음. 구현 중 실제 관측이 설계와 다르면 실패 원인을 조사하고 기록한 뒤 필요한 계획을 갱신한다.
