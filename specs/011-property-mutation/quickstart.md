# Quickstart: 10단계 검증 실행

전제: Docker 실행 중, `backend/`에서 실행. 시험 비밀번호는 테스트 코드가 주입하므로 별도 환경 변수가 필요 없다.

```sh
cd backend
./gradlew test --tests '*ReferenceReservationModelTest'          # 독립 모델 Unit
./gradlew propertyTest                                            # 기본 seed 속성 검증
QT_SEED=<seed> ./gradlew propertyTest                             # seed 재현/새 seed 탐색
./gradlew integrationTest --tests '*ReservationPropertyRegressionIT'
./gradlew mutationSmoke                                           # 세 규칙 PIT + 80% 게이트
./gradlew deepTest                                                # 최종 심화 검증
```

기대 결과와 판정 규칙: [verification-contract](contracts/verification-contract.md). 보고서: `build/test-results/<task>`, `build/reports/tests/<task>`, `build/reports/pitest/`.
