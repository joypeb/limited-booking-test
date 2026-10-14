# 검증 안내: 2단계 저장 기반

업무 IT·V1과 읽기/테스트 지원을 구현·실행 검증했다. 아래는 재현 안내이며 실제 결과는 docs/test-evidence.md를 따른다. [데이터 모델](data-model.md)·[계약](contracts/persistence-contract.md)·[구현 순서](plan.md)를 함께 읽는다.

## 사전 조건

- 기존 Java25·Gradle Wrapper9.7.1·Docker 실행 환경. 기존 고정 PostgreSQL 이미지 및 BOM 값은 [버전 기록](../../docs/versions.md)을 따른다.
- SchemaIT·두 RepositoryIT·DatabaseSupportIT과 실제 업무 V1 구현 완료. RED 단계는 아래 별도 절차대로 진행한다.
- Testcontainers가 생성한 동적 접속값과 테스트 전용 DB만 사용한다. Compose·local 프로필·.env·reservation_local·reservation_load는 이 검증의 접속 대상이 아니다. 기본 종료에 volume 삭제를 넣지 않는다.
- JUnit 병렬 실행 비활성화, 테스트 준비 커밋, 데이터 초기화 reservations→resources, 종료 상한과 실제 종료 확인을 유지한다.

## 1. 현재 명령과 환경 확인

저장소 루트에서 시작해 다음을 실행한다. Docker 확인은 기동 상태만 확인하며 테스트 결과는 아니다.

```sh
cd backend
./gradlew --version
docker info
```

JDK·Wrapper·Docker가 준비되지 않으면 먼저 환경 원인을 기록한다. 명령 출력에 자격증명을 붙이지 않는다. 개발 DB를 시작하거나 수동 생성할 필요는 없다.

## 2. 최초 의도적 실패 확인 (개발 이력)

개발 당시 첫 대상은 `SchemaIT.rejectsInvalidAvailability`였다. 최종 트리에는 제약이 이미 있어 아래 명령은 GREEN이며 임시 후보를 재생성하거나 적용 V1을 편집하지 않는다. 구현 순서의 테스트 전용 임시 baseline으로 컬럼/타입만 준비한 상태에서 아래를 실행한다.

```sh
./gradlew test integrationTest --tests '*SchemaIT.rejectsInvalidAvailability'
```

기대 결과는 Unit 정상 실행과 선택 IT 실패, 비정상 수량 INSERT가 성공해 기대 거부 assertion이 실패하는 것이다. baseline에서의 테스트 탐지력 증거로 기록한다. 컴파일 오류·42P01·Docker 오류·0매칭 실패는 해당 증거가 아니다.

실패 확인 뒤 현재 제약만 후보 CREATE DDL에 최소 추가하고 새 소유 DB에서 같은 테스트의 GREEN을 확인한다. 후보는 테스트 전용 db/schema-candidate/schema.sql이고 Flyway로 적용하지 않는다. 대상 필수 값·PK·이름 길이, 예약 키·참조·상태·필수 값·PK·길이·인덱스도 각각 테스트→RED→후보 최소 DDL→GREEN 순서로 진행한다. 초기 길이 컬럼은 text 등 해당 제한이 없는 타입, 해당 필수 값은 nullable로 준비하고 검증한 제약을 하나씩 유지한다. 후보 시험은 CREATE 정의를 갱신한 뒤 매번 새 DB에서 수행한다. 모든 대상 제약을 인수한 최종 후보를 수정 없이 업무 V1로 복사하고 임시 파일·분기를 제거한 뒤 새 DB에서 실제 Flyway V1을 처음 적용해 전체 사례를 검증한다. 후보 RED/GREEN은 제약 SQL 검증이고 업무 V1 GREEN은 마이그레이션 적용 검증이다. 적용된 V1을 편집하거나 clean/repair로 통과시키지 않는다.

## 3. 스키마 제약과 두 준비 조건

```sh
./gradlew test integrationTest --tests '*SchemaIT'
```

test는 Unit을, --tests가 붙은 integrationTest는 SchemaIT를 실행한다. Unit에 같은 필터를 지정하지 않는다. 게이트는 실행 수>0·실패0·skip0·현재 listener/XML 일치를 요구한다.

SchemaIT 내부에서 fresh/reapplied 준비 조건마다 같은 제약 사례를 검증한다. 최초 적용1건, 재적용0건·validate 성공·표식 데이터와 이력 유지가 기대값이다. 데이터 초기화는 적용 이력과 저장 구조를 보존한다. 모드별 사례 실행 수와 제약별 실제 SQLSTATE·행 잔류 여부를 기록한다. 비공백 길이 초과는22001로 거부하고 이름100자/키64자 뒤 일반공백1자는 상한으로 절단된 저장 값을 기대하는 별도 정상 사례로 확인한다. 입력 API의 길이/문자 규칙 인수로 보고하지 않는다.

## 4. 조회와 테스트 지원 검증

```sh
./gradlew test integrationTest --tests '*ResourceRepositoryIT' --tests '*ReservationRepositoryIT' --tests '*DatabaseSupportIT'
```

기대 결과는 대상4필드·예약7필드 일치, 행 없음과 사용자/키 분리, 취소 예약 조회, 저장 UTC 시각 일치, 별도 연결의 준비 데이터 조회, 연속 초기화 후 0행과 이력 보존이다. 보존식 검사는 정상 데이터 통과와 의도적 불일치/없는 대상 실패를 모두 요구한다. 이때 검사 실패는 테스트가 기대한 예외로 확인하는 것이며 테스트 자체는 통과해야 한다.

## 5. 기존 지원 코드 영향 검증

ContainerTestSupport를 변경했으므로 기존 프로브·HTTP 지원 IT를 포함한 일반 IT를 확인한다.

```sh
./gradlew verify
```

verify는 기존 Unit+일반IT를 실행한다. 기존 프로브는 명시한 db/toolchain 경로를 유지하고 업무 V1과 섞이지 않아야 한다. 이번 범위에 영향 없는 Property/PIT/부하까지 실행 완료로 보고하지 않는다. 실패나 새 수정이 없다면 전체 심화 검증을 추가 반복하지 않는다.

## 6. 결과와 판정 기록

[실행 증거](../../docs/test-evidence.md)에 실행 명령·종료 코드·Unit/IT 수·모드별 실행 수·실패/skip·JDK/DB 이미지·실행 소유 DB 식별·migration 적용 수·이력·정리 결과를 남긴다. RED는 원인과 후보 구조 사용을, GREEN은 후보 DDL 검증과 실제 main V1 적용 검증을 구분한다. 후보 최종 내용과 V1 복사의 동일성·임시 경로 제거도 기록한다.

업무 HTTP·권한·멱등 재현·생성/취소 원자성·경쟁·DB 장애는 미검증으로 남긴다. 실행 후에만 계획서 2단계 체크박스를 갱신하고 도메인 문서·README의 구현 연결을 실제 파일에 맞춘다. 검증 대상별 커밋에는 해당 결과를 연결한다. 다음 단계는 작은 규칙과 입력 경계값이며 이번 명령에서 구현하지 않는다.

## 7. CHK022 고의 실패·복구 검증

```sh
./gradlew test integrationTest --tests '*SchemaIT.rollsBackFailedMigrationAndRecoversInNewDatabase' --tests '*DatabaseSupportIT.rollsBackFailedResetAndRetriesAfterFaultRemoval'
```

마이그레이션 시험은 실제 V1을 읽어 만든 테스트 전용 실패 사본을 트랜잭션으로 적용한다. 업무 DDL 이후22012 오류를 받고 두 업무 테이블·인덱스 부재·V1 성공 이력0건을 별도 연결에서 확인한다. Flyway 이력 테이블 자체의 존재는 허용한다. 실패 자원 증거·종료 뒤 새 실행소유 DB에 원본 V1을1건 적용하고 제약을 확인한다. 원본 checksum과 실패 사본 경로/오류를 구분하며 clean/repair로 우회하지 않는다.

초기화 시험은 커밋된 양쪽 행/값·이력의 스냅샷을 보관하고 대상 DELETE에만22012를 내는 테스트 전용 장애를 설치한다. 예약 삭제 뒤 실패한 초기화가 전체 롤백돼 별도 연결의 스냅샷이 같아야 한다. 장애 제거 후 같은 DB의 새 트랜잭션으로 명시적으로 초기화를 재요청해 두 테이블0건·구조/이력 유지·장애객체 정리를 확인한다.

두 고의 오류는 예상 예외로 인수해 테스트 자체가 통과해야 한다. 종료 코드·실제 시험 수·오류 원인·실패/복구 DB 식별·스냅샷 비교·정리를 기록한다. 연결 단절 등 관측 불가능한 경우는 불명/실패로 보고하며 이번 검증의 통과로 취급하지 않는다. 상세 범위는 [실패 처리 계약](contracts/persistence-contract.md)을 따른다. 두 실패/복구 사례는 실제로 실행·검증했으며 상세 결과는 실행 증거를 따른다.

현재 선택 전체는 Unit3/업무IT72, verify는 Unit3/일반IT78이다. 실패0·skip0이며 업무IT 모드별35건과 독립 실패복구/환경 각1건으로 구분한다. 테스트 XML/HTML은 backend/build의 기존 경로를 사용한다.
