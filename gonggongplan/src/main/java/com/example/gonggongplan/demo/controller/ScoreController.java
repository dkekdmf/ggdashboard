package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.dto.ScoreRequestDto;
import com.example.gonggongplan.demo.dto.ScoreResponseDto;
import com.example.gonggongplan.demo.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 3단계 반응속도 기록과 순위표를 다루는 창구.
 *
 * 수업 흐름이 두 단계로 되어 있다.
 *   예선(WEB)     — 아이들이 각자 기기에서 화면을 눌러 재고, 몇 번이든 도전
 *   결승(ARDUINO) — 예선 상위 선수들이 앞으로 나와 실제 아두이노 버튼으로 대결
 *
 * 두 방식은 반응 특성이 달라서(화면은 그리는 시간이 더 붙는다) 순위를 나눠서 매긴다.
 */
@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    /** 측정 결과 저장. mode 는 "WEB"(화면 예선) 또는 "ARDUINO"(결승). */
    @PostMapping
    public ResponseEntity<ScoreResponseDto> saveScore(@RequestBody ScoreRequestDto dto) {
        return ResponseEntity.ok(scoreService.saveScore(dto));
    }

    /** 부문별 TOP 10. 예: /api/scores/top10?mode=ARDUINO */
    @GetMapping("/top10")
    public ResponseEntity<List<ScoreResponseDto>> getTop10(
            @RequestParam(defaultValue = "WEB") String mode) {
        return ResponseEntity.ok(scoreService.getTop10Scores(mode));
    }

    /** 화면 예선 기록에서 결승 진출자를 뽑는다 (사람마다 한 번만). */
    @GetMapping("/finalists")
    public ResponseEntity<List<ScoreResponseDto>> getFinalists(
            @RequestParam(defaultValue = "4") int count) {
        return ResponseEntity.ok(scoreService.getFinalists(count));
    }

    /** 지금까지 도전한 기록 수. mode 를 주면 부문별로 센다. */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(
            @RequestParam(required = false) String mode) {
        return ResponseEntity.ok(Map.of("count", scoreService.countScores(mode)));
    }
}
