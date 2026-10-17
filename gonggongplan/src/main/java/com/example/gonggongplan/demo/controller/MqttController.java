package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.mqtt.EmbeddedMqttBroker;
import com.example.gonggongplan.demo.mqtt.MqttGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 브라우저가 MQTT 에 붙기 전에 "브로커가 떠 있는지, 몇 번 문으로 들어가면 되는지"를 물어보는 곳.
 * 주소를 화면에 적어 두지 않고 서버에서 받아 오면, 설정을 바꿔도 화면을 고칠 필요가 없다.
 */
@RestController
@RequestMapping("/api/mqtt")
@RequiredArgsConstructor
public class MqttController {

    private final EmbeddedMqttBroker broker;   // 브로커(중개소)가 떠 있는지 아는 쪽
    private final MqttGateway gateway;         // 서버가 브로커에 붙었는지 아는 쪽

    /**
     * GET /api/mqtt/info
     *
     * 브라우저는 이 정보를 받아서 판단한다.
     *   brokerRunning 이 false → MQTT 를 포기하고 기존 HTTP 방식으로 동작
     *   true → ws://(주소):(websocketPort) 로 접속해 방송을 듣기 시작
     *
     * 토픽 이름까지 함께 내려주는 이유는, 화면 코드에 주소와 토픽을 적어 두지 않기 위해서다.
     * 서버에서 이름을 바꿔도 화면은 고칠 필요가 없다.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();

        info.put("brokerRunning", broker.isRunning());
        info.put("serverConnected", gateway.isConnected());
        info.put("websocketPort", broker.getWebsocketPort());

        Map<String, String> topics = new HashMap<>();
        topics.put("dht", MqttGateway.TOPIC_DHT);
        topics.put("game", MqttGateway.TOPIC_GAME);
        topics.put("status", MqttGateway.TOPIC_STATUS);
        topics.put("cmdLed", MqttGateway.TOPIC_CMD_LED);
        topics.put("cmdGame", MqttGateway.TOPIC_CMD_GAME);
        info.put("topics", topics);

        return ResponseEntity.ok(info);
    }
}
