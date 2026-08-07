package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.service.ArduinoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/arduino")
@CrossOrigin(origins = "*")
public class ArduinoController {

    private final ArduinoService arduinoService;

    public ArduinoController(ArduinoService arduinoService) {
        this.arduinoService = arduinoService;
    }

    // 1. RGB LED 색상 제어 API
    @PostMapping("/rgb")
    public ResponseEntity<Map<String, Object>> controlRgb(@RequestBody Map<String, String> request) {
        String color = request.get("color");

        if (color == null || color.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "색상 명령이 없습니다."));
        }

        boolean success = arduinoService.sendCommand(color);

        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "color", color));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "아두이노 연결 실패"));
        }
    }

    // 2. 온습도 데이터 조회 API (👉 추가됨!)
    @GetMapping("/dht")
    public ResponseEntity<Map<String, Object>> getDhtData() {
        return ResponseEntity.ok(Map.of(
                "temperature", arduinoService.getLatestTemperature(),
                "humidity", arduinoService.getLatestHumidity()
        ));
    }
}