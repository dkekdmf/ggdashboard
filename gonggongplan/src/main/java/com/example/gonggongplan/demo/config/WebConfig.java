package com.example.gonggongplan.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 브라우저의 "다른 주소로는 요청을 못 보낸다"는 보안 규칙(CORS)을 풀어 주는 설정.
 *
 * 브라우저는 기본적으로 지금 보고 있는 주소와 다른 주소로는 요청을 막는다.
 * 교실에서 선생님 노트북 주소(예: http://192.168.0.10:8080)로 아이들이 접속하거나,
 * 화면 파일을 따로 열어 두고 시험할 때 이 규칙에 걸려 요청이 실패한다.
 *
 * 교실 안에서만 쓰는 서버라 모든 출처를 허용해 두었다.
 * 인터넷에 공개하는 서버라면 allowedOrigins 에 실제 주소만 적어야 한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")          // /api 로 시작하는 주소에만 적용
                .allowedOrigins("*")            // 어느 주소에서 와도 허용 (교실용)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
