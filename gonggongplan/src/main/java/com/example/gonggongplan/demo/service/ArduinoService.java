package com.example.gonggongplan.demo.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class ArduinoService {

    private SerialPort comPort;
    private int latestReactionTime = 0; // 최근 측정된 반응 속도 저장

    @PostConstruct
    public void init() {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort p : ports) {
            String name = p.getSystemPortName();
            if (name.contains("usbmodem") || name.contains("usbserial") || name.contains("COM")) {
                comPort = p;
                break;
            }
        }

        if (comPort != null) {
            comPort.setBaudRate(9600); // 아두이노와 통신 속도 일치
            if (comPort.openPort()) {
                System.out.println("🔌 [아두이노 연결 성공] 포트: " + comPort.getSystemPortName());
                setupSerialListener();
            }
        }
    }

    private void setupSerialListener() {
        comPort.addDataListener(new SerialPortDataListener() {
            private StringBuilder buffer = new StringBuilder();

            @Override
            public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;

                byte[] newData = new byte[comPort.bytesAvailable()];
                comPort.readBytes(newData, newData.length);

                for (byte b : newData) {
                    if (b == '\n') {
                        parseSerialData(buffer.toString().trim());
                        buffer.setLength(0);
                    } else if (b != '\r') {
                        buffer.append((char) b);
                    }
                }
            }
        });
    }

    private void parseSerialData(String line) {
        // "REACT:350" 형태로 들어오면 숫자만 추출
        if (line.startsWith("REACT:")) {
            try {
                this.latestReactionTime = Integer.parseInt(line.substring(6).trim());
                System.out.println("⏱️ [반응속도 측정됨]: " + latestReactionTime + "ms");
            } catch (Exception e) {
                System.out.println("데이터 파싱 오류: " + line);
            }
        }
    }

    // 컨트롤러에서 호출하여 값을 가져감
    public int getReactionTime() {
        return latestReactionTime;
    }

    // 웹에서 값을 읽어간 후에는 0으로 초기화 (중복 읽기 방지)
    public void clearReactionTime() {
        this.latestReactionTime = 0;
    }

    @PreDestroy
    public void close() {
        if (comPort != null && comPort.isOpen()) comPort.closePort();
    }
}