package com.example.gonggongplan.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 프로그램이 시작되는 곳. 여기서 main 이 실행되면 서버 전체가 켜진다.
 *
 * @SpringBootApplication 하나로 아래 일들이 한꺼번에 일어난다.
 *   - 같은 폴더 아래의 @Controller, @Service 같은 부품들을 모두 찾아 준비
 *   - 웹 서버(Tomcat)를 8080 번에서 띄움
 *   - 데이터베이스 연결 준비
 *
 * @EnableScheduling 은 "일정 시간마다 저절로 돌아가는 일"을 켜 준다.
 * MqttGateway 가 0.3초마다 아두이노 상태를 방송하는 데 필요하다.
 */
@EnableScheduling
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}
