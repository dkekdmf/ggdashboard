package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.service.ArduinoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 아두이노와 주고받는 요청을 받는 창구. (1·2·3단계와 5단계가 여기를 쓴다)
 *
 * 실제 시리얼 통신은 ArduinoService 가 하고, 이 클래스는 전달만 한다.
 * 이렇게 나눠 두면 통신 방식이 바뀌어도(예: MQTT 추가) 창구는 그대로 쓸 수 있다.
 *
 * 요청 방향이 두 가지다.
 *   읽기(GET)  : 아두이노가 보내 준 값을 화면이 가져간다  — 상태·온습도·게임
 *   쓰기(POST) : 화면이 아두이노에게 명령을 보낸다        — 무드등·게임 시작
 */
@RestController
@RequestMapping("/api/arduino")   // 이 클래스의 주소는 모두 /api/arduino 로 시작
@RequiredArgsConstructor          // final 필드를 받는 생성자를 롬복이 자동으로 만들어 준다
public class ArduinoController {

    private final ArduinoService arduinoService;

    /** 아두이노 연결 상태 — 화면 우측 상단 배지에서 2초마다 호출 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean connected = arduinoService.isConnected();

        Map<String, Object> status = new HashMap<>();
        status.put("connected", connected);
        status.put("portName", connected ? arduinoService.getPortName() : "");
        return ResponseEntity.ok(status);
    }

    /** 온·습도 센서 값 — {"temperature":24.5,"humidity":50.0,"received":true} */
    @GetMapping("/dht")
    public ResponseEntity<ArduinoService.DhtDataDto> getDht() {
        return ResponseEntity.ok(arduinoService.getDhtData());
    }

    /** 반응속도 게임 상태 — {"state":"RESULT","message":"⚡ 번개같은 반응속도!","time":240} */
    @GetMapping("/reaction")
    public ResponseEntity<ArduinoService.ReactionDataDto> getReaction() {
        return ResponseEntity.ok(arduinoService.getCurrentState());
    }

    /** 반응속도 게임을 웹에서 시작 */
    @PostMapping("/reaction/start")
    public ResponseEntity<Map<String, Object>> startReaction() {
        boolean sent = arduinoService.startGame();
        return ResponseEntity.ok(Map.of("sent", sent));
    }

    /**
     * 1단계 · 색 이름으로 무드등 켜기. body: {"color":"RED"}
     *
     * 돌려주는 sent 가 중요하다.
     *   true  = 아두이노로 명령을 내보냈다
     *   false = 아두이노가 연결되지 않아 보내지 못했다
     * 화면은 이 값을 보고 "⚠️ 아두이노 미연결" 을 표시한다.
     */
    @PostMapping("/led")
    public ResponseEntity<Map<String, Object>> setLed(@RequestBody Map<String, String> req) {
        String color = req.get("color");
        boolean sent = arduinoService.sendLedCommand(color);

        Map<String, Object> res = new HashMap<>();
        res.put("sent", sent);
        res.put("color", arduinoService.getLedColor());
        return ResponseEntity.ok(res);
    }

    /**
     * 1단계 · 숫자로 색을 직접 만들기 (analogWrite 실습). body: {"r":255,"g":0,"b":0}
     *
     * 색 이름으로는 만들 수 없는 색(흰색·주황색 등)을 켤 때 쓴다.
     * 음성 명령에서 "불 켜줘" 처럼 색을 말하지 않은 경우 흰색(255,255,255)으로 이 주소를 쓴다.
     *
     * getOrDefault 를 두 번 쓴 이유: 값이 아예 없으면 0을, 값이 null 로 오면 역시 0으로 맞춘다.
     */
    @PostMapping("/led/rgb")
    public ResponseEntity<Map<String, Object>> setRgb(@RequestBody Map<String, Integer> req) {
        int r = req.getOrDefault("r", 0) == null ? 0 : req.getOrDefault("r", 0);
        int g = req.getOrDefault("g", 0) == null ? 0 : req.getOrDefault("g", 0);
        int b = req.getOrDefault("b", 0) == null ? 0 : req.getOrDefault("b", 0);

        boolean sent = arduinoService.sendRgb(r, g, b);

        Map<String, Object> res = new HashMap<>();
        res.put("sent", sent);
        res.put("color", arduinoService.getLedColor());
        return ResponseEntity.ok(res);
    }

    /**
     * 부저로 소리 내기. body: {"hz":1000, "ms":200}
     *
     * Node-RED 나 다른 화면에서도 소리를 낼 수 있게 열어 둔 주소다.
     * {"off":true} 를 보내면 소리를 멈춘다.
     */
    @PostMapping("/buzzer")
    public ResponseEntity<Map<String, Object>> buzzer(@RequestBody Map<String, Object> req) {
        if (Boolean.TRUE.equals(req.get("off"))) {
            return ResponseEntity.ok(Map.of("sent", arduinoService.stopTone(), "off", true));
        }

        int hz = req.get("hz") instanceof Number n ? n.intValue() : 1000;
        int ms = req.get("ms") instanceof Number n ? n.intValue() : 200;

        boolean sent = arduinoService.playTone(hz, ms);
        return ResponseEntity.ok(Map.of("sent", sent, "hz", hz, "ms", ms));
    }

    /**
     * 3단계 · 참기 게임을 시작한다.
     * 아두이노가 "삑" 소리를 내고, 그 순간부터 시간을 재기 시작한다.
     */
    @PostMapping("/hold/start")
    public ResponseEntity<Map<String, Object>> startHold(
            @RequestBody(required = false) Map<String, Boolean> req) {
        // 소리를 켤지는 화면에서 정한다. 아무 값이 없으면 조용히 시작한다.
        boolean withSound = req != null && Boolean.TRUE.equals(req.get("sound"));

        boolean sent = arduinoService.startHoldGame(withSound);
        return ResponseEntity.ok(Map.of("sent", sent, "sound", withSound));
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
     * 10단계 · 모스 부호로 두드린 기록을 가져간다.
     *
     * 한 번 가져가면 서버에서는 비워지므로, 같은 두드림을 두 번 세지 않는다.
     * 응답: {"taps":[120, 480, 110]}  ← 각 숫자는 몇 밀리초 눌렸는지
     *
     * 점(·)인지 선(−)인지는 화면이 판단한다. 아이마다 손 빠르기가 달라서
     * 그 기준을 아이에 맞춰 조정해야 하기 때문이다.
     */
    @GetMapping("/taps")
    public ResponseEntity<Map<String, Object>> getTaps() {
        return ResponseEntity.ok(Map.of("taps", arduinoService.drainTaps()));
    }

    /**
     * 10단계 · 두드릴 때 나는 소리를 켜고 끈다. body: {"on":true}
     * 교실이 시끄러울 때 끄고, 필요할 때만 켠다.
     */
    @PostMapping("/morse/sound")
    public ResponseEntity<Map<String, Object>> setMorseSound(@RequestBody Map<String, Boolean> req) {
        boolean on = Boolean.TRUE.equals(req.get("on"));
        boolean sent = arduinoService.setMorseSound(on);
        return ResponseEntity.ok(Map.of("sent", sent, "on", on));
    }

    /**
     * 10단계 · 모스 부호를 아두이노가 소리로 들려준다. (받아쓰기 미션)
     * body: {"code":".- -..."}
     */
    @PostMapping("/morse/play")
    public ResponseEntity<Map<String, Object>> playMorse(@RequestBody Map<String, String> req) {
        boolean sent = arduinoService.playMorse(req.get("code"));
        return ResponseEntity.ok(Map.of("sent", sent));
    }

    /**
     * 5단계 스마트홈 규칙이 쓰는 주소. body: {"command":"GREEN"}
     *
     * 하는 일은 /led 와 똑같다. 이름만 다르게 둔 것인데,
     * 규칙 엔진 쪽에서는 "색"보다 "명령"이라는 말이 뜻이 잘 맞아서 그대로 남겼다.
     */
    @PostMapping("/control")
    public ResponseEntity<Map<String, Object>> control(@RequestBody Map<String, String> req) {
        String command = req.get("command");
        boolean sent = arduinoService.sendLedCommand(command);

        Map<String, Object> res = new HashMap<>();
        res.put("sent", sent);
        res.put("sentCommand", command);
        return ResponseEntity.ok(res);
    }
}
