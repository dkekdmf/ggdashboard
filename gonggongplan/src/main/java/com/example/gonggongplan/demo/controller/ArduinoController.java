package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.service.ArduinoReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}