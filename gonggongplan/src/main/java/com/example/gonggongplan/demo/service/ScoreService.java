package com.example.gonggongplan.demo.service;

import com.example.gonggongplan.demo.dto.ScoreRequestDto;
import com.example.gonggongplan.demo.dto.ScoreResponseDto;
import com.example.gonggongplan.demo.model.Score;
import com.example.gonggongplan.demo.repository.ScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScoreService {

    /** 이 값보다 큰 기록만 순위에 넣는다 (0ms 같은 잘못된 값 걸러내기). */
    private static final int MIN_VALID_TIME = 0;

    /** 사람이 버튼을 누르는 데 이만큼보다 빠를 수는 없다. 그보다 빠르면 잘못된 기록으로 본다. */
    private static final int HUMAN_FLOOR_MS = 80;

    public static final String MODE_WEB = "WEB";
    public static final String MODE_ARDUINO = "ARDUINO";

    private final ScoreRepository scoreRepository;

    @Transactional
    public ScoreResponseDto saveScore(ScoreRequestDto dto) {
        Score saved = scoreRepository.save(Score.builder()
                .playerName(cleanName(dto.playerName()))
                .mode(cleanMode(dto.mode()))
                .schoolName(dto.schoolName())
                .grade(dto.grade())
                .gender(dto.gender())
                .reactionTime(dto.reactionTime())
                .evaluation(dto.evaluation())
                .build());

        System.out.println("⏱️ 반응속도 기록 저장: [" + saved.getMode() + "] "
                + saved.getPlayerName() + " / " + saved.getReactionTime() + "ms");

        return toDto(saved);
    }

    /** 이름이 비었거나 너무 길면 순위표가 깨지므로 여기서 다듬는다. */
    private String cleanName(String name) {
        if (name == null || name.isBlank()) return "익명";
        String trimmed = name.trim();
        return trimmed.length() > 12 ? trimmed.substring(0, 12) : trimmed;
    }

    private String cleanMode(String mode) {
        return MODE_ARDUINO.equalsIgnoreCase(mode) ? MODE_ARDUINO : MODE_WEB;
    }

    /**
     * 부문별 TOP 10.
     * 한 아이가 여러 번 도전해 순위표를 독차지하지 않도록 사람마다 최고 기록 한 건만 남긴다.
     */
    @Transactional(readOnly = true)
    public List<ScoreResponseDto> getTop10Scores(String mode) {
        return bestPerPlayer(cleanMode(mode), 10);
    }

    /**
     * 화면 예선 기록에서 결승 진출자를 뽑는다.
     * 같은 사람이 여러 번 도전했으면 가장 빠른 기록 한 건만 남긴다.
     */
    @Transactional(readOnly = true)
    public List<ScoreResponseDto> getFinalists(int count) {
        return bestPerPlayer(MODE_WEB, Math.max(2, Math.min(count, 8)));
    }

    /**
     * 빠른 순으로 가져와 이름별 첫 기록(= 그 사람의 최고 기록)만 남긴다.
     * 사람이 낼 수 없는 기록은 애초에 제외한다.
     */
    private List<ScoreResponseDto> bestPerPlayer(String mode, int limit) {
        List<Score> candidates =
                scoreRepository.findTop50ByModeAndReactionTimeGreaterThanOrderByReactionTimeAsc(mode, HUMAN_FLOOR_MS);

        Set<String> seen = new LinkedHashSet<>();
        List<ScoreResponseDto> result = new ArrayList<>();

        for (Score score : candidates) {
            if (!seen.add(score.getPlayerName())) continue;

            result.add(toDto(score));
            if (result.size() == limit) break;
        }
        return result;
    }

    /** 순위표 아래에 "지금까지 ○○명이 도전했어요" 를 보여 주는 데 쓴다. */
    @Transactional(readOnly = true)
    public long countScores(String mode) {
        return mode == null || mode.isBlank()
                ? scoreRepository.countByReactionTimeGreaterThan(MIN_VALID_TIME)
                : scoreRepository.countByModeAndReactionTimeGreaterThan(cleanMode(mode), MIN_VALID_TIME);
    }

    private ScoreResponseDto toDto(Score score) {
        return new ScoreResponseDto(
                score.getId(),
                score.getPlayerName(),
                score.getMode(),
                score.getSchoolName(),
                score.getGrade(),
                score.getGender(),
                score.getReactionTime(),
                score.getEvaluation()
        );
    }
}
