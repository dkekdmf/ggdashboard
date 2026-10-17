package com.example.gonggongplan.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이 노트북이 교실 안에서 어떤 주소로 보이는지 알려 준다.
 *
 * 아이들이 휴대폰으로 접속하려면 주소를 알아야 하는데,
 * 숫자를 하나하나 입력하게 하면 번거롭고 오타도 난다.
 * 그래서 이 정보로 QR 코드를 그려서 찍기만 하면 되도록 한다.
 *
 * 주소를 화면에 적어 두지 않고 서버가 알려 주는 이유는,
 * 학교 와이파이에서 IP 가 자주 바뀌기 때문이다.
 * 이렇게 하면 IP 가 바뀌어도 화면만 새로고침하면 QR 이 저절로 갱신된다.
 */
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    @Value("${server.port:8080}")
    private int serverPort;

    /** Node-RED 는 따로 도는 프로그램이라 포트만 설정으로 받아 둔다. */
    @Value("${nodered.port:1880}")
    private int noderedPort;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        String ip = findLanAddress();

        Map<String, Object> result = new HashMap<>();
        result.put("ip", ip);
        result.put("found", ip != null);

        if (ip != null) {
            // 수업에서 쓰는 주소들을 미리 만들어 준다. 화면은 이걸 QR 로 그리기만 하면 된다.
            Map<String, String> urls = new LinkedHashMap<>();
            urls.put("lesson1", "http://" + ip + ":" + serverPort + "/");
            urls.put("lesson2", "http://" + ip + ":" + serverPort + "/lesson2.html");
            urls.put("nodered", "http://" + ip + ":" + noderedPort + "/ui");
            result.put("urls", urls);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 교실 와이파이에서 쓰는 주소를 찾는다.
     *
     * 노트북에는 주소가 여러 개 있다(와이파이, 가상 네트워크, 도커 등).
     * 그중 아이들 휴대폰이 닿을 수 있는 것만 골라야 한다.
     *   · 꺼져 있는 것 제외
     *   · 자기 자신(127.0.0.1) 제외
     *   · IPv6 제외 (QR 로 만들면 너무 길고, 휴대폰이 잘 못 붙는다)
     *   · 192.168.x.x 처럼 집·학교 안에서만 쓰는 주소만 채택
     */
    private String findLanAddress() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;

                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)) continue;
                    if (addr.isLoopbackAddress()) continue;

                    if (addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ 네트워크 주소를 찾지 못했습니다: " + e.getMessage());
        }
        return null;
    }
}
