package com.example.gonggongplan.demo.mqtt;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 앱 안에서 함께 도는 MQTT 브로커(중개소).
 *
 * 브로커를 따로 설치해 띄우게 하면 수업 전 준비물이 늘고 고장 날 지점도 늘어난다.
 * 그래서 스프링 앱이 켜질 때 브로커도 같이 켜지도록 했다. 선생님은 앱 하나만 실행하면 된다.
 *
 * 입구가 두 개인 이유:
 *   1883 — 스프링 서버가 붙는 일반 MQTT
 *   9001 — 브라우저가 붙는 WebSocket (브라우저는 일반 MQTT를 쓸 수 없다)
 */
@Component
public class EmbeddedMqttBroker {

    @Value("${mqtt.broker.port:1883}")
    private int brokerPort;

    @Value("${mqtt.broker.websocket-port:9001}")
    private int websocketPort;

    @Value("${mqtt.broker.enabled:true}")
    private boolean enabled;

    private Server broker;

    @PostConstruct
    public void start() {
        if (!enabled) {
            System.out.println("ℹ️ MQTT 브로커가 꺼져 있습니다 (mqtt.broker.enabled=false)");
            return;
        }

        Properties config = new Properties();
        config.setProperty("port", String.valueOf(brokerPort));
        config.setProperty("websocket_port", String.valueOf(websocketPort));
        config.setProperty("host", "0.0.0.0");

        // 교실 안에서만 쓰는 브로커라 비밀번호 없이 연다.
        // 외부에 공개할 때는 반드시 계정을 붙여야 한다.
        config.setProperty("allow_anonymous", "true");
        config.setProperty("persistence_enabled", "false");

        try {
            broker = new Server();
            broker.startServer(new MemoryConfig(config));
            System.out.println("📡 MQTT 브로커 시작 (MQTT " + brokerPort + " / WebSocket " + websocketPort + ")");

        } catch (Exception e) {
            // 브로커가 안 떠도 앱은 반드시 떠야 한다.
            //
            // IOException 만 잡았더니, 포트가 이미 쓰이고 있을 때 Netty 가 다른 종류의
            // 예외를 던져서 앱 전체가 시작되지 않았다(앱을 두 번 실행하면 늘 그렇게 된다).
            // MQTT 가 없어도 수업은 HTTP 방식으로 그대로 진행되므로 여기서 막고 넘어간다.
            broker = null;

            System.out.println("⚠️ MQTT 브로커를 시작하지 못했습니다: " + e.getMessage());
            System.out.println("   (앱은 그대로 동작합니다. 화면은 HTTP 방식으로 통신합니다)");
            System.out.println("   같은 앱이 이미 켜져 있지는 않은지 확인해 보세요:");
            System.out.println("     lsof -nP -iTCP:" + brokerPort + " -sTCP:LISTEN");
        }
    }

    public boolean isRunning() {
        return broker != null;
    }

    public int getWebsocketPort() {
        return websocketPort;
    }

    @PreDestroy
    public void stop() {
        if (broker != null) {
            broker.stopServer();
            System.out.println("📡 MQTT 브로커를 닫았습니다.");
        }
    }
}
