# 환경과 기록 규칙

근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 3절, 7절 1·11·12단계, 8.3·13절과 [AGENTS.md](../../AGENTS.md).

## 로컬 환경과 버전

- 앱은 `127.0.0.1:8080`, PostgreSQL은 `127.0.0.1:5432`의 `reservation_local`, Redis는 `127.0.0.1:6379`를 기본으로 한다. 테스트는 격리된 동적 접속값, load는 별도 `reservation_load` DB를 사용한다.
- local은 원래 20건/60초 제한을 사용한다. 테스트·load의 변경값과 원래 제한 시험을 구분한다. 앱·Compose·테스트는 UTF-8/UTC로 고정한다.
- `.env`·비밀번호는 커밋하지 않는다. `.env.example`에는 변수명만 제공한다. Compose의 환경 설정이 호스트 JVM에 자동 주입된다고 가정하지 말고 셸·IDE에서 별도 설정한다.
- 로컬 DB는 named volume으로 보관한다. 기본 종료 절차에 볼륨 삭제를 넣지 않는다.
- Boot BOM 버전을 우선하고, PostgreSQL 18.x·Redis 이미지는 정확한 태그/가능하면 digest로 고정한다. `latest`는 사용하지 않는다.
- PIT·Property·계약 검증기·k6의 버전과 Java 25·JUnit·Testcontainers 조합은 최소 실행으로 검증한다. 과거 JUnit 5·Testcontainers 1.x 예제를 그대로 복사하지 않는다.
- 도구 후보·공식 문서 확인·실제 채택·실행 호환성 확인을 구분한다. 미검증 후보를 확정된 도구로 기록하지 않는다.
- 기본 Gradle 명령은 `backend/`에서 실행한다. local 설정 후에만 local 프로필 실행을 안내한다. 실제 존재하는 태스크·설정과 검증 범위를 확인한다.
- Git 작업 전 `git rev-parse --show-toplevel`을 확인한다. 상위 저장소에서 `git add .`·`git add -A`를 사용하지 않는다. 독립 저장소 준비는 해당 단계에서 수행한다.

## 문서 책임과 실행 증거

지속적인 규칙·도메인 지식은 `knowledge/`, 개발 결과 기록은 프로젝트 루트 `docs/`로 구분한다. 아래 기록 파일은 해당 개발 단계에 생성·유지하고, 파일 작성만으로 실행 완료를 주장하지 않는다.

| 위치 | 기록할 내용 |
|---|---|
| `knowledge/domain/<domain-name>.md` | 책임·데이터·불변식·계약·검증 사례·실제 구현 연결 |
| `knowledge/rules/` | 공통 개발 규칙과 설계 근거 |
| `docs/openapi.yaml` | 고정 OpenAPI 3.0.3 요청·응답 계약 |
| `docs/versions.md` | JDK 배포판·패치, 도구·이미지·의존성 해석·실행 호환성 |
| `docs/decisions.md` | 선택 이유·대안·변경 영향 |
| `docs/test-evidence.md` | 명령·종료 코드·테스트 수·seed·경쟁/장애 조건·미검증 |
| `docs/performance.md` | 기기·설정·데이터·부하 조건·결과·DB 검사·한계 |
| 프로젝트 README | 실제 환경 설정·실행·API 흐름·검증·종료 절차 |
| `docs/traceability.md` | 규칙 → 대상 클래스 → 테스트 → 명령 → 증거 추적표와 검증하지 않은 범위(12단계) |
| `docs/demo.md` | 5분 시연 대본·작은 변경 연습 안내·사용자 수행 기록 칸(12단계) |
| `.github/workflows/` | verify(PR·push)·deep-tests(수동) CI 정의. 파일 존재는 외부 실행 성공이 아니다 |
| `scripts/check-*.sh` | 워크플로·README·추적표의 정적 검사(RED→GREEN 판정 도구) |

- 규칙 → 실제 클래스/SQL → 테스트 → 명령 → 증거를 연결한다. 아직 없는 파일과 명령은 예정·미구현으로 표시한다.
- 실행 기록에는 날짜·단계·대상 커밋(없으면 명시)·환경·기대값·변경·명령·종료 코드·테스트 수·seed·최소 재현·문제·선택 이유·미완료·다음 한 작업·실제 시간을 적는다.
- 개발 중 발견한 결함과 학습용 고의 결함을 구분한다. 실행하지 않은 검증·시연·설명 능력을 완료했다고 적지 않는다.
- 계획 체크박스는 실제 실행·검증 후에만 표시한다. CI 파일 작성과 외부 CI 성공을 구분한다. push·공개·외부 workflow 실행은 별도 사용자 지시가 있을 때 수행한다.

## 로컬 부하 판정

- 준비 실행 30초 → 본 측정 2분을 3회 수행하고 각 측정 전 load 전용 데이터를 초기화한다.
- 낮은 경쟁은 10VU·여러 대상·충분한 수량·다른 키의 생성→취소다. 최초 기준선 후 잠정 p95<500ms·예상치 못한 오류율<1%로 판정한다.
- 높은 경쟁은 수량1000·한 대상·50VU·서로 다른 키로 총2000회다. 201 1000건, SOLD_OUT 409 1000건, 최종 CONFIRMED 1000·available 0을 기대한다.
- 재요청은 별도 시나리오로 HTTP 성공 수와 고유 예약 수를 구분한다. 429·503·500·timeout을 예상 품절과 섞지 않는다.
- k6 check뿐 아니라 threshold 종료 코드와 종료 후 DB 불변식 검사로 판정한다.
- 기기·CPU 아키텍처·JDK·Docker 메모리·이미지·풀·제한값·데이터·대상 commit·p50/p95/p99·처리량·상태 코드별 수를 남긴다. Basic·BCrypt를 포함한 전체 HTTP 비용과 순수 서비스 비용을 구분한다.
- 병목 하나를 측정하고 하나씩 변경해 같은 조건으로 비교한다. 근거 없이 가상 스레드·풀 증가·캐시를 추가하지 않는다. 로컬 결과를 운영 규모의 증거로 일반화하지 않는다.

## 완료 보고

변경 이유, 실제 검증 결과, 미완료·미검증 범위, 사용자가 읽을 핵심 코드·SQL·테스트 또는 문서, 다음 한 작업을 한국어로 보고한다. 문서 변경만 검증했다면 애플리케이션·DB·Redis·CI 테스트를 실행한 것처럼 보고하지 않는다.

2026-10-13 1단계 환경 구성: root compose.yaml, backend/application-local.yaml, .env.example, README 및 docs/versions.md를 작성·최소 실행 확인했다. toolchain Boot 계정은 기동 확인용이며 업무 인증이 아니다. PostgreSQL18 volume 대상은 /var/lib/postgresql, 고정 digest는 Compose/Testcontainers에 공통 적용한다. 구체적 현재 값·실행 수는 docs/versions.md와 docs/test-evidence.md를 참조한다.


### 테스트 PostgreSQL 연결 모드 (2026-10-21)

`ContainerTestSupport`의 로컬 소유 PostgreSQL은 TLS 인증서를 구성하지 않는 `ssl=off` fixture다. 컨테이너 JDBC URL에 `sslmode=disable`을 명시해 준비 확인·Flyway·Hikari·직접 JDBC의 불필요한 SSL 협상을 제거한다. 이 규칙은 앱 기본/local 설정이나 외부 DB의 TLS 정책을 바꾸지 않는다. 실제 서버 설정과 연결의 비TLS 여부는 DatabaseSupportIT에서 검사한다. 최초 SSL 협상08P01의 비정상 응답 발생원은 미확정이며 이 설정을 원인 규명으로 표현하지 않는다. 결정·실행·한계는 docs/decisions.md와 docs/test-evidence.md를 따른다.

### 11단계 부하 실행 적용 (2026-10-24)

- load 프로필(`application-load.yaml`)은 `LOAD_RATE_LIMIT` 필수 환경 변수(기본값 없음)로 제한값을 받는다. 부하 100000/60초, 제한 시험 20/60초로 앱을 따로 기동한다.
- `load/prepare-db.sh`가 같은 PostgreSQL에 `reservation_load`를 없을 때만 만들고 Flyway는 앱 기동 시 적용한다. `load/reset.sh`는 reservation_load와 USER_A·USER_B 제한 키만 초기화한다.
- `load/run.sh <시나리오> <label>`이 reset → k6(`--summary-export`) → `check-invariants.sql`을 실행하고 `docs/performance-runs/<날짜>-<시나리오>-<label>/`에 결과·meta(commit·dirty·제한값·이미지·종료 코드)를 남긴다. 기존 결과 디렉터리를 덮어쓰지 않는다. k6 또는 DB 검사 비0이면 비0 종료.
- 원시 출력은 원문을 보존하며 `.gitattributes`로 해당 경로만 공백 검사에서 제외한다. 결과에 비밀번호·Authorization 값이 없음을 커밋 전에 검사한다.
- 제한값을 바꿔 앱을 재기동할 때는 이전 프로세스 종료와 `127.0.0.1:8080` LISTEN 해제를 확인한 뒤 기동한다. 실행기는 서버의 실제 제한값을 조회하지 못하므로 기록값과 실제 값이 어긋날 수 있다(2026-10-24 rate-limit v2 무효 실행).
- 판정기 자체를 고의 불일치(`EXPECT_CREATED=999`)와 보존식 위반 데이터로 실패시켜 종료 코드 반영을 확인한다. 상세는 [load 안내](../../load/README.md)·[성능 기록](../../docs/performance.md).

### 12단계 CI·재현 적용 (2026-10-26)

- CI는 계획서 6.2절의 기존 Gradle 태스크만 호출한다(verify: `verifyContract`, deep-tests: `propertyTest mutationSmoke`). runner·JDK·action은 버전/SHA로 고정하고 `continue-on-error`·실패 무시를 두지 않으며 보고서는 always 업로드한다. 워크플로 변경 시 `scripts/check-workflows.sh`를 실행한다.
- 외부 run은 실제 run id·결론·실행 수를 확인한 경우에만 성공으로 기록한다. workflow_dispatch 워크플로는 기본 브랜치에 있어야 실행된다.
- README 명령을 바꾸면 `scripts/check-readme.sh`와 함께 바뀐 절차를 새 clone·깨끗한 셸에서 다시 실행한다. 새 환경 재현은 디렉터리 이름으로 Compose 프로젝트를 분리하고 임시 프로젝트 볼륨만 정리한다.
- `load/run.sh`의 meta `git_dirty`는 결과 디렉터리 생성 전 상태다(2026-10-26 수정). 이전 meta의 dirty 표기는 신뢰하지 않는다. 연속 실행에서 dirty=false를 유지하려면 결과 디렉터리를 실행 사이에 작업 트리 밖으로 옮기거나 깨끗한 clone을 사용한다.
