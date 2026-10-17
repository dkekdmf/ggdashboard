"""무드등이 몇 번 쓰다 보면 먹통이 되는 문제를 고친다.

원인이 두 가지였다.

① 보드가 초록불(GO) 상태에 갇힌다
   반응속도 게임은 버튼을 누를 때까지 영원히 기다렸다.
   게임 중에는 LED 명령이 와도 색을 바꾸지 않으므로(신호등 우선),
   아무도 버튼을 안 누르면 무드등이 계속 먹통이 된다.
   → 초록불에 시간 제한을 둬서 저절로 대기 상태로 돌아가게 한다.

② 시리얼 쓰기가 한 번 실패하면 영원히 실패한다
   USB 를 다시 꽂거나 통신이 어긋나면 쓰기가 막히는데,
   스스로 복구하지 못해 앱을 재시작해야만 했다.
   → 쓰기에 실패하면 포트를 닫고 다시 열어 한 번 더 시도한다.
"""
import pathlib

# ══════════════════════════════════════════ ① 스케치 — 초록불 시간 제한

ino = pathlib.Path('arduino/gonggong_kit/gonggong_kit.ino')
s = ino.read_text()

old = """const unsigned long RESULT_HOLD    = 3000;  // 결과를 보여주는 시간"""
new = """const unsigned long RESULT_HOLD    = 3000;  // 결과를 보여주는 시간

// 초록불이 켜졌는데 아무도 안 누르면 여기서 끝낸다.
// 시간 제한이 없으면 게임이 영영 안 끝나고, 그동안 무드등 명령이 무시된다.
const unsigned long GO_TIMEOUT     = 15000;"""
assert s.count(old) == 1, '스케치: RESULT_HOLD 설정을 찾지 못했습니다'
s = s.replace(old, new)

old = """    case GO:
      if (pressed) {"""
new = """    case GO:
      // 아무도 안 누르면 스스로 대기 상태로 돌아간다.
      // 그래야 무드등이 다시 말을 듣는다.
      if (millis() - goStartedAt >= GO_TIMEOUT) {
        Serial.println("GAME:TIMEOUT");
        enterState(WAIT);
        break;
      }

      if (pressed) {"""
assert s.count(old) == 1, '스케치: GO 상태 처리 부분을 찾지 못했습니다'
s = s.replace(old, new)

ino.write_text(s)
print("① 스케치: 초록불에 15초 시간 제한 추가")


# ══════════════════════════════════════════ ② 서버 — 쓰기 실패 시 자동 재연결

svc = pathlib.Path('gonggongplan/src/main/java/com/example/gonggongplan/demo/service/ArduinoService.java')
s = svc.read_text()

old = """    /**
     * 아두이노로 한 줄을 실제로 내보낸다. 모든 명령이 이 문을 통과한다.
     * 끝에 \\n 을 붙이는 이유: 아두이노 쪽이 줄바꿈을 "명령 끝" 신호로 쓴다.
     */
    private boolean write(String payload) {
        if (!isConnected()) {
            System.out.println("⚠️ 아두이노가 연결되어 있지 않아 명령을 보낼 수 없습니다: " + payload);
            return false;
        }
        try {
            serialPort.getOutputStream().write((payload + "\\n").getBytes(StandardCharsets.UTF_8));
            serialPort.getOutputStream().flush();   // 버퍼에 쌓아 두지 말고 즉시 내보낸다
            System.out.println("서버 → 아두이노: " + payload);
            return true;
        } catch (Exception e) {
            System.out.println("⚠️ 명령 전송 실패: " + e.getMessage());
            return false;
        }
    }"""

new = """    /** 재연결을 너무 자주 시도하지 않도록 두는 간격. */
    private static final long RECONNECT_COOLDOWN_MS = 3000;
    private volatile long lastReconnectAt = 0;

    /**
     * 아두이노로 한 줄을 실제로 내보낸다. 모든 명령이 이 문을 통과한다.
     * 끝에 \\n 을 붙이는 이유: 아두이노 쪽이 줄바꿈을 "명령 끝" 신호로 쓴다.
     *
     * 한 번 실패하면 포트를 다시 열고 한 번 더 시도한다.
     * USB 를 다시 꽂거나 통신이 어긋났을 때 앱을 재시작하지 않아도 복구되도록 하기 위함이다.
     */
    private boolean write(String payload) {
        if (isConnected() && writeOnce(payload)) return true;

        // 여기까지 왔다는 건 연결이 끊겼거나 쓰기가 막혔다는 뜻이다
        if (!reconnect()) {
            System.out.println("⚠️ 아두이노에 명령을 보내지 못했습니다: " + payload);
            return false;
        }

        return writeOnce(payload);
    }

    /** 실제로 한 번 써 본다. 실패하면 false 만 돌려주고 판단은 위에서 한다. */
    private boolean writeOnce(String payload) {
        try {
            serialPort.getOutputStream().write((payload + "\\n").getBytes(StandardCharsets.UTF_8));
            serialPort.getOutputStream().flush();   // 버퍼에 쌓아 두지 말고 즉시 내보낸다
            System.out.println("서버 → 아두이노: " + payload);
            return true;
        } catch (Exception e) {
            System.out.println("⚠️ 명령 전송 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 포트를 닫고 다시 찾아 연다.
     *
     * USB 를 다른 구멍에 꽂으면 이름이 바뀌므로(cu.usbmodem1101 → cu.usbmodem101)
     * 기억해 둔 포트를 다시 쓰지 않고 처음부터 다시 찾는다.
     */
    private synchronized boolean reconnect() {
        long now = System.currentTimeMillis();
        if (now - lastReconnectAt < RECONNECT_COOLDOWN_MS) return false;
        lastReconnectAt = now;

        System.out.println("🔄 아두이노 연결을 다시 시도합니다...");

        try {
            if (serialPort != null && serialPort.isOpen()) serialPort.closePort();
        } catch (Exception ignored) {
            // 이미 사라진 포트를 닫는 중 나는 오류는 무시해도 된다
        }

        if (readThread != null) readThread.interrupt();

        serialPort = resolvePort();
        if (serialPort == null) {
            System.out.println("   아두이노를 찾지 못했습니다. USB 를 확인해 주세요.");
            return false;
        }

        serialPort.setBaudRate(9600);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);

        if (!serialPort.openPort()) {
            System.out.println("   포트를 열지 못했습니다: " + serialPort.getSystemPortName());
            serialPort = null;
            return false;
        }

        System.out.println("✅ 아두이노 다시 연결됨! 포트: " + serialPort.getSystemPortName());

        readThread = new Thread(this::readLoop, "arduino-serial-reader");
        readThread.setDaemon(true);
        readThread.start();

        return true;
    }"""

assert s.count(old) == 1, '서비스: write 메서드를 찾지 못했습니다'
s = s.replace(old, new)

svc.write_text(s)
print("② 서버: 쓰기 실패 시 포트를 다시 열고 재시도")
