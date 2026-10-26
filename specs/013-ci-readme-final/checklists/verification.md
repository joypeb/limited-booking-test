# Verification Requirements Checklist: CI·README·최종 검증

**Purpose**: 12단계 요구사항이 완전·명확·측정 가능하고 계획서와 일치하는지 점검(구현 검사 아님)
**Created**: 2026-10-26
**Feature**: [spec.md](../spec.md)

## 완전성

- [x] CHK001 계획서 12단계 체크박스 10개가 모두 FR에 대응하는가? [Completeness] — verify.yml(FR-001·002·004·006), deep-tests(FR-003), build·실행·대기/healthcheck/URL(FR-007·008), Quick Start(FR-007·009), 새 환경(FR-010), 최종 commit 실행(FR-011), 추적표(FR-013), 시연(FR-015), 작은 변경(FR-015), AI 결함(FR-014)
- [x] CHK002 13절 미체크 항목 9개의 판정 근거가 정의됐는가? [Completeness, FR-016, plan D8] — 로컬 재현(US3), 실행 기록·경쟁·계약·속성·PIT(기존 단계 근거 + US4 재실행), CI 구분(US1·US2), 고의 오류 잔존(FR-012), AI 없는 설명(사용자 대기)
- [x] CHK003 사람 전용 항목의 처리(준비 자료·미체크)가 명시됐는가? [Completeness, Clarifications Q2, FR-015]
- [x] CHK004 외부 실행 불가·미검증 범위(실패 전파, deep-tests 외부 run)가 명시됐는가? [Completeness, Clarifications, SC-002]
- [x] CHK005 문서 기록 대상(decisions·versions·performance·test-evidence·계획서·knowledge·AGENTS)이 나열됐는가? [Completeness, FR-016, plan D8]

## 명확성·측정 가능성

- [x] CHK006 “최종 commit”이 검사 가능한 기준(이후 허용 경로)으로 정의됐는가? [Clarity, Assumptions, plan D6]
- [x] CHK007 워크플로 정적 검사 항목이 ID별 통과 조건으로 정의됐는가? [Measurability, contracts C1]
- [x] CHK008 README 점검 항목과 “만들지 않은 명령 0건”의 판정 방법이 정의됐는가? [Measurability, contracts C2 R3·R4]
- [x] CHK009 API 전체 흐름의 단계별 기대 상태·code·수량이 정의됐는가? [Measurability, contracts C4]
- [x] CHK010 추적표 필수 규칙과 참조 존재 판정이 정의됐는가? [Measurability, contracts C3]

## 일관성

- [x] CHK011 CI 명령이 계획서 6.2절 태스크와 일치하는가? [Consistency, research R4] — verifyContract·propertyTest·mutationSmoke 모두 build.gradle 정의
- [x] CHK012 Maven 경로 표기(target/pit-reports·failsafe)와 Gradle 실제 경로의 차이가 기록 대상인가? [Consistency, FR-003, plan D2]
- [x] CHK013 k6 1회 실행과 규칙 문서의 3회 측정의 관계가 설명됐는가? [Consistency, research R9]
- [x] CHK014 404 code가 OpenAPI enum(`NOT_FOUND`)과 일치하는가? [Consistency, contracts C4] — 계획 작성 중 `RESERVATION_NOT_FOUND` 오기를 발견해 수정
- [x] CHK015 CI 트리거가 사용자 결정·헌법 1.0.1과 일치하는가? [Consistency, Clarifications Q1]

## 시나리오·예외

- [x] CHK016 외부 runner 아키텍처·성능 차이에 의한 실패 처리 방침이 있는가? [Edge Case, Spec Edge Cases, Clarifications]
- [x] CHK017 새 환경 재현이 기존 로컬 볼륨·포트와 충돌할 때의 방침이 있는가? [Edge Case, research R8]
- [x] CHK018 기준점 이후 코드가 바뀌는 경우(리뷰 수정)의 재검증 규칙이 있는가? [Edge Case, Spec Edge Cases]
- [x] CHK019 k6 결과의 기준 커밋 불일치(dirty)를 무효로 분류하는가? [Edge Case]
- [x] CHK020 근거 없는 AI 결함 사례를 배제하는 규칙이 있는가? [Edge Case, FR-014, US5-3]

## 범위

- [x] CHK021 범위 밖(Qodana·배포·CV·release/태그·정기 실행·OCR Actions)이 명시됐는가? [Scope]
- [x] CHK022 업무 계약·SQL·의존성 무변경과 실제 결함 시 TDD 예외가 명시됐는가? [Scope, FR-017]

## Notes

- 22/22 충족(2026-10-26, Orchestrator 근거 대조). 요구사항 품질 판정이며 구현·실행 완료 표시가 아니다.
