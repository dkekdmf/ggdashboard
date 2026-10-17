"""3단계에 '참기 게임'을 넣는다.

반응속도가 "얼마나 빠른가"를 재는 것이라면, 참기 게임은 "얼마나 정확한가"를 잰다.
시작 신호가 울린 뒤 마음속으로 시간을 세어 정확히 그때 눌러야 한다.

측정은 아두이노가 한다. 화면이 0.5초마다 물어보는 방식으로는
누른 순간을 정확히 알 수 없기 때문이다.
"""
import pathlib

# ══════════════════════════════════════════════ 1) 아두이노 스케치

ino = pathlib.Path('arduino/gonggong_kit/gonggong_kit.ino')
s = ino.read_text()

# 설정
old = """// 게임 상황마다 내는 소리 (음 높이 Hz, 길이 ms)"""
new = """// ── 참기 게임 (3단계)
// 시작 신호를 듣고 정해진 시간만큼 마음속으로 센 뒤 버튼을 누른다.
const unsigned int TONE_HOLD_START = 900;    // 시작 신호음
const unsigned long HOLD_TIMEOUT   = 40000;  // 이 시간까지 안 누르면 그냥 끝낸다

// 게임 상황마다 내는 소리 (음 높이 Hz, 길이 ms)"""
assert s.count(old) == 1, '스케치: 소리 설정 부분을 찾지 못했습니다'
s = s.replace(old, new)

# 상태 변수
old = """String rxBuffer = "";"""
new = """// 참기 게임 진행 상태
bool holdOn = false;
unsigned long holdStartedAt = 0;

String rxBuffer = "";"""
assert s.count(old) == 1, '스케치: rxBuffer 선언을 찾지 못했습니다'
s = s.replace(old, new)

# 참기 게임 로직 (updateGame 앞에 넣는다)
old = """// ─────────────────────────────── 반응속도 게임 진행"""
new = """// ─────────────────────────────── 참기 게임 진행 (3단계)
//
// 시작 신호를 내고, 버튼이 눌릴 때까지 기다린 뒤 걸린 시간을 보낸다.
// 화면에 시계를 보여 주지 않는 것이 이 게임의 핵심이라,
// 진행 중에는 아두이노도 아무 신호를 내지 않는다.
void startHold() {
  holdOn = true;
  holdStartedAt = millis();

  writeRgb(0, 0, 255);              // 시작했다는 표시로만 잠깐 파란불
  beep(TONE_HOLD_START, 120);       // "삑" — 이 순간부터 세기 시작
  Serial.println("HOLD:START");
}

void updateHold(bool pressed) {
  if (pressed) {
    unsigned long held = millis() - holdStartedAt;

    holdOn = false;
    showMoodColor();
    Serial.print("HOLD:RESULT,");
    Serial.println(held);
    return;
  }

  // 너무 오래 안 누르면 그냥 끝낸다 (버튼을 잊었거나 자리를 떠난 경우)
  if (millis() - holdStartedAt >= HOLD_TIMEOUT) {
    holdOn = false;
    showMoodColor();
    Serial.println("HOLD:CANCEL");
  }
}

// ─────────────────────────────── 반응속도 게임 진행"""
assert s.count(old) == 1, '스케치: 반응속도 게임 구역을 찾지 못했습니다'
s = s.replace(old, new)

# HOLD:START 명령 받기
old = """  } else if (line.startsWith("MSOUND:")) {"""
new = """  } else if (line.startsWith("HOLD:START")) {
    // 참기 게임 시작. 반응속도 게임이 도는 중이면 받지 않는다.
    if (gameState == WAIT && !holdOn) startHold();

  } else if (line.startsWith("MSOUND:")) {"""
assert s.count(old) == 1, '스케치: MSOUND 처리 부분을 찾지 못했습니다'
s = s.replace(old, new)

# loop 에서 갈라 준다
old = """void loop() {
  readSerial();
  updateMorse();
  sendDhtIfDue();
  updateGame(buttonJustPressed());"""
new = """void loop() {
  readSerial();

  // 버튼 읽기는 한 곳에서만 한다. 여러 곳에서 읽으면 같은 누름을 두 번 세게 된다.
  bool pressed = buttonJustPressed();

  // 참기 게임 중에는 다른 활동이 끼어들지 않게 한다.
  // (반응속도 신호등이 켜지거나 모스가 글자를 만들면 게임이 엉킨다)
  // 온·습도 전송은 계속 돌려도 게임에 영향이 없어서 함께 둔다.
  sendDhtIfDue();

  if (holdOn) {
    updateHold(pressed);
    return;
  }

  updateMorse();
  updateGame(pressed);"""
assert s.count(old) == 1, '스케치: loop 를 찾지 못했습니다'
s = s.replace(old, new)

# 머리말 프로토콜
old = """ *    보드 → PC :  TAP:120        모스를 120ms 동안 두드렸다"""
new = """ *    보드 → PC :  TAP:120        모스를 120ms 동안 두드렸다
 *                 HOLD:START     참기 게임이 시작됐다
 *                 HOLD:RESULT,10800   시작 후 10.8초에 눌렀다
 *                 HOLD:CANCEL    너무 오래 안 눌러서 취소됐다"""
assert s.count(old) == 1, '스케치: 프로토콜 설명을 찾지 못했습니다'
s = s.replace(old, new)

old = """ *                 MSOUND:0 / 1   두드릴 때 나는 소리 끄기 / 켜기 (기본 꺼짐)"""
new = """ *                 MSOUND:0 / 1   두드릴 때 나는 소리 끄기 / 켜기 (기본 꺼짐)
 *                 HOLD:START     참기 게임 시작"""
assert s.count(old) == 1, '스케치: MSOUND 설명을 찾지 못했습니다'
s = s.replace(old, new)

ino.write_text(s)
print("① 아두이노 스케치: 참기 게임 추가")


# ══════════════════════════════════════════════ 2) 서버 서비스

svc = pathlib.Path('gonggongplan/src/main/java/com/example/gonggongplan/demo/service/ArduinoService.java')
s = svc.read_text()

old = """    /** 쌓아 둘 수 있는 두드림 수. 화면이 오래 안 가져가도 메모리가 넘치지 않게 한다. */
    private static final int TAP_QUEUE_LIMIT = 200;"""
new = """    /** 쌓아 둘 수 있는 두드림 수. 화면이 오래 안 가져가도 메모리가 넘치지 않게 한다. */
    private static final int TAP_QUEUE_LIMIT = 200;

    // ── 참기 게임 결과 (3단계)
    //
    // 아두이노가 "HOLD:RESULT,10800" 을 보내면 여기에 넣어 둔다.
    // 화면이 가져가면 비우므로 같은 결과를 두 번 세지 않는다.
    private final java.util.concurrent.ConcurrentLinkedQueue<Integer> holdQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();"""
assert s.count(old) == 1, '서비스: 두드림 큐 부분을 찾지 못했습니다'
s = s.replace(old, new)

old = """        } else if (line.startsWith("TAP:")) {
            parseTap(line.substring(4));"""
new = """        } else if (line.startsWith("HOLD:")) {
            parseHold(line.substring(5));
        } else if (line.startsWith("TAP:")) {
            parseTap(line.substring(4));"""
assert s.count(old) == 1, '서비스: TAP 처리 부분을 찾지 못했습니다'
s = s.replace(old, new)

old = """    /** "120" 을 숫자로 바꿔 두드림 목록에 넣는다. */"""
new = """    /**
     * 참기 게임 소식을 받는다.
     *   "RESULT,10800" → 10.8초에 눌렀다
     *   "START", "CANCEL" → 알림만이라 따로 담아 두지 않는다
     */
    private void parseHold(String payload) {
        if (!payload.startsWith("RESULT")) return;

        int comma = payload.indexOf(',');
        if (comma < 0) return;

        try {
            int held = Integer.parseInt(payload.substring(comma + 1).trim());

            while (holdQueue.size() >= 20) holdQueue.poll();
            holdQueue.add(held);
        } catch (NumberFormatException e) {
            System.out.println("⚠️ 참기 게임 결과 해석 실패: " + payload);
        }
    }

    /** 참기 게임을 시작하라고 아두이노에 알린다. */
    public boolean startHoldGame() {
        return write("HOLD:START");
    }

    /** 쌓인 참기 게임 결과를 모두 가져가고 비운다. */
    public java.util.List<Integer> drainHoldResults() {
        java.util.List<Integer> results = new java.util.ArrayList<>();

        Integer one;
        while ((one = holdQueue.poll()) != null) results.add(one);

        return results;
    }

    /** "120" 을 숫자로 바꿔 두드림 목록에 넣는다. */"""
assert s.count(old) == 1, '서비스: parseTap 앞부분을 찾지 못했습니다'
s = s.replace(old, new)

svc.write_text(s)
print("② 서버 서비스: 참기 게임 처리 추가")


# ══════════════════════════════════════════════ 3) 서버 컨트롤러

ctl = pathlib.Path('gonggongplan/src/main/java/com/example/gonggongplan/demo/controller/ArduinoController.java')
s = ctl.read_text()

old = """    /**
     * 10단계 · 모스 부호로 두드린 기록을 가져간다."""
new = """    /**
     * 3단계 · 참기 게임을 시작한다.
     * 아두이노가 "삑" 소리를 내고, 그 순간부터 시간을 재기 시작한다.
     */
    @PostMapping("/hold/start")
    public ResponseEntity<Map<String, Object>> startHold() {
        boolean sent = arduinoService.startHoldGame();
        return ResponseEntity.ok(Map.of("sent", sent));
    }

    /**
     * 3단계 · 참기 게임 결과를 가져간다. 한 번 가져가면 비워진다.
     * 응답: {"results":[10800]}  ← 시작 후 10.8초에 눌렀다는 뜻
     */
    @GetMapping("/hold")
    public ResponseEntity<Map<String, Object>> getHold() {
        return ResponseEntity.ok(Map.of("results", arduinoService.drainHoldResults()));
    }

    /**
     * 10단계 · 모스 부호로 두드린 기록을 가져간다."""
assert s.count(old) == 1, '컨트롤러: 두드림 주소 앞부분을 찾지 못했습니다'
s = s.replace(old, new)

ctl.write_text(s)
print("③ 서버 컨트롤러: /hold 주소 추가")
