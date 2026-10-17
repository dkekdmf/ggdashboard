"""두 가지를 고친다.

1. MQTT WebSocket 주소에 /mqtt 경로를 붙인다.
   Moquette 브로커는 이 경로에서만 WebSocket 을 받는다.
   (앞서 검증할 때 자바 클라이언트로 1883 포트를 써서 이 문제가 드러나지 않았다)

2. 차시를 옮길 때 로그인 화면이 다시 뜨지 않게 한다.
   로그인한 내용을 sessionStorage 에 적어 두고, 다음 페이지에서 그대로 쓴다.
   localStorage 가 아니라 sessionStorage 를 쓰는 이유: 브라우저를 닫으면 지워져서,
   교실 공용 컴퓨터에서 다음 아이가 앞 아이 이름으로 시작하는 일이 없다.
"""
import pathlib

FILES = ['index.html', 'lesson2.html']

# ── 1) WebSocket 경로
OLD_URL = "  const url = `ws://${location.hostname}:${info.websocketPort}`;"
NEW_URL = """  // 경로(/mqtt)를 꼭 붙여야 한다. 브로커가 그 경로에서만 WebSocket 을 받는다.
  // 페이지가 https 로 열렸으면 ws 는 브라우저가 막으므로 wss 로 맞춘다.
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const url = `${scheme}://${location.hostname}:${info.websocketPort}/mqtt`;"""

# ── 2) 로그인 기억하기
OLD_LOGIN = """  document.getElementById('display-name').innerText = `🙋 ${playerName}`;
  document.getElementById('display-school-grade').innerText = `${schoolName} ${grade}`;"""
NEW_LOGIN = """  // 차시를 옮겨도 다시 로그인하지 않도록 적어 둔다
  saveLogin({ playerName, schoolName, grade, gender });

  applyLogin({ playerName, schoolName, grade, gender });"""

HELPERS = """/* ==========================================================================
   로그인 기억하기
   차시가 두 페이지로 나뉘어서, 페이지를 옮길 때마다 로그인하면 번거롭다.
   그래서 sessionStorage 에 적어 두고 다음 페이지에서 그대로 쓴다.

   localStorage 를 쓰지 않는 이유: 브라우저를 닫아도 남아 있어서,
   교실 공용 컴퓨터에서 다음 아이가 앞 아이 이름으로 시작해 버린다.
   sessionStorage 는 창을 닫으면 지워진다.
   ========================================================================== */
const LOGIN_KEY = 'gonggong.login';

function saveLogin(info) {
  try {
    sessionStorage.setItem(LOGIN_KEY, JSON.stringify(info));
  } catch (e) {
    // 저장이 안 되더라도 수업은 계속되어야 한다 (다음 페이지에서 한 번 더 로그인하면 된다)
    console.warn('로그인 정보 저장 실패:', e);
  }
}

function loadLogin() {
  try {
    const raw = sessionStorage.getItem(LOGIN_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

/** 로그인 내용을 화면에 반영하고 로그인 창을 닫는다. */
function applyLogin(info) {
  document.getElementById('input-name').value = info.playerName || '';
  document.getElementById('input-school').value = info.schoolName || '';
  document.getElementById('input-grade').value = info.grade || '';
  document.getElementById('input-gender').value = info.gender || '';

  document.getElementById('display-name').innerText = `🙋 ${info.playerName}`;
  document.getElementById('display-school-grade').innerText = `${info.schoolName} ${info.grade}`;
  document.getElementById('display-gender').innerText = info.gender;
  document.getElementById('login-modal').classList.add('hidden');
}

/**
 * 이미 로그인한 적이 있으면 로그인 창을 건너뛴다.
 * 1교시에서 2교시로 넘어갈 때 이름을 다시 입력하지 않게 하는 것이 목적이다.
 */
function restoreLogin() {
  const info = loadLogin();
  if (info && info.playerName) applyLogin(info);
}

/** 다른 아이가 이어서 할 때 쓰는 새로 로그인. */
function logoutAndRestart() {
  try {
    sessionStorage.removeItem(LOGIN_KEY);
  } catch (e) {
    // 지우지 못해도 아래에서 새로 불러오면 로그인 창이 다시 뜬다
  }
  location.reload();
}

"""

for name in FILES:
    path = pathlib.Path('gonggongplan/src/main/resources/static') / name
    text = path.read_text()

    assert text.count(OLD_URL) == 1, f'{name}: MQTT 주소 줄을 찾지 못했습니다'
    text = text.replace(OLD_URL, NEW_URL)

    assert text.count(OLD_LOGIN) == 1, f'{name}: 로그인 처리 부분을 찾지 못했습니다'
    text = text.replace(OLD_LOGIN, NEW_LOGIN)

    # 로그인 창을 숨기던 옛 줄 정리 (applyLogin 이 대신 한다)
    old_tail = """  document.getElementById('display-gender').innerText = gender;
  document.getElementById('login-modal').classList.add('hidden');
}"""
    assert text.count(old_tail) == 1, f'{name}: 로그인 마무리 부분을 찾지 못했습니다'
    text = text.replace(old_tail, "}")

    # 도움 함수들을 로그인 처리 앞에 넣는다
    anchor = "async function handleLogin(event) {"
    assert text.count(anchor) == 1, f'{name}: handleLogin 을 찾지 못했습니다'
    text = text.replace(anchor, HELPERS + anchor)

    # 첫 실행 때 로그인 복원을 먼저 한다
    old_init = "const INIT_TASKS = [\n  { fn: refreshProgress,  needs: null },"
    new_init = "const INIT_TASKS = [\n  { fn: restoreLogin,      needs: 'login-modal' },\n  { fn: refreshProgress,  needs: null },"
    assert text.count(old_init) == 1, f'{name}: 초기화 묶음을 찾지 못했습니다'
    text = text.replace(old_init, new_init)

    # 헤더의 이름 옆에 "다른 사람으로 시작" 버튼
    old_name = """<span id="display-name" class="text-xs font-black text-slate-800">🙋 친구</span>"""
    new_name = """<span id="display-name" class="text-xs font-black text-slate-800">🙋 친구</span>
                    <button onclick="logoutAndRestart()" class="text-[10px] text-slate-400 hover:text-slate-600 underline">바꾸기</button>"""
    assert text.count(old_name) == 1, f'{name}: 헤더 이름 표시를 찾지 못했습니다'
    text = text.replace(old_name, new_name)

    path.write_text(text)
    print(f"  {name}: MQTT 경로 + 로그인 기억 적용")
