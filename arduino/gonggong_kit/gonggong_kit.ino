/*
 * 공공 대시보드 학습 키트 (Arduino Uno 기준)
 * ------------------------------------------------------------------
 * 이 스케치 하나로 웹 대시보드의 1~5단계가 모두 동작합니다.
 *
 *  [배선]
 *    DHT11 데이터핀 ------ D2      (VCC=5V, GND=GND)
 *    RGB LED  R ---------- D9      ※ 공통 캐소드(-) 기준. 각 다리에 220Ω 저항
 *    RGB LED  G ---------- D10
 *    RGB LED  B ---------- D5      ※ D11 이 아닌 이유는 아래 설명 참고
 *    피에조 부저 (+) ------ D7      부저 (-) 는 GND
 *    터치 모듈 SIG -------- D4      (VCC=5V, GND=GND)
 *    RGB LED  공통 -------- GND
 *    푸시버튼 ------------- D3 ↔ GND (내부 풀업 사용, 저항 불필요)
 *
 *  핀을 다르게 꽂았다면 아래 상수만 바꾸면 됩니다.
 *  공통 애노드(+) RGB LED라면 COMMON_ANODE 를 true 로 바꾸세요.
 *
 *  [필요 라이브러리]
 *    라이브러리 매니저에서 "DHT sensor library" (Adafruit) 설치
 *
 *  [시리얼 프로토콜] 9600 baud
 *    보드 → PC :  DHT:24.5,50.0 / GAME:WAIT / GAME:READY / GAME:GO
 *                 GAME:FOUL / GAME:RESULT,342
 *    보드 → PC :  TAP:120        모스를 120ms 동안 두드렸다
 *                 HOLD:START     참기 게임이 시작됐다
 *                 HOLD:RESULT,10800   시작 후 10.8초에 눌렀다
 *                 HOLD:CANCEL    너무 오래 안 눌러서 취소됐다
 *    PC → 보드 :  LED:255,0,0 / GAME:START / BUZZ:1000,200 / BUZZ:OFF
 *                 PLAY:.-..      모스 부호를 소리로 들려주기
 *                 MSOUND:0 / 1   두드릴 때 나는 소리 끄기 / 켜기 (기본 꺼짐)
 *                 HOLD:START,1   참기 게임 시작 (뒤의 1 은 시작 소리 켜기, 0 이면 조용히)
 */



#include <DHT.h>

// ─────────────────────────────── 설정 (여기만 바꾸면 됩니다)
const uint8_t PIN_DHT    = 2;
const uint8_t PIN_BUTTON = 3;
const uint8_t PIN_B      = 5;    // 파란색. D11 이 아니라 D5 인 것이 중요하다(아래 설명)
const uint8_t PIN_BUZZER = 7;    // 피에조 부저
const uint8_t PIN_R      = 9;
const uint8_t PIN_G      = 10;

// 모스 부호를 두드릴 입력.
// 터치 모듈을 아직 안 꽂았으면 USE_TOUCH 를 false 로 두면 택트스위치로도 두드릴 수 있다.
const uint8_t PIN_TOUCH  = 4;
const bool USE_TOUCH     = true;

// ⚠️ 부저와 LED 가 서로 방해하지 않게 하는 규칙
//
// 소리를 내는 tone() 은 내부 타이머 하나를 독차지하는데,
// 그 타이머가 D3 · D11 번 핀의 밝기 조절(analogWrite)과 같은 것이다.
// 그래서 파란색을 D11 에 두면 소리가 나는 동안 파란색이 흔들린다.
// 지금처럼 파란색을 D5 에 두면 서로 겹치지 않는다. D11 은 비워 두는 편이 좋다.
//
// 부저는 아무 디지털 핀이나 괜찮다(밝기 조절을 쓰지 않으므로).

const bool COMMON_ANODE  = false;   // 공통 애노드 LED면 true
#define DHT_TYPE DHT11              // DHT22를 쓴다면 DHT22 로 변경

const unsigned long DHT_INTERVAL   = 2000;  // 온·습도 전송 주기(ms)
const unsigned long READY_MIN      = 2000;  // 빨간불 최소 유지 시간
const unsigned long READY_MAX      = 5000;  // 빨간불 최대 유지 시간
const unsigned long RESULT_HOLD    = 3000;  // 결과를 보여주는 시간

// 초록불이 켜졌는데 아무도 안 누르면 여기서 끝낸다.
// 시간 제한이 없으면 게임이 영영 안 끝나고, 그동안 무드등 명령이 무시된다.
//
// 5초면 반응속도 게임에는 넉넉하다(보통 0.2~0.5초 안에 누른다).
// 실수로 스위치를 건드렸을 때 무드등이 빨리 돌아오는 쪽을 택했다.
const unsigned long GO_TIMEOUT     = 5000;
const unsigned long DEBOUNCE_MS    = 30;    // 버튼 채터링 제거

// ── 참기 게임 (3단계)
// 시작 신호를 듣고 정해진 시간만큼 마음속으로 센 뒤 버튼을 누른다.
const unsigned int TONE_HOLD_START = 900;    // 시작 신호음
const unsigned long HOLD_TIMEOUT   = 40000;  // 이 시간까지 안 누르면 그냥 끝낸다

// 게임 상황마다 내는 소리 (음 높이 Hz, 길이 ms)
const unsigned int TONE_READY  = 440;   // 빨간불 — 낮게 한 번
const unsigned int TONE_GO     = 1200;  // 초록불 — 높고 짧게 (지금 누르라는 신호)
const unsigned int TONE_FOUL   = 200;   // 반칙 — 낮고 길게
const unsigned int TONE_RESULT = 1600;  // 측정 완료 — 맑은 소리

// ── 모스 부호 (10단계)
const unsigned int TONE_MORSE   = 700;   // 두드릴 때 나는 음
const unsigned long TAP_MIN_MS  = 25;    // 이보다 짧으면 실수로 스친 것으로 본다
const unsigned long TAP_MAX_MS  = 3000;  // 이보다 길면 손을 얹어 둔 것으로 본다

// 두드릴 때마다 소리가 나면 교실이 시끄러워서 기본은 꺼 둔다.
// 소리가 없어도 파란불이 켜지므로 두드린 것은 눈으로 확인된다.
// 웹에서 MSOUND:1 을 보내면 켜지고, MSOUND:0 이면 다시 꺼진다.
bool morseSoundOn = false;

// ─────────────────────────────── 내부 상태
DHT dht(PIN_DHT, DHT_TYPE);

enum GameState { WAIT, READY, GO, DONE };
GameState gameState = WAIT;

unsigned long stateEnteredAt = 0;   // 현재 상태로 들어온 시각
unsigned long readyDuration  = 0;   // 이번 판의 빨간불 지속 시간
unsigned long goStartedAt    = 0;   // 초록불이 켜진 시각

// 무드등(5단계 스마트홈)에서 지정한 색. 게임이 끝나면 이 색으로 되돌아간다.
uint8_t moodR = 0, moodG = 0, moodB = 0;

unsigned long lastDhtSentAt = 0;

// 버튼 디바운스
bool lastRawPressed   = false;
bool stablePressed    = false;
unsigned long lastBounceAt = 0;
unsigned long lastPressAt  = 0;   // 실제로 눌린 순간 (반응시간 계산 기준)

// 참기 게임 진행 상태
bool holdOn = false;
unsigned long holdStartedAt = 0;

String rxBuffer = "";

// ─────────────────────────────── LED 헬퍼
void writeRgb(uint8_t r, uint8_t g, uint8_t b) {
  if (COMMON_ANODE) {
    analogWrite(PIN_R, 255 - r);
    analogWrite(PIN_G, 255 - g);
    analogWrite(PIN_B, 255 - b);
  } else {
    analogWrite(PIN_R, r);
    analogWrite(PIN_G, g);
    analogWrite(PIN_B, b);
  }
}

void showMoodColor() {
  writeRgb(moodR, moodG, moodB);
}

// ─────────────────────────────── 부저 헬퍼
//
// tone() 은 길이를 함께 주면 그 시간이 지날 때까지 기다리지 않고 바로 돌아온다.
// 그래서 소리를 내는 동안에도 게임과 센서가 멈추지 않는다.
// delay() 로 소리를 내면 그 사이 버튼 누름을 놓치므로 쓰지 않는다.
void beep(unsigned int hz, unsigned long ms) {
  tone(PIN_BUZZER, hz, ms);
}

void beepOff() {
  noTone(PIN_BUZZER);
}

// ─────────────────────────────── 게임 상태 전환
void enterState(GameState next) {
  gameState = next;
  stateEnteredAt = millis();

  switch (next) {
    case WAIT:
      showMoodColor();
      beepOff();
      Serial.println("GAME:WAIT");
      break;

    case READY:
      readyDuration = random(READY_MIN, READY_MAX);
      writeRgb(255, 0, 0);            // 빨간불
      beep(TONE_READY, 150);          // 낮게 한 번 — "준비"
      Serial.println("GAME:READY");
      break;

    case GO:
      goStartedAt = millis();
      writeRgb(0, 255, 0);            // 초록불
      beep(TONE_GO, 80);              // 높고 짧게 — 눈을 안 봐도 알 수 있다
      Serial.println("GAME:GO");
      break;

    case DONE:
      break;
  }
}

// ─────────────────────────────── 택트스위치 (눌린 순간 1회만 true)
bool buttonJustPressed() {
  bool raw = (digitalRead(PIN_BUTTON) == LOW);   // 내부 풀업이라 눌리면 LOW

  if (raw != lastRawPressed) {
    lastRawPressed = raw;
    lastBounceAt = millis();     // 신호가 흔들리기 시작한 시점 = 손가락이 닿은 시점
    return false;
  }

  if (millis() - lastBounceAt < DEBOUNCE_MS) return false;

  if (raw != stablePressed) {
    stablePressed = raw;

    // 웹에서 배선이 제대로 됐는지 눈으로 확인할 수 있도록 눌림 상태를 알려준다
    Serial.println(stablePressed ? "BTN:1" : "BTN:0");

    if (stablePressed) {
      // 채터링이 가라앉기를 기다린 30ms 만큼 늦게 재면 기록이 부풀려지므로,
      // 신호가 처음 흔들린 시점을 실제로 누른 시각으로 삼는다.
      lastPressAt = lastBounceAt;
      return true;
    }
  }
  return false;
}

// ─────────────────────────────── PC에서 온 명령 처리
void handleCommand(String line) {
  line.trim();
  if (line.length() == 0) return;

  if (line.startsWith("LED:")) {
    String payload = line.substring(4);
    int c1 = payload.indexOf(',');
    int c2 = payload.indexOf(',', c1 + 1);
    if (c1 < 0 || c2 < 0) return;

    moodR = payload.substring(0, c1).toInt();
    moodG = payload.substring(c1 + 1, c2).toInt();
    moodB = payload.substring(c2 + 1).toInt();

    // 게임 중에는 신호등 색이 우선. 대기 중일 때만 즉시 반영한다.
    if (gameState == WAIT) showMoodColor();

  } else if (line.startsWith("GAME:START")) {
    if (gameState == WAIT) enterState(READY);

  } else if (line.startsWith("HOLD:START")) {
    // 참기 게임 시작. 반응속도 게임이 도는 중이면 받지 않는다.
    // "HOLD:START,1" 처럼 뒤에 1 이 붙어 오면 시작 소리를 낸다.
    if (gameState == WAIT && !holdOn) {
      startHold(line.indexOf(",1") > 0);
    }

  } else if (line.startsWith("MSOUND:")) {
    // 두드릴 때 소리를 켜고 끈다. MSOUND:1 = 켜기, MSOUND:0 = 끄기
    //
    // 아두이노의 trim() 은 자바와 달라서 값을 돌려주지 않고 자기 자신을 고친다.
    // 그래서 substring(...).trim().startsWith(...) 처럼 이어 쓸 수 없고, 한 줄씩 나눠야 한다.
    String flag = line.substring(7);
    flag.trim();
    morseSoundOn = flag.startsWith("1");
    if (!morseSoundOn) beepOff();
    Serial.print("MSOUND:");
    Serial.println(morseSoundOn ? "1" : "0");

  } else if (line.startsWith("PLAY:")) {
    // 웹이 보낸 모스 부호를 들려준다 (받아쓰기 미션)
    playMorse(line.substring(5));

  } else if (line.startsWith("BUZZ:")) {
    // 웹에서 소리를 내게 한다.
    //   BUZZ:1000,200  → 1000Hz 로 0.2초
    //   BUZZ:OFF       → 소리 끄기
    String payload = line.substring(5);

    if (payload.startsWith("OFF")) {
      beepOff();
      return;
    }

    int comma = payload.indexOf(',');
    if (comma < 0) return;

    unsigned int hz = payload.substring(0, comma).toInt();
    unsigned long ms = payload.substring(comma + 1).toInt();

    // 사람이 들을 수 있는 범위를 크게 벗어난 값은 무시한다
    if (hz < 50 || hz > 5000) return;
    if (ms == 0 || ms > 3000) ms = 200;

    beep(hz, ms);
  }
}

// ─────────────────────────────── 모스 부호 두드리기 (10단계)
//
// 하는 일은 단순하다.
//   누르는 동안 → LED 를 켜고 부저를 울린다 (곧바로 반응해야 재미있다)
//   손을 떼면   → 몇 밀리초 눌렸는지 "TAP:120" 으로 보낸다
//
// 점(·)인지 선(−)인지는 아두이노가 정하지 않는다.
// 아이마다 손 빠르기가 달라서, 그 기준은 웹에서 아이에 맞춰 조정한다.

bool morseDown = false;             // 지금 누르고 있는지
unsigned long morseDownAt = 0;      // 누르기 시작한 시각
unsigned long morseBounceAt = 0;    // 채터링 제거용
bool morseLastRaw = false;

/** 두드림 입력이 지금 눌려 있는지 읽는다. */
bool morseRawPressed() {
  if (USE_TOUCH) {
    // 터치 모듈(TTP223)은 손이 닿으면 HIGH 를 낸다
    return digitalRead(PIN_TOUCH) == HIGH;
  }
  // 택트스위치는 내부 풀업이라 눌리면 LOW
  return digitalRead(PIN_BUTTON) == LOW;
}

void updateMorse() {
  // 게임 중에는 LED 가 신호등으로 쓰이므로 모스는 쉰다 (색이 뒤엉키지 않게)
  if (gameState != WAIT) return;

  bool raw = morseRawPressed();

  // 신호가 흔들리는 동안은 판단을 미룬다
  if (raw != morseLastRaw) {
    morseLastRaw = raw;
    morseBounceAt = millis();
    return;
  }
  if (millis() - morseBounceAt < DEBOUNCE_MS) return;

  if (raw && !morseDown) {
    // 막 누르기 시작했다 → 즉시 소리와 빛으로 반응
    morseDown = true;
    morseDownAt = millis();
    writeRgb(0, 0, 255);                 // 파란불은 항상 켠다 (소리를 꺼도 보이도록)
    if (morseSoundOn) {
      tone(PIN_BUZZER, TONE_MORSE);      // 길이를 주지 않으면 끌 때까지 계속 울린다
    }

  } else if (!raw && morseDown) {
    // 손을 뗐다 → 누른 시간을 재서 보낸다
    morseDown = false;
    unsigned long held = millis() - morseDownAt;

    beepOff();
    showMoodColor();

    if (held >= TAP_MIN_MS && held <= TAP_MAX_MS) {
      Serial.print("TAP:");
      Serial.println(held);
    }
  }
}

/**
 * 웹이 보낸 모스 부호를 소리와 빛으로 들려준다. (받아쓰기 미션용)
 * 예: PLAY:.-.. → 점·선·점·점
 *
 * 여기서는 delay() 를 쓴다. 들려주는 동안은 아이가 듣기만 하면 되고,
 * 순서대로 또박또박 울려야 받아쓸 수 있기 때문이다.
 */
void playMorse(String code) {
  const unsigned long DOT = 200;   // 점 길이
  const unsigned long DASH = 600;  // 선 길이 (점의 3배가 표준)
  const unsigned long GAP = 200;   // 기호 사이 쉼

  for (unsigned int i = 0; i < code.length(); i++) {
    char c = code.charAt(i);
    unsigned long dur = 0;

    if (c == '.') dur = DOT;
    else if (c == '-') dur = DASH;
    else if (c == ' ') { delay(GAP * 3); continue; }   // 글자 사이는 더 길게 쉰다
    else continue;

    writeRgb(0, 0, 255);
    tone(PIN_BUZZER, TONE_MORSE);
    delay(dur);

    beepOff();
    showMoodColor();
    delay(GAP);
  }
  Serial.println("PLAY:DONE");
}

void readSerial() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      handleCommand(rxBuffer);
      rxBuffer = "";
    } else if (c != '\r') {
      if (rxBuffer.length() < 64) rxBuffer += c;   // 버퍼 폭주 방지
    }
  }
}

// ─────────────────────────────── 온·습도 주기 전송
void sendDhtIfDue() {
  if (millis() - lastDhtSentAt < DHT_INTERVAL) return;
  lastDhtSentAt = millis();

  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) return;   // 읽기 실패한 판은 그냥 건너뛴다

  Serial.print("DHT:");
  Serial.print(t, 1);
  Serial.print(",");
  Serial.println(h, 1);
}

// ─────────────────────────────── 참기 게임 진행 (3단계)
//
// 시작 신호를 내고, 버튼이 눌릴 때까지 기다린 뒤 걸린 시간을 보낸다.
// 화면에 시계를 보여 주지 않는 것이 이 게임의 핵심이라,
// 진행 중에는 아두이노도 아무 신호를 내지 않는다.
void startHold(bool withSound) {
  holdOn = true;
  holdStartedAt = millis();

  // 파란불은 항상 켠다. 소리를 꺼도 시작 시점을 알 수 있어야 하기 때문이다.
  writeRgb(0, 0, 255);

  // 교실에서 여러 아이가 하면 시끄러워서, 소리는 웹에서 고르게 했다.
  if (withSound) beep(TONE_HOLD_START, 120);

  Serial.println("HOLD:START");
}

void updateHold(bool pressed) {
  if (pressed) {
    unsigned long held = millis() - holdStartedAt;

    holdOn = false;
    showMoodColor();
    Serial.print("HOLD:RESULT,");
    Serial.println(held);
    return;
  }

  // 너무 오래 안 누르면 그냥 끝낸다 (버튼을 잊었거나 자리를 떠난 경우)
  if (millis() - holdStartedAt >= HOLD_TIMEOUT) {
    holdOn = false;
    showMoodColor();
    Serial.println("HOLD:CANCEL");
  }
}

// ─────────────────────────────── 반응속도 게임 진행
void updateGame(bool pressed) {
  switch (gameState) {
    case WAIT:
      // 대기 중에 버튼을 누르면 게임 시작
      if (pressed) enterState(READY);
      break;

    case READY:
      if (pressed) {                       // 초록불 전에 누름 = 반칙
        Serial.println("GAME:FOUL");
        writeRgb(255, 0, 0);
        beep(TONE_FOUL, 500);              // 낮고 길게 — 틀렸다는 느낌
        enterState(DONE);
      } else if (millis() - stateEnteredAt >= readyDuration) {
        enterState(GO);
      }
      break;

    case GO:
      // 아무도 안 누르면 스스로 대기 상태로 돌아간다.
      // 그래야 무드등이 다시 말을 듣는다.
      if (millis() - goStartedAt >= GO_TIMEOUT) {
        Serial.println("GAME:TIMEOUT");
        enterState(WAIT);
        break;
      }

      if (pressed) {
        // 초록불이 켜지기 직전에 누른 아주 빠듯한 경우 음수가 되지 않도록 보정
        unsigned long reaction = (lastPressAt > goStartedAt) ? (lastPressAt - goStartedAt) : 1;
        Serial.print("GAME:RESULT,");
        Serial.println(reaction);
        beep(TONE_RESULT, 200);            // 맑은 소리 — 측정 완료
        enterState(DONE);
      }
      break;

    case DONE:
      // 결과를 잠깐 보여준 뒤 대기 상태로 복귀
      if (millis() - stateEnteredAt >= RESULT_HOLD) enterState(WAIT);
      break;
  }
}

// ─────────────────────────────── 표준 진입점
void setup() {
  Serial.begin(9600);

  pinMode(PIN_BUTTON, INPUT_PULLUP);
  pinMode(PIN_R, OUTPUT);
  pinMode(PIN_G, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_TOUCH, INPUT);       // 터치 모듈은 스스로 HIGH/LOW 를 내주므로 풀업이 필요 없다
  writeRgb(0, 0, 0);

  dht.begin();

  // 켜졌다는 것을 소리로 알린다. 배선이 맞는지 바로 확인할 수 있다.
  beep(880, 120);

  // 매번 같은 순서로 빨간불이 꺼지면 아이들이 타이밍을 외워버리므로 시드를 섞는다
  randomSeed(analogRead(A0));

  enterState(WAIT);
}

void loop() {
  readSerial();

  // 버튼 읽기는 한 곳에서만 한다. 여러 곳에서 읽으면 같은 누름을 두 번 세게 된다.
  bool pressed = buttonJustPressed();

  // 참기 게임 중에는 다른 활동이 끼어들지 않게 한다.
  // (반응속도 신호등이 켜지거나 모스가 글자를 만들면 게임이 엉킨다)
  // 온·습도 전송은 계속 돌려도 게임에 영향이 없어서 함께 둔다.
  sendDhtIfDue();

  if (holdOn) {
    updateHold(pressed);
    return;
  }

  updateMorse();
  updateGame(pressed);
}
