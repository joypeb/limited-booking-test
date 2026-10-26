# Security·UX Requirements Checklist: 로컬 브라우저 예약 콘솔

**Purpose**: 구현 전 보안 경계·오류 표시·접근성 요구사항의 완전성·명확성·일관성 검증(요구사항의 단위 테스트)
**Created**: 2026-10-26
**Feature**: [spec.md](../spec.md), [plan.md](../plan.md), [contracts/](../contracts/)
**Depth/Audience**: Standard / PR Reviewer(기본값: 대화형 질문 없이 진행, 초점은 보안 경계·오류/UX 상위 2개 군)

## Requirement Completeness

- [x] CHK001 백엔드에 전달 가능한 경로·메서드 집합이 열거되어 있는가? [Completeness, Spec §FR-003, C1.1]
- [x] CHK002 전달 계층이 복사·제거·이름 변경하는 요청/응답 헤더가 모두 열거되어 있는가? [Completeness, Spec §FR-005, C1.3·C1.4]
- [x] CHK003 콘솔 자체 오류의 상황·상태·code가 모두 정의되어 있는가? [Completeness, C1.5, data-model 오류 코드 표]
- [x] CHK004 비밀번호가 남으면 안 되는 위치가 모두 열거되어 있는가(저장소·쿠키·URL·로그·파일·최근 기록)? [Completeness, Spec §FR-002·FR-014]
- [x] CHK005 백엔드 오류 code 10종 각각의 표시 문구가 정의되어 있는가? [Completeness, Spec §SC-002, C2.4]
- [x] CHK006 계정 전환 중 도착한 응답의 처리 요구가 있는가? [Completeness, Spec §FR-012]

## Requirement Clarity

- [x] CHK007 "다른 출처" 판정 기준(Host·Origin·Sec-Fetch-Site)이 구체적인 값으로 정의되어 있는가? [Clarity, Spec Clarifications, C1.2]
- [x] CHK008 "입력을 다듬지 않음"과 "전송 차단"의 경계(수량 문법·UUID)가 명확한가? [Clarity, Spec §FR-008, data-model 입력 규칙]
- [x] CHK009 콘솔의 시간 상한(서버 10초·클라이언트 15초)이 수치로 정해져 있는가? [Clarity, Spec Clarifications, plan D1·D2]
- [x] CHK010 "재현 여부" 표시와 현재 상태 안내 문구가 고정되어 있는가? [Clarity, Spec §FR-010, C2.3]

## Requirement Consistency

- [x] CHK011 401 처리(대화상자 억제)와 계약 관찰(인증 요구 헤더 표시) 요구가 서로 충돌하지 않는가? [Consistency, Spec §FR-006, C1.4]
- [x] CHK012 백엔드 503과 콘솔 502/504의 구분 요구가 spec·contract·data-model에서 일치하는가? [Consistency, Spec §FR-011, C1.5, C2.4]
- [x] CHK013 헌법 원칙 VI·api-and-security 규칙(CORS 미추가)과 전달 설계가 일치하는가? [Consistency, plan Constitution Check]
- [x] CHK014 FR-018의 문서 갱신 대상이 기존 "범위 밖" 문구 위치(Explorer 조사 8항목)를 모두 포함하는가? [Consistency, plan D7]

## Acceptance Criteria Quality

- [x] CHK015 SC-002~SC-005가 자동 테스트 또는 종단 검증 중 무엇으로 확인되는지 명시되어 있는가? [Measurability, Spec §SC]
- [x] CHK016 SC-001의 "5분" 기준과 확인 방법(종단 검증 기록 1회 이상)이 명시되어 있는가? [Measurability, Spec §SC-001]

## Scenario Coverage

- [x] CHK017 예외 흐름(401·403·404·409×2·429·503·연결 실패·비JSON 응답)이 시나리오나 엣지 케이스로 다뤄지는가? [Coverage, Spec US1~US3·Edge Cases]
- [x] CHK018 회복 흐름(재시도 없음, 사용자의 명시적 재전송만)이 정의되어 있는가? [Coverage, Spec §FR-009·FR-011]
- [x] CHK019 413·415처럼 콘솔 화면으로 정상 재현하기 어려운 백엔드 오류의 처리 범위가 기록되어 있는가? [Coverage, research R7] — 화면은 code 표시만 보장, chunked 413은 백엔드 테스트 담당으로 기록.

## Edge Case Coverage

- [x] CHK020 경로 조작 문자열·인코딩된 구분자에 대한 요구가 있는가? [Edge Case, Spec Edge Cases, C1.1]
- [x] CHK021 최근 기록 저장소 손상·용량(최대 개수) 처리 요구가 있는가? [Edge Case, data-model HistoryEntry]
- [x] CHK022 빈 생성 키·사용자가 바꾼 키의 처리 요구가 있는가? [Edge Case, data-model 입력 규칙, Spec US2-5]

## Non-Functional Requirements

- [x] CHK023 접근성 요구(레이블·라이브 영역·키보드)가 검증 가능한 형태로 정의되어 있는가? [Accessibility, Spec §FR-016, C2]
- [x] CHK024 콘솔 응답 보안 헤더와 제외한 CSP 범위가 기록되어 있는가? [Security, Spec §FR-019, Clarifications]
- [x] CHK025 localhost 전용 실행 요구가 측정 가능하게(바인딩 주소) 정의되어 있는가? [Security, Spec §FR-017, plan D5]

## Dependencies & Assumptions

- [x] CHK026 E2E가 로컬 개발 DB를 쓰지 않는다는 요구와 격리 방법이 정의되어 있는가? [Assumption, plan D6, research R8]
- [x] CHK027 프런트엔드 CI 제외가 사용자 결정 대기로 명시되어 있는가? [Assumption, Spec Assumptions]

## Notes

- 1회차 검토(2026-10-26, Orchestrator): 생성 시 미체크로 만든 뒤 spec·plan·contracts·data-model과 대조해 전 항목 통과로 표시했다. 대조 중 발견한 보완은 없음(CHK019는 research R7에 범위 한계를 기록한 것으로 통과).
- 독립 검토: Analyze 단계와 Reviewer 로컬 리뷰에서 다시 확인한다.
