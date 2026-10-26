#!/usr/bin/env bash
# C1 W1~W7 검사. Bash, Docker, Python 3 표준 라이브러리만 사용한다.
# W1이 YAML/Actions 문법을 검사하고 W2~W7은 보수적인 정적 검사를 한다.
# 따옴표 밖 # 주석은 제거하며 따옴표 안 #은 값으로 보존한다. run 블록의
# 셸 주석도 제거하지만 heredoc/여러 줄 셸 문자열/동적 표현식은 해석하지 않는다.
# 구조 검사는 들여쓰기 기반 block mapping/sequence용이다. flow mapping,
# anchor/alias/merge는 해석 누락으로 통과하지 않도록 W4~W7에서 거부한다.
# 문자열 검사는 실행 의미를 증명하지 않는다(예: echo 안 금지 문자열도 탐지).
# 실행 검사는 verify의 단일 명령, deep의 단일 명령 또는 현재 INPUT_QT_SEED
# 검증 블록+마지막 명령만 허용한다. 임의 셸·heredoc·folded scalar는 거부한다.
# setup과 실행은 같은 job의 명시적 steps여야 하며 조건부 단계는 허용하지 않는다.
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
    for id in W2 W3 W4 W5 W6 W7; do
        printf 'FAIL %s: 정적 검사에 필요한 python3가 없습니다\n' "$id"
    done
    exit 1
fi

if python3 - "$WORKFLOW_DIR" <<'PY'
import pathlib
import re
import sys

directory = pathlib.Path(sys.argv[1])
errors = {key: [] for key in ('W2', 'W3', 'W4', 'W5', 'W6', 'W7')}


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


def check_permissions(node, file, scope):
    if node is None:
        return
    if node.value.strip() or any(scalar(child.value) not in ('read', 'none') or child.children for child in node.children):
        fail('W5', file, f'{scope} permissions는 block mapping의 read/none만 허용')


def run_lines(node):
    value = node.value.strip()
    first, _, rest = value.partition('\n')
    if re.fullmatch(r'\|[+-]?', first):
        value = rest
    elif first.startswith(('>', '|')):
        return []
    else:
        value = scalar(value)
    return [line.strip() for line in value.splitlines() if line.strip()]


def check_execution(jobs, file, key, command, deep=False):
    # 임의 prefix를 허용하면 exit/echo/미종료 heredoc으로 명령을 무력화할 수 있다.
    # 현재 워크플로의 seed 검증만 지원한다. 변경 시 이 계약도 명시적으로 갱신한다.
    seed_prefix = [
        'if [ -n "$INPUT_QT_SEED" ]; then',
        'if ! [[ "$INPUT_QT_SEED" =~ ^[0-9]+$ ]]; then',
        'echo "qt_seed는 정수만 허용합니다" >&2',
        'exit 1', 'fi', 'export QT_SEED="$INPUT_QT_SEED"', 'fi',
    ]
    found = False
    for job in jobs.children if jobs else []:
        steps = job.child('steps')
        java_ready = gradle_ready = False
        for step in steps.children if steps else []:
            run = step.child('run')
            if run and './gradlew' in run.value:
                found = True
                reasons = []
                if step.child('if') is not None or job.child('if') is not None:
                    reasons.append('실행 단계/job의 if 조건 금지')
                workdir = step.child('working-directory')
                if workdir is None or scalar(workdir.value) != 'backend':
                    reasons.append('working-directory: backend 필요')
                if not (java_ready and gradle_ready):
                    reasons.append('같은 job의 setup-java(25)·setup-gradle 뒤에서 실행 필요')
                actual = run_lines(run)
                if actual != [command] and not (deep and actual == seed_prefix + [command]):
                    reasons.append('지원하지 않는 run 형식: 지정 명령 단독 또는 허용된 seed 블록 필요')
                if deep and actual == seed_prefix + [command]:
                    env = step.child('env')
                    seed = env.child('INPUT_QT_SEED') if env else None
                    if seed is None or scalar(seed.value) != '${{ inputs.qt_seed }}':
                        reasons.append('INPUT_QT_SEED: ${{ inputs.qt_seed }} 필요')
                if reasons:
                    fail(key, file, f'job {job.key}: ' + ', '.join(reasons))
            uses = step.child('uses')
            if uses and scalar(uses.value).startswith('actions/setup-java@'):
                config = step.child('with')
                version = config.child('java-version') if config else None
                java_ready = step.child('if') is None and version is not None and scalar(version.value) == '25'
            if uses and scalar(uses.value).startswith('gradle/actions/setup-gradle@'):
                gradle_ready = step.child('if') is None
    if not found:
        fail(key, file, f'{command} 실행 단계 필요')


def branch_names(node):
    if node is None:
        return []
    value = node.value.strip()
    if value.startswith('[') and value.endswith(']') and not node.children:
        return [scalar(item) for item in value[1:-1].split(',')]
    if not value and node.children and all(child.key == '-' and not child.children for child in node.children):
        return [scalar(child.value) for child in node.children]
    return []


for file in sorted((*directory.glob('*.yml'), *directory.glob('*.yaml'))):
    try:
        lines = [uncomment(line) for line in file.read_text(encoding='utf-8').splitlines()]
    except (OSError, UnicodeError) as error:
        for key in errors:
            fail(key, file, f'읽기 실패: {error}')
        continue
    text = '\n'.join(lines)
    root = parse(lines)
    if any(re.fullmatch(r'---\s*', line) for line in lines[1:]):
        fail('W5', file, '첫 줄 이후 YAML 문서 구분자(---) 금지: 단일 문서만 지원')
    # 축약 매핑/별칭을 조용히 무시하면 uses/timeout 등의 누락이 PASS가 된다.
    # 표현식 ${{ ... }}와 branches/runs-on의 flow sequence는 이 제한과 무관하다.
    unsupported = any(re.search(
        r'''^\s*(?:-\s+)?(?:\{|(?:[\w-]+|"[\w-]+"|'[\w-]+'):\s*(?:\{(?!\{)|[&*])|<<:)''', line
    ) for line in lines)
    if unsupported:
        for key in ('W4', 'W5', 'W6'):
            fail(key, file, '구조 검사는 flow mapping/anchor/alias/merge를 지원하지 않음')
        if file.name == 'deep-tests.yml':
            fail('W7', file, '구조 검사는 flow mapping/anchor/alias/merge를 지원하지 않음')
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
    check_permissions(permissions, file, '워크플로')
    jobs = root.child('jobs')
    if jobs is None or not jobs.children:
        fail('W5', file, '검사할 job이 없음(block mapping 필요)')
    else:
        for job in jobs.children:
            check_permissions(job.child('permissions'), file, f'job {job.key}')
            timeout = job.child('timeout-minutes')
            if timeout is None or not scalar(timeout.value):
                fail('W5', file, f'job {job.key}에 timeout-minutes 필요')
    if file.name == 'deep-tests.yml':
        triggers = root.child('on')
        if triggers is None or triggers.value.strip() or [node.key for node in triggers.children] != ['workflow_dispatch']:
            fail('W7', file, '트리거는 workflow_dispatch만 허용')
        dispatch = triggers.child('workflow_dispatch') if triggers else None
        inputs = dispatch.child('inputs') if dispatch else None
        if inputs is None or inputs.child('qt_seed') is None:
            fail('W7', file, 'workflow_dispatch.inputs.qt_seed 필요')
        steps = [step for node in root.walk() if node.key == 'steps' for step in node.children]
        runs = [scalar(node.value).lstrip('|>+-\n ') for step in steps for node in step.children if node.key == 'run']
        check_execution(jobs, file, 'W7', './gradlew --no-daemon propertyTest mutationSmoke', deep=True)
        if not any(re.search(r'(?m)^\s*(?:bash\s+)?(?:\./)?\.github/scripts/property-summary\.sh(?:\s|$)', run) for run in runs):
            fail('W7', file, '.github/scripts/property-summary.sh 호출 필요')
        # D2는 Property/PIT artifact를 별도 단계로 올린다. always()인 업로드
        # 단계들의 path만 합산하며 / 또는 /** 접미사를 같은 디렉터리로 본다.
        uploaded = set()
        for step in steps:
            uses, condition, config = step.child('uses'), step.child('if'), step.child('with')
            path = config.child('path') if config else None
            if uses and scalar(uses.value).startswith('actions/upload-artifact@') and condition and scalar(condition.value) in ('always()', '${{ always() }}') and path:
                for value in scalar(path.value).lstrip('|>+-\n ').splitlines():
                    uploaded.add(re.sub(r'/\*\*$', '', value.strip()).rstrip('/'))
        for required in ('backend/build/reports/pitest', 'backend/build/test-results/propertyTest'):
            if required not in uploaded:
                fail('W7', file, f'if: always() upload-artifact에 {required} 경로 필요')
        concurrency = root.child('concurrency')
        cancel = concurrency.child('cancel-in-progress') if concurrency else None
        if cancel is None or scalar(cancel.value) != 'false':
            fail('W7', file, 'concurrency.cancel-in-progress: false 필요')
    if file.name != 'verify.yml':
        continue
    triggers = root.child('on')
    for event in ('pull_request', 'push'):
        trigger = triggers.child(event) if triggers else None
        branches = trigger.child('branches') if trigger else None
        branch_values = branch_names(branches)
        if set(branch_values) != {'develop', 'main'}:
            fail('W6', file, f'{event} branches는 정확히 develop/main만 허용(제외·와일드카드 금지)')
        if trigger and trigger.child('branches-ignore') is not None:
            fail('W6', file, f'{event} branches-ignore 금지')
    if triggers and triggers.child('schedule'):
        fail('W6', file, 'schedule 트리거 금지')
    steps = [step for node in root.walk() if node.key == 'steps' for step in node.children]
    if not any(node.key == 'java-version' and scalar(node.value) == '25' for node in root.walk()):
        fail('W6', file, 'java-version: 25 필요')
    if not any(node.key == 'uses' and scalar(node.value).startswith('gradle/actions/setup-gradle@') for node in root.walk()):
        fail('W6', file, 'gradle/actions/setup-gradle 단계 필요')
    check_execution(jobs, file, 'W6', './gradlew --no-daemon verifyContract')
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
if not (directory / 'deep-tests.yml').is_file():
    errors['W7'].append('deep-tests.yml 파일이 없습니다')
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
printf 'PASS 7 checks\n'
