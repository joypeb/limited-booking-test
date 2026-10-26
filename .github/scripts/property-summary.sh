#!/usr/bin/env bash
# propertyTest XML(TEST-*.xml)의 system-out에서 재현 정보를 추출한다.
# 사용: property-summary.sh <XML 디렉터리>
# 출력: 파일명: seed=/attempts=/generated-attempts=/scenario-digest= 줄, 마지막에 testsuite 합계.
# XML이 없거나 재현 정보 줄이 하나도 없으면 종료 1.
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "사용법: $0 <XML 디렉터리>" >&2
    exit 1
fi

python3 - "$1" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

directory = pathlib.Path(sys.argv[1])
files = sorted(directory.glob('TEST-*.xml')) if directory.is_dir() else []
if not files:
    print(f'TEST-*.xml이 없습니다: {directory}', file=sys.stderr)
    sys.exit(1)

keys = ('seed=', 'attempts=', 'generated-attempts=', 'scenario-digest=')
totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
found = 0
for file in files:
    root = ET.parse(file).getroot()
    for suite in ([root] if root.tag == 'testsuite' else root.iter('testsuite')):
        for name in totals:
            totals[name] += int(suite.get(name, '0'))
    for out in root.iter('system-out'):
        for line in (out.text or '').splitlines():
            if any(key in line for key in keys):
                print(f'{file.name}: {line.strip()}')
                found += 1
if found == 0:
    print('seed/attempts/scenario-digest 줄이 없습니다', file=sys.stderr)
    sys.exit(1)
print('합계: ' + ' '.join(f'{name}={value}' for name, value in totals.items()))
PY
