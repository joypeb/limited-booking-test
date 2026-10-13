# 환경과 도구 버전

2026-10-13, 1단계 작업 브랜치 `chore/setup-test-toolchain`. 상태는 **후보 → 설치/취득됨 → 버전 확인됨 → 해당 최소 사례 실행 확인됨**으로 구분한다. 설치나 버전 출력은 업무·연동 호환성 확인이 아니다.

| 항목 | 실제 확인 | 상태·범위 |
|---|---|---|
| CPU/OS | arm64, macOS 27.0.1 | 호스트 확인 |
| JDK | Amazon Corretto 25.0.4+7-LTS | java 및 Gradle 실행 확인 |
| Gradle Wrapper | 9.7.1 | --version 및 기존 test 실행 확인 |
| Boot | 기존 설정 4.1.1 | 기존 contextLoads 1건 통과; 새 starter 해석 예정 |
| Docker | CLI/daemon 29.2.1, aarch64 | Desktop 기동·데몬 연결 확인 |
| Docker 메모리 | 8,216,915,968 bytes (약 7.65GiB) | docker info 실제값 |
| Compose | v5.1.0 | 버전 출력 확인; 서비스 기동 예정 |
| k6 | 2.3.0, go1.27.1, darwin/arm64 | 부족 도구만 brew install 후 버전 확인; 부하 미실시 |
| PostgreSQL | postgres:18.6-bookworm | 취득 완료; 저장소 연동 예정 |
| Redis | redis:8.10.2-trixie | 취득 완료; 저장소 연동 예정 |
| Jupiter/Testcontainers/Flyway | Boot BOM 후보 | 실제 dependencyInsight 및 최소 사례 예정 |
| QuickTheories/계약 검증기/PIT | research.md 후보 | 설치·실행 미확인 |

Docker 이미지 digest와 실제 해석 의존성은 해당 검증 후 추가한다. 전체 1단계 인수는 아직 미완료다.

이미지 확인(각 명령 종료0):

- PostgreSQL18.6, arm64, `postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c`
- Redis8.10.2, arm64, `redis@sha256:2c2dff791878316e3b083188be0d578423b24813cdda5de0e80ea4f7b3e6bfd6`
- `docker image inspect`및 `docker run --rm <image> <version command>`로 실제 실행 버전을 확인했다. 연동은 미검증이다.

실제 Gradle dependencyInsight(종료0): Jupiter6.0.3, Testcontainers2.0.5, Flyway12.4.0, PostgreSQL JDBC42.7.13. Boot BOM 관리 버전 override 없음. 연동 테스트는 아직 미검증이다.

Jupiter6.0.3/Testcontainers2.0.5/Flyway12.4.0/JDBC42.7.13 및 고정 PG18.6/Redis8.10.2 조합: Unit3·일반IT4 최소 사례 실행 확인. 업무 API·경쟁 정합성 호환성은 검증하지 않았다.
