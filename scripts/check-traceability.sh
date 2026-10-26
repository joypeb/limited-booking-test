#!/usr/bin/env bash
# C3 T1~T5 정적 검사. Bash와 Python 3 표준 라이브러리만 사용한다.
# Markdown은 한 줄 pipe table/백틱 값/inline 링크/ATX 제목만 지원한다.
# 셀의 <br> 또는 쉼표로 구분된 여러 백틱 값을 각각 검사한다. fenced code와
# HTML 주석은 표로 보지 않는다. 복잡한 중첩 Markdown/참조형 링크는 미지원이다.
# 대상 경로는 저장소 루트, 증거 링크는 입력 문서 디렉터리(기본 docs/) 기준이다.
# Gradle 판정은 check-readme.sh R3의 리터럴 선언/옵션 목록을 복제한다.
# Java는 파일·메서드 이름의 정적 존재만 확인한다. JUnit 발견/실행이나
# 규칙·대상·시험·증거 사이 의미상 타당성은 이 검사로 증명하지 않는다.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd -- "$ROOT"
file=${1:-docs/traceability.md}
if [[ $# -gt 1 || ! -f "$file" ]]; then
    printf 'FAIL T0: 파일 인자는 존재하는 파일 하나여야 합니다: %s\n' "$file"
    exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
    printf 'FAIL T0: python3가 필요합니다\n'
    exit 1
fi
python3 - "$file" <<'PY'
import collections
import fnmatch
import pathlib
import re
import shlex
import sys
from urllib.parse import unquote, urlsplit

file = pathlib.Path(sys.argv[1]).resolve()
errors = {f'T{number}': [] for number in range(1, 6)}
try:
    text = file.read_text(encoding='utf-8')
except (OSError, UnicodeError) as error:
    print(f'FAIL T0: 파일 읽기 실패: {error}')
    sys.exit(1)
text = re.sub(r'<!--.*?-->', '', text, flags=re.S)
lines = []
fence = None
for line in text.splitlines():
    marker = re.match(r'^\s{0,3}(`{3,}|~{3,})(.*)$', line)
    if fence is None and marker:
        fence = marker[1]
    elif fence is not None:
        if marker and marker[1][0] == fence[0] and len(marker[1]) >= len(fence) and not marker[2].strip():
            fence = None
    else:
        lines.append(line)


def cells(line):
    # 백틱 안의 셸 pipe는 열 구분자가 아니다. \|도 셀 내부에 보존한다.
    parts, start, quote = [], 0, None
    for match in re.finditer(r'`+|(?<!\\)\|', line):
        token = match[0]
        if token.startswith('`'):
            if quote is None:
                quote = token
            elif quote == token:
                quote = None
        elif quote is None:
            parts.append(line[start:match.start()].strip())
            start = match.end()
    parts.append(line[start:].strip())
    if line.strip().startswith('|'):
        parts = parts[1:]
    if line.strip().endswith('|'):
        parts = parts[:-1]
    return parts


def table(start, key):
    header = cells(lines[start])
    if start + 1 >= len(lines) or not all(re.fullmatch(r':?-{3,}:?', value) for value in cells(lines[start + 1])):
        errors[key].append('표 구분 행이 없습니다')
        return header, []
    rows = []
    for line in lines[start + 2:]:
        if not line.strip().startswith('|'):
            break
        row = cells(line)
        if len(row) != len(header):
            errors[key].append(f'표 열 수 불일치: {row[0] if row else "빈 행"}')
        else:
            rows.append(row)
    return header, rows


def codes(cell):
    return [match[2].strip() for match in re.finditer(r'(`+)(.+?)\1', cell)]


expected_header = ['ID', '규칙(계획서 절)', '대상', '테스트', '명령', '증거']
first = next((index for index, line in enumerate(lines) if line.strip().startswith('|')), None)
rows = []
if first is not None:
    header, rows = table(first, 'T1')
    if header != expected_header:
        errors['T1'].append('첫 번째 표의 머리가 계약과 다릅니다')
        rows = []
counts = collections.Counter(row[0] for row in rows)
missing = [f'R{number:02}' for number in range(1, 22) if f'R{number:02}' not in counts]
if missing:
    errors['T1'].append('필수 ID 누락: ' + ', '.join(missing))
duplicates = [key for key, count in counts.items() if count > 1]
if duplicates:
    errors['T1'].append('중복 ID: ' + ', '.join(duplicates))
if not rows:
    for key in ('T2', 'T3', 'T4'):
        errors[key].append('검사할 추적표 데이터 행이 없습니다')

# README 검사와 동일한 리터럴 Gradle 태스크/옵션 처리.
allowed = {'test', 'build', 'bootRun', 'bootJar', 'clean', 'compileJava', 'compileTestJava', 'check', 'dependencies'}
try:
    gradle = pathlib.Path('backend/build.gradle').read_text(encoding='utf-8')
    gradle = re.sub(r"""'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|/\*.*?\*/|//[^\n]*""",
                    lambda match: '' if match[0].startswith(('//', '/*')) else match[0], gradle, flags=re.S)
    allowed.update(re.findall(r'''\btasks\.(?:register|named|create)\s*\(\s*['"]([A-Za-z][\w-]*)['"]''', gradle))
    allowed.update(re.findall(r'\btask\s+([A-Za-z][\w-]*)\b', gradle))
except (OSError, UnicodeError) as error:
    errors['T4'].append(f'build.gradle 읽기 실패: {error}')
value_options = {'--tests', '--args', '--init-script', '-I', '--project-dir', '-p', '--settings-file', '-c',
                 '--build-file', '-b', '--gradle-user-home', '-g', '--max-workers', '--console', '--warning-mode',
                 '--configuration-cache-problems', '--dependency-verification', '--write-verification-metadata',
                 '--exclude-task', '-x', '--include-build', '--project-cache-dir', '--priority', '-D', '-P'}
separators = {';', '&&', '||', '|', '&', '(', ')', '<', '>', '>>', '<<'}
sources = collections.defaultdict(list)
for source in pathlib.Path('backend/src/test').rglob('*.java'):
    sources[source.stem].append(source)
source_text = {}


def java_text(path):
    if path not in source_text:
        raw = path.read_text(encoding='utf-8')
        # 주석/문자열 속 method(는 메서드의 존재 근거로 사용하지 않는다.
        source_text[path] = re.sub(r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*', '', raw, flags=re.S)
    return source_text[path]


def matches_filter(pattern):
    for name, paths in sources.items():
        for path in paths:
            source = java_text(path)
            package = re.search(r'\bpackage\s+([\w.]+)\s*;', source)
            full = f'{package[1]}.{name}' if package else name
            if any(fnmatch.fnmatchcase(candidate, pattern) for candidate in (name, full)):
                return True
            if '.' in pattern:
                class_pattern, method_pattern = pattern.rsplit('.', 1)
                if any(fnmatch.fnmatchcase(candidate, class_pattern) for candidate in (name, full)) and any(
                    fnmatch.fnmatchcase(method, method_pattern) for method in re.findall(r'\b(\w+)\s*\(', source)
                ):
                    return True
    return False


for rule_id, _, target_cell, test_cell, command_cell, evidence_cell in rows:
    targets = codes(target_cell)
    if not targets:
        errors['T2'].append(f'{rule_id}: 대상 백틱 경로가 없습니다')
    for target in targets:
        prefixes = ('backend/src/main/',) + (('docs/', 'backend/src/test/') if rule_id in ('R20', 'R21') else ())
        if not target.startswith(prefixes) or not pathlib.Path(target).exists():
            errors['T2'].append(f'{rule_id}: 허용되지 않거나 없는 대상 경로: {target}')
    tests = codes(test_cell)
    if not tests:
        errors['T3'].append(f'{rule_id}: Class#method가 없습니다')
    for test in tests:
        match = re.fullmatch(r'([\w.$]+)#([\w$]+)', test)
        paths = sources.get(match[1].split('.')[-1], []) if match else []
        if not match or len(paths) != 1:
            errors['T3'].append(f'{rule_id}: 테스트 클래스가 정확히 1개여야 합니다: {test}')
        elif not re.search(r'\b' + re.escape(match[2]) + r'\s*\(', java_text(paths[0])):
            errors['T3'].append(f'{rule_id}: 메서드가 없습니다: {test}')
    commands = codes(command_cell)
    command_count = 0
    for command in commands:
        try:
            lexer = shlex.shlex(command, posix=True, punctuation_chars=';&|()<>')
            lexer.whitespace_split = True
            lexer.commenters = '#'
            args = list(lexer)
        except ValueError as error:
            errors['T4'].append(f'{rule_id}: 명령 해석 실패: {error}')
            continue
        for index, token in enumerate(args):
            if token != './gradlew':
                continue
            command_count += 1
            cursor, task_count = index + 1, 0
            while cursor < len(args) and args[cursor] not in separators:
                argument = args[cursor]
                if argument in value_options:
                    if cursor + 1 >= len(args) or args[cursor + 1] in separators:
                        errors['T4'].append(f'{rule_id}: 옵션 값 누락: {argument}')
                    elif argument == '--tests' and not matches_filter(args[cursor + 1]):
                        errors['T4'].append(f'{rule_id}: 매칭되는 테스트가 없습니다: {args[cursor + 1]}')
                    cursor += 2
                    continue
                if argument.startswith('--tests=') and not matches_filter(argument.split('=', 1)[1]):
                    errors['T4'].append(f'{rule_id}: 매칭되는 테스트가 없습니다: {argument}')
                if not argument.startswith('-'):
                    task_count += 1
                    if argument.lstrip(':') not in allowed:
                        errors['T4'].append(f'{rule_id}: 정의되지 않은 Gradle 태스크: {argument}')
                cursor += 1
            if not task_count:
                errors['T4'].append(f'{rule_id}: 실행 태스크가 없습니다')
    if not command_count:
        errors['T4'].append(f'{rule_id}: ./gradlew 명령이 없습니다')
    evidence_count = 0
    for match in re.finditer(r'\[[^\]]+\]\((<[^>]+>|[^\s)]+)(?:\s+"[^"]*")?\)', evidence_cell):
        target = match[1].strip('<>')
        parsed = urlsplit(target)
        if parsed.scheme or parsed.netloc or target.startswith('/'):
            errors['T4'].append(f'{rule_id}: 증거는 상대 파일 링크여야 합니다: {target}')
            continue
        evidence_count += 1
        path = file.parent / unquote(parsed.path) if parsed.path else file
        if not path.is_file():
            errors['T4'].append(f'{rule_id}: 증거 파일이 없습니다: {target}')
    if not evidence_count:
        errors['T4'].append(f'{rule_id}: 상대 증거 링크가 없습니다')

scope = next((index for index, line in enumerate(lines) if re.fullmatch(r'##\s+검증하지 않은 범위\s*', line)), None)
scope_rows = []
if scope is not None:
    end = next((index for index in range(scope + 1, len(lines)) if re.match(r'^#{1,2}\s', lines[index])), len(lines))
    start = next((index for index in range(scope + 1, end) if lines[index].strip().startswith('|')), None)
    if start is not None:
        header, scope_rows = table(start, 'T5')
        if '이유' not in header:
            errors['T5'].append('미검증 표에 이유 열이 없습니다')
        else:
            reason_index = header.index('이유')
            for row in scope_rows:
                if not re.sub(r'<br\s*/?>|[`*\s]', '', row[reason_index]):
                    errors['T5'].append(f'미검증 범위 {row[0]}의 이유가 비어 있습니다')
if not scope_rows:
    errors['T5'].append('## 검증하지 않은 범위 절에 표 데이터 행이 필요합니다')
for key, reasons in errors.items():
    if reasons:
        print(f'FAIL {key}: ' + '; '.join(dict.fromkeys(reasons)))
sys.exit(1) if any(errors.values()) else print('PASS 5 checks')
PY
