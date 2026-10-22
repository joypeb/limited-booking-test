# 구현 후 검증 안내

2026-10-22 US1까지 구현·지정 인수했다. RedisRateLimitIT와 순수 판정/설정 Unit은 실제 존재·통과했으며 RateLimitApiIT·RedisFailureIT는 아직 없다. 아래 명령 중 HTTP/장애/경쟁/최종 전체 게이트는 후속 인수 안내다. 최신 지시로 US1 중간검토에서 멈추며 전체 구현 완료를 뜻하지 않는다. 모델은 [data-model](data-model.md), 정확한 응답은 [계약](contracts/rate-limit-contract.md), 장애근거는 [research](research.md)를 따른다.

## 준비

저장소 루트에서 git top-level·feature/redis-rate-limit·기존변경을 확인한다. Java25·Wrapper9.7.1·Docker 기동 및 기존 고정 PG18.6/Redis8.10.2 이미지·digest를 유지한다. 테스트는 Testcontainers 동적포트·소유 DB/Redis만 사용하며 .env나 localhost 개발DB에 연결하지 않는다. Testcontainers의 랜덤 인증설정과 실제 Basic 헤더를 쓴다. 앱 DB 풀10/획득3000ms·READ COMMITTED·2s/5s timeout, Redis명령/연결200ms의 실제값을 검사한다.

Qodana CLI/정식 실행 경로·2026.2 이미지·인증 환경이 필요하다. 현재 확보되지 않았으며 오케스트레이터가 확인 중이다. 토큰을 출력하지 않는다. 필수 정적검사 전까지 전체 구현 완료를 선언하지 않는다.

## 실행 명령

아래 명령은 backend/에서 실행한다. 하나씩 실행하고 실패 원인을 분석한다. 누락 시험을 skip하거나 실패명령을 반복해 감추지 않는다.

```sh
./gradlew test
./gradlew integrationTest --tests '*RedisRateLimitIT'
./gradlew integrationTest --tests '*RateLimitApiIT'
./gradlew integrationTest --tests '*RedisFailureIT'
./gradlew integrationTest --tests '*ReservationConcurrencyIT'
./gradlew build verify verifyContractDependencies
```

마지막 명령은 필수 `./gradlew build`, `./gradlew verify`, `./gradlew verifyContractDependencies`를 같은 태스크 그래프에서 실행하는 조합이다. build와 verify가 공유하는 test는 한 번만 실행하며, 별도 최종 Unit 실행을 반복할 필요는 없다. 앞선 지정 시험은 TDD/개별 인수 안내이며 새 변경·실패·미해결 문제가 없으면 불필요하게 반복하지 않는다. 실제 조합 명령·종료 코드와 빌드 산출물 생성·의존성 검사 결과를 docs/test-evidence.md에 남긴다.

Gradle은 현재 실행listener/XML의 발견>=1·실패/오류/skip0을 요구한다. 지정 경쟁110회와 지원fixture의 실행수는 구분한다. Qodana는 루트 qodana.yaml을 실제 지원 실행경로에서 적용하고 종료코드·분석결과·any0/라이선스 게이트를 별도 기록한다. 존재하지 않는 Gradle 정적태스크는 호출하지 않는다.

## 검증 시나리오와 기대 결과

| 검사 | 준비/실행 | 기대 |
| --- | --- | --- |
| 기본 경계·격리 | 테스트 키 삭제 후 기본20/60000, 혼합 생성/취소21회; B/ADMIN 첫요청 | 처음20 후속진행·21번째429/양수Retry-After; 사용자독립 |
| 기본 동시집계 | capacity40 직접커밋; A가40개의 다른 생성키·동시시작 | 201정확20·429정확20·Rediscount40·TTL존재; SQL available20/CONFIRMED20 |
| TTL 비연장 | 첫/후속/초과 때 PTTL 기록 및 경과 비교 | 새 TTL이 최초 만료시점을 늘리지 않음; TTL -1없음 |
| 만료 전용 | 별도IT 설정3/1000ms,20ms polling·최대5초 | 키 만료 후첫요청 허용/count1; 다른 기본시험값은 유지 |
| Retry-After | 1/999/1000/1001/60000ms 순수판정·실제 Lua결과 | 1/1/1/2/60초; HTTP헤더는 판정PTTL 근거 |
| 입력/순서 | JSON/UUID/키·미정의/중복필드·415·취소본문·타인/없는예약 | 한도안기존400/415/404·카운트+1, 소진뒤429 |
| 인증/크기 | 미인증·틀린비번,8192/8193byte·길이없음/chunked | 401헤더·413우선; 대상이아니거나 앞단거부면Redis호출0 |
| 인코딩/중복등록 | 기존%63ancel/%72eservations 경로,빈/비어있지않은본문 | 정상과동일1회집계,이후기존취소/400; 한요청count+1 |
| 재요청/취소 | 성공키재전송·중복취소·취소후생성재전송 | 한도안기존응답·시각재현; 소진/장애시429/503·DB무변경 |
| 실제 지연 | 전용관리연결 CLIENT PAUSE1000 WRITE OK 확인후HTTP | 200ms명령timeout실제관측·503; Controller/TX0·DB무변경 |
| 실제 연결장애 | 소유Redis중단,기존연결/첫접속각검증 | 쓰기503, 정상PG두GET200/resourcePOST201 |
| 실제 Redis오류 | 소유 제한키를리스트타입으로만들어실제WRONGTYPE오류 | 검증된 의존성실행오류503·후속진입0; 제품스크립트결함과분리 |
| DB실패 유지 | 제한통과후테스트전용실제SQL/잠금실패→복구 | DB503전체롤백·count유지; 같은키재시도count+1/201 |
| 요청/TX순서 | 실제limiter/Controller/Service/transaction시작 관측 | Redis시점TX비활성·허용완료후TX; 거부때진입0 |
| 기존 회귀 | 실제Redis연결·대상키만DEL;경쟁10000/60000 | 원래응답·C07/UNIQUE·C01~C11각10회·종료보호유지 |

TTL 관측은 최초요청 완료 뒤 간격을 두고 측정해 비연장을 검사한다. 앱과 Redis 경과/스케줄 오차를 기록하고 관측이 불가능할 만큼 창이 만료되면 해당 조건을 통과로 간주하지 않는다. 동시시험은 창60초 안에 결과를 모아야 하며 상한30초·Future전체결과/오류를 수집한다.

## 장애 복구·종료·불변식

지연 시험은 finally에 독립관리 CLIENT UNPAUSE, 최대5초PING, 서버/HTTP/executor 각각10초 종료를 확인한다. Redis timeout 뒤 지연명령이 실행될 수 있으므로 count0을 기대하지 않는다. 정상 키삭제는 복구와 모든 작업종료 후에만 한다. 중단한 컨테이너 재생성은 새동적포트/새ApplicationContext로 분리한다.

기존 경쟁100요청120초·나머지30초, gate5초, HTTP9초, 종료10초를 유지한다. 새작업도 결과·예외·상한·모든 종료를 확인한 다음 기존 DatabaseAssertions 단일SELECT로 수량보존을 검사한다. 제한/장애거부는 별도전체행snapshot으로 상태·시각·성공키무변경을 검사한다. 종료/전송실패 발생시 StopState 최초원인과 전체결과를 보존하고 다음 부모setup의 DB/Redis reset·준비·제출이0인지 fixture로 확인한다. FLUSHALL·개발DB초기화·무제한대기는 금지한다.

## 기록과 완료 판정

docs/test-evidence.md에 RED/GREEN명령·종료코드·실제수/실패/skip·제한/창/timeout·의존성정확버전·장애예외/경과·경쟁조건/110회·모든정리를 기록한다. 결정/버전은 docs/decisions.md·versions.md, 동작연결은 README·knowledge 4도메인/목록·기준계획서에 실제상태로 반영한다. OpenAPI는기존429/503·Retry-After계약과대조하며 불필요한계약변경은하지않는다.

성공은 관련/기존회귀·일반verify·`./gradlew build`·기존 `./gradlew verifyContractDependencies`·필수Qodana를 모두 실제확인한 때다. 미실행·환경장애·정리실패를 완료로 표시하지 않는다. 9단계전체계약·속성/변이/부하·외부CI/배포는 이번 인수에 포함하지 않는다.
