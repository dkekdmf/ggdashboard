package com.example.gonggongplan.demo.mqtt;

import com.example.gonggongplan.demo.service.ArduinoService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 서버와 브라우저 사이를 MQTT 로 잇는 창구.
 *
 * 지금까지 브라우저는 0.5초마다 서버에 "바뀐 거 있어요?" 하고 물어봤다(폴링).
 * MQTT 를 쓰면 서버가 값이 바뀔 때만 한 번 방송하고, 듣고 있던 브라우저들이 받는다.
 * 아이들이 각자 기기로 접속할 때 요청 수가 크게 줄어든다.
 *
 * 주제(토픽) 이름
 *   gonggong/sensor/dht   서버 → 브라우저 : 온·습도
 *   gonggong/game/state   서버 → 브라우저 : 반응속도 게임 상태
 *   gonggong/status       서버 → 브라우저 : 아두이노 연결 상태
 *   gonggong/cmd/led      브라우저 → 서버 : 무드등 명령
 *   gonggong/cmd/game     브라우저 → 서버 : 게임 시작 명령
 *
 * HTTP 방식도 그대로 남겨 두었다. MQTT 가 안 붙어도 수업은 진행되어야 하기 때문이다.
 */
@Component
public class MqttGateway implements MqttCallback {

    public static final String TOPIC_DHT = "gonggong/sensor/dht";
    public static final String TOPIC_GAME = "gonggong/game/state";
    public static final String TOPIC_STATUS = "gonggong/status";
    public static final String TOPIC_CMD_LED = "gonggong/cmd/led";
    public static final String TOPIC_CMD_GAME = "gonggong/cmd/game";

    @Value("${mqtt.broker.port:1883}")
    private int brokerPort;

    @Value("${mqtt.enabled:true}")
    private boolean enabled;

    private final ArduinoService arduinoService;
    private MqttClient client;

    // 같은 값을 계속 방송하지 않도록 마지막으로 보낸 내용을 기억한다
    private String lastGamePayload = "";
    private String lastDhtPayload = "";
    private String lastStatusPayload = "";

    public MqttGateway(ArduinoService arduinoService) {
        this.arduinoService = arduinoService;
    }

    /** 브로커가 먼저 떠 있어야 붙을 수 있으므로 앱이 완전히 준비된 뒤에 연결한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!enabled) {
            System.out.println("ℹ️ MQTT 연동이 꺼져 있습니다 (mqtt.enabled=false)");
            return;
        }

        try {
            // MemoryPersistence 를 주지 않으면 실행한 폴더에
            // "gonggong-server-tcplocalhost1883" 같은 작업 폴더를 만든다.
            // 우리는 보관 기능을 쓰지 않으므로 메모리만 쓰게 한다.
            client = new MqttClient("tcp://localhost:" + brokerPort, "gonggong-server",
                    new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);

            client.setCallback(this);
            client.connect(options);

            // 브라우저가 보내는 명령을 듣는다
            client.subscribe(TOPIC_CMD_LED);
            client.subscribe(TOPIC_CMD_GAME);

            System.out.println("📡 MQTT 서버 연결 완료 — 명령 구독 중");
        } catch (MqttException e) {
            System.out.println("⚠️ MQTT 연결 실패(HTTP 방식으로 계속 동작합니다): " + e.getMessage());
            client = null;
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    // ───────────────────────────────── 방송하기

    /**
     * 0.3초마다 아두이노 상태를 살펴보고, 바뀐 것만 방송한다.
     * 아두이노가 값을 보낼 때마다 즉시 방송하는 편이 더 좋지만,
     * 그러려면 ArduinoService 를 크게 고쳐야 해서 우선 이 방식으로 둔다.
     */
    private int tick = 0;

    @Scheduled(fixedDelay = 300)
    public void publishChanges() {
        if (!isConnected()) return;

        // 보관(retained) 메시지가 새 구독자에게 전달되지 않는 경우가 있어서,
        // 3초에 한 번은 바뀌지 않았어도 다시 방송한다.
        // 그래야 수업 중간에 접속한 아이 화면도 금방 값이 채워진다.
        if (++tick % 10 == 0) {
            lastDhtPayload = "";
            lastGamePayload = "";
            lastStatusPayload = "";
        }

        var dht = arduinoService.getDhtData();
        if (dht.received()) {
            String payload = String.format("{\"temperature\":%.1f,\"humidity\":%.1f}",
                    dht.temperature(), dht.humidity());
            publishIfChanged(TOPIC_DHT, payload, lastDhtPayload, p -> lastDhtPayload = p);
        }

        var game = arduinoService.getCurrentState();
        String gamePayload = String.format("{\"state\":\"%s\",\"message\":\"%s\",\"time\":%d,\"buttonPressed\":%b}",
                game.state(), escape(game.message()), game.time(), game.buttonPressed());
        publishIfChanged(TOPIC_GAME, gamePayload, lastGamePayload, p -> lastGamePayload = p);

        String statusPayload = String.format("{\"connected\":%b,\"portName\":\"%s\"}",
                arduinoService.isConnected(), arduinoService.getPortName());
        publishIfChanged(TOPIC_STATUS, statusPayload, lastStatusPayload, p -> lastStatusPayload = p);
    }

    private void publishIfChanged(String topic, String payload, String previous, java.util.function.Consumer<String> remember) {
        if (payload.equals(previous)) return;

        remember.accept(payload);
        publish(topic, payload);
    }

    /** retained 로 보내서, 나중에 접속한 브라우저도 마지막 값을 바로 받는다. */
    public void publish(String topic, String payload) {
        if (!isConnected()) return;

        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(0);
            message.setRetained(true);
            client.publish(topic, message);
        } catch (MqttException e) {
            System.out.println("⚠️ MQTT 발행 실패 [" + topic + "]: " + e.getMessage());
        }
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ───────────────────────────────── 듣기

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        System.out.println("📡 MQTT 수신 [" + topic + "] " + payload);

        if (TOPIC_CMD_LED.equals(topic)) {
            handleLedCommand(payload);
        } else if (TOPIC_CMD_GAME.equals(topic)) {
            if (payload.contains("START")) arduinoService.startGame();
        }
    }

    /** {"color":"RED"} 또는 {"r":255,"g":0,"b":0} 두 형태를 받는다. */
    private void handleLedCommand(String payload) {
        String color = extract(payload, "color");

        if (color != null) {
            arduinoService.sendLedCommand(color);
            return;
        }

        Integer r = extractInt(payload, "r");
        Integer g = extractInt(payload, "g");
        Integer b = extractInt(payload, "b");
        if (r != null && g != null && b != null) {
            arduinoService.sendRgb(r, g, b);
        }
    }

    /**
     * 작은 JSON 한 줄에서 값만 꺼낸다.
     * 라이브러리를 더 붙이지 않으려고 직접 찾는 방식을 썼다.
     */
    private String extract(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return null;

        int colon = json.indexOf(':', at);
        int start = json.indexOf('"', colon);
        if (start < 0) return null;

        int end = json.indexOf('"', start + 1);
        return end < 0 ? null : json.substring(start + 1, end);
    }

    private Integer extractInt(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return null;

        int colon = json.indexOf(':', at);
        if (colon < 0) return null;

        StringBuilder digits = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (digits.length() > 0) break;
        }
        return digits.length() == 0 ? null : Integer.parseInt(digits.toString());
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("⚠️ MQTT 연결이 끊어졌습니다: " + cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 보낸 뒤 할 일은 없다
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                System.out.println("📡 MQTT 연결을 닫았습니다.");
            }
        } catch (MqttException e) {
            System.out.println("⚠️ MQTT 종료 중 오류: " + e.getMessage());
        }
    }
}
