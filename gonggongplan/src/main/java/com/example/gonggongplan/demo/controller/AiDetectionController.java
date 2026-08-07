package com.example.gonggongplan.demo.controller; // ⚠️ 본인 프로젝트 패키지명 확인!

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiDetectionController {

    @Value("${gemini.api.key:YOUR_GEMINI_KEY}")
    private String apiKey;

    @PostMapping("/detect")
    public ResponseEntity<Map<String, String>> detectObject(@RequestBody Map<String, String> request) {
        System.out.println("👉 [서버 확인] 현재 적용된 API 키 앞부분: " + (apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) : apiKey));
        Map<String, String> result = new HashMap<>();
        String base64Image = request.get("image");

        if (base64Image == null || base64Image.isEmpty()) {
            result.put("objectName", "이미지 데이터가 전달되지 않았습니다.");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            // Base64 데이터 헤더 (data:image/jpeg;base64,) 제거
            String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 구글 Gemini 1.5 Flash REST API URL
            // AiDetectionController.java
            // AiDetectionController.java
            // ✅ [수정] 내 계정 목록에 존재하는 'gemini-flash-latest' 모델 사용
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