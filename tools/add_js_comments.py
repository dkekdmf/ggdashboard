"""index.html 안 자바스크립트 함수에 한 줄 설명을 붙여 준다.

이미 설명이 있는 함수는 건드리지 않는다.
함수 선언 줄을 찾아 바로 위에 같은 들여쓰기로 주석을 넣는다.
"""
import pathlib
import re

DOC = {
    # ── 단계 진행 관리
    "unlockStep": "잠겨 있던 단계를 열어 만질 수 있게 한다.",
    "countDone": "지금까지 완료한 단계가 몇 개인지 센다.",
    "refreshProgress": "상단 진행률 숫자와 막대를 새로 그린다.",

    # ── 로그인
    "selectGender": "성별 버튼을 눌렀을 때 고른 것만 강조한다.",
    "handleLogin": "이름·학교·학년을 서버에 남기고 대시보드로 들어간다.",

    # ── 1단계 무드등
    "changeLed": "색 이름으로 무드등을 켠다. MQTT 가 있으면 그쪽으로, 없으면 HTTP 로 보낸다.",
    "understandVoice": "아이가 한 말에서 '무엇을 하라는 것인지'를 찾아낸다.",
    "setVoiceButton": "마이크 버튼 모양을 듣는 중/대기 중으로 바꾼다.",
    "showVoiceResult": "내가 한 말과 AI 의 판단을 화면에 보여 준다.",
    "toggleVoice": "음성 인식을 켜고 끈다. 브라우저에 내장된 기능을 쓴다.",

    # ── 2단계 센서
    "fetchDhtData": "서버에 온·습도를 물어본다. MQTT 로 받고 있으면 쉰다.",

    # ── MQTT
    "setMqttBadge": "헤더의 MQTT 연결 표시등을 바꾼다.",
    "clearMqttLog": "엿보기 창에 쌓인 통신 기록을 지운다.",
    "connectMqtt": "브로커 주소를 서버에서 받아 와 MQTT 에 접속하고 주제를 구독한다.",

    # ── 3단계 반응속도
    "startReactionGame": "아두이노에게 게임을 시작하라고 알린다.",
    "fetchReactionData": "서버에 게임 상태를 물어본다. MQTT 로 받고 있으면 쉰다.",
    "setWebPad": "화면 예선 사각형의 색과 글씨를 바꾼다.",
    "tapWebPad": "예선 사각형을 눌렀을 때, 지금 단계에 맞는 일을 한다.",
    "startWebRound": "빨간불을 켜고 2~5초 뒤 무작위로 초록불로 바꾼다.",
    "finishWebRound": "초록불이 켜진 순간부터 누를 때까지 걸린 시간을 재서 기록한다.",
    "evaluateWebTime": "걸린 시간을 아이들이 알아볼 문장으로 바꾼다.",
    "loadFinalists": "예선 기록에서 결승 진출자를 서버로부터 받아 온다.",
    "renderFinalists": "결승 진출자 목록을 그린다. 아직 안 뛴 선수에게는 도전 버튼을 붙인다.",
    "recordFinalistResult": "그 선수의 결승 기록을 적고, 모두 끝나면 우승자를 발표한다.",
    "saveRecord": "반응속도 기록 한 건을 서버에 저장한다.",
    "switchRankingTab": "순위표를 화면 예선/아두이노 결승으로 바꿔 본다.",
    "loadRanking": "지금 고른 부문의 TOP 10 을 받아 와 그린다.",
    "updateArduinoHeaderUI": "헤더의 아두이노 연결 표시등을 바꾼다.",

    # ── 5단계 조건문
    "toggleSmartRule": "내가 만든 규칙을 작동시키거나 멈춘다.",
    "evaluateSmartHomeRule": "지금 센서 값이 내가 정한 조건에 맞는지 따져 보고, 맞으면 무드등을 바꾼다.",
    "codeFromObjectName": "AI 가 한국어로 답한 사물 이름을 규칙이 알아듣는 코드로 바꾼다.",

    # ── 4단계 AI 카메라
    "showCameraError": "카메라를 켤 수 없는 이유를 화면에 적는다.",
    "openSudokuCamera": "카메라를 스도쿠 읽기 모드로 열어, 문제 사진을 찍게 한다.",
    "openScannerModal": "카메라를 켜고 실시간 감지를 시작한다.",
    "closeScannerModal": "카메라를 끄고 창을 닫는다. 중간에 오류가 나도 반드시 닫히게 했다.",
    "startLiveWatch": "0.7초마다 화면을 살펴 무엇이 보이는지 알아낸다.",
    "stopLiveWatch": "실시간 감지를 멈춘다.",
    "handleLiveDetection": "감지 결과를 화면에 띄우고, 5단계 규칙 엔진에도 알려 준다.",
    "captureAndDetect": "사진을 찍어 서버로 보낸다. 모드에 따라 사물 인식 또는 스도쿠 읽기를 한다.",

    # ── 6단계 블록 프로그램
    "addBlock": "고른 명령 블록을 내 프로그램 맨 뒤에 붙인다.",
    "clearProgram": "만든 프로그램을 모두 지운다.",
    "runProgram": "블록을 위에서 아래로 차례대로 실행하고, 정한 횟수만큼 되풀이한다.",
    "stopProgram": "실행을 중간에 멈춘다.",
    "setCode": "코드 보기 칸에 글을 채워 넣는다.",

    # ── 7단계 스도쿠
    "changeSudokuSize": "칸 크기를 바꾸고 새 문제를 만든다.",
    "newSudoku": "완성된 판을 하나 만든 뒤 일부를 지워 문제로 만든다.",
    "resetSudokuStats": "시도 횟수와 되돌아간 횟수를 0으로 되돌린다.",
    "setSudokuStatus": "스도쿠 상태 글씨를 바꾼다.",
    "solveSudoku": "컴퓨터가 푸는 과정을 눈에 보이게 실행한다.",
    "backtrack": "한 칸에 1부터 넣어 보고 막히면 되돌아간다. 이것이 백트래킹이다.",
    "stopSudoku": "푸는 것을 중간에 멈춘다.",
    "cellName": "칸 위치를 '2번째 줄, 3번째 칸' 처럼 읽기 쉬운 말로 바꾼다.",
    "askHint": "지금 판을 살펴 가장 쉬운 힌트를 찾아 알려 준다.",
    "nextHintLevel": "힌트를 한 단계 더 자세히 알려 준다. 어디를 볼지 → 왜 그런지 → 답.",
    "showHint": "힌트 상자에 글을 띄운다.",
    "closeHint": "힌트 상자를 닫고 강조를 없앤다.",

    # ── 8단계 미로
    "mazeIndex": "가로·세로 위치를 한 줄로 늘어놓은 번호로 바꾼다.",
    "applyMazeLevel": "고른 난이도에 맞춰 미로 크기와 칸 크기를 정한다.",
    "clearMazeWalls": "가운데 벽을 모두 지워 빈 방으로 만든다. 직접 미로를 그릴 때 쓴다.",
    "resetMazeRun": "탐색하며 칠해 둔 색과 숫자를 처음 상태로 되돌린다.",
    "setMazeStatus": "미로 상태 글씨를 바꾼다.",
    "paintCell": "칸 하나의 색만 바꾼다. 전체를 다시 그리지 않아 큰 미로에서도 빠르다.",
    "repaintMaze": "모든 칸의 색을 다시 칠한다.",
    "toggleWall": "누른 칸을 벽으로 만들거나 길로 되돌린다.",
    "mazeNeighbors": "그 칸에서 갈 수 있는 이웃 칸들을 찾는다. 벽은 빼고 돌려준다.",
    "mazeLed": "무드등 연동이 켜져 있으면 결과를 색으로 알린다.",
    "runMaze": "고른 방법으로 길을 찾고, 찾으면 지나온 길을 되짚어 표시한다.",
    "stopMaze": "길 찾기를 중간에 멈춘다.",

    # ── 9단계 가위바위보
    "rpsKey": "바로 앞 두 판을 기억하는 열쇠말을 만든다. AI 는 이것으로 버릇을 찾는다.",
    "playRps": "내가 낸 것과 AI 가 낸 것을 견주어 승패를 가리고, 이번 판을 기억한다.",
    "rpsLed": "무드등 연동이 켜져 있으면 승패를 색으로 알린다.",
    "updateRpsUi": "판수·승패·최근 승률 막대를 새로 그린다.",
    "resetRps": "AI 의 기억을 모두 지운다. 승률이 다시 33% 근처로 떨어진다.",

    # ── 퀴즈
    "buildQuiz": "한 단계의 빈칸 채우기 퀴즈를 화면에 만든다.",
    "answerQuiz": "고른 답을 채점하고, 맞으면 빈칸을 채워 완성된 코드를 보여 준다.",
    "updateQuizCount": "맞힌 퀴즈 개수를 상단에 표시한다.",
    "buildAllQuizzes": "1~9단계 퀴즈를 한꺼번에 만든다.",

    # ── 설문
    "buildSurvey": "10문항 만족도 조사를 화면에 만든다.",
    "surveyButtonClass": "척도 버튼이 골라졌는지에 따라 모양을 정한다.",
    "updateSurveyProgress": "몇 문항에 답했는지 세어 보여 준다.",
    "submitSurvey": "빠진 문항을 확인한 뒤 서버에 제출한다.",
}

path = pathlib.Path('gonggongplan/src/main/resources/static/index.html')
text = path.read_text()
lines = text.split('\n')

DECL = re.compile(r'^(\s*)(?:async\s+)?function\s+(\w+)\s*\(')
out = []
added = 0

for i, line in enumerate(lines):
    m = DECL.match(line)
    if m:
        name = m.group(2)
        indent = m.group(1)
        # 바로 위 3줄 안에 이미 설명이 있으면 넘어간다
        above = [lines[j].strip() for j in range(max(0, i - 3), i)]
        has_doc = any(a.startswith(('*', '/*')) or a.startswith('// ') for a in above)

        if name in DOC and not has_doc:
            out.append(f"{indent}/** {DOC[name]} */")
            added += 1

    out.append(line)

path.write_text('\n'.join(out))
print(f"설명을 붙인 함수: {added}개")
