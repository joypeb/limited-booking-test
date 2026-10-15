# 검증 안내: 순수 규칙 경계와 취소 판단

작성일: 2026-10-15. **구현 후 실행할 안내이며 이번 계획 작성의 기능 테스트 성공 기록이 아니다.** 아래 신규 테스트 클래스는 아직 없다. 현재 실행하면 대상 미발견으로 실패해야 하며 이를 규칙의 RED로 세지 않는다. 실제 결과는 [실행 증거](../../docs/test-evidence.md)에 기록한다.

## 전제와 실행 위치

기존 JDK25·Gradle Wrapper·해석된 테스트 의존성을 사용한다. 새 dependency·Docker·Compose·애플리케이션 기동·인증 비밀번호·DB/Redis 연결은 이 Unit 실행에 필요하지 않다. Gradle 배포판/의존성이 캐시에 없으면 최초 취득을 위한 네트워크가 필요할 수 있으며 환경 실패를 기능 실패로 기록하지 않는다. `.env`·로컬 DB를 준비하거나 테스트에 연결하지 않는다.

```sh
cd /Users/joypeb/Desktop/projects/limited-booking/backend
java --version
./gradlew --version
```

예상 기준은 Java25, Wrapper9.7.1이다. JDK25는 이번 계획에서 Corretto25.0.4+7-LTS를 확인했으나 Gradle 실행은 이번에 하지 않았다. 현재 정확한 설치·해석 상태는 [버전 기록](../../docs/versions.md)과 실행 시 출력을 대조한다.

## 한 규칙씩 RED와 GREEN 확인

[계획](plan.md)의 순서로 테스트를 먼저 작성한다. 필요한 메서드 시그니처의 컴파일 가능한 최소 골격을 마련한 뒤, 해당 검증 없는 상태에서 아래 선택 Unit을 실행해 [계약 행렬](contracts/rules-contract.md)의 잘못된 값 수용 또는 반환값 불일치로 실패하는지 확인한다. 예: no-op 수량 검사에서 -1의 예외가 발생하지 않아 실패, 원문 반환 이름에서 앞뒤 공백 제거 기대값 불일치다. 컴파일 오류·우연한 NPE·태스크 미발견·환경 장애를 도메인 실패 탐지로 대체하지 않는다.

```sh
./gradlew test --tests 'com.limitedbooking.backend.resource.ResourceRulesTest'
./gradlew test --tests 'com.limitedbooking.backend.reservation.IdempotencyKeyTest'
./gradlew test --tests 'com.limitedbooking.backend.reservation.CancellationDecisionTest'
```

세 명령을 일괄 반복하기보다 현재 구현 중인 클래스의 명령 하나를 사용한다. RED에서 해당 assertion 실패와 종료 코드 비0을 확인한 뒤 현재 규칙만 최소 구현하고 같은 명령에서 종료0·실패/오류/skip0을 확인한다. 다음 입력 그룹마다 같은 순서를 따른다. `--tests`는 실행 필터로 공유 test sourceSet의 다른 소스 컴파일을 생략하지 않는다.

검증 결과는 이름 정상/거부·정리한 반환값·수량 정상/거부·키 원문/동등성/생성자·취소 판단으로 구분한다. 테스트가 구현과 같은 계산을 반복해 기대값을 만드는 대신 고정 입력과 명시한 값을 비교해야 한다. 순수 규칙은 시간을 사용하지 않아 Clock 설정이 필요하지 않다.

## 최종 전체 Unit 인수

개별 규칙의 GREEN 후 다음을 한 번 실행한다.

```sh
./gradlew test
```

이 명령은 일반 Unit만 실행한다. 새 세 클래스와 기존 ToolchainSmokeTest가 발견돼야 하고, Spring·컨테이너를 기동하는 IT나 property/contract 태그의 프로브는 실행하지 않아야 한다. 기존 프로브 Unit의3회와 신규 규칙의 실제 매개변수 실행 수를 구분해 기록한다. 실패나 코드 변경이 없으면 동일한 전체 검사를 불필요하게 반복하지 않는다.

현재 build.gradle이 출력하는 `test: tests=N, failed=0, skipped=0` 및 XML을 확인한다.

- 보고서: `backend/build/reports/tests/test/index.html`.
- XML: `backend/build/test-results/test/TEST-*.xml`.
- 새 ResourceRulesTest·IdempotencyKeyTest·CancellationDecisionTest가 각각 존재하고 실행 수>0이어야 한다.
- 합계 실행 수는 listener와 일치하고 failures/errors/skipped는 각각0이어야 한다. 파라미터 invocation을 클래스/메서드 수와 혼동하지 않는다.
- `testResult`의 현재 실행·0건·skip·XML 게이트를 완화하지 않는다. 이전 보고서나 캐시 결과를 새 실행으로 쓰지 않는다.

위 보고서 경로는 실행 후 생성되는 산출물 안내다. 이번 계획의 존재하는 실행 증거로 링크하지 않는다. 테스트가 실패하면 실제 assertion/실패 원인을 확인한 뒤 수정하고 관련 범위만 재검증한다. skip·mock·기대값 변경으로 숨기지 않는다.

## 실행·설명·후속 연동 기록

실행 날짜·대상 브랜치/커밋 또는 미커밋·JDK/Gradle·규칙별 RED/GREEN 명령·종료 코드·검증 수·실패/오류/skip 수·최소 실패 입력과 기대 결과를 docs/test-evidence.md에 남긴다. 환경 버전 변화가 있으면 docs/versions.md와 대조한다. 순수 고정 사례이므로 Property seed·경쟁 조건은 이번에는 적용하지 않으며 심화/HTTP/DB/Redis는 미실행이라고 기록한다.

개발자가 최소 경계 하나를 직접 설명해야 한다. 예를 들어 capacity=0은 허용 범위의 하한이며, 대상 생성이 가능하지만 예약 가능한 수량은 없다는 이유를 설명하고 해당 assertion의 탐지 대상을 확인한다. 이 문장의 존재가 사용자 설명 인수 완료를 뜻하지 않는다. 모든 assertion의 확인 대상과 실패 조건도 검토하며 답변/확인 근거가 없으면 설명 인수를 미완료로 남긴다.

실제 요청 경로 사용은 [계약의 후속 호출자 표](contracts/rules-contract.md)와 같이4~6단계에 검증한다. 이번 Unit 통과가 전체3단계의 요청 경로 사용 조건·취소 원자성·중복 복구 방지의 인수를 완료하지 않는다. 기존 프로브/PIT/Property는 유지하고 업무 심화 검증으로의 교체는 후속 단계로 남긴다. 마지막에는 문서 링크/형식·git diff --check와 실제 변경 파일을 검토한 뒤 검증 대상별 커밋을 작성한다. 외부 push/PR·병합은 이번 절차에 포함하지 않는다.
