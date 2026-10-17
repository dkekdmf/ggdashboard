package com.example.gonggongplan.demo.service;

import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 아두이노와 통신하는 유일한 창구.
 *
 * 시리얼 포트는 프로세스 하나당 딱 한 번만 열 수 있기 때문에,
 * 온·습도 / LED / 반응속도 게임을 모두 이 서비스 한 곳에서 처리한다.
 *
 * 아두이노 → 서버 (한 줄에 하나씩)
 *   DHT:24.5,50.0     온도(℃), 습도(%)
 *   GAME:WAIT         대기 중
 *   GAME:READY        빨간불 (아직 누르면 안 됨)
 *   GAME:GO           초록불 (지금 누르세요!)
 *   GAME:FOUL         반칙 (초록불 전에 누름)
 *   GAME:RESULT,342   반응 시간 342ms
 *
 * 서버 → 아두이노
 *   LED:255,0,0       RGB 값으로 무드등 제어
 *   GAME:START        반응속도 게임 시작
 */
@Service
public class ArduinoService {

    /** application.properties 의 arduino.port 로 덮어쓸 수 있다. */
    @Value("${arduino.port:auto}")
    private String configuredPort;

    /** USB 시리얼 통로. 이것이 null 이거나 닫혀 있으면 아두이노와 대화할 수 없다. */
    private SerialPort serialPort;

    /** 아두이노가 보내는 줄을 계속 읽는 전담 일꾼(스레드). */
    private Thread readThread;

    // ── 아래 값들에 volatile 을 붙인 이유
    //
    // 두 가지 일이 동시에 돌아간다.
    //   ① 시리얼 읽기 스레드가 값을 계속 새로 쓴다
    //   ② 웹 요청이 들어올 때마다 그 값을 읽어 간다
    //
    // volatile 이 없으면 각 스레드가 자기 쪽에 값을 복사해 두고 쓰는 일이 생겨서,
    // 센서 값이 바뀌었는데 화면에는 옛 값이 계속 보일 수 있다.
    // volatile 은 "항상 최신 값을 보라"는 표시다.

    private volatile double temperature = 0.0;    // 최근 온도 (℃)
    private volatile double humidity = 0.0;       // 최근 습도 (%)
    private volatile boolean dhtReceived = false; // 센서 값을 한 번이라도 받았는지 (화면에 "--" 표시용)

    private volatile String gameState = "IDLE";   // IDLE / READY / GO / FOUL / RESULT
    private volatile String gameMessage = "아두이노 스위치를 누르면 시작합니다.";
    private volatile int reactionTime = 0;        // 마지막 반응 시간 (ms)

    private volatile String ledColor = "OFF";     // 지금 켜져 있는 색 (화면 표시용)

    // ── 모스 부호 두드림 (10단계)
    //
    // 아두이노가 "TAP:120" 을 보내면 여기에 쌓아 둔다.
    // 화면이 가져가면 비운다. 그래서 같은 두드림을 두 번 세지 않는다.
    //
    // 여러 스레드가 함께 쓰므로 스스로 순서를 지켜 주는 큐를 쓴다.
    private final java.util.concurrent.ConcurrentLinkedQueue<Integer> tapQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 쌓아 둘 수 있는 두드림 수. 화면이 오래 안 가져가도 메모리가 넘치지 않게 한다. */
    private static final int TAP_QUEUE_LIMIT = 200;

    // ── 참기 게임 결과 (3단계)
    //
    // 아두이노가 "HOLD:RESULT,10800" 을 보내면 여기에 넣어 둔다.
    // 화면이 가져가면 비우므로 같은 결과를 두 번 세지 않는다.
    private final java.util.concurrent.ConcurrentLinkedQueue<Integer> holdQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 택트스위치가 지금 눌려 있는지. 배선 확인용으로 화면에 그대로 보여 준다. */
    private volatile boolean buttonPressed = false;
    private volatile long lastPressAtMs = 0;

    /** 화면이 0.5초마다 물어보므로, 그보다 짧게 누른 경우를 놓치지 않도록 잠깐 붙잡아 두는 시간 */
    private static final long PRESS_LATCH_MS = 600;

    /**
     * 앱이 켜질 때 딱 한 번 실행된다(@PostConstruct).
     * 아두이노를 찾아 통로를 열고, 읽기 전담 일꾼을 출근시킨다.
     *
     * 중요한 점: 아두이노가 없어도 예외를 던지지 않고 그냥 넘어간다.
     * 아두이노가 안 꽂혀 있다고 수업 전체가 멈추면 안 되기 때문이다.
     * (화면 예선·스도쿠·미로 같은 활동은 아두이노 없이도 돌아간다)
     */
    @PostConstruct
    public void init() {
        serialPort = resolvePort();

        if (serialPort == null) {
            System.out.println("❌ 아두이노를 찾지 못했습니다. USB 케이블과 arduino.port 설정을 확인하세요.");
            return;
        }

        // 아두이노 스케치의 Serial.begin(9600) 과 숫자가 같아야 한다. 다르면 글자가 깨져 들어온다.
        serialPort.setBaudRate(9600);

        // TIMEOUT_SCANNER: 한 줄이 다 올 때까지 기다리는 방식. Scanner 로 줄 단위로 읽기에 알맞다.
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);

        if (!serialPort.openPort()) {
            // 시리얼 포트는 한 번에 한 프로그램만 열 수 있다.
            // 아두이노 IDE 의 시리얼 모니터나 다른 앱이 먼저 잡고 있으면 여기서 실패한다.
            System.out.println("❌ 포트 열기 실패: " + serialPort.getSystemPortName()
                    + " (아두이노 IDE 시리얼 모니터가 켜져 있으면 꺼주세요)");
            serialPort = null;
            return;
        }

        System.out.println("✅ 아두이노 연결 성공! 포트: " + serialPort.getSystemPortName());

        // 읽기는 끝없이 기다리는 일이라 따로 스레드를 둔다. 안 그러면 앱 시작이 여기서 멈춘다.
        readThread = new Thread(this::readLoop, "arduino-serial-reader");
        readThread.setDaemon(true);   // 데몬으로 두면 앱을 끌 때 이 일꾼도 함께 정리된다
        readThread.start();
    }

    /**
     * 쓸 포트를 고른다.
     *
     * arduino.port 에 정확한 이름을 적어 두면 그것을 쓰고,
     * "auto" 면 이름에 usbmodem·usbserial 이 들어간 포트를 찾는다.
     * USB 구멍을 바꿔 꽂으면 이름이 바뀌기 때문에(cu.usbmodem1101 → cu.usbmodem101)
     * 자동 찾기를 기본으로 두는 편이 수업에서 덜 번거롭다.
     */
    private SerialPort resolvePort() {
        if (configuredPort != null && !configuredPort.isBlank() && !"auto".equalsIgnoreCase(configuredPort)) {
            return SerialPort.getCommPort(configuredPort);
        }

        for (SerialPort p : SerialPort.getCommPorts()) {
            String name = p.getSystemPortName();
            if (name.contains("usbmodem") || name.contains("usbserial") || name.startsWith("COM")) {
                return p;
            }
        }
        return null;
    }

    /**
     * 아두이노가 보내는 줄을 끝없이 읽는다. 전담 스레드에서만 돌아간다.
     * 한 줄이 들어올 때마다 parseLine 으로 넘겨 뜻을 해석한다.
     */
    private void readLoop() {
        try (Scanner scanner = new Scanner(serialPort.getInputStream(), StandardCharsets.UTF_8)) {
            while (!Thread.currentThread().isInterrupted() && scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    parseLine(line);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ 시리얼 읽기 중단: " + e.getMessage());
        }
    }

    /**
     * 들어온 한 줄이 무슨 뜻인지 가려 알맞은 곳으로 넘긴다.
     *
     * 앞머리 글자로 종류를 구분하는 방식이다.
     *   "DHT:23.4,52.0"   → 온·습도
     *   "GAME:GO"         → 게임 상태
     *   "BTN:1"           → 버튼 눌림
     * 앞머리가 없는 줄은 예전 스케치가 보내던 한글 문장으로 보고 따로 해석한다.
     */
    private void parseLine(String line) {
        System.out.println("아두이노 → " + line);

        if (line.startsWith("DHT:")) {
            parseDht(line.substring(4));
        } else if (line.startsWith("GAME:")) {
            parseGame(line.substring(5));
        } else if (line.startsWith("HOLD:")) {
            parseHold(line.substring(5));
        } else if (line.startsWith("TAP:")) {
            parseTap(line.substring(4));
        } else if (line.startsWith("BTN:")) {
            buttonPressed = line.substring(4).trim().startsWith("1");
            // 짧게 톡 누르면 화면이 물어보는 사이에 지나가 버리므로 누른 시각을 적어 둔다
            if (buttonPressed) lastPressAtMs = System.currentTimeMillis();
        } else if (line.startsWith("T:")) {
            // 기존에 보드에 올라가 있던 스케치 포맷: "T:23.90,H:51.00"
            parseDht(line.replace("T:", "").replace("H:", ""));
        } else {
            parseLegacyGameText(line);
        }
    }

    /**
     * 예전 스케치는 게임 상태를 한글 문장으로 출력했다.
     * 스케치를 새로 올리지 않아도 반응속도 게임이 동작하도록 그 포맷도 받아 준다.
     */
    private void parseLegacyGameText(String text) {
        if (text.contains("빨간불")) {
            parseGame("READY");
        } else if (text.contains("초록불")) {
            parseGame("GO");
        } else if (text.contains("반칙")) {
            parseGame("FOUL");
        } else if (text.contains("반응 시간")) {
            String digits = text.replaceAll("[^0-9]", "");
            parseGame(digits.isEmpty() ? "RESULT" : "RESULT," + digits);
        } else if (text.contains("다시 도전") || text.contains("게임이 시작됩니다")) {
            parseGame("WAIT");
        }
    }

    /**
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

    /**
     * 참기 게임을 시작하라고 아두이노에 알린다.
     *
     * @param withSound 시작할 때 "삑" 소리를 낼지.
     *                  교실에서 여러 아이가 하면 시끄러워서 기본은 끈다.
     *                  소리를 꺼도 파란불이 켜지므로 시작 시점은 알 수 있다.
     */
    public boolean startHoldGame(boolean withSound) {
        return write("HOLD:START," + (withSound ? "1" : "0"));
    }

    /** 쌓인 참기 게임 결과를 모두 가져가고 비운다. */
    public java.util.List<Integer> drainHoldResults() {
        java.util.List<Integer> results = new java.util.ArrayList<>();

        Integer one;
        while ((one = holdQueue.poll()) != null) results.add(one);

        return results;
    }

    /** "120" 을 숫자로 바꿔 두드림 목록에 넣는다. */
    private void parseTap(String payload) {
        try {
            int held = Integer.parseInt(payload.trim());

            // 너무 많이 쌓이면 오래된 것부터 버린다
            while (tapQueue.size() >= TAP_QUEUE_LIMIT) tapQueue.poll();

            tapQueue.add(held);
        } catch (NumberFormatException e) {
            System.out.println("⚠️ 두드림 값 해석 실패: " + payload);
        }
    }

    /**
     * 쌓인 두드림을 모두 가져가고 목록을 비운다.
     * 화면이 0.2초마다 물어보므로, 그 사이에 여러 번 두드려도 순서대로 다 받는다.
     */
    public java.util.List<Integer> drainTaps() {
        java.util.List<Integer> taps = new java.util.ArrayList<>();

        Integer tap;
        while ((tap = tapQueue.poll()) != null) taps.add(tap);

        return taps;
    }

    /**
     * 부저로 소리를 낸다.
     *
     * @param hz 음 높이. 사람이 들을 수 있는 범위만 받는다(낮은 도 ~ 아주 높은 음).
     * @param ms 길이. 너무 길면 수업이 시끄러워지므로 3초로 제한한다.
     *
     * 아두이노 쪽은 tone() 을 쓰므로 소리가 나는 동안에도 센서와 버튼이 멈추지 않는다.
     */
    public boolean playTone(int hz, int ms) {
        if (hz < 50 || hz > 5000) return false;

        int length = (ms <= 0 || ms > 3000) ? 200 : ms;
        return write("BUZZ:" + hz + "," + length);
    }

    /** 부저를 끈다. */
    public boolean stopTone() {
        return write("BUZZ:OFF");
    }

    /**
     * 모스를 두드릴 때 나는 소리를 켜고 끈다.
     *
     * 교실에서 여러 아이가 두드리면 시끄러워서 기본은 꺼져 있다.
     * 받아쓰기 미션처럼 소리가 필요한 활동에서만 켜면 된다.
     * (소리를 꺼도 두드릴 때 파란불은 켜지므로 눈으로 확인된다)
     */
    public boolean setMorseSound(boolean on) {
        return write("MSOUND:" + (on ? "1" : "0"));
    }

    /** 모스 부호를 아두이노가 소리로 들려주게 한다. (받아쓰기 미션) */
    public boolean playMorse(String code) {
        if (code == null || code.isBlank()) return false;

        // 점·선·쉼표만 남긴다. 엉뚱한 글자가 섞이면 아두이노가 헷갈린다.
        String cleaned = code.replaceAll("[^.\\- ]", "");
        if (cleaned.isBlank()) return false;
        if (cleaned.length() > 60) cleaned = cleaned.substring(0, 60);

        return write("PLAY:" + cleaned);
    }

    /**
     * "23.4,52.0" 을 온도와 습도로 나눠 담는다.
     *
     * 값이 두 개가 아니거나 숫자로 못 읽으면 그냥 버린다.
     * 시리얼은 앱이 켜지는 순간 앞부분이 잘려 들어오는 일이 흔해서(예: "T:00"),
     * 이상한 줄 하나 때문에 멈추지 않도록 조용히 넘긴다.
     */
    private void parseDht(String payload) {
        String[] parts = payload.split(",");
        if (parts.length < 2) return;

        try {
            temperature = Double.parseDouble(parts[0].trim());
            humidity = Double.parseDouble(parts[1].trim());
            dhtReceived = true;
        } catch (NumberFormatException e) {
            System.out.println("⚠️ 온·습도 값 해석 실패: " + payload);
        }
    }

    /**
     * 게임 상태를 받아 화면에 보여 줄 말로 바꿔 둔다.
     *
     * 아두이노는 "GO" 처럼 짧은 낱말만 보낸다.
     * 한글 안내 문장을 여기(서버)에서 만드는 이유는,
     * 아두이노 메모리가 작아서 긴 한글을 담기 부담스럽고,
     * 문구를 고칠 때 스케치를 다시 올리지 않아도 되기 때문이다.
     */
    private void parseGame(String payload) {
        // "RESULT,342" 처럼 값이 붙어 오는 경우가 있어 콤마로 잘라 본다
        String[] parts = payload.split(",");
        String event = parts[0].trim().toUpperCase();

        switch (event) {
            case "READY" -> {
                gameState = "READY";
                gameMessage = "🔴 빨간불! 아직 누르면 안 돼요.";
                reactionTime = 0;
            }
            case "GO" -> {
                gameState = "GO";
                gameMessage = "🟢 초록불! 지금 누르세요!";
            }
            case "FOUL" -> {
                gameState = "FOUL";
                gameMessage = "❌ 반칙! 초록불이 켜지기 전에 눌렀어요.";
                reactionTime = 0;
            }
            case "RESULT" -> {
                gameState = "RESULT";
                if (parts.length > 1) {
                    try {
                        reactionTime = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        reactionTime = 0;
                    }
                }
                gameMessage = evaluate(reactionTime);
            }
            default -> {
                gameState = "IDLE";
                gameMessage = "아두이노 스위치를 누르면 시작합니다.";
                reactionTime = 0;
            }
        }
    }

    /** 반응 시간(ms)을 아이들이 이해하기 쉬운 문장으로 바꿔 준다. */
    public static String evaluate(int ms) {
        if (ms <= 0) return "측정 결과를 기다리는 중이에요.";
        if (ms < 200) return "⚡ 번개같은 반응속도!";
        if (ms < 300) return "🎉 훌륭한 반응속도!";
        if (ms < 450) return "🙂 무난한 반응속도!";
        return "🐢 조금 느렸어요. 다시 도전!";
    }

    // ---------------------------------------------------------------- 명령 보내기

    /**
     * 색 이름을 아두이노가 알아듣는 숫자 명령으로 바꿔 보낸다.
     * "RED" → "LED:255,0,0" 처럼 변환한다. 대소문자는 가리지 않는다.
     *
     * @return 실제로 내보냈으면 true, 아두이노가 없거나 모르는 색이면 false
     */
    public boolean sendLedCommand(String color) {
        if (color == null) return false;

        String payload = switch (color.trim().toUpperCase()) {
            case "RED" -> "LED:255,0,0";
            case "GREEN" -> "LED:0,255,0";
            case "BLUE" -> "LED:0,0,255";
            case "OFF" -> "LED:0,0,0";
            default -> null;
        };
        if (payload == null) {
            System.out.println("⚠️ 알 수 없는 LED 명령: " + color);
            return false;
        }

        if (write(payload)) {
            ledColor = color.trim().toUpperCase();
            return true;
        }
        return false;
    }

    /**
     * 0~255 값을 그대로 보낸다. 화면의 슬라이더가 analogWrite 와 같은 값을 쓰기 때문에,
     * 학생이 옮긴 숫자가 곧 아두이노 코드의 숫자가 된다.
     */
    public boolean sendRgb(int r, int g, int b) {
        int rr = clamp(r), gg = clamp(g), bb = clamp(b);

        if (!write("LED:" + rr + "," + gg + "," + bb)) return false;

        ledColor = (rr == 0 && gg == 0 && bb == 0) ? "OFF" : "RGB(" + rr + "," + gg + "," + bb + ")";
        return true;
    }

    /** 0보다 작거나 255보다 큰 값이 와도 안전한 범위로 맞춘다. */
    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * 웹에서 반응속도 게임을 시작시킨다.
     *
     * 명령을 보내기 전에 화면용 상태를 먼저 READY 로 바꿔 둔다.
     * 아두이노가 "GAME:READY" 를 보내 줄 때까지 잠깐 틈이 있어서,
     * 그 사이 화면이 "대기 중" 으로 보이면 버튼을 눌렀는데 반응이 없는 것처럼 느껴진다.
     */
    public boolean startGame() {
        reactionTime = 0;
        gameState = "READY";
        gameMessage = "🔴 빨간불! 아직 누르면 안 돼요.";
        return write("GAME:START");
    }

    /** 재연결을 너무 자주 시도하지 않도록 두는 간격. */
    private static final long RECONNECT_COOLDOWN_MS = 3000;
    private volatile long lastReconnectAt = 0;

    /**
     * 아두이노로 한 줄을 실제로 내보낸다. 모든 명령이 이 문을 통과한다.
     * 끝에 \n 을 붙이는 이유: 아두이노 쪽이 줄바꿈을 "명령 끝" 신호로 쓴다.
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
            serialPort.getOutputStream().write((payload + "\n").getBytes(StandardCharsets.UTF_8));
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
    }

    // ---------------------------------------------------------------- 상태 조회
    // 아래 메서드들은 컨트롤러와 MQTT 가 현재 상태를 가져갈 때 쓴다.

    /** 통로가 실제로 열려 있는지. USB 를 뽑으면 false 가 된다. */
    public boolean isConnected() {
        return serialPort != null && serialPort.isOpen();
    }

    /** 연결된 포트 이름 (예: cu.usbmodem101). 화면 배지에 보여 준다. */
    public String getPortName() {
        return serialPort != null ? serialPort.getSystemPortName() : "";
    }

    /**
     * 반응속도 게임 상태를 묶어 돌려준다.
     *
     * recentlyPressed 가 눌러 둔 장치다.
     * 화면은 0.5초마다 물어보는데 아이가 톡 짧게 누르면 그 사이에 지나가 버린다.
     * 그래서 "지금 눌려 있다" 뿐 아니라 "0.6초 안에 눌린 적 있다" 도 눌림으로 본다.
     * 덕분에 배선 확인용 표시등이 짧은 누름도 놓치지 않는다.
     */
    public ReactionDataDto getCurrentState() {
        boolean recentlyPressed =
                buttonPressed || (System.currentTimeMillis() - lastPressAtMs) < PRESS_LATCH_MS;
        return new ReactionDataDto(gameState, gameMessage, reactionTime, recentlyPressed);
    }

    /** 온·습도 값. received 가 false 면 아직 센서 값을 못 받은 것이다. */
    public DhtDataDto getDhtData() {
        return new DhtDataDto(temperature, humidity, dhtReceived);
    }

    /** 지금 켜진 색 이름. 화면의 "현재 무드등" 표시에 쓴다. */
    public String getLedColor() {
        return ledColor;
    }

    @PreDestroy
    public void close() {
        if (readThread != null) {
            readThread.interrupt();
        }
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            System.out.println("🔌 아두이노 시리얼 포트를 닫았습니다.");
        }
    }

    public record ReactionDataDto(String state, String message, int time, boolean buttonPressed) {}

    /** received 가 false 면 아직 센서 값이 한 번도 안 들어온 상태 (화면에 "--" 표시용) */
    public record DhtDataDto(double temperature, double humidity, boolean received) {}
}
