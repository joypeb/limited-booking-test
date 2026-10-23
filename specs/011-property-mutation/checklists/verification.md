# Verification Requirements Checklist: 속성·변이·고의 변경

**Purpose**: 10단계 요구사항이 완전·명확·측정 가능하고 계획서와 일치하는지 점검(구현 검사 아님)
**Created**: 2026-10-23
**Feature**: [spec.md](../spec.md)

## 완전성

- [x] CHK001 계획서 10단계 체크박스 10개가 모두 FR에 대응하는가? [Completeness, Spec §FR-001~017] — command(FR-001)/논리ID(FR-002)/초기화·크기(FR-004)/비교(FR-005)/seed·회귀(FR-007·009)/verifyProperty(FR-008)/PIT 대상(FR-011)/생존 분류(FR-013)/고의 4종(FR-014)/deepTest(FR-016)
- [x] CHK002 “타인의 예약 취소”와 “존재하지 않는 참조”의 기대 결과가 정의됐는가? [Completeness, Spec §US1-4, data-model 전이 3]
- [x] CHK003 대상 없음(MISSING) 경로의 기대 결과와 키 우선 순서가 정의됐는가? [Completeness, data-model 전이 1]
- [x] CHK004 고의 변경 4종 각각의 위치·실행 명령·기대 판정이 정의됐는가? [Completeness, contracts C4]

## 명확성·측정 가능성

- [x] CHK005 “최소 100회”가 생성 예산이 아닌 실제 실행 수로 정의됐는가? [Clarity, Spec §FR-006, research R7]
- [x] CHK006 축소 품질 기준(≤5건)과 근거가 정량화됐는가? [Measurability, Spec §SC-002, Clarifications]
- [x] CHK007 탐지율 분모·분자(killed/generated)와 무효 상태 처리가 명시됐는가? [Clarity, contracts C3]
- [x] CHK008 시각 비교가 모델 밖 “최초 값 동일성”으로 정의되어 모델이 구현 시각을 복제하지 않는가? [Clarity, data-model 결과]

## 일관성

- [x] CHK009 속성 경로가 Redis·HTTP를 우회한다는 한계가 계획서 10단계 문구와 일치하는가? [Consistency, Spec §FR-010]
- [x] CHK010 순차 관측 불가 변경(B)의 속성 미탐지를 결함으로 세지 않는 규칙이 7단계 경쟁 책임과 일치하는가? [Consistency, Clarifications, research R4]
- [x] CHK011 `propertyTest`의 `*IT` 확장이 `integrationTest` 제외 태그와 충돌하지 않는가? [Consistency, research R2]
- [x] CHK012 `mutationSmoke` 이름 유지·대상 교체가 계획서 6.2 실행 계약과 모순되지 않는가? [Consistency, research R5] — 6.2 표의 설명 문구 갱신 작업 필요(Tasks 포함)

## 시나리오·예외 범위

- [x] CHK013 capacity 0, 품절 뒤 다른 대상 성공, 취소 후 재요청, 중복 취소가 edge case로 정의됐는가? [Coverage, Spec §Edge Cases]
- [x] CHK014 실행기가 예상 밖 예외를 결과 종류로 바꾸지 않는다는 요구가 있는가? [Coverage, plan 설계 요점 3]
- [x] CHK015 축소 재실행마다 DB·대응표 초기화가 요구되는가? [Coverage, Spec §FR-004]

## 추적성

- [x] CHK016 고의 결함과 실제 구현 결함의 구분 기록이 요구되는가? [Traceability, Spec §US2-3, Assumptions]
- [x] CHK017 기록 대상 문서(test-evidence·versions·decisions·README·계획서·knowledge)가 지정됐는가? [Traceability, Spec §FR-017, plan Constitution IV]

## Notes

- 전 항목 통과. CHK012의 문서 갱신은 Tasks의 문서 동기화 작업으로 처리한다.
