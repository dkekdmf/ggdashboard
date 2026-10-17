package com.example.gonggongplan.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 반응속도 기록 한 건을 화면으로 돌려줄 때 쓰는 그릇.
 * 순위표와 결승 진출자 목록이 이 모양으로 내려간다.
 */
@Getter
@AllArgsConstructor
public class ScoreResponseDto {

    private Long id;             // 기록 번호
    private String playerName;   // 순위표에 보여 줄 이름
    private String mode;         // "WEB"(화면 예선) 또는 "ARDUINO"(결승)
    private String schoolName;
    private String grade;
    private String gender;
    private int reactionTime;    // 반응 시간 (ms). 작을수록 빠르다
    private String evaluation;   // "⚡ 번개같은 반응속도!" 같은 평가 문구
}
