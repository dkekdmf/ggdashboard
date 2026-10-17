package com.example.gonggongplan.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 3단계 반응속도 게임의 기록 한 건. */
@Entity
// 테이블 이름을 대문자로 두면 Hibernate 가 기존 score 테이블을 못 찾아
// 변경(ALTER) 대신 생성(CREATE)을 시도하다 실패한다. 실제 이름과 똑같이 맞춘다.
@Table(name = "score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 순위표에 보여 줄 이름(별명). 랭킹의 핵심이라 반드시 채운다. */
    private String playerName;

    private String schoolName;
    private String grade;
    private String gender;

    /**
     * 어떤 방식으로 쟀는지. "WEB" = 화면 클릭(예선), "ARDUINO" = 실제 버튼(결승).
     * 두 방식은 반응 특성이 달라 한 표에 섞으면 공정하지 않으므로 나눠서 집계한다.
     */
    private String mode;

    /** 반응 시간 (ms). 작을수록 빠르다. */
    private int reactionTime;

    /** "⚡ 번개같은 반응속도!" 같은 평가 문구 */
    private String evaluation;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
