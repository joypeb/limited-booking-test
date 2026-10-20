# 7단계 검증 실행 안내

이 문서는 구현 후 사용할 안내다. 현재 신규 ReservationConcurrencyIT·동시성 지원은 미구현이므로 아래 경쟁 명령의 성공을 확인한 상태가 아니다.

## 준비

계획 작성 중 원래 작업 폴더가 외부 작업으로 develop으로 전환되어, 이번 설계는 `docs/reservation-concurrency-spec` 브랜치의 별도 worktree에서 마무리했다. 아래 경로는 그 worktree 기준이다. 구현 시 실제 작업 브랜치·활성 기능 경로를 먼저 확인한다.

저장소 루트에서 계획서2~5절/7단계와 [명세](spec.md), [경쟁 계약](contracts/concurrency-contract.md), [모델](data-model.md)을 확인한다. JDK25·Docker daemon이 필요하며 개발 DB/Compose volume을 시험에 사용하지 않는다. ContainerTestSupport의 고정 PostgreSQL 이미지를 사용하고 Basic 계정은 기존 테스트 전용 값만 주입한다. .env 값·Authorization을 기록하지 않는다.

원래 작업 폴더의 Wrapper 사용자 변경9.8.0은 보존했다. 별도 worktree는 추적 파일9.7.1이며 최근 실행 기록도9.7.1이다. 어느 폴더에서 실행하는지와 실제 값을 기록한다. 이 안내는 버전 변경 지시가 아니다.

```sh
cd /Users/joypeb/Desktop/projects/limited-booking-concurrency-plan/backend
java --version
./gradlew --version
docker version
```

Hikari maximumPoolSize10/connectionTimeout3000ms, DB read committed/lock_timeout2s/statement_timeout5s, UTF-8/UTC를 실제로 확인한다. RANDOM_PORT·동적 테스트 DB만 사용하며 reservations→resources 초기화는 이전 작업의 서버 종료를 확인한 후에만 한다.

## 단계별 검증 (신규 시험 구현 후)

한 대상씩 실패 탐지→최소 구현/정리→관련 재검증을 진행한다. 새 지원 Unit은 작성 후 지정 실행하고 실제 수를 기록한다.

```sh
./gradlew test --tests '*ConcurrencyTestSupportTest'
./gradlew integrationTest --tests '*DatabaseSupportIT'
./gradlew test integrationTest --tests '*ReservationConcurrencyIT'
./gradlew verify
```

위 복합 명령의 --tests는 integrationTest에 적용되고 test는 기존 Unit 전체를 실행한다. 전체 verify의 기존137 Unit/138 IT는 과거 결과이며 이번 통과 수의 기대 고정값이 아니다. 신규 반복 인수는11종×10=110회, 지원 테스트 증가 수는 별도로 집계한다. XML은 `build/test-results/integrationTest/TEST-*.xml`, HTML은 `build/reports/tests/integrationTest/index.html`이다. UP-TO-DATE 결과만 있으면 신규 실행 증거가 아니므로 필요한 지정 시험에 --rerun-tasks로 실행을 요청하고 그 이유를 기록한다.

## 결과 판정

[계약 표](contracts/concurrency-contract.md)의 C01~C11을 회차별로 판정한다. 지정 명령 종료0만으로 끝내지 않고 XML의 발견 수·실패/오류/skip0, 경로 관측·최종 snapshot을 확인한다. Future 예외·작업 누락·정리 실패·미관측 경로가 있으면 실패이며 반복 성공을 골라서 보고하지 않는다.

특히 C07의 UPDATE0/재조회와 C08의 실제 UNIQUE/전체 롤백을 구분한다. C09와 C10은 두 요청 모두 시작되었고 의도한 선행 결과가 확정된 뒤 후행 판단이 진행됐는지 확인한다. C11은 의도한500과 정상 경쟁 요청의201을 구별한다. 취소 후 생성 재요청의 최초CONFIRMED와 현재 GET/CANCELLED는 기존 회귀 시험에서도 유지한다.

## 증거와 정리

docs/test-evidence.md에 명령·종료 코드·실제 수·대상 commit·날짜·시간·이미지/digest·작업자/풀·연결 관측값·샘플 한계·반복별 상태·경로/순서 관측·최종 snapshot·미검증을 남긴다. 실제 버전은 docs/versions.md, 설계 변경은 docs/decisions.md에 기록한다. 현재 문서 작성 단계에서는 계획서 체크박스를 표시하지 않는다.

매 회차 finally에서 모든 대기 해제→작업 종료·서버 in-flight0 확인→Spy/플래그 초기화→HTTP 종료 순서로 정리한다. 클래스 소유 컨텍스트와 컨테이너는 기존 DirtiesContext/종료 listener 규칙을 따른다. 정리 실패로 다음 회차를 진행하지 않고 개발 DB·volume을 지우지 않는다. 로컬 반복은 모든 실행 순서나 운영 성능의 증명이 아니다. 다음8단계에서 Redis 제한 도입 시 실제 HTTP 경쟁 경로를 유지하고10000건/60초·회차별 테스트 전용 키 초기화를 명시한다.
