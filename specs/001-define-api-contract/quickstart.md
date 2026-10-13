# 0단계 문서 검토 안내

이 안내는 계약·사례·결정 기록의 문서 검증용이다. 서버 기동·컨테이너·curl 실행이나 도구 설치는 요구하지 않는다. 실제 API 동작의 검증 안내는 구현 후 작성한다.

## 준비

1. 저장소 루트에서 [명세](spec.md), [계약 설계](contracts/api-contract.md), [모델](data-model.md), [조사](research.md)를 읽는다.
2. 후속 작업에서 `docs/openapi.yaml`, `docs/decisions.md`, `docs/test-evidence.md`를 작성한다. 현재 계획 작성으로 이 파일들이 생성된 것은 아니다.
3. 각 API 사례에 인증 사용자·초기 상태·입력·응답·최종 상태를 기록한다. 실제 비밀번호를 예시에 넣지 않는다.

## 현재 설계 산출물 검사

저장소 루트에서 실행한다. 아래 검사는 Python 표준 라이브러리만 사용하며 OpenAPI validator가 아니다.

```sh
python3 - <<'PYCODE'
from pathlib import Path
import re
root = Path('specs/001-define-api-contract')
files = [root / name for name in (
    'spec.md', 'plan.md', 'research.md', 'data-model.md',
    'quickstart.md', 'contracts/api-contract.md',
    'checklists/requirements.md',
)]
for file in files:
    text = file.read_text(encoding='utf-8')
    # 예시 코드 블록은 문서 링크·미치환 표식 검사에서 제외한다.
    prose = re.sub(r'```[^\n]*\n.*?```', '', text, flags=re.S)
    assert 'NEEDS CLARIFICATION' not in prose, file
    for target in re.findall(r'\]\(([^\n]+?)\)', prose):
        target = target.strip('<>')
        if not target.startswith(('https://', 'http://')):
            assert (file.parent / target.split('#', 1)[0]).exists(), (file, target)
print('설계 문서 7개와 상대 링크 검사 통과')
PYCODE
git diff --check
```

기대 결과: 두 명령의 종료 코드 0. 첫 검사는 설계 파일·링크의 존재만 확인하며 내용 정확성·실제 API 통과를 증명하지 않는다. `git diff --check`는 추적된 변경의 공백 검사이며 새 파일 내용은 별도 검토한다.

## 후속 0단계 산출물 확인

아래 명령은 최종 문서 작성 뒤 실행한다. 아직 파일이 없으면 실패가 정상이며 완료 처리하지 않는다.

```sh
test -s docs/openapi.yaml
test -s docs/decisions.md
test -s docs/test-evidence.md
```

OpenAPI YAML 파싱·자동 스키마 검증 명령은 아직 채택하지 않았다. 1단계에서 실제 도구 준비·호환성 확인 뒤 정확한 명령을 기록한다.

## 수동 검토 절차와 기대 결과

| 대상 | 대조 절차 | 합격 조건 |
| --- | --- | --- |
| 다섯 API | 계약 설계의 권한·입력·DTO·성공·실패·헤더와 OpenAPI 대조 | 누락·모순 0건 |
| 최소 사례 | 각 API 정상 1개·비정상 2개 이상, 초기 상태와 결과 확인 | 15개 이상, 모든 결과 판정 가능 |
| 수량 1 흐름 | 명세 흐름 1~6행에 남은 수량·CONFIRMED 수·K 기록 대조 | 합계 항상 1, 취소 복구 1회, 추가 예약 없음 |
| 실패 키 복구 | 계약의 B 점유 → A/F 품절 → B 취소 → A/F 성공 | F는 실패에 미보존, 마지막 수량 0·활성 1 |
| 키 범위 | 같은 사용자·다른 대상, 다른 키, 다른 사용자·같은 키 | 충돌과 별개 생성 구분 |
| 엄격한 입력 | 미정의 필드·중복 키·UUID·capacity·이모지 길이·본문·미디어 유형 | 설명·기대 상태 일치, 실행 검증은 미실행 |
| 현재 상태와 재현 | 취소 후 생성 재요청과 GET 비교 | 생성 CONFIRMED·현재 CANCELLED, 동일 생성 시각 |
| 오류·제한 | ProblemDetail 10개 code와 헤더·처리 순서 대조 | 401·429 필수 헤더, 거부 시 업무 무변경 |
| 결정 기록 | JDBC·성공 예약 키·Redis 진입 제한 | 각각 3~5줄, 이유·대안·영향 있음 |
| 설명 능력 | 사용자가 네 키/취소 규칙과 세 선택을 자기 말로 설명 | 실제 확인 여부 기록, 대리 작성만으로 완료하지 않음 |

## 증거와 완료 판정

후속 작업의 `docs/test-evidence.md`에 날짜·실제 브랜치/커밋·문서 검사 명령·종료 코드·사례 수·수량표 검토 결과·본인 설명 여부·미실행 범위를 적는다. 앱 테스트 수는 문서 검사와 구분한다. API·DB·Redis·OpenAPI 자동 검증·CI는 실행 전 미검증이다.

이번 계획의 완성과 원 계획 0단계의 완료는 별개다. 위 문서 산출물·사례·본인 설명이 실제 확인된 뒤에만 0단계 완료를 표시한다. 다음 명령은 `$speckit-tasks`다.

## 작성된 문서의 내용·링크·공백 검사

0단계 산출물 작성 후 아래 명령을 저장소 루트에서 실행한다. 표준 라이브러리로 파일·링크·필수 표식만 검사한다. YAML 파싱·OpenAPI validator·실제 HTTP 검증이 아니다. 공백 정책 확인 또는 T025 설명이 미완료라면 이 명령의 성공으로 전체 0단계를 완료 처리하지 않는다.

```sh
python3 - <<'PYCODE'
from pathlib import Path
import re
files = list(Path('specs/001-define-api-contract').rglob('*.md'))
files += [Path('knowledge/README.md')]
files += list(Path('knowledge/rules').glob('*.md'))
files += [f for f in Path('knowledge/domain').glob('*.md') if f.name != '_template.md']
files += list(Path('docs').glob('*.md'))
for file in files:
    text = file.read_text(encoding='utf-8')
    prose = re.sub(r'```[^\n]*\n.*?```', '', text, flags=re.S)
    for target in re.findall(r'\]\(([^\n]+?)\)', prose):
        target = target.strip('<>')
        if not target.startswith(('http://', 'https://')):
            assert (file.parent / target.split('#', 1)[0]).exists(), (file, target)
    for line in text.splitlines():
        assert not line.rstrip(' ').endswith('\t'), file
        assert not re.search(r'[^ ] {1}$| {3,}$', line), (file, line)
for domain in ('resource', 'reservation', 'security', 'ratelimit'):
    text = Path(f'knowledge/domain/{domain}.md').read_text()
    assert len(re.findall(r'^## [1-9]\.', text, re.M)) == 9, domain
contract = Path('docs/openapi.yaml').read_text()
assert contract.startswith('openapi: 3.0.3\n')
assert len(re.findall(r'^      operationId:', contract, re.M)) == 5
for token in ('basicAuth', 'additionalProperties: false', 'Idempotency-Key',
              'Location:', 'Idempotent-Replayed:', 'Retry-After:',
              'WWW-Authenticate:', 'nullable: true', '8192', '415', '413'):
    assert token in contract, token
refs = re.findall(r"\$ref: '#/components/(\w+)/(\w+)'", contract)
for section, name in refs:
    assert re.search(r'^    ' + re.escape(name) + r':$', contract, re.M), (section, name)
evidence = Path('docs/test-evidence.md').read_text()
assert len(re.findall(r'^\| F\d{2} \|', evidence, re.M)) == 19
assert len(re.findall(r'^\| C\d{2} \|', evidence, re.M)) == 5
assert len(re.findall(r'^\| R\d{2} \|', evidence, re.M)) == 41
print(f'문서 {len(files)}개 링크·형식, 도메인4개, API5개 필수 표식, 사례65행 존재 검사 통과')
PYCODE
```

출력의 사례 65행은 F19+C5+R41의 문서 행 수이며 앱 테스트 수가 아니다. 필수 표식 존재 검사로 계약의 의미나 YAML 문법을 자동 검증했다고 표현하지 않는다. 위 검사에 이어 `git diff --check`와 최종 산출물 3개의 `test -s`를 각각 실행하고 종료 코드를 기록한다.
