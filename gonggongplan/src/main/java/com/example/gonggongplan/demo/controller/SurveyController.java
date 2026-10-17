package com.example.gonggongplan.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 수업 만족도 조사 응답을 받아 CSV 파일로 쌓아 둔다.
 *
 * 데이터베이스를 쓰지 않고 파일에 붙여 쓰는 이유는, 선생님이 수업이 끝난 뒤
 * 바로 내려받아 엑셀로 열어 볼 수 있어야 하기 때문이다.
 */
@RestController
@RequestMapping("/api/survey")
public class SurveyController {

    /** 문항 수. 프론트엔드와 반드시 같아야 한다. */
    private static final int QUESTION_COUNT = 10;

    /** 저장할 파일 경로. application.properties 의 survey.file 로 바꿀 수 있다. */
    @Value("${survey.file:survey_results.csv}")
    private String surveyFile;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 설문 응답 한 건을 받아 파일 끝에 한 줄 덧붙인다.
     *
     * synchronized 를 붙인 이유가 중요하다.
     * 아이 30명이 동시에 제출하면 여러 요청이 같은 파일에 동시에 쓰게 되는데,
     * 그러면 글자가 서로 섞여 줄이 망가진다. 한 번에 한 명씩만 쓰도록 순서를 세운다.
     */
    @PostMapping
    public synchronized ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        // 문항 수가 맞지 않으면 저장하지 않는다.
        // 화면이 바뀌어 문항이 늘거나 줄었을 때 엉뚱한 줄이 쌓이는 것을 막는다.
        Object rawAnswers = request.get("answers");
        if (!(rawAnswers instanceof List<?> answers) || answers.size() != QUESTION_COUNT) {
            result.put("ok", false);
            result.put("message", "답변이 " + QUESTION_COUNT + "개가 아닙니다.");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            Path path = Paths.get(surveyFile);
            boolean isNew = Files.notExists(path);

            StringBuilder sb = new StringBuilder();

            if (isNew) {
                // 엑셀이 한글을 깨뜨리지 않도록 BOM 을 먼저 넣는다
                sb.append('﻿');
                sb.append("제출시각,학교,학년,성별");
                for (int i = 1; i <= QUESTION_COUNT; i++) sb.append(",문항").append(i);
                sb.append('\n');
            }

            sb.append(csv(LocalDateTime.now().format(STAMP)))
              .append(',').append(csv(str(request.get("schoolName"))))
              .append(',').append(csv(str(request.get("grade"))))
              .append(',').append(csv(str(request.get("gender"))));

            for (Object answer : answers) {
                sb.append(',').append(csv(str(answer)));
            }
            sb.append('\n');

            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            long count = countResponses(path);
            System.out.println("📝 만족도 조사 응답 저장됨 (누적 " + count + "명) → " + path.toAbsolutePath());

            result.put("ok", true);
            result.put("count", count);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            e.printStackTrace();
            result.put("ok", false);
            result.put("message", "저장 중 오류: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /** 지금까지 몇 명이 냈는지. 화면에 "○○번째 응답" 을 보여 주는 데 쓴다. */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count() {
        long count = countResponses(Paths.get(surveyFile));
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** 선생님이 결과를 엑셀로 열어 보기 위해 내려받는 주소. */
    @GetMapping("/csv")
    public ResponseEntity<?> download() {
        Path path = Paths.get(surveyFile);
        if (Files.notExists(path)) {
            return ResponseEntity.ok(Map.of("ok", false, "message", "아직 제출된 응답이 없습니다."));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"survey_results.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new FileSystemResource(path));
    }

    /** 파일의 줄 수로 응답 인원을 센다. 파일이 없으면 아직 아무도 안 낸 것이다. */
    private long countResponses(Path path) {
        if (Files.notExists(path)) return 0;
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return Math.max(0, lines.count() - 1);   // 첫 줄은 제목이라 뺀다
        } catch (IOException e) {
            return 0;
        }
    }

    /** 값이 없을 때 "null" 이라는 글자가 파일에 적히지 않도록 빈 칸으로 바꾼다. */
    private String str(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * CSV 한 칸을 안전하게 감싼다.
     *
     * 아이가 자유 서술에 "재밌었다, 특히 AI가" 처럼 쉼표를 쓰면
     * 그냥 적을 경우 칸이 하나 더 생겨 표 전체가 밀린다.
     * 그래서 큰따옴표로 묶고, 안에 있는 따옴표는 두 번 써서 표시한다(CSV 규칙).
     * 줄바꿈도 칸을 깨뜨리므로 공백으로 바꾼다.
     */
    private String csv(String value) {
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        return '"' + cleaned.replace("\"", "\"\"") + '"';
    }
}
