// 10단계 모스 부호 로직을 브라우저 없이 검증한다.
// index.html 의 코드를 그대로 옮겨 놓고 돌려 본다.

var MORSE_TABLE = {
  '.-': 'A', '-...': 'B', '-.-.': 'C', '-..': 'D', '.': 'E',
  '..-.': 'F', '--.': 'G', '....': 'H', '..': 'I', '.---': 'J',
  '-.-': 'K', '.-..': 'L', '--': 'M', '-.': 'N', '---': 'O',
  '.--.': 'P', '--.-': 'Q', '.-.': 'R', '...': 'S', '-': 'T',
  '..-': 'U', '...-': 'V', '.--': 'W', '-..-': 'X', '-.--': 'Y',
  '--..': 'Z',
  '-----': '0', '.----': '1', '..---': '2', '...--': '3', '....-': '4',
  '.....': '5', '-....': '6', '--...': '7', '---..': '8', '----.': '9'
};

var MORSE_REVERSE = {};
for (var code in MORSE_TABLE) MORSE_REVERSE[MORSE_TABLE[code]] = code;

var MORSE_WORDS = ['SOS', 'HI', 'OK', 'CAT', 'DOG', 'SUN', 'AI', 'LOVE', 'STAR', 'MOON'];

function wordToMorse(word) {
  return word.split('').map(function (ch) { return MORSE_REVERSE[ch] || ''; })
             .filter(Boolean).join(' ');
}

// ── 검사 1 : 부호표에 겹치는 것이 없는지
var codes = Object.keys(MORSE_TABLE);
var letters = codes.map(function (c) { return MORSE_TABLE[c]; });
print((codes.length === new Set(codes).size ? '통과' : '실패') +
      ' : 부호 ' + codes.length + '개, 중복 없음');
print((letters.length === new Set(letters).size ? '통과' : '실패') +
      ' : 글자 ' + letters.length + '개, 중복 없음');

// ── 검사 2 : 낱말 → 부호 → 낱말 왕복이 맞는지
var bad = 0;
MORSE_WORDS.forEach(function (w) {
  var code = wordToMorse(w);
  var back = code.split(' ').map(function (c) { return MORSE_TABLE[c] || '?'; }).join('');
  if (back !== w) { print('  불일치: ' + w + ' -> ' + code + ' -> ' + back); bad++; }
});
print((bad === 0 ? '통과' : '실패') + ' : 받아쓰기 낱말 ' + MORSE_WORDS.length + '개 왕복 변환');

// ── 검사 3 : 두드린 시간으로 점·선을 가려내는지
function decideSymbol(heldMs, threshold) { return heldMs < threshold ? '.' : '-'; }

// SOS = ... --- ...  (짧게3 길게3 짧게3)
var taps = [100, 110, 95, 500, 520, 480, 105, 100, 115];
var built = taps.map(function (t) { return decideSymbol(t, 250); }).join('');
print((built === '...---...' ? '통과' : '실패') + ' : SOS 두드림 판정 → ' + built);

// ── 검사 4 : 기준 자동 조정이 손 느린 아이에게 맞춰지는지
function adapt(history) {
  if (history.length < 6) return null;
  var sorted = history.slice().sort(function (a, b) { return a - b; });
  var shortG = sorted.slice(0, Math.floor(sorted.length / 2));
  var longG = sorted.slice(Math.ceil(sorted.length / 2));
  var avg = function (a) { return a.reduce(function (x, y) { return x + y; }, 0) / a.length; };
  var s = avg(shortG), l = avg(longG);
  if (l - s < 80) return null;
  return Math.max(80, Math.min(Math.round((s + l) / 2), 800));
}

// 손이 느린 아이: 점도 400ms, 선은 1000ms
var slow = [400, 420, 380, 1000, 980, 1020, 410, 990];
var th = adapt(slow);
var ok = th !== null && th > 400 && th < 1000;
print((ok ? '통과' : '실패') + ' : 손 느린 아이 기준 자동 조정 → ' + th + 'ms');

// 그 기준으로 다시 판정해 보면 제대로 갈리는지
var slowTaps = [400, 1000, 400];
var slowBuilt = slowTaps.map(function (t) { return decideSymbol(t, th); }).join('');
print((slowBuilt === '.-.' ? '통과' : '실패') + ' : 조정된 기준으로 판정 → ' + slowBuilt + ' (R)');

// 전부 비슷하게 두드린 경우엔 기준을 함부로 바꾸지 않아야 한다
var flat = [200, 210, 205, 195, 215, 200];
print((adapt(flat) === null ? '통과' : '실패') + ' : 구분이 안 되면 기준 유지');
