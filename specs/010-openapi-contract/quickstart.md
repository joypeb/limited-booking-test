# 9단계 검증 안내

**현재 상태(2026-10-23)**: 아래 클래스·시험은 구현됐고 명령은 실제 실행·인수했다(T001~T036, PR #12 리뷰 보완 T037~). 실제 수·종료 코드는 [실행 증거](../../docs/test-evidence.md)와 [작업 목록](tasks.md)을 따른다. 이 안내는 계획 단계에 작성했으며, 당시의 "예정·미실행" 표현은 현재 상태에 맞게 고쳤다. 작성 당시에는 Gradle/Docker/HTTP/DTO 변이를 실행하지 않았다.

## 준비

프로젝트 루트에서 현재 브랜치가 feature/010-openapi-contract이고 사용자 변경을 보존했는지 확인한다. Java25·Docker·기존 Wrapper9.7.1을 사용한다. 원본 사용자9.8 변경을 이 worktree에 자동 적용하거나 하향하지 않는다. 컨테이너 이미지/버전·해석된 라이브러리는 docs/versions.md와 ContainerTestSupport를 대조한다. Testcontainers가 만든 동적 DB/Redis만 쓰며 Compose 개발 데이터나 모든 Redis 키를 초기화하지 않는다. 비밀번호는 지원 fixture의 테스트 값이고 응답·로그·증거에서 노출하지 않는다.

## 후속 구현의 좁은 검증

아래 명령은 backend 디렉터리에서 각각 실행한다. 실패는 원인을 확인하고 최소 수정한 뒤 재검증하며 반복/skip으로 숨기지 않는다.

```sh
./gradlew test --tests '*ApiContractValidatorTest'
./gradlew contractTest --tests '*ApiContractIT'
./gradlew contractTest --tests '*ApiContractDependencyIT'
```

마지막 명령은 같은 파일의 `ApiContractDependencyIT`(DB 중단)와 `RedisApiContractDependencyIT`(Redis 중단)를 함께 발견한다. 첫 시험은 정상/정확한 예상 request 위반/response 항상검사/UTC·헤더/default/strict JSON 보완을 검사한다. 업무 IT는 [계약 적용표](contracts/validation-contract.md)의 모든 셀과 정상·실패 시퀀스·응답 훼손을 검사한다. 파괴적 의존성 fixture는 실제 소유 DB/Redis 장애503·독립API·DB무변경을 확인한다. 예상 request 위반이 누락되거나 무관한 위반이 추가되면 실패해야 한다.

## 실제 HTTP 인수 시퀀스

1. ADMIN으로 capacity0·정규화 이름·일반 대상 생성과 USER GET을 검증한다. 예약 가능한 대상은 테스트 준비를 사전 커밋한다.
2. USER_A 생성201(false)→같은 키201(true)→GET200(CONFIRMED)→취소200→중복취소200→생성재전송201(true, 최초 CONFIRMED)→GET200(CANCELLED)을 연결한다. JSON 필드 값·Location·시각·최종 DB 수량식을 비교한다.
3. 적용표의 모든 상태/code별 사례를 실제 인증·필터로 만든다. 409 두 code, 503 DB/Redis, 모든 작업413 및 chunked/실제UTF-8바이트·인증전 우선순위를 구분한다. 429 판정PTTL은 테스트 전용 관측으로 비교한다.
4. 각 정상 교환의 응답 사본 한 요소만 바꾸어 거부를 확인한다. requestBody/headers를 정상 DTO로 다시 만드는 어댑터는 금지한다. 실제 body/상태/헤더 원문을 보존한다.
5. 모든 요청 종료 확인 후 단일 DBsnapshot·소유 Redis 키·정리 결과를 기록한다. client shutdown/await10초, raw socket10초·request9초, 소유container 종료10초 패턴을 유지한다. 미종료/전송 불확실 뒤 다음 reset/준비/제출은0이어야 한다. 최초 원인·정리 실패를 함께 보존한다.

## 별도 실제 DTO 타입변이 실험

구현이 정상 통과한 뒤 단독으로 수행한다. 원복할 정확한 파일 사본/해시와 기존 diff를 임시 경로에 보관한다. 다른 작업의 수정이 없는 상태를 확인하며 파일 전체 checkout/reset으로 사용자 변경을 지우지 않는다.

- 대상: 실제 `backend/src/main/java/com/limitedbooking/backend/resource/ResourceView.java`의 available component int→String. 기존 int 생성자 호출은 (UUID,String,int,int) 보조생성자가 String.valueOf(available)로 위임하도록 최소 보완한다. 이는 실제 DTO 타입·실제 JSON 숫자→문자열 변이이고 controller/SQL/고정 schema/시험 기대값을 바꾸지 않는다. 최종 코드에 남기지 않는다.
- `./gradlew compileJava compileTestJava`가 종료0이어야 한다. 컴파일 실패면 탐지 증거가 아니므로 원인/최소 보완 후 다시 확인한다.
- baseline에서 통과한 `ApiContractIT`의 `resourceSuccessMatchesContract`(구현된 고정 메서드)를 지정 실행한다.

```sh
./gradlew contractTest --tests '*ApiContractIT.resourceSuccessMatchesContract'
```

- 실제 앱 응답201/200에서 available JSON 토큰이 문자열이고 schema integer 위반으로 위 시험이 실패해야 한다. 종료 비0·schema key/location·실제 HTTP 관측을 기록한다. DB 기동/컴파일/일반 assertion 실패를 스키마 탐지로 오인하지 않는다.
- 보관한 정확한 내용으로 해당 임시 변경만 원복하고 diff/해시를 비교한다. 같은 지정 명령이 종료0임을 기록한다. finally 수준의 원복 절차를 유지하고 실패/중단 때도 미원복을 알린다. 이후 전체 최종 게이트를 실행한다.

## 필수 최종 인수

```sh
./gradlew build verifyContract verifyContractDependencies
```

verifyContract가 verify(Unit·일반IT)+contractTest를 포함한다. 일반IT에서 기존 ReservationConcurrencyIT C01~C11 각10회·종료fixture·ReservationRollbackIT/ReservationCancelIT·RedisFailureIT 계열·RateLimitApiIT·기존 권한/입력 HTTP 회귀가 실제 발견됐는지 확인한다. 기존 프로브만 통과하거나 예전 XML만 남은 상태는 실패다. 새 업무 시험 수와 전체 Unit/IT/Contract 수를 구분하고 매 Test 태스크의 listener/XML이 일치하며 실행>0·실패/오류/skip0이어야 한다. 실행 수는 구현 후 관측값으로 기록하며 과거193/312 등을 새 결과로 복사하지 않는다.

기존 verifyContractDependencies는 미해결 의존성과 취약 json-patch/jackson-coreutils 재유입을 차단한다. 런타임과 테스트 classpath 경계도 유지한다. 기존 게이트 자체의 고의 실패를 변경 없이 반복할 필요는 없으며 게이트를 수정했다면 해당 탐지 회귀를 추가한다.

## 기록과 미검증

docs/test-evidence.md에 명령·종료코드·태스크/클래스별 수·실패/오류/skip·최소 재현·응답 훼손 종류별 탐지·DTO 컴파일/HTTP RED/원복 GREEN·실제 의존성/제한/종료·미검증을 남긴다. versions/decisions/README·knowledge 연결·계획서 완료 표시는 실제 검증 후 갱신한다. performance는 비용/시간을 실제 측정한 경우에만 기록하고 운영 규모로 일반화하지 않는다.

Qodana는 최신 사용자 “추후 직접/이번 스킵” 지시에 따라 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. Property/PIT 확장·부하·외부CI·배포는 이번9단계 밖이다. 작성 당시의 다음 작업은 speckit-tasks였고, 이후 [작업 목록](tasks.md)대로 구현·인수했다.
