package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.service.ArduinoReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/arduino")
@RequiredArgsConstructor
public class ArduinoController {

    private final ArduinoReactionService arduinoService;

    /**
     * 프론트엔드에서 0.5초(또는 1초)마다 호출하는 반응속도 게임 상태 API
     * 반환값 예시: {"state": "READY", "message": "초록불을 보세요!", "time": 0}
     *            {"state": "RESULT", "message": "번개같은 반응속도!", "time": 240}
     */
    @GetMapping("/reaction")
    public ResponseEntity<ArduinoReactionService.ReactionDataDto> getReactionData() {
        ArduinoReactionService.ReactionDataDto currentState = arduinoService.getCurrentState();
        return ResponseEntity.ok(currentState);
    }
    // ArduinoController.java 내부 추가
    @PostMapping("/control")
    public ResponseEntity<Map<String, String>> controlArduino(@RequestBody Map<String, String> req) {
        String command = req.get("command"); // "RED", "GREEN", "BLUE", "OFF"

        arduinoService.sendSerialCommand(command);

        Map<String, String> res = new HashMap<>();
        res.put("status", "ok");
        res.put("sentCommand", command);
        return ResponseEntity.ok(res);
    }
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getArduinoStatus() {
        Map<String, Object> status = new HashMap<>();

        // 서비스에서 포트가 정상적으로 열려있는지 확인
        boolean isConnected = arduinoService.isConnected();

        status.put("connected", isConnected);
        status.put("portName", isConnected ? arduinoService.getPortName() : "");

        return ResponseEntity.ok(status);
    }
}