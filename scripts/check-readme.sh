#!/usr/bin/env bash
# C2 R1~R6 정적 검사. Bash와 Python 3 표준 라이브러리만 사용한다.
# Markdown 휴리스틱: ATX 제목(##/###), backtick/tilde fenced code와
# 한 줄 inline code를 지원한다. HTML 주석은 제외한다. Setext 제목,
# 들여쓰기 코드 블록, 복잡한 중첩/이스케이프 Markdown은 해석하지 않는다.
# 셸은 shlex로 옵션 값/구분자/주석을 구분하되 변수·함수·동적 태스크는
# 실행하지 않는다. 경로는 저장소 루트 기준의 리터럴 참조만 검사한다.
# R6은 '테스트 실행'/'검증 명령' 절에 한정한다. README의 Unit308·일반IT316
# 같은 표기를 지원하며 과거 기록 절의 다른 수치가 현재 표를 통과시키지 못한다.
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd -- "$ROOT"
counts=308,322,102,4
readme=README.md
file_set=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --counts)
            if [[ $# -lt 2 ]]; then
                printf 'FAIL R0: --counts에는 U,I,C,P 값이 필요합니다\n'
                exit 1
            fi
            counts=$2
            shift 2
            ;;
        -*)
            printf 'FAIL R0: 알 수 없는 옵션: %s\n' "$1"
            exit 1
            ;;
        *)
            if [[ $file_set -eq 1 ]]; then
                printf 'FAIL R0: 파일 인자는 하나만 지정합니다\n'
                exit 1
            fi
            readme=$1
            file_set=1
            shift
            ;;
    esac
done
if [[ ! "$counts" =~ ^[0-9]+,[0-9]+,[0-9]+,[0-9]+$ ]]; then
    printf 'FAIL R0: --counts 형식은 U,I,C,P입니다: %s\n' "$counts"
    exit 1
fi
if [[ ! -f "$readme" ]]; then
    printf 'FAIL R0: 파일이 없습니다: %s\n' "$readme"
    exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
    printf 'FAIL R0: python3가 필요합니다\n'
    exit 1
fi

python3 - "$readme" "$counts" <<'PY'
import pathlib
import re
import shlex
import sys
from urllib.parse import unquote

file = pathlib.Path(sys.argv[1])
expected = sys.argv[2].split(',')
errors = {f'R{number}': [] for number in range(1, 7)}
try:
    text = file.read_text(encoding='utf-8')
except (OSError, UnicodeError) as error:
    print(f'FAIL R0: 파일 읽기 실패: {error}')
    sys.exit(1)
text = re.sub(r'<!--.*?-->', '', text, flags=re.S)

# 제목은 fenced code 밖에서만 찾고 명령은 fenced/inline code에서 추출한다.
headings, fragments, code_lines = [], [], []
fence = None
lines = text.splitlines()
for index, line in enumerate(lines):
    marker = re.match(r'^\s{0,3}(`{3,}|~{3,})(.*)$', line)
    if fence is None and marker:
        fence = marker[1]
        code_lines = []
    elif fence is not None:
        if marker and marker[1][0] == fence[0] and len(marker[1]) >= len(fence) and not marker[2].strip():
            fragments.append(('block', '\n'.join(code_lines)))
            fence = None
        else:
            code_lines.append(line)
    else:
        heading = re.match(r'^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$', line)
        if heading:
            headings.append((index, len(heading[1]), heading[2]))
        fragments.extend(('inline', match[2]) for match in re.finditer(r'(`+)(.+?)\1', line))
if fence is not None:
    fragments.append(('block', '\n'.join(code_lines)))


def section(start, level):
    end = next((index for index, depth, _ in headings if index > start and depth <= level), len(lines))
    return '\n'.join(lines[start:end]), end


quick = next(((index, depth) for index, depth, title in headings if depth == 2 and title == 'Quick Start'), None)
required = ('필요 환경', '환경 변수', 'Compose', '마이그레이션', '애플리케이션 실행', 'health', 'API 전체 흐름', '종료')
if quick is None:
    errors['R1'].append('## Quick Start 절이 없습니다')
else:
    _, end = section(*quick)
    titles = [title for index, depth, title in headings if quick[0] < index < end and depth == 3]
    if len(titles) != 8 or any(word not in title for word, title in zip(required, titles)):
        errors['R1'].append('Quick Start의 ### 하위 절 8개와 필수 제목 순서가 필요합니다')

if not any(all(word in section(index, depth)[0] for word in ('healthcheck', '/actuator/health', '127.0.0.1'))
           for index, depth, _ in headings if depth in (2, 3)):
    errors['R2'].append('한 절에 healthcheck·/actuator/health·127.0.0.1 설명이 필요합니다')

allowed = {'test', 'build', 'bootRun', 'bootJar', 'clean', 'compileJava', 'compileTestJava', 'check', 'dependencies'}
try:
    gradle = pathlib.Path('backend/build.gradle').read_text(encoding='utf-8')
    # '**/*Test.class' 같은 문자열 속 /*는 Groovy 주석이 아니다.
    gradle = re.sub(r"""'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|/\*.*?\*/|//[^\n]*""",
                    lambda match: '' if match[0].startswith(('//', '/*')) else match[0], gradle, flags=re.S)
    allowed.update(re.findall(r'''\btasks\.(?:register|named|create)\s*\(\s*['"]([A-Za-z][\w-]*)['"]''', gradle))
    allowed.update(re.findall(r'\btask\s+([A-Za-z][\w-]*)\b', gradle))
except (OSError, UnicodeError) as error:
    errors['R3'].append(f'build.gradle 읽기 실패: {error}')

value_options = {'--tests', '--args', '--init-script', '-I', '--project-dir', '-p', '--settings-file', '-c',
                 '--build-file', '-b', '--gradle-user-home', '-g', '--max-workers', '--console', '--warning-mode',
                 '--configuration-cache-problems', '--dependency-verification', '--write-verification-metadata',
                 '--exclude-task', '-x', '--include-build', '--project-cache-dir', '--priority', '-D', '-P'}
separators = {';', '&&', '||', '|', '&', '(', ')', '<', '>', '>>', '<<'}


def tokens(source):
    lexer = shlex.shlex(source.replace('\\\n', ' '), posix=True, punctuation_chars=';&|()<>')
    lexer.whitespace_split = True
    lexer.commenters = '#'
    return list(lexer)


unknown = set()
for kind, source in fragments:
    # 셸 명령은 줄 경계를 넘기지 않는다(명시적 역슬래시 연속 줄은 결합).
    for line in source.replace('\\\n', ' ').splitlines():
        if './gradlew' not in line and not (kind == 'block' and 'docker' in line):
            continue
        try:
            args = tokens(line)
        except ValueError as error:
            errors['R3' if './gradlew' in line else 'R5'].append(f'명령 구문 해석 실패: {error}')
            continue
        for index, token in enumerate(args):
            if token != './gradlew':
                continue
            cursor = index + 1
            while cursor < len(args) and args[cursor] not in separators:
                argument = args[cursor]
                if argument in value_options:
                    cursor += 2
                    continue
                if not argument.startswith('-') and argument.lstrip(':') not in allowed:
                    unknown.add(argument)
                cursor += 1
        if kind == 'block':
            for index in range(len(args) - 1):
                if args[index:index + 2] != ['docker', 'compose']:
                    continue
                command = []
                for argument in args[index + 2:]:
                    if argument in separators:
                        break
                    command.append(argument)
                if 'down' in command and any(argument in ('-v', '--volumes') or argument.startswith('--volumes=') for argument in command):
                    errors['R5'].append('코드 블록의 docker compose down에 볼륨 삭제 옵션이 있습니다')
if unknown:
    errors['R3'].append('정의되지 않은 Gradle 태스크: ' + ', '.join(sorted(unknown)))

# Markdown 링크 대상/inline code/본문 모두의 리터럴 경로를 검사한다.
references = re.findall(r'''(?<![\w/.-])(?:\./)?(?:load/|scripts/|\.github/)[^\s`<>()[\]{}|"',;:]*''', text)
missing = set()
for reference in references:
    path = unquote(reference.split('#', 1)[0]).rstrip('.')
    if not pathlib.Path(path).exists():
        missing.add(path)
if missing:
    errors['R4'].append('존재하지 않는 경로: ' + ', '.join(sorted(missing)))

count_sections = [section(index, depth)[0] for index, depth, title in headings
                  if depth in (2, 3) and ('테스트 실행' in title or '검증 명령' in title)]
count_text = re.sub(r'[`*]', '', '\n'.join(count_sections))
for label, count in zip(('Unit', 'IT', 'Contract', 'Property'), expected):
    if not re.search(re.escape(label) + r'\s*' + re.escape(count) + r'(?!\d)', count_text):
        errors['R6'].append(f'테스트 실행/검증 명령 절에 {label}{count} 표기가 필요합니다')

for key, reasons in errors.items():
    if reasons:
        print(f'FAIL {key}: ' + '; '.join(reasons))
if any(errors.values()):
    sys.exit(1)
print('PASS 6 checks')
PY
