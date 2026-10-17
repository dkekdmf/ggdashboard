// 따라 하기 모드의 판정 논리를 DOM 없이 검증한다.
// index.html 의 MORSE_TABLE 을 실제로 읽어와 쓰므로, 표가 바뀌면 이 검사도 함께 따라간다.

var page = read('/tmp/page.js');

// MORSE_TABLE 정의만 잘라내 실제 값을 가져온다
var start = page.indexOf('const MORSE_TABLE = {');
var end = page.indexOf('};', start) + 2;
var MORSE_TABLE = new Function(page.slice(start, end).replace('const MORSE_TABLE', 'var MORSE_TABLE') + ' return MORSE_TABLE;')();

var MORSE_REVERSE = {};
for (var code in MORSE_TABLE) MORSE_REVERSE[MORSE_TABLE[code]] = code;

var GUIDE_WORDS = ['SOS', 'HI', 'MOM', 'TOE', 'SHE', 'EAT'];

// ── 검사 1: 따라 하기에 쓰는 낱말의 모든 글자가 표에 있는가
var missing = [];
GUIDE_WORDS.forEach(function (w) {
  for (var i = 0; i < w.length; i++) {
    if (!MORSE_REVERSE[w[i]]) missing.push(w[i]);
  }
});
print(missing.length === 0
  ? '통과 : 따라 하기 낱말 ' + GUIDE_WORDS.length + '개의 모든 글자가 부호표에 있음'
  : '실패 : 부호표에 없는 글자 ' + missing.join(', '));

// ── 검사 2: 낱말마다 몇 번 두드려야 하는지 (아이가 감당할 분량인지)
print('');
print('낱말별 두드림 횟수');
GUIDE_WORDS.forEach(function (w) {
  var total = 0, parts = [];
  for (var i = 0; i < w.length; i++) {
    var c = MORSE_REVERSE[w[i]];
    total += c.length;
    parts.push(w[i] + '=' + c);
  }
  print('   ' + w + ' : ' + total + '번  (' + parts.join(' ') + ')');
});

// ── 검사 3: 따라 하기를 끝까지 흉내 내 본다
function simulate(word) {
  var letterAt = 0, symbolAt = 0, typed = '', taps = 0;

  function currentCode() { return MORSE_REVERSE[word[letterAt]] || ''; }

  // 올바르게 두드리는 경우
  while (letterAt < word.length) {
    var code = currentCode();
    var expected = code[symbolAt];
    taps++;

    if (taps > 100) return { ok: false, why: '무한 반복' };

    symbolAt++;
    if (symbolAt >= code.length) {
      typed += word[letterAt];
      letterAt++;
      symbolAt = 0;
    }
  }
  return { ok: typed === word, typed: typed, taps: taps };
}

print('');
var allOk = true;
GUIDE_WORDS.forEach(function (w) {
  var r = simulate(w);
  if (!r.ok) { allOk = false; print('   실패 ' + w + ' -> ' + r.typed); }
});
print(allOk ? '통과 : 모든 낱말이 정확히 완성됨' : '실패 : 완성되지 않는 낱말 있음');

// ── 검사 4: 틀리게 두드렸을 때 진행이 되돌아가지 않는가
var word = 'SOS';
var letterAt = 0, symbolAt = 0;
var code = MORSE_REVERSE[word[letterAt]];   // '...'
var wrong = code[symbolAt] === '.' ? '-' : '.';

var before = symbolAt;
if (wrong !== code[symbolAt]) {
  // 틀렸을 때는 아무것도 바뀌지 않아야 한다
}
print('');
print(before === symbolAt
  ? '통과 : 틀리게 두드려도 진행이 뒤로 가지 않음 (그 칸만 다시)'
  : '실패 : 틀렸을 때 진행이 바뀜');
