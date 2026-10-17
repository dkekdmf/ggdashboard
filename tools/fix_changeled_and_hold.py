"""두 가지를 고친다.

1. changeLed 가 1단계 화면 요소(led-status, 슬라이더)에 의존해서
   2교시 페이지에서 첫 줄부터 터졌다. 그래서 6단계 블록 프로그램과
   7·8·9단계 무드등 연동이 모두 동작하지 않았다.
   → 없는 요소는 건너뛰고 LED 명령은 그대로 보내도록 고친다.

2. 참기 게임 시작 소리를 기본으로 끈다.
   교실에서 시끄럽다는 의견이 있었다. 대신 파란불로 시작을 알리고,
   필요하면 화면에서 켤 수 있게 스위치를 둔다.
"""
import pathlib

STATIC = pathlib.Path('gonggongplan/src/main/resources/static')

# ══════════════════════════════════════════ 1) changeLed 를 두 페이지에서 쓸 수 있게

OLD_LED = """async function changeLed(color) {
  document.getElementById('led-status').innerText = LED_LABEL[color] || color;

  // MQTT 가 살아 있으면 방송으로 보낸다. 없으면 기존 HTTP 로 보낸다.
  if (publishMqtt(mqttTopics?.cmdLed, { color })) {
    syncSlidersTo(color);
    if (color !== 'OFF') tryComplete(1);
    return;
  }
"""

NEW_LED = """/**
 * 무드등 색을 바꾼다.
 *
 * 이 함수는 1교시(1단계 버튼)와 2교시(6·7·8·9단계 연동)에서 모두 쓴다.
 * 그런데 "현재 색" 표시와 슬라이더는 1단계에만 있는 화면 요소다.
 * 그래서 없는 요소는 건너뛰고, LED 명령 보내는 일만 양쪽에서 똑같이 한다.
 * (이 구분을 빼먹어서 2교시의 블록 프로그램이 첫 줄에서 멈춘 적이 있다)
 */
function showLedStatus(text) {
  if (hasEl('led-status')) document.getElementById('led-status').innerText = text;
}

async function changeLed(color) {
  showLedStatus(LED_LABEL[color] || color);

  // MQTT 가 살아 있으면 방송으로 보낸다. 없으면 기존 HTTP 로 보낸다.
  if (publishMqtt(mqttTopics?.cmdLed, { color })) {
    if (hasEl('slider-r')) syncSlidersTo(color);
    if (color !== 'OFF') tryComplete(1);
    return;
  }
"""

OLD_TAIL = """    if (!data.sent) {
      document.getElementById('led-status').innerText = '⚠️ 아두이노 미연결';
      return;
    }
  } catch (e) {
    console.error('LED 제어 실패:', e);
    document.getElementById('led-status').innerText = '⚠️ 서버 연결 실패';
    return;
  }

  syncSlidersTo(color);
  if (color !== 'OFF') tryComplete(1);
}"""

NEW_TAIL = """    if (!data.sent) {
      showLedStatus('⚠️ 아두이노 미연결');
      return;
    }
  } catch (e) {
    console.error('LED 제어 실패:', e);
    showLedStatus('⚠️ 서버 연결 실패');
    return;
  }

  if (hasEl('slider-r')) syncSlidersTo(color);
  if (color !== 'OFF') tryComplete(1);
}"""

# 숫자로 색을 직접 보내는 쪽도 같은 문제가 있다
OLD_RGB = """async function setRgbLed(r, g, b) {
  document.getElementById('led-status').innerText = `RGB(${r}, ${g}, ${b})`;"""
NEW_RGB = """async function setRgbLed(r, g, b) {
  showLedStatus(`RGB(${r}, ${g}, ${b})`);"""

OLD_RGB2 = """    if (!data.sent) {
      document.getElementById('led-status').innerText = '⚠️ 아두이노 미연결';
      return;
    }
  } catch (e) {
    console.error('LED 제어 실패:', e);
    document.getElementById('led-status').innerText = '⚠️ 서버 연결 실패';
    return;
  }

  tryComplete(1);
}"""
NEW_RGB2 = """    if (!data.sent) {
      showLedStatus('⚠️ 아두이노 미연결');
      return;
    }
  } catch (e) {
    console.error('LED 제어 실패:', e);
    showLedStatus('⚠️ 서버 연결 실패');
    return;
  }

  tryComplete(1);
}"""

for name in ['index.html', 'lesson2.html']:
    path = STATIC / name
    s = path.read_text()

    assert s.count(OLD_LED) == 1, f'{name}: changeLed 앞부분을 찾지 못했습니다'
    s = s.replace(OLD_LED, NEW_LED)

    assert s.count(OLD_TAIL) == 1, f'{name}: changeLed 뒷부분을 찾지 못했습니다'
    s = s.replace(OLD_TAIL, NEW_TAIL)

    assert s.count(OLD_RGB) == 1, f'{name}: setRgbLed 앞부분을 찾지 못했습니다'
    s = s.replace(OLD_RGB, NEW_RGB)

    assert s.count(OLD_RGB2) == 1, f'{name}: setRgbLed 뒷부분을 찾지 못했습니다'
    s = s.replace(OLD_RGB2, NEW_RGB2)

    path.write_text(s)
    print(f"  {name}: changeLed 를 두 차시 모두에서 쓸 수 있게 고침")


# ══════════════════════════════════════════ 2) 참기 게임 시작 소리 스위치 (1교시에만 있음)

path = STATIC / 'index.html'
s = path.read_text()

OLD_UI = """                <div class="flex items-center gap-2">
                    <span class="text-[11px] font-bold text-purple-500">목표</span>
                    <select id="hold-target" class="p-1.5 rounded-lg border border-purple-200 text-[11px] font-bold text-slate-700 bg-white">
                        <option value="5">5초</option>
                        <option value="10" selected>10초</option>
                        <option value="15">15초</option>
                    </select>
                </div>"""
NEW_UI = """                <div class="flex items-center gap-3">
                    <label class="flex items-center gap-1.5 text-[11px] font-bold text-purple-600 cursor-pointer">
                        <input type="checkbox" id="hold-sound" class="w-3.5 h-3.5 accent-purple-600" />
                        🔊 시작 소리
                    </label>
                    <div class="flex items-center gap-2">
                        <span class="text-[11px] font-bold text-purple-500">목표</span>
                        <select id="hold-target" class="p-1.5 rounded-lg border border-purple-200 text-[11px] font-bold text-slate-700 bg-white">
                            <option value="5">5초</option>
                            <option value="10" selected>10초</option>
                            <option value="15">15초</option>
                        </select>
                    </div>
                </div>"""
assert s.count(OLD_UI) == 1, '참기 게임 설정 줄을 찾지 못했습니다'
s = s.replace(OLD_UI, NEW_UI)

# 안내 문구도 함께 (소리가 없을 때 무엇을 보고 시작하는지)
OLD_HINT = """                시작 소리가 나면 <b>마음속으로 세어</b> 정확히 그때 누르세요."""
NEW_HINT = """                시작하면 <b>파란불</b>이 켜져요. 그때부터 <b>마음속으로 세어</b> 정확히 그 순간에 누르세요."""
assert s.count(OLD_HINT) == 1, '참기 게임 안내 문구를 찾지 못했습니다'
s = s.replace(OLD_HINT, NEW_HINT)

# 시작 요청에 소리 여부를 함께 보낸다
OLD_START = """  // 아두이노가 있으면 그쪽이 소리를 내고 시간을 잰다
  try {
    const res = await fetch('/api/arduino/hold/start', { method: 'POST' });
    const data = await res.json();
    holdUsingArduino = !!data.sent;
  } catch (e) {
    holdUsingArduino = false;
  }

  if (holdUsingArduino) {
    setHoldUi('🔔 삑! 지금부터 세세요 — 아두이노 버튼을 누르면 끝', '아두이노 버튼을 누르세요');"""
NEW_START = """  // 아두이노가 있으면 그쪽이 시간을 잰다.
  // 소리는 교실이 시끄러워지므로 기본은 끄고, 필요할 때만 켠다(파란불로도 알 수 있다).
  const withSound = document.getElementById('hold-sound').checked;

  try {
    const res = await fetch('/api/arduino/hold/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sound: withSound })
    });
    const data = await res.json();
    holdUsingArduino = !!data.sent;
  } catch (e) {
    holdUsingArduino = false;
  }

  if (holdUsingArduino) {
    setHoldUi('🔵 파란불! 지금부터 세세요 — 아두이노 버튼을 누르면 끝', '아두이노 버튼을 누르세요');"""
assert s.count(OLD_START) == 1, '참기 게임 시작 부분을 찾지 못했습니다'
s = s.replace(OLD_START, NEW_START)

path.write_text(s)
print("  index.html: 참기 게임 시작 소리 스위치 추가 (기본 꺼짐)")
