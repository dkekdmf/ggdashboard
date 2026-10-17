// index.html 의 자바스크립트가 "페이지를 열자마자" 오류 없이 도는지 확인하는 검사기.
//
// 문법 검사만으로는 잡히지 않는 오류가 있다.
// 예전에 실제로 있었던 일: 위쪽에서 아래쪽 const 를 당겨 쓰다가 ReferenceError 가 나서
// 그 아래 모든 함수가 정의되지 않았고, 화면의 버튼이 전부 먹통이 되었다.
//
// 쓰는 법 (jsc 는 macOS 에 내장된 자바스크립트 엔진):
//   python3 tools/extract_js.py            <- index.html 에서 <script> 만 /tmp/page.js 로 뽑고
//   jsc tools/page_load_check.js           <- 이 파일을 실행
//
// 브라우저가 아니므로 document 같은 것들을 아주 단순하게 흉내 낸다.

function El(id) {
  this.id = id;
  this.style = {};
  this.classList = { add: function () {}, remove: function () {}, toggle: function () {}, contains: function () { return false; } };
  this.children = [];
  this.innerHTML = '';
  this.innerText = '';
  this.checked = false;
  this.className = '';

  // 화면 요소마다 기본값이 달라야 코드가 제대로 흘러간다.
  // 예: sudoku-size 가 '4' 가 아니면 엉뚱한 크기로 스도쿠를 만들다 멈춘다.
  var V = {
    'sudoku-size': '4', 'rule-threshold': '25', 'loop-count': '3',
    'sudoku-speed': '140', 'finalist-count': '4', 'input-name': '지훈',
    'maze-speed': '90', 'maze-level': 'EASY'
  };
  this.value = (V[id] !== undefined) ? V[id] : '60';

  this.appendChild = function (c) { this.children.push(c); };
  this.removeChild = function () {};
  this.addEventListener = function () {};
  this.scrollIntoView = function () {};
  this.getContext = function () { return { drawImage: function () {} }; };
  this.onclick = null;
  this.disabled = false;
  this.step = '1';
  this.srcObject = null;
  this.firstChild = null;
}

var els = {};

// 그 페이지에 실제로 있는 id 만 인정한다.
// 이렇게 해야 "다른 차시 요소를 건드리다 죽는" 문제를 잡아낼 수 있다.
var KNOWN_IDS = {};
try {
  JSON.parse(read('/tmp/page_ids.json')).forEach(function (id) { KNOWN_IDS[id] = true; });
} catch (e) {
  KNOWN_IDS = null;   // 목록이 없으면 예전처럼 다 있다고 본다
}

var document = {
  getElementById: function (id) {
    if (KNOWN_IDS && !KNOWN_IDS[id]) return null;   // 실제로 없는 요소
    if (!els[id]) els[id] = new El(id);
    return els[id];
  },
  createElement: function (t) { return new El('created-' + t); },
  querySelectorAll: function () { return []; },
  addEventListener: function () {}
};

var window = {};

// 페이지가 몇 교시인지 알려 주는 값. 검사할 차시를 여기서 바꾼다.
// (jsc 에 인자를 넘기기 어려워서 환경 대신 이 값을 직접 고친다)
var LESSON_UNDER_TEST = LESSON_UNDER_TEST || '1';
document.body = { dataset: { lesson: LESSON_UNDER_TEST } };
var performance = { now: function () { return 0; } };

// 로그인 기억하기가 쓰는 저장소. 비어 있는 상태를 흉내 낸다.
var _store = {};
var sessionStorage = {
  getItem: function (k) { return _store[k] !== undefined ? _store[k] : null; },
  setItem: function (k, v) { _store[k] = String(v); },
  removeItem: function (k) { delete _store[k]; }
};
var navigator = { mediaDevices: null };
var location = { hostname: 'localhost' };
var console = { log: function () {}, warn: function () {}, error: function () {} };

function setInterval() { return 0; }
function clearInterval() {}
function setTimeout() { return 0; }
function fetch() { return Promise.resolve({ ok: true, json: function () { return Promise.resolve({}); } }); }
function alert() {}

// 바깥에서 불러오는 라이브러리들도 흉내만 낸다
var mqtt = { connect: function () { return { on: function () {}, subscribe: function () {}, publish: function () {} }; } };
var cocoSsd = { load: function () { return Promise.resolve({ detect: function () { return Promise.resolve([]); } }); } };

try {
  load('/tmp/page.js');
  print('✅ 로드 중 오류 없음 — 모든 함수가 정의됨');
} catch (e) {
  print('❌ 로드 중 오류: ' + e);
  if (e.stack) print(e.stack.split('\n').slice(0, 4).join('\n'));
}
