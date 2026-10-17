package com.example.gonggongplan.demo.repository;

import com.example.gonggongplan.demo.model.Score;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    /** 부문별 빠른 순(오름차순) 상위 10개. 0ms 같은 잘못된 기록은 제외한다. */
    List<Score> findTop10ByModeAndReactionTimeGreaterThanOrderByReactionTimeAsc(String mode, int minTime);

    long countByModeAndReactionTimeGreaterThan(String mode, int minTime);

    long countByReactionTimeGreaterThan(int minTime);

    /**
     * 결승 진출자를 뽑을 때 쓴다.
     * 한 사람이 여러 번 도전해 상위권을 독차지하지 않도록, 가져온 뒤 이름별로 한 명만 남긴다.
     */
    List<Score> findTop50ByModeAndReactionTimeGreaterThanOrderByReactionTimeAsc(String mode, int minTime);
}
