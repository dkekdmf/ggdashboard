package com.example.gonggongplan.demo.service;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 로그인한 학생 정보를 구글 스프레드시트로 보내는 일을 한다.
 *
 * 데이터베이스에도 저장하지만, 구글 시트에 함께 쌓으면 선생님이
 * 프로그램을 몰라도 브라우저에서 바로 명단을 확인할 수 있다.
 *
 * 받는 쪽은 구글 Apps Script 로 만든 작은 웹 앱이고,
 * 그 스크립트가 받은 내용을 시트에 한 줄씩 적어 준다.
 */
@Service
public class GoogleSheetService {

    /** 구글 Apps Script 로 만들어 둔 받는 곳 주소. 스크립트를 다시 배포하면 이 주소가 바뀐다. */
    private static final String GOOGLE_SHEET_WEBHOOK_URL = "https://script.google.com/macros/s/AKfycbyH3FYLhSkIRaQ-KZTckZFTlsB_-FeEtVh6FYVsCPbP9qjsBPPgGWHpKCwZ61T4pKJfrA/exec";

    /**
     * 학생 정보를 구글 시트로 보낸다.
     *
     * 별도 스레드(new Thread)에서 보내는 이유가 중요하다.
     * 구글 서버가 느리게 답하면 그동안 아이 화면이 멈춰 버리기 때문이다.
     * 따로 보내 두고 로그인은 곧바로 끝내서, 전송이 늦거나 실패해도 수업이 막히지 않게 했다.
     */
    public void sendLoginInfoToGoogleSheet(String username, String schoolName, String grade, String gender) {
        new Thread(() -> {
            try {
                // 값이 비어 있으면 "-" 로 채운다. 시트의 칸이 밀리지 않게 하려는 것이다.
                String jsonPayload = String.format(
                        "{\"username\":\"%s\", \"schoolName\":\"%s\", \"grade\":\"%s\", \"gender\":\"%s\"}",
                        username != null ? username : "-",
                        schoolName != null ? schoolName : "-",
                        grade != null ? grade : "-",
                        gender != null ? gender : "-"
                );

                // 구글 Apps Script 는 실제 처리 주소로 한 번 넘기므로(리다이렉트) 따라가도록 설정한다
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
                // 전송 실패는 수업 진행과 무관하므로 기록만 남기고 넘어간다
                System.err.println("❌ [구글 시트 전송 실패]: " + e.getMessage());
            }
        }).start();
    }
}