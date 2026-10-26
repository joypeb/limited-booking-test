#!/usr/bin/env bash
# C1 W1~W6 검사. Bash, Docker, Python 3 표준 라이브러리만 사용한다.
# W1이 YAML/Actions 문법을 검사하고 W2~W6은 보수적인 정적 검사를 한다.
# 따옴표 밖 # 주석은 제거하며 따옴표 안 #은 값으로 보존한다. run 블록의
# 셸 주석도 제거하지만 heredoc/여러 줄 셸 문자열/동적 표현식은 해석하지 않는다.
# 구조 검사는 들여쓰기 기반 block mapping/sequence용이다. flow mapping,
# anchor/alias/merge는 해석 누락으로 통과하지 않도록 W4~W6에서 거부한다.
# 문자열 검사는 실행 의미를 증명하지 않는다(예: echo 안 금지 문자열도 탐지).
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd -- "$ROOT"
WORKFLOW_DIR=${1:-.github/workflows}
if [[ ! -d "$WORKFLOW_DIR" ]]; then
    printf 'FAIL W0: 워크플로 디렉터리가 없습니다: %s\n' "$WORKFLOW_DIR"
    exit 1
fi
WORKFLOW_DIR=$(cd -- "$WORKFLOW_DIR" && pwd -P)
shopt -s nullglob
files=()
for file in "$WORKFLOW_DIR"/*.yml "$WORKFLOW_DIR"/*.yaml; do
    [[ -f "$file" ]] && files+=("$file")
done
if [[ ${#files[@]} -eq 0 ]]; then
    printf 'FAIL W0: *.yml 또는 *.yaml 파일이 없습니다: %s\n' "$WORKFLOW_DIR"
    exit 1
fi

failed=0
image='rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667'
# 파일을 명시하면 /repo의 .git 유무와 무관하게 모든 입력 파일을 검사한다.
container_files=()
for file in "${files[@]}"; do
    container_files+=("/repo/.github/workflows/${file##*/}")
done
if lint_output=$(docker run --rm -v "$WORKFLOW_DIR:/repo/.github/workflows:ro" \
    -w /repo "$image" -color=false "${container_files[@]}" 2>&1); then
    :
else
    printf 'FAIL W1: actionlint 오류 또는 Docker 실행 실패\n'
    printf '%s\n' "$lint_output" | sed -n '1,20p'
    failed=1
fi

if ! command -v python3 >/dev/null 2>&1; then
    for id in W2 W3 W4 W5 W6; do
        printf 'FAIL %s: 정적 검사에 필요한 python3가 없습니다\n' "$id"
    done
    exit 1
fi

if python3 - "$WORKFLOW_DIR" <<'PY'
import pathlib
import re
import sys

directory = pathlib.Path(sys.argv[1])
errors = {key: [] for key in ('W2', 'W3', 'W4', 'W5', 'W6')}


def uncomment(line):
    quote = None
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
        elif char == '\\' and quote != "'":
            escaped = True
        elif char == quote:
            quote = None
        elif char in "\"'" and quote is None:
            quote = char
        elif char == '#' and quote is None and (index == 0 or line[index - 1].isspace()):
            return line[:index].rstrip()
    return line.rstrip()


def scalar(value):
    return value.strip().strip('"\'')


class Node:
    def __init__(self, key='', value='', indent=-1):
        self.key, self.value, self.indent = key, value, indent
        self.children = []

    def child(self, key):
        return next((node for node in self.children if node.key == key), None)

    def walk(self):
        yield self
        for node in self.children:
            yield from node.walk()


def parse(lines):
    root = Node()
    stack = [root]
    block = None
    for line in lines:
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        if block is not None and indent > block.indent:
            block.value += '\n' + line.strip()
            continue
        block = None
        while stack[-1].indent >= indent:
            stack.pop()
        content = line.strip()
        if content.startswith('- '):
            item = Node('-', '', indent)
            stack[-1].children.append(item)
            stack.append(item)
            content = content[2:]
            indent += 2
        match = re.match(r'''([\w-]+|"[\w-]+"|'[\w-]+'):\s*(.*)$''', content)
        if not match:
            stack[-1].value += '\n' + content
            continue
        node = Node(scalar(match[1]), match[2], indent)
        stack[-1].children.append(node)
        stack.append(node)
        if re.fullmatch(r'[|>][+-]?[0-9]?', node.value):
            block = node
    return root


def fail(key, file, reason):
    errors[key].append(f'{file.name}: {reason}')


for file in sorted((*directory.glob('*.yml'), *directory.glob('*.yaml'))):
    try:
        lines = [uncomment(line) for line in file.read_text(encoding='utf-8').splitlines()]
    except (OSError, UnicodeError) as error:
        for key in errors:
            fail(key, file, f'읽기 실패: {error}')
        continue
    text = '\n'.join(lines)
    root = parse(lines)
    # 축약 매핑/별칭을 조용히 무시하면 uses/timeout 등의 누락이 PASS가 된다.
    # 표현식 ${{ ... }}와 branches/runs-on의 flow sequence는 이 제한과 무관하다.
    unsupported = any(re.search(
        r'''^\s*(?:-\s+)?(?:\{|(?:[\w-]+|"[\w-]+"|'[\w-]+'):\s*(?:\{(?!\{)|[&*])|<<:)''', line
    ) for line in lines)
    if unsupported:
        for key in ('W4', 'W5', 'W6'):
            fail(key, file, '구조 검사는 flow mapping/anchor/alias/merge를 지원하지 않음')
    if 'continue-on-error' in text:
        fail('W2', file, 'continue-on-error 금지')
    if re.search(r'\|\|\s*(?:true\b|:(?=\s|$))|\bset\s+\+e\b|--exclude-task\b', text) or any(
        re.search(r'gradlew\b.*(?:^|\s)-x(?:\s|$)', line) for line in lines
    ) or any(node.key == 'run' and 'gradlew' in node.value and re.search(
        r'(?:^|\s)-x(?:\s|$)', node.value
    ) for node in root.walk()):
        fail('W3', file, '실패 무시 구문 또는 Gradle 태스크 제외')
    for node in root.walk():
        if node.key == 'uses' and not re.fullmatch(
            r'[\w.-]+/[\w.-]+(?:/[\w./-]+)?@[0-9a-fA-F]{40}', scalar(node.value)
        ):
            fail('W4', file, f'40자 SHA로 고정되지 않은 uses: {node.value}')
        if node.key == 'runs-on' and 'latest' in '\n'.join(n.value for n in node.walk()).lower():
            fail('W4', file, 'runs-on에 latest 포함')
    permissions = root.child('permissions')
    contents = permissions.child('contents') if permissions else None
    if contents is None or scalar(contents.value) != 'read':
        fail('W5', file, '워크플로 permissions.contents: read 필요(block mapping)')
    jobs = root.child('jobs')
    if jobs is None or not jobs.children:
        fail('W5', file, '검사할 job이 없음(block mapping 필요)')
    else:
        for job in jobs.children:
            timeout = job.child('timeout-minutes')
            if timeout is None or not scalar(timeout.value):
                fail('W5', file, f'job {job.key}에 timeout-minutes 필요')
    if file.name != 'verify.yml':
        continue
    triggers = root.child('on')
    for event in ('pull_request', 'push'):
        trigger = triggers.child(event) if triggers else None
        branches = trigger.child('branches') if trigger else None
        branch_values = re.findall(r'[\w/-]+', '\n'.join(n.value for n in branches.walk())) if branches else []
        if not {'develop', 'main'}.issubset(branch_values):
            fail('W6', file, f'{event} 트리거의 develop/main branches 필요')
    if triggers and triggers.child('schedule'):
        fail('W6', file, 'schedule 트리거 금지')
    steps = [step for node in root.walk() if node.key == 'steps' for step in node.children]
    if not any(node.key == 'java-version' and scalar(node.value) == '25' for node in root.walk()):
        fail('W6', file, 'java-version: 25 필요')
    if not any(node.key == 'uses' and scalar(node.value).startswith('gradle/actions/setup-gradle@') for node in root.walk()):
        fail('W6', file, 'gradle/actions/setup-gradle 단계 필요')
    if not any(node.key == 'run' and re.search(r'(?m)^\s*\./gradlew --no-daemon verifyContract\s*$', scalar(node.value).lstrip('|>+-\n ')) for node in root.walk()):
        fail('W6', file, './gradlew --no-daemon verifyContract 실행 필요')
    upload_found = False
    for step in steps:
        uses, condition, config = step.child('uses'), step.child('if'), step.child('with')
        path = config.child('path') if config else None
        if uses and scalar(uses.value).startswith('actions/upload-artifact@'):
            paths = scalar(path.value).lstrip('|>+-\n ') if path else ''
            if condition and scalar(condition.value) in ('always()', '${{ always() }}') and all(
                re.search(r'(?m)^\s*' + re.escape(required) + r'(?:/\*\*)?\s*$', paths)
                for required in ('backend/build/test-results', 'backend/build/reports/tests')
            ):
                upload_found = True
    if not upload_found:
        fail('W6', file, '동일 upload-artifact 단계에 if: always()와 두 보고서 경로 필요')

if not (directory / 'verify.yml').is_file():
    errors['W6'].append('verify.yml 파일이 없습니다')
for key, reasons in errors.items():
    if reasons:
        print(f'FAIL {key}: ' + '; '.join(reasons))
sys.exit(1 if any(errors.values()) else 0)
PY
then
    :
else
    failed=1
fi

if [[ $failed -ne 0 ]]; then
    exit 1
fi
printf 'PASS 6 checks\n'
