"""index.html 을 두 차시 페이지로 나눈다.

  1교시 index.html   : 1~5단계 (아두이노와 AI)
  2교시 lesson2.html : 6~10단계 (컴퓨터의 생각법)

자바스크립트는 두 파일이 똑같이 갖는다. 그래서 먼저 원본의 JS 를
"내 차시에 있는 단계만 다룬다"는 방식으로 고친 뒤 복제한다.
없는 화면 요소를 건드려도 죽지 않도록 안전장치도 함께 넣는다.
"""
import pathlib
import re

SRC = pathlib.Path('gonggongplan/src/main/resources/static/index.html')
OUT2 = pathlib.Path('gonggongplan/src/main/resources/static/lesson2.html')

text = SRC.read_text()

# ─────────────────────────────────────────────────────────── 1) 공통 JS 수정

# (1) 차시별 단계 범위를 body 의 data-lesson 으로 정한다
old = """const TOTAL_STEPS = 10;
const stepDone = { 1: false, 2: false, 3: false, 4: false, 5: false, 6: false, 7: false, 8: false, 9: false, 10: false };"""
new = """/* 이 페이지가 몇 교시인지 body 에 적어 둔 값으로 판단한다.
   1교시는 1~5단계(아두이노와 AI), 2교시는 6~10단계(컴퓨터의 생각법)를 담는다.
   자바스크립트는 두 페이지가 똑같이 쓰고, 여기서 정한 범위만 다룬다. */
const LESSON = document.body.dataset.lesson === '2' ? 2 : 1;
const STEP_FIRST = LESSON === 1 ? 1 : 6;
const STEP_LAST = LESSON === 1 ? 5 : 10;
const TOTAL_STEPS = STEP_LAST - STEP_FIRST + 1;

/** 이 페이지가 다루는 단계 번호들 (예: 1교시면 [1,2,3,4,5]) */
const STEP_NUMBERS = Array.from({ length: TOTAL_STEPS }, (_, i) => STEP_FIRST + i);

const stepDone = {};
STEP_NUMBERS.forEach(n => { stepDone[n] = false; });

/** 화면에 그 요소가 있는지. 다른 차시의 요소를 건드리다 죽지 않게 막아 준다. */
function hasEl(id) {
  return document.getElementById(id) !== null;
}"""
assert text.count(old) == 1, '단계 선언부를 찾지 못했습니다'
text = text.replace(old, new)

# (2) 모든 단계 열기 — 내 차시 범위만
old = """  for (let n = 1; n <= TOTAL_STEPS; n++) {
    document.getElementById(`step-${n}`).classList.remove('step-locked');
    if (!stepDone[n]) {
      document.getElementById(`chip-${n}`).className =
          'rounded-xl py-2 text-[11px] font-bold bg-indigo-600 text-white';
    }
  }"""
new = """  STEP_NUMBERS.forEach(n => {
    if (!hasEl(`step-${n}`)) return;

    document.getElementById(`step-${n}`).classList.remove('step-locked');
    if (!stepDone[n] && hasEl(`chip-${n}`)) {
      document.getElementById(`chip-${n}`).className =
          'rounded-xl py-2 text-[11px] font-bold bg-indigo-600 text-white';
    }
  });"""
assert text.count(old) == 1, '모든 단계 열기 부분을 찾지 못했습니다'
text = text.replace(old, new)

# (3) 첫 단계는 잠기지 않아야 한다 → tryComplete 의 2단계 예외를 범위에 맞게
old = """function tryComplete(n) {
  if (n === 2 && !freeMode && !stepDone[1]) return;
  completeStep(n);
}"""
new = """function tryComplete(n) {
  // 이 차시에 없는 단계는 무시한다 (다른 차시 페이지의 일이다)
  if (stepDone[n] === undefined) return;

  // 2단계는 센서 값이 저절로 들어와서, 1단계를 하기 전에 저절로 완료되면
  // 순서대로 배우는 흐름이 무너진다. 그래서 2단계만 앞 단계를 요구한다.
  if (n === 2 && !freeMode && !stepDone[1]) return;

  completeStep(n);
}"""
assert text.count(old) == 1, 'tryComplete 를 찾지 못했습니다'
text = text.replace(old, new)

# (4) completeStep / unlockStep / countDone 안전하게
old = """function completeStep(n) {
  if (stepDone[n]) return;
  stepDone[n] = true;

  document.getElementById(`done-${n}`).classList.remove('hidden');
  document.getElementById(`chip-${n}`).className =
      'rounded-xl py-2 text-[11px] font-bold bg-emerald-500 text-white';"""
new = """function completeStep(n) {
  if (stepDone[n] === undefined || stepDone[n]) return;
  stepDone[n] = true;

  if (hasEl(`done-${n}`)) document.getElementById(`done-${n}`).classList.remove('hidden');
  if (hasEl(`chip-${n}`)) {
    document.getElementById(`chip-${n}`).className =
        'rounded-xl py-2 text-[11px] font-bold bg-emerald-500 text-white';
  }"""
assert text.count(old) == 1, 'completeStep 을 찾지 못했습니다'
text = text.replace(old, new)

old = """function unlockStep(n) {
  if (n > TOTAL_STEPS) return;"""
new = """function unlockStep(n) {
  if (n > STEP_LAST || !hasEl(`step-${n}`)) return;"""
assert text.count(old) == 1, 'unlockStep 을 찾지 못했습니다'
text = text.replace(old, new)

# (5) 퀴즈는 내 차시 것만 만든다
old = """function buildAllQuizzes() {
  for (let n = 1; n <= 9; n++) buildQuiz(n);
  updateQuizCount();
}"""
if text.count(old) == 0:
    old = """function buildAllQuizzes() {
  for (let n = 1; n <= 10; n++) buildQuiz(n);
  updateQuizCount();
}"""
new = """function buildAllQuizzes() {
  STEP_NUMBERS.forEach(buildQuiz);
  updateQuizCount();
}"""
assert text.count(old) == 1, '퀴즈 생성부를 찾지 못했습니다'
text = text.replace(old, new)

# (6) 퀴즈 개수 표시도 내 차시 기준으로
old = """function updateQuizCount() {
  const el = document.getElementById('quiz-count');
  if (el) el.innerText = quizSolved.size;
}"""
new = """function updateQuizCount() {
  const el = document.getElementById('quiz-count');
  if (el) el.innerText = quizSolved.size;

  const total = document.getElementById('quiz-total');
  if (total) total.innerText = TOTAL_STEPS;
}"""
assert text.count(old) == 1, 'updateQuizCount 를 찾지 못했습니다'
text = text.replace(old, new)

# (7) 타이머·MQTT 가 다른 차시 요소를 건드리다 죽지 않게 앞에서 막는다
guards = [
    ("function applyDht(data) {", "  if (!hasEl('temp-val')) return;   // 이 차시에 2단계가 없으면 할 일이 없다\n"),
    ("function applyReaction(data) {", "    if (!hasEl('reaction-message')) return;   // 이 차시에 3단계가 없으면 할 일이 없다\n"),
    ("function updateButtonIndicator(pressed) {", "  if (!hasEl('button-indicator')) return;\n"),
    ("function addMorseTap(heldMs) {", "  if (!hasEl('morse-pad')) return;   // 이 차시에 10단계가 없으면 할 일이 없다\n"),
]
for sig, guard in guards:
    assert text.count(sig) == 1, f'{sig} 를 찾지 못했습니다'
    text = text.replace(sig, sig + "\n" + guard.rstrip('\n'), 1)

# ─────────────────────────────────────────────────────────── 2) 구역 잘라내기

lines = text.split('\n')

# <section id="step-N"> 부터 같은 들여쓰기의 </section> 까지를 찾는다
bounds = {}
i = 0
while i < len(lines):
    m = re.match(r'^(\s*)<section id="step-(\d+)"', lines[i])
    if m:
        indent, num = m.group(1), int(m.group(2))
        for j in range(i + 1, len(lines)):
            if lines[j] == f'{indent}</section>':
                bounds[num] = (i, j)
                i = j
                break
    i += 1

assert len(bounds) == 10, f'단계 구역을 10개 찾지 못했습니다 (찾은 것: {sorted(bounds)})'


def build(keep):
    """keep 에 없는 단계 구역과 칩을 지운 HTML 을 만든다."""
    drop = set()
    for num, (a, b) in bounds.items():
        if num not in keep:
            drop.update(range(a, b + 1))

    out = []
    for idx, ln in enumerate(lines):
        if idx in drop:
            continue
        # 진행률 카드의 단계 칩도 내 차시 것만 남긴다
        chip = re.match(r'^\s*<div id="chip-(\d+)"', ln)
        if chip and int(chip.group(1)) not in keep:
            continue
        out.append(ln)
    return '\n'.join(out)


page1 = build({1, 2, 3, 4, 5})
page2 = build({6, 7, 8, 9, 10})

# ─────────────────────────────────────────────────────────── 3) 차시별 머리말

NAV1 = '''        <a href="/lesson2.html" class="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-violet-50 border border-violet-200 text-violet-700 text-xs font-bold hover:bg-violet-100 transition">
            2교시 · 컴퓨터의 생각법 ➔
        </a>'''

NAV2 = '''        <a href="/index.html" class="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-indigo-50 border border-indigo-200 text-indigo-700 text-xs font-bold hover:bg-indigo-100 transition">
            ◀ 1교시 · 아두이노와 AI
        </a>'''


def brand(html, lesson, title, subtitle, nav, chip_cols):
    html = html.replace(
        '<body class="bg-slate-100 min-h-screen p-4 md:p-8 font-sans">',
        f'<body data-lesson="{lesson}" class="bg-slate-100 min-h-screen p-4 md:p-8 font-sans">')

    html = html.replace('<title>공공 코딩 교실 · 1~10단계</title>', f'<title>{title}</title>')
    html = html.replace('<title>공공 코딩 교실 · 1~9단계</title>', f'<title>{title}</title>')

    # 진행률 카드 제목
    html = re.sub(r'<h2 class="text-lg font-black text-slate-800">오늘의 코딩 여행 \d+단계</h2>',
                  f'<h2 class="text-lg font-black text-slate-800">{subtitle}</h2>', html)

    # 칩 줄의 칸 수
    html = re.sub(r'<div class="grid grid-cols-\d+ md:grid-cols-\d+ gap-2 text-center">',
                  f'<div class="grid grid-cols-{chip_cols} md:grid-cols-{chip_cols} gap-2 text-center">', html)

    # 진행률·퀴즈 숫자를 차시 단계 수로
    html = re.sub(r'(<span id="progress-count">0</span> / )\d+( 완료)', r'\g<1>5\g<2>', html)
    html = re.sub(r'(🧩 <span id="quiz-count">0</span> / )\d+', r'\g<1><span id="quiz-total">5</span>', html)

    # 다른 차시로 가는 링크를 헤더에 넣는다
    html = html.replace('        <div id="mqtt-badge"', nav + '\n\n        <div id="mqtt-badge"', 1)
    return html


page1 = brand(page1, 1, '공공 코딩 교실 · 1교시 아두이노와 AI',
              '1교시 · 아두이노와 AI (1~5단계)', NAV1, 5)
page2 = brand(page2, 2, '공공 코딩 교실 · 2교시 컴퓨터의 생각법',
              '2교시 · 컴퓨터의 생각법 (6~10단계)', NAV2, 5)

SRC.write_text(page1)
OUT2.write_text(page2)

print(f"1교시 index.html   : {len(page1.splitlines())}줄")
print(f"2교시 lesson2.html : {len(page2.splitlines())}줄")
