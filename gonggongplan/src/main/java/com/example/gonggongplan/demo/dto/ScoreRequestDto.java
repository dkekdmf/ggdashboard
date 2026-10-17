package com.example.gonggongplan.demo.dto;

/**
 * 반응속도 기록 저장 요청.
 *
 * record 로 둔 이유: 예전에는 @Getter 만 있고 setter 가 없어서
 * Jackson 이 JSON 값을 필드에 채울 수 없었다(반응시간이 항상 0으로 저장됨).
 */
public record ScoreRequestDto(
        String playerName,     // 순위표에 보여 줄 이름 (예: 지훈)
        String mode,           // "WEB" (화면 예선) 또는 "ARDUINO" (결승)
        String schoolName,     // 학교 이름 (예: 서울초등학교)
        String grade,          // 학년 (예: 5학년)
        String gender,         // 성별 (예: 남학생)
        int reactionTime,      // 반응 속도 (예: 350)
        String evaluation      // 평가 문구
) {}
