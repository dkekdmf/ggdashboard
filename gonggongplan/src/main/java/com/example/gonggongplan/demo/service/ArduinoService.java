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
    private double latestTemperature = 24.0; // 기본값
    private double latestHumidity = 50.0;    // 기본값

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
            comPort.setBaudRate(9600);
            if (comPort.openPort()) {
                System.out.println("🔌 [아두이노 연결 성공] 포트명: " + comPort.getSystemPortName());

                // 시리얼 수신 이벤트 리스너 등록
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
                        parseDhtData(buffer.toString().trim());
                        buffer.setLength(0);
                    } else if (b != '\r') {
                        buffer.append((char) b);
                    }
                }
            }
        });
    }

    private void parseDhtData(String line) {
        // 데이터 형식: "DHT:24.5,50.0"
        if (line.startsWith("DHT:")) {
            try {
                String[] parts = line.substring(4).split(",");
                if (parts.length == 2) {
                    this.latestTemperature = Double.parseDouble(parts[0]);
                    this.latestHumidity = Double.parseDouble(parts[1]);
                    System.out.println("🌡️ [온습도 수신] 온도: " + latestTemperature + "°C / 습도: " + latestHumidity + "%");
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean sendCommand(String command) {
        if (comPort != null && comPort.isOpen()) {
            byte[] bytes = command.getBytes();
            comPort.writeBytes(bytes, bytes.length);
            return true;
        }
        return false;
    }

    public double getLatestTemperature() { return latestTemperature; }
    public double getLatestHumidity() { return latestHumidity; }

    @PreDestroy
    public void close() {
        if (comPort != null && comPort.isOpen()) comPort.closePort();
    }
}