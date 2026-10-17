"""10단계 모스 부호 자바스크립트를 index.html 에 넣는다."""
import pathlib

MORSE_JS = r'''/* ==========================================================================
   📡 10단계 · 모스 부호로 비밀 편지
   -------------------------------------------------------------------------
   짧게 누르면 점(·), 길게 누르면 선(−). 두 가지로 모든 글자를 만든다.

   역할을 나눈 이유가 중요하다.
     아두이노 — 누르는 동안 소리와 빛으로 즉시 반응하고, "몇 ms 눌렸는지"만 보낸다
     웹      — 그 시간을 보고 점·선을 판정하고 글자로 조립한다

   점·선을 가르는 기준을 웹에서 정하는 까닭은, 아이마다 손 빠르기가 달라서
   기준을 그 아이에게 맞춰 조정해야 하기 때문이다 (아래 adaptThreshold).
   ========================================================================== */

/** 국제 모스 부호표. 해독은 AI 가 아니라 이 표로 정확하게 한다. */
const MORSE_TABLE = {
  '.-': 'A', '-...': 'B', '-.-.': 'C', '-..': 'D', '.': 'E',
  '..-.': 'F', '--.': 'G', '....': 'H', '..': 'I', '.---': 'J',
  '-.-': 'K', '.-..': 'L', '--': 'M', '-.': 'N', '---': 'O',
  '.--.': 'P', '--.-': 'Q', '.-.': 'R', '...': 'S', '-': 'T',
  '..-': 'U', '...-': 'V', '.--': 'W', '-..-': 'X', '-.--': 'Y',
  '--..': 'Z',
  '-----': '0', '.----': '1', '..---': '2', '...--': '3', '....-': '4',
  '.....': '5', '-....': '6', '--...': '7', '---..': '8', '----.': '9'
};

/** 글자 → 부호 (받아쓰기 문제를 만들 때 쓴다) */
const MORSE_REVERSE = Object.fromEntries(
  Object.entries(MORSE_TABLE).map(([code, letter]) => [letter, code])
);

/** 받아쓰기 문제로 낼 낱말들. 짧고 아이들이 아는 것만 골랐다. */
const MORSE_WORDS = ['SOS', 'HI', 'OK', 'CAT', 'DOG', 'SUN', 'AI', 'LOVE', 'STAR', 'MOON'];

let morseCurrent = '';        // 지금 조립 중인 한 글자 (예: ".-")
let morseText = '';           // 지금까지 받아쓴 편지
let morseThreshold = 250;     // 이보다 짧으면 점, 길면 선 (ms)
let morseHistory = [];        // 지금까지 두드린 길이들 — 기준을 조정하는 재료
let morseLetterTimer = null;  // 잠깐 쉬면 글자를 끊어 주는 타이머
let morseQuizWord = '';       // 받아쓰기 문제의 정답

/** 두드린 길이를 받아 점·선으로 판정하고 글자를 조립한다. */
function addMorseTap(heldMs) {
  const symbol = heldMs < morseThreshold ? '.' : '-';

  morseCurrent += symbol;
  if (morseCurrent.length > 6) morseCurrent = morseCurrent.slice(-6);   // 부호는 최대 5~6칸

  adaptThreshold(heldMs);
  renderMorse();

  // 1.2초 동안 안 두드리면 글자가 끝난 것으로 본다
  clearTimeout(morseLetterTimer);
  morseLetterTimer = setTimeout(morseLetterBreak, 1200);
}

/**
 * 아이의 손 빠르기에 기준을 맞춘다.
 *
 * 두드린 길이들을 모아 짧은 무리와 긴 무리로 나누고, 그 가운데를 기준으로 삼는다.
 * 손이 느린 아이도 빠른 아이도 자기 속도로 쓸 수 있게 되고,
 * "데이터가 쌓이면 기계가 나에게 맞춰 준다"는 것을 눈으로 보게 된다.
 */
function adaptThreshold(heldMs) {
  morseHistory.push(heldMs);
  if (morseHistory.length > 30) morseHistory.shift();
  if (morseHistory.length < 6) return;   // 자료가 너무 적으면 섣불리 바꾸지 않는다

  const sorted = [...morseHistory].sort((a, b) => a - b);
  const shortGroup = sorted.slice(0, Math.floor(sorted.length / 2));
  const longGroup = sorted.slice(Math.ceil(sorted.length / 2));

  const avg = arr => arr.reduce((a, b) => a + b, 0) / arr.length;
  const shortAvg = avg(shortGroup);
  const longAvg = avg(longGroup);

  // 두 무리가 뚜렷이 갈릴 때만 기준을 옮긴다 (전부 비슷하게 두드렸으면 그대로 둔다)
  if (longAvg - shortAvg < 80) return;

  morseThreshold = Math.round((shortAvg + longAvg) / 2);
  morseThreshold = Math.max(80, Math.min(morseThreshold, 800));
  document.getElementById('morse-threshold').innerText = morseThreshold;
}

/** 조립 중이던 부호를 글자로 바꿔 편지에 붙인다. */
function morseLetterBreak() {
  clearTimeout(morseLetterTimer);
  if (morseCurrent === '') return;

  const letter = MORSE_TABLE[morseCurrent];
  morseText += letter || '?';     // 표에 없는 부호는 물음표로 남겨 둔다
  morseCurrent = '';

  renderMorse();
  if (morseText.length >= 2) tryComplete(10);
}

/** 마지막 글자 하나를 지운다. */
function morseBackspace() {
  if (morseCurrent !== '') morseCurrent = morseCurrent.slice(0, -1);
  else morseText = morseText.slice(0, -1);
  renderMorse();
}

function morseClear() {
  morseCurrent = '';
  morseText = '';
  document.getElementById('morse-ai').classList.add('hidden');
  renderMorse();
}

function renderMorse() {
  document.getElementById('morse-current').innerText = morseCurrent || '―';
  document.getElementById('morse-text').innerText = morseText || '―';
}

/** 두드릴 때 화면도 함께 반응하게 한다 (아두이노 없이 마우스로 할 때 특히 필요). */
function setMorsePad(down) {
  const pad = document.getElementById('morse-pad');
  const text = document.getElementById('morse-pad-text');

  pad.className = down
      ? 'h-28 rounded-2xl bg-cyan-400 border-2 border-cyan-600 flex flex-col items-center justify-center select-none cursor-pointer transition-colors duration-75'
      : 'h-28 rounded-2xl bg-white border-2 border-cyan-300 flex flex-col items-center justify-center select-none cursor-pointer transition-colors duration-75';
  text.className = down ? 'text-base font-black text-white' : 'text-base font-black text-cyan-700';
  text.innerText = down ? '두드리는 중...' : '터치 모듈을 두드리세요';
}

/* ── 화면을 눌러서도 두드릴 수 있게 한다 (아두이노가 없는 기기용) ── */
let padDownAt = 0;

function morsePadDown() {
  padDownAt = performance.now();
  setMorsePad(true);
}

function morsePadUp() {
  if (padDownAt === 0) return;

  const held = Math.round(performance.now() - padDownAt);
  padDownAt = 0;
  setMorsePad(false);

  if (held >= 25) addMorseTap(held);
}

/* ── 아두이노가 보낸 두드림을 가져온다 ── */

/**
 * 0.2초마다 서버에 쌓인 두드림을 가져온다.
 * 서버는 한 번 준 것을 비우므로 같은 두드림을 두 번 세지 않는다.
 */
async function fetchMorseTaps() {
  try {
    const res = await fetch('/api/arduino/taps');
    if (!res.ok) return;

    const data = await res.json();
    (data.taps || []).forEach(addMorseTap);
  } catch (e) {
    // 서버가 꺼져 있을 수 있으므로 조용히 넘어간다
  }
}
setInterval(fetchMorseTaps, 200);

/* ── AI 에게 낱말을 물어보기 ── */

/**
 * 받아쓴 글자가 이상할 때 AI 가 원래 낱말을 짐작해 준다.
 * 해독은 이미 부호표로 정확히 했으므로, AI 는 "실수를 감안한 추측" 만 맡는다.
 */
async function askMorseGuess() {
  const box = document.getElementById('morse-ai');
  const text = document.getElementById('morse-ai-text');

  if (morseText.length < 2) {
    box.classList.remove('hidden');
    text.innerText = '먼저 두 글자 이상 두드려 볼까요?';
    return;
  }

  box.classList.remove('hidden');
  text.innerText = '🦉 AI 선생님이 생각하는 중...';

  try {
    const res = await fetch('/api/ai/morse-guess', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ word: morseText })
    });
    const data = await res.json();

    text.innerText = data.ok
        ? `혹시 "${data.guess}" 를 쓰려고 했나요? ${data.reason}`
        : '지금은 짐작하기 어려워요. 조금 더 두드려 볼까요?';
  } catch (e) {
    text.innerText = '지금은 AI 선생님을 부를 수 없어요.';
  }
}

/* ── 거꾸로! 듣고 받아쓰기 ── */

/** 낱말을 모스 부호 문자열로 바꾼다. 글자 사이는 빈칸으로 띄운다. */
function wordToMorse(word) {
  return word.split('').map(ch => MORSE_REVERSE[ch] || '').filter(Boolean).join(' ');
}

async function startMorseQuiz() {
  morseQuizWord = MORSE_WORDS[Math.floor(Math.random() * MORSE_WORDS.length)];
  document.getElementById('morse-quiz-result').innerText = '';
  document.getElementById('morse-answer').value = '';

  await playMorseOnArduino(wordToMorse(morseQuizWord));
}

async function replayMorseQuiz() {
  if (!morseQuizWord) {
    document.getElementById('morse-quiz-result').innerText = '먼저 [문제 듣기]를 눌러 주세요';
    return;
  }
  await playMorseOnArduino(wordToMorse(morseQuizWord));
}

/** 아두이노가 소리로 들려주게 한다. 아두이노가 없으면 화면에 부호를 보여 준다. */
async function playMorseOnArduino(code) {
  const result = document.getElementById('morse-quiz-result');

  try {
    const res = await fetch('/api/arduino/morse/play', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code })
    });
    const data = await res.json();

    // 아두이노가 없으면 소리를 낼 수 없으니 눈으로라도 볼 수 있게 한다
    result.innerText = data.sent ? '🔊 잘 들어 보세요!' : `(소리 없음) ${code}`;
  } catch (e) {
    result.innerText = `(소리 없음) ${code}`;
  }
}

function checkMorseQuiz() {
  const result = document.getElementById('morse-quiz-result');

  if (!morseQuizWord) {
    result.innerText = '먼저 [문제 듣기]를 눌러 주세요';
    return;
  }

  const answer = document.getElementById('morse-answer').value.trim().toUpperCase();

  if (answer === morseQuizWord) {
    result.innerText = `🎉 정답! ${morseQuizWord}`;
    tryComplete(10);
  } else {
    result.innerText = `❌ 아니에요. 다시 들어 볼까요?`;
  }
}

/** 모스 부호표를 화면에 그린다. */
function buildMorseTable() {
  const box = document.getElementById('morse-table');
  if (!box) return;

  box.innerHTML = '';
  Object.entries(MORSE_TABLE).forEach(([code, letter]) => {
    const cell = document.createElement('div');
    cell.className = 'bg-white border border-slate-200 rounded-lg px-2 py-1 text-center';
    cell.innerHTML = `<b class="text-slate-800">${letter}</b> <span class="text-cyan-600">${code}</span>`;
    box.appendChild(cell);
  });
}

'''

path = pathlib.Path('gonggongplan/src/main/resources/static/index.html')
text = path.read_text()

anchor = "/* ==========================================================================\n   마무리 설문"
assert text.count(anchor) == 1
text = text.replace(anchor, MORSE_JS + anchor)

# 두드림 패드에 마우스·터치 손잡이 달기
pad_a = '<div id="morse-pad" class="h-28 rounded-2xl bg-white border-2 border-cyan-300'
pad_b = ('<div id="morse-pad" onmousedown="morsePadDown()" onmouseup="morsePadUp()" onmouseleave="morsePadUp()" '
         'ontouchstart="event.preventDefault(); morsePadDown()" ontouchend="event.preventDefault(); morsePadUp()" '
         'class="h-28 rounded-2xl bg-white border-2 border-cyan-300')
assert text.count(pad_a) == 1
text = text.replace(pad_a, pad_b)

path.write_text(text)
print("모스 자바스크립트 추가 완료")
