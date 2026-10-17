"""10단계 모스 부호에 '따라 하기' 모드를 넣는다. 한 번만 실행하면 된다."""
import pathlib

p = pathlib.Path('gonggongplan/src/main/resources/static/index.html')
s = p.read_text()

anchor = """/** 두드린 길이를 받아 점·선으로 판정하고 글자를 조립한다. */
function addMorseTap(heldMs) {"""

new = """/* ==========================================================================
   🎯 따라 하기 모드
   표를 외우지 않아도, 타이밍을 스스로 판단하지 않아도 할 수 있게 돕는다.
   화면이 "지금 이걸 두드리세요" 하고 한 칸씩 알려 주고, 맞으면 다음으로 넘어간다.
   틀리면 그 칸만 다시 하면 되므로 처음부터 되돌아가지 않는다.
   ========================================================================== */

/** 따라 하기에서 낼 낱말. 개수만 세면 되는 쉬운 글자로만 만들었다. */
const GUIDE_WORDS = ['SOS', 'HI', 'MOM', 'TOE', 'SHE', 'EAT'];

let guideOn = false;
let guideWord = '';        // 목표 낱말 (예: "SOS")
let guideLetterAt = 0;     // 몇 번째 글자를 하고 있나
let guideSymbolAt = 0;     // 그 글자의 몇 번째 부호를 기다리나

/** 점·선을 보기 좋은 기호로 바꾼다. 정규식을 쓰지 않고 나눠 붙인다. */
function prettyCode(code) {
  return code.split('.').join('\\u00b7').split('-').join('\\u2212');
}

function startGuide() {
  guideOn = true;
  guideWord = GUIDE_WORDS[Math.floor(Math.random() * GUIDE_WORDS.length)];
  guideLetterAt = 0;
  guideSymbolAt = 0;

  // 따라 하기 중에는 자유 입력과 섞이지 않도록 편지를 비운다
  morseCurrent = '';
  morseText = '';
  clearTimeout(morseLetterTimer);

  document.getElementById('guide-box').classList.remove('hidden');
  renderGuide();
  renderMorse();
}

function stopGuide() {
  guideOn = false;
  document.getElementById('guide-box').classList.add('hidden');
}

/** 지금 두드려야 하는 글자의 부호 (예: "...") */
function guideCurrentCode() {
  return MORSE_CODE[guideWord[guideLetterAt]] || '';
}

function renderGuide() {
  if (!guideOn) return;

  const letter = guideWord[guideLetterAt];
  const code = guideCurrentCode();

  document.getElementById('guide-word').innerText = guideWord;
  document.getElementById('guide-letter').innerText = letter;
  document.getElementById('guide-letter-index').innerText = guideLetterAt + 1;
  document.getElementById('guide-letter-code').innerText = prettyCode(code);

  // 부호를 한 칸씩 그린다. 이미 맞힌 것은 초록, 지금 할 것은 크게 강조.
  const box = document.getElementById('guide-symbols');
  box.innerHTML = '';

  for (let i = 0; i < code.length; i++) {
    const span = document.createElement('span');
    span.innerText = prettyCode(code[i]);

    if (i < guideSymbolAt) {
      span.className = 'text-emerald-500';
    } else if (i === guideSymbolAt) {
      span.className = 'text-cyan-600 animate-pulse scale-125';
    } else {
      span.className = 'text-slate-300';
    }
    box.appendChild(span);
  }

  const next = code[guideSymbolAt];
  document.getElementById('guide-hint').innerText =
      next === '.' ? '짧게 톡! 하세요' : '길게 꾹— 누르세요';

  document.getElementById('guide-done').innerText =
      guideWord.slice(0, guideLetterAt).split('').join(' ') || '-';
}

/**
 * 따라 하기 중에 두드림을 받았을 때 처리한다.
 * @return true 면 따라 하기가 맡았으니 자유 입력 쪽은 건너뛴다
 */
function guideHandleTap(symbol) {
  if (!guideOn) return false;

  const code = guideCurrentCode();
  const expected = code[guideSymbolAt];

  if (symbol !== expected) {
    // 틀렸다 — 이 칸만 다시 하면 된다. 편지는 건드리지 않는다.
    document.getElementById('guide-hint').innerText =
        expected === '.' ? '앗, 더 짧게 톡! 해보세요' : '앗, 더 길게 꾹— 눌러보세요';
    return true;
  }

  guideSymbolAt++;

  // 글자를 다 두드렸으면 편지에 붙이고 다음 글자로 넘어간다
  if (guideSymbolAt >= code.length) {
    morseText += guideWord[guideLetterAt];
    guideLetterAt++;
    guideSymbolAt = 0;
    renderMorse();

    if (guideLetterAt >= guideWord.length) {
      finishGuide();
      return true;
    }
  }

  renderGuide();
  return true;
}

function finishGuide() {
  document.getElementById('guide-symbols').innerHTML = '';
  document.getElementById('guide-hint').innerText = '🎉 완성! ' + guideWord + ' 를 보냈어요!';
  document.getElementById('guide-done').innerText = guideWord.split('').join(' ');

  guideOn = false;
  tryComplete(10);

  // 잠깐 결과를 보여 준 뒤 다음 낱말로 넘어간다 (그만하기를 누르지 않았을 때만)
  setTimeout(function () {
    if (!document.getElementById('guide-box').classList.contains('hidden')) startGuide();
  }, 2500);
}

/** 두드린 길이를 받아 점·선으로 판정하고 글자를 조립한다. */
function addMorseTap(heldMs) {"""

assert s.count(anchor) == 1, s.count(anchor)
s = s.replace(anchor, new)

# ── addMorseTap 이 따라 하기를 먼저 거치게 한다
a = """  const symbol = heldMs < morseThreshold ? '.' : '-';

  morseCurrent += symbol;"""
b = """  const symbol = heldMs < morseThreshold ? '.' : '-';

  // 두드린 길이를 화면에 그대로 보여 준다.
  // 아이가 "내가 340ms 로 눌렀구나" 하고 스스로 손 감각을 맞출 수 있다.
  const lastEl = document.getElementById('morse-last');
  if (lastEl) lastEl.innerText = heldMs + 'ms ' + (symbol === '.' ? '\\u00b7' : '\\u2212');

  adaptThreshold(heldMs);

  // 따라 하기 모드면 그쪽이 맡는다
  if (guideHandleTap(symbol)) return;

  morseCurrent += symbol;"""
assert s.count(a) == 1, s.count(a)
s = s.replace(a, b)

# adaptThreshold 가 위로 올라갔으니 아래 중복 호출을 지운다
a = """  if (morseCurrent.length > 6) morseCurrent = morseCurrent.slice(-6);   // 부호는 최대 5~6칸

  adaptThreshold(heldMs);
  renderMorse();"""
b = """  if (morseCurrent.length > 6) morseCurrent = morseCurrent.slice(-6);   // 부호는 최대 5~6칸

  renderMorse();"""
assert s.count(a) == 1, s.count(a)
s = s.replace(a, b)

# ── 기준값을 터치 모듈에 맞게 올린다
a = "let morseThreshold = 250;     // 이보다 짧으면 점, 길면 선 (ms)"
b = """// 터치 모듈은 딸깍 버튼과 달라서 살짝 대도 0.3~0.5초가 쉽게 나온다.
// 250ms 로 두면 톡 쳐도 전부 선(-)으로 잡혀서, 기준을 올려 두었다.
let morseThreshold = 400;     // 이보다 짧으면 점, 길면 선 (ms)"""
assert s.count(a) == 1
s = s.replace(a, b)

# 글자 끊기는 시간도 넉넉하게
a = """  // 1.2초 동안 안 두드리면 글자가 끝난 것으로 본다
  clearTimeout(morseLetterTimer);
  morseLetterTimer = setTimeout(morseLetterBreak, 1200);"""
b = """  // 2.5초 동안 안 두드리면 글자가 끝난 것으로 본다.
  // 부호표를 보면서 천천히 두드리는 아이도 한 글자를 완성할 수 있게 넉넉히 잡았다.
  clearTimeout(morseLetterTimer);
  morseLetterTimer = setTimeout(morseLetterBreak, 2500);"""
assert s.count(a) == 1
s = s.replace(a, b)

# ── 두드린 길이 표시 자리 만들기
a = """                    <span class="text-[11px] font-bold text-cyan-500">
                        내 기준: 짧게 ~<span id="morse-threshold">250</span>ms
                    </span>"""
b = """                    <span class="text-[11px] font-bold text-cyan-500">
                        방금 <span id="morse-last" class="font-mono">-</span>
                        · 내 기준 ~<span id="morse-threshold">400</span>ms
                    </span>"""
assert s.count(a) == 1
s = s.replace(a, b)

p.write_text(s)
print("따라 하기 모드 + 기준 시간 조정 완료")
