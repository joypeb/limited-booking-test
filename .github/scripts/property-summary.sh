#!/usr/bin/env bash
# propertyTest XML(TEST-*.xml)의 system-out에서 재현 정보를 추출한다.
# 사용: property-summary.sh <XML 디렉터리>
# 출력: 파일명: seed=/attempts=/generated-attempts=/scenario-digest= 줄, 마지막에 testsuite 합계.
# 줄과 합계를 먼저 출력한 뒤 필수 재현 정보를 판정한다(실패해도 요약이 artifact로 남도록).
# 필수 정보(없거나 형식이 틀리면 누락 항목을 stderr에 출력하고 종료 1):
#   ReservationPropertyIT XML: seed=<정수>, attempts=<정수 ≥100>, scenario-digest=<64자 16진>
#   ToolchainPropertyTest XML: generated-attempts=<정수>
# XML이 없어도 종료 1이다. 이 스크립트는 정보 존재만 판정하며, failures/errors가 0보다 커도
# 정보가 있으면 종료 0이다. 테스트 실패 판정은 Gradle 몫이다.
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "사용법: $0 <XML 디렉터리>" >&2
    exit 1
fi

python3 - "$1" <<'PY'
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

directory = pathlib.Path(sys.argv[1])
files = sorted(directory.glob('TEST-*.xml')) if directory.is_dir() else []
if not files:
    print(f'TEST-*.xml이 없습니다: {directory}', file=sys.stderr)
    sys.exit(1)

keys = ('seed=', 'attempts=', 'generated-attempts=', 'scenario-digest=')
totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
lines_by_file = {}
for file in files:
    root = ET.parse(file).getroot()
    for suite in ([root] if root.tag == 'testsuite' else root.iter('testsuite')):
        for name in totals:
            totals[name] += int(suite.get(name, '0'))
    found = lines_by_file[file.name] = []
    for out in root.iter('system-out'):
        for line in (out.text or '').splitlines():
            if any(key in line for key in keys):
                print(f'{file.name}: {line.strip()}')
                found.append(line)
print('합계: ' + ' '.join(f'{name}={value}' for name, value in totals.items()))
sys.stdout.flush()

# 파일 종류별 필수 재현 정보. (이름 조각, 설명, 정규식, 추가 조건)
required = (
    ('ReservationPropertyIT', 'seed', r'(?<![\w-])seed=(\d+)(?!\w)', None),
    ('ReservationPropertyIT', 'attempts(≥100)', r'(?<![\w-])attempts=(\d+)(?!\w)', lambda v: int(v) >= 100),
    ('ReservationPropertyIT', 'scenario-digest(64자 16진)', r'(?<![\w-])scenario-digest=([0-9a-fA-F]{64})(?![0-9a-fA-F])', None),
    ('ToolchainPropertyTest', 'generated-attempts', r'(?<![\w-])generated-attempts=(\d+)(?!\w)', None),
)
missing = []
for part, label, pattern, check in required:
    targets = [name for name in lines_by_file if part in name]
    if not targets:
        missing.append(f'{part} XML 없음({label})')
        continue
    for name in targets:
        values = [m[1] for line in lines_by_file[name] for m in re.finditer(pattern, line)]
        if not any(check is None or check(v) for v in values):
            missing.append(f'{name}: {label} 누락 또는 형식 오류')
if missing:
    for item in missing:
        print('필수 재현 정보 누락: ' + item, file=sys.stderr)
    sys.exit(1)
PY
