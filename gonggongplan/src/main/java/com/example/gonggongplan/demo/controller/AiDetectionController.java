package com.example.gonggongplan.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 구글 Gemini(AI)에게 물어보는 일을 모아 둔 창구. 세 곳에서 쓴다.
 *
 *   /detect  — 4단계 : 사진 속 사물이 무엇인지 (예: "스마트폰")
 *   /sudoku  — 7단계 : 사진 속 스도쿠 격자의 숫자들
 *   /hint    — 7단계 : 스도쿠 힌트를 아이 말투로 다듬기
 *
 * 왜 브라우저가 Gemini 에 직접 묻지 않고 서버를 거치는가?
 * API 키를 화면에 두면 누구나 볼 수 있어 도용된다. 키는 서버에만 두고,
 * 브라우저는 우리 서버에게만 부탁하도록 했다.
 *
 * 한 가지 중요한 원칙: /hint 는 AI 에게 스도쿠를 풀게 시키지 않는다.
 * 답은 웹이 정확히 계산하고, AI 는 말투만 다듬는다. AI 는 논리를 자주 틀리기 때문이다.
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiDetectionController {

    /**
     * Gemini API 키. application.properties 의 gemini.api.key 에서 읽어 온다.
     * 설정이 없으면 기본값이 들어가고, 그때는 호출이 실패한다.
     */
    @Value("${gemini.api.key:YOUR_GEMINI_KEY}")
    private String apiKey;

    /**
     * 사진 속 스도쿠 격자를 읽어 숫자 배열로 돌려준다. (7단계 "사진으로 문제 넣기")
     * body: {"image": "data:image/jpeg;base64,...", "size": 4 또는 9}
     * 응답: {"ok": true, "cells": [0,3,0,1, ...]}  빈 칸은 0
     */
    @PostMapping("/sudoku")
    public ResponseEntity<Map<String, Object>> readSudoku(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        String base64Image = (String) request.get("image");
        int size = request.get("size") instanceof Number n ? n.intValue() : 9;
        if (size != 4 && size != 9) size = 9;

        if (base64Image == null || base64Image.isEmpty()) {
            result.put("ok", false);
            result.put("message", "이미지 데이터가 전달되지 않았습니다.");
            return ResponseEntity.badRequest().body(result);
        }

        String prompt = String.format(
                "이 사진은 %d×%d 스도쿠 문제입니다. 격자의 왼쪽 위 칸부터 오른쪽으로 읽어 나가며 "
                        + "모든 줄을 순서대로 읽어 주세요. 숫자가 없는 빈 칸은 0으로 적습니다. "
                        + "숫자 %d개를 쉼표로만 구분해서 한 줄로 답하고, 다른 말은 절대 쓰지 마세요.",
                size, size, size * size);

        try {
            String text = askGemini(prompt, base64Image);
            if (text == null) {
                result.put("ok", false);
                result.put("message", "AI가 사진을 읽지 못했습니다.");
                return ResponseEntity.ok(result);
            }

            List<Integer> cells = parseCells(text, size);
            if (cells == null) {
                result.put("ok", false);
                result.put("message", "숫자를 " + (size * size) + "개로 읽지 못했습니다. 격자가 잘 보이게 다시 찍어 주세요.");
                result.put("raw", text);
                return ResponseEntity.ok(result);
            }

            result.put("ok", true);
            result.put("cells", cells);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("ok", false);
            result.put("message", "서버 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /** 응답 문자열에서 숫자만 추려 격자 크기와 맞는지 확인한다. */
    private List<Integer> parseCells(String text, int size) {
        List<Integer> cells = new ArrayList<>();
        for (String token : text.replaceAll("[^0-9,]", "").split(",")) {
            if (token.isEmpty()) continue;
            try {
                int v = Integer.parseInt(token.trim());
                if (v < 0 || v > size) v = 0;   // 크기를 벗어난 숫자는 빈 칸으로 본다
                cells.add(v);
            } catch (NumberFormatException ignored) {
                // 숫자로 못 읽은 조각은 버린다
            }
        }
        return cells.size() == size * size ? cells : null;
    }

    /**
     * 스도쿠 힌트를 초등학생 말투로 바꿔 준다. (7단계 "AI 선생님에게 물어보기")
     *
     * 어느 칸에 무엇이 들어가는지는 웹에서 이미 정확히 계산해서 보내 준다.
     * Gemini 에게 스도쿠를 풀게 시키면 논리를 자주 틀리기 때문에,
     * 여기서는 "설명을 예쁘게 다듬는 일"만 맡긴다.
     *
     * body: {"situation": "...", "revealAnswer": false}
     */
    @PostMapping("/hint")
    public ResponseEntity<Map<String, Object>> explainHint(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        String situation = (String) request.get("situation");
        boolean reveal = Boolean.TRUE.equals(request.get("revealAnswer"));

        if (situation == null || situation.isBlank()) {
            result.put("ok", false);
            result.put("message", "설명할 내용이 없습니다.");
            return ResponseEntity.badRequest().body(result);
        }

        String prompt = "너는 초등학생에게 스도쿠를 가르치는 친절한 선생님이야.\n"
                + "아래 상황을 초등학생이 이해할 수 있는 쉬운 말로 두 문장 이내로 설명해 줘.\n"
                + (reveal
                    ? "답 숫자를 알려주되, 왜 그 숫자인지 이유를 꼭 함께 말해 줘.\n"
                    : "답 숫자는 절대 알려주지 말고, 어디를 어떻게 살펴보면 좋을지만 알려 줘.\n")
                + "존댓말을 쓰고, 격려하는 말투로 해 줘. 다른 말은 붙이지 마.\n\n"
                + "[상황]\n" + situation;

        try {
            String text = askGemini(prompt, null);
            if (text == null || text.isBlank()) {
                result.put("ok", false);
                result.put("message", "AI가 답하지 못했습니다.");
                return ResponseEntity.ok(result);
            }

            result.put("ok", true);
            result.put("text", text.trim());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("힌트 설명 실패: " + e.getMessage());
            result.put("ok", false);
            result.put("message", "AI 선생님을 부르지 못했어요.");
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 10단계 · 모스 부호로 받아쓴 글자가 이상할 때 AI 가 단어를 추측한다.
     *
     * body: {"word": "HELLQ"}
     * 응답: {"ok": true, "guess": "HELLO", "reason": "마지막 글자를 잘못 두드린 것 같아요"}
     *
     * 해독 자체는 화면이 모스 부호표로 정확하게 한다.
     * AI 는 "두드리다 실수한 것 같은데 원래 무슨 낱말이었을까?" 만 맡는다.
     * 스도쿠 힌트와 같은 원칙이다 — 정확한 일은 규칙이, 짐작은 AI 가.
     */
    @PostMapping("/morse-guess")
    public ResponseEntity<Map<String, Object>> guessMorseWord(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        String word = request.get("word");
        if (word == null || word.isBlank()) {
            result.put("ok", false);
            result.put("message", "받아쓴 글자가 없습니다.");
            return ResponseEntity.badRequest().body(result);
        }

        String prompt = "초등학생이 모스 부호를 두드려 다음 글자를 받아썼어. 그런데 실수가 있는 것 같아.\n"
                + "원래 쓰려던 낱말이 무엇일지 하나만 짐작해 줘.\n"
                + "영어 낱말 또는 사람 이름으로 생각하고, 아래 형식으로만 답해. 다른 말은 쓰지 마.\n"
                + "낱말|이유(초등학생에게 한 문장으로, 존댓말)\n\n"
                + "받아쓴 글자: " + word.trim();

        try {
            String text = askGemini(prompt, null);
            if (text == null || text.isBlank()) {
                result.put("ok", false);
                result.put("message", "AI 가 답하지 못했습니다.");
                return ResponseEntity.ok(result);
            }

            // "HELLO|마지막 글자를..." 를 둘로 나눈다
            String[] parts = text.trim().split("\\|", 2);
            result.put("ok", true);
            result.put("guess", parts[0].trim());
            result.put("reason", parts.length > 1 ? parts[1].trim() : "");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("모스 추측 실패: " + e.getMessage());
            result.put("ok", false);
            result.put("message", "AI 선생님을 부르지 못했어요.");
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Gemini 에 지시문을 보내고 답변 텍스트만 꺼내 온다.
     * base64Image 가 null 이면 글자만 보낸다.
     */
    private String askGemini(String prompt, String base64Image) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + apiKey;

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        if (base64Image != null && !base64Image.isBlank()) {
            String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", cleanBase64)));
        }

        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", parts)));

        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

        List candidates = (List) response.getBody().get("candidates");
        if (candidates == null || candidates.isEmpty()) return null;

        Map content = (Map) ((Map) candidates.get(0)).get("content");
        List responseParts = (List) content.get("parts");
        if (responseParts == null || responseParts.isEmpty()) return null;

        return (String) ((Map) responseParts.get(0)).get("text");
    }

    /**
     * 4단계 · 사진 한 장을 보내면 그 안의 주요 사물 이름을 한국어로 돌려준다.
     * body: {"image": "data:image/jpeg;base64,..."}
     * 응답: {"objectName": "스마트폰"}
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, String>> detectObject(@RequestBody Map<String, String> request) {
        // 키가 제대로 들어왔는지 확인하는 용도. 앞 10글자만 찍어 키 전체가 로그에 남지 않게 한다.
        System.out.println("👉 [서버 확인] 현재 적용된 API 키 앞부분: " + (apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) : apiKey));
        Map<String, String> result = new HashMap<>();
        String base64Image = request.get("image");

        if (base64Image == null || base64Image.isEmpty()) {
            result.put("objectName", "이미지 데이터가 전달되지 않았습니다.");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            // 브라우저가 보낸 사진은 "data:image/jpeg;base64,/9j/4AA..." 형태다.
            // Gemini 는 앞머리 설명 없이 순수한 데이터만 받으므로 쉼표 뒤만 잘라 쓴다.
            String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + apiKey;
            Map<String, Object> textPart = Map.of("text", "이 사진 중심에 있는 주요 사물이 무엇인지 한국어로 단어 1~2개로 아주 명확하게 말해줘. 오직 사물 이름만 응답해줘.");
            Map<String, Object> inlineData = Map.of(
                    "mime_type", "image/jpeg",
                    "data", cleanBase64
            );
            Map<String, Object> imagePart = Map.of("inline_data", inlineData);

            Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
            Map<String, Object> body = Map.of("contents", List.of(content));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // API 호출
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            List candidates = (List) response.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map firstCandidate = (Map) candidates.get(0);
                Map contentResp = (Map) firstCandidate.get("content");
                List parts = (List) contentResp.get("parts");
                Map firstPart = (Map) parts.get(0);
                String detectedText = (String) firstPart.get("text");

                result.put("objectName", detectedText.trim().replaceAll("[\"'\n]", ""));
                return ResponseEntity.ok(result);
            } else {
                result.put("objectName", "사물 인식 실패");
                return ResponseEntity.ok(result);
            }

        } catch (HttpClientErrorException e) {
            System.err.println("Gemini API 거절 에러: " + e.getResponseBodyAsString());
            result.put("objectName", "API 키가 올바르지 않거나 구글 요청 실패");
            return ResponseEntity.status(e.getStatusCode()).body(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("objectName", "서버 내부 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}