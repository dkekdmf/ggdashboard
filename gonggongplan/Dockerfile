# 공공 코딩 교실 배포 이미지
#
# Alpine 대신 데비안 기반을 쓰는 이유:
#   시리얼 통신 라이브러리(jSerialComm)가 품고 있는 C 라이브러리가
#   Alpine 의 libc 와 맞지 않아 실행 중 오류가 날 수 있다.
FROM eclipse-temurin:17-jre

WORKDIR /app

# bootJar 로 만든 실행 가능한 jar 하나만 담는다
COPY build/libs/demo-0.0.1-SNAPSHOT.jar app.jar

# 8080 웹 / 1883 MQTT / 9001 MQTT(WebSocket, 브라우저가 붙는 문)
# 9001 을 열지 않으면 브라우저가 MQTT 에 붙지 못하고 HTTP 방식으로만 동작한다.
EXPOSE 8080 1883 9001

# 설문 결과 CSV 는 컨테이너를 지우면 사라지므로 볼륨으로 빼는 것을 권한다
VOLUME ["/app/data"]

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
