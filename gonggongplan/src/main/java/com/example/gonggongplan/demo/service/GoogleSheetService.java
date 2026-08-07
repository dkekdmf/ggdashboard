package com.example.gonggongplan.demo.service;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GoogleSheetService {

    // 🔴 구글 Apps Script 웹 앱 URL
    private static final String GOOGLE_SHEET_WEBHOOK_URL = "https://script.google.com/macros/s/AKfycbyH3FYLhSkIRaQ-KZTckZFTlsB_-FeEtVh6FYVsCPbP9qjsBPPgGWHpKCwZ61T4pKJfrA/exec";

    public void sendLoginInfoToGoogleSheet(String username, String schoolName, String grade, String gender) {
        new Thread(() -> {
            try {
                String jsonPayload = String.format(
                        "{\"username\":\"%s\", \"schoolName\":\"%s\", \"grade\":\"%s\", \"gender\":\"%s\"}",
                        username != null ? username : "-",
                        schoolName != null ? schoolName : "-",
                        grade != null ? grade : "-",
                        gender != null ? gender : "-"
                );

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GOOGLE_SHEET_WEBHOOK_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("📊 [구글 시트 로그인 정보 전송 완료] 응답: " + response.body());

            } catch (Exception e) {
                System.err.println("❌ [구글 시트 전송 실패]: " + e.getMessage());
            }
        }).start();
    }
}