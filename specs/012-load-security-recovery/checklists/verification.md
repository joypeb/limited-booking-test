# Verification Requirements Checklist: 부하·보안·장애 복구

**Purpose**: 11단계 요구사항이 완전·명확·측정 가능하고 계획서와 일치하는지 점검(구현 검사 아님)
**Created**: 2026-10-24
**Feature**: [spec.md](../spec.md)

## 완전성

- [x] CHK001 계획서 11단계 체크박스 13개가 모두 FR에 대응하는가? [Completeness] — 보안 권한·위조(FR-001·003), 입력·413·노출(FR-001·002·004), DB 중단·잠금(FR-005~008·010), 응답 유실(FR-009), 측정 반복(FR-016), 낮은/높은 경쟁·재요청(FR-014·016), 제한(FR-012·014), expectedStatuses·SOLD_OUT(FR-014), threshold·DB 검사(FR-013·015), 기록(FR-017), 병목 1개(FR-018)
- [x] CHK002 기존 충족 항목과 신규 항목의 경계가 정의됐는가? [Completeness, Spec Clarifications Q1, contracts C1]
- [x] CHK003 장애 종류별 주입·해제·상한·복구 기대가 정의됐는가? [Completeness, data-model 장애 사례, contracts C2]
- [x] CHK004 생성 잠금 시간 초과의 기존 근거와 취소 잠금의 신규 범위가 구분됐는가? [Completeness, Spec §FR-007]
- [x] CHK005 load DB·제한 카운터 초기화 대상이 load 전용으로 한정됐는가? [Completeness, Spec Edge Cases, FR-013]

## 명확성·측정 가능성

- [x] CHK006 “유한한 응답 시간”이 수치 상한으로 정의됐는가? [Clarity, Clarifications Q4 — 4·6·12초]
- [x] CHK007 “예상치 못한 오류”의 범위가 정의됐는가(예상 상태 외 모든 응답·시간 초과)? [Clarity, contracts C3 unexpected]
- [x] CHK008 높은 경쟁의 정확한 건수 기준과 409 code 조건이 정의됐는가? [Measurability, Spec US3-2, SC-004]
- [x] CHK009 재요청 시나리오에서 HTTP 성공 수와 고유 예약 수의 기대가 수치로 정의됐는가? [Measurability, data-model replay 500/50]
- [x] CHK010 로그 비노출 검사의 대상 문자열과 빈 캡처 거부가 정의됐는가? [Clarity, contracts S11]

## 일관성

- [x] CHK011 제한값(부하 100,000/60초, 제한 시험 20/60초, IT 10,000/60초)이 서로 구분돼 기록되는가? [Consistency, FR-012, contracts C2 공통]
- [x] CHK012 소켓 읽기 상한 7초가 lock 2초·statement 5초·연결 획득 3초 계층과 모순되지 않는가? [Consistency, research R3]
- [x] CHK013 실패 키 비보존 규칙과 장애 복구 기대(Replayed false)가 일치하는가? [Consistency, 계획서 2절, Spec US2-2]
- [x] CHK014 응답 유실 기대(Replayed true·같은 id)가 멱등 재현 규칙과 일치하는가? [Consistency, 계획서 4.1절]

## 시나리오·예외

- [x] CHK015 장애 중 500이 나오는 경우의 처리(결함 기록·최소 분류, 500 숨김 금지)가 정의됐는가? [Exception Flow, plan 설계 7]
- [x] CHK016 응답 정지가 재현되지 않을 때의 처리(수정하지 않고 기록)가 정의됐는가? [Exception Flow, Clarifications Q3]
- [x] CHK017 부하 판정 미달과 정합성 결함이 구분되는가? [Exception Flow, Assumptions, contracts C3]
- [x] CHK018 응답 유실에서 커밋 미확인 시 실패 처리가 정의됐는가? [Edge Case, Spec Edge Cases]

## 의존성·가정

- [x] CHK019 계정 3개만 사용하는 가정과 BCrypt 포함 측정의 한계가 기록됐는가? [Assumption, FR-017]
- [x] CHK020 k6 버전의 실행 확인 필요가 미실행 상태로 표시됐는가? [Dependency, research R8]

## Notes

- 모든 항목은 2026-10-24 spec·plan·contracts 대조로 확인했다. 구현·실행 검증 항목이 아니다.
