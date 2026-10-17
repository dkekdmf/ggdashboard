"""3단계 화면에 참기 게임을 넣는다. index.html 에만 있으면 된다 (3단계는 1교시)."""
import pathlib

path = pathlib.Path('gonggongplan/src/main/resources/static/index.html')
s = path.read_text()

# ══════════════════════════════════════════════ 화면

HTML = '''        <!-- ⏱️ 참기 게임 — 빠르기가 아니라 정확성을 잰다 -->
        <div class="bg-purple-50 border border-purple-200 rounded-2xl p-4 space-y-3">
            <div class="flex flex-wrap justify-between items-center gap-2">
                <span class="text-xs font-black text-purple-700">⏱️ 참기 게임 · 시간 맞히기</span>
                <div class="flex items-center gap-2">
                    <span class="text-[11px] font-bold text-purple-500">목표</span>
                    <select id="hold-target" class="p-1.5 rounded-lg border border-purple-200 text-[11px] font-bold text-slate-700 bg-white">
                        <option value="5">5초</option>
                        <option value="10" selected>10초</option>
                        <option value="15">15초</option>
                    </select>
                </div>
            </div>

            <p class="text-[11px] text-purple-500 leading-relaxed">
                시작 소리가 나면 <b>마음속으로 세어</b> 정확히 그때 누르세요.
                시계를 보면 안 돼요! 반응속도와 달리 <b>빠르기가 아니라 정확성</b>을 겨룹니다.
            </p>

            <div id="hold-box" class="p-5 rounded-2xl bg-white border-2 border-purple-200 text-center">
                <p id="hold-state" class="text-sm font-black text-purple-700">시작 버튼을 누르세요</p>
                <div id="hold-result" class="hidden mt-2">
                    <div class="text-3xl font-black text-purple-700"><span id="hold-actual">-</span>초</div>
                    <p class="text-xs font-bold text-slate-500 mt-1">
                        목표 <span id="hold-goal">10</span>초 · 오차 <span id="hold-error" class="font-black">-</span>
                    </p>
                    <p id="hold-grade" class="text-sm font-black mt-1">-</p>
                </div>
            </div>

            <button id="hold-btn" onclick="holdAction()" class="w-full py-3 bg-purple-600 hover:bg-purple-700 text-white font-black rounded-xl text-sm transition active:scale-95">
                ▶️ 시작하기
            </button>

            <div id="hold-history" class="hidden bg-white rounded-xl p-3 space-y-1">
                <span class="text-[11px] font-black text-purple-600">내 기록 (오차가 줄어드나요?)</span>
                <div id="hold-list" class="font-mono text-[11px] text-slate-600 space-y-0.5"></div>
            </div>
        </div>

'''

anchor = '        <div id="quiz-3" class="border-t border-slate-100 pt-4"></div>'
assert s.count(anchor) == 1, '3단계 퀴즈 자리를 찾지 못했습니다'
s = s.replace(anchor, HTML + anchor)

# ══════════════════════════════════════════════ 자바스크립트

JS = '''/* ==========================================================================
   ⏱️ 참기 게임 (3단계)
   반응속도가 "얼마나 빠른가"를 잰다면, 이것은 "얼마나 정확한가"를 잰다.
   아이가 아무리 잘해도 컴퓨터를 못 이긴다. 그게 이 활동의 교육 목적이다 —
   컴퓨터가 잘하는 일과 사람이 잘하는 일이 다르다는 것을 몸으로 알게 된다.

   시간 측정은 아두이노가 한다. 화면이 0.5초마다 물어보는 방식으로는
   누른 순간을 정확히 알 수 없기 때문이다.
   아두이노가 없으면 화면 단추로도 할 수 있게 해 두었다.
   ========================================================================== */
let holdRunning = false;
let holdWebStartedAt = 0;      // 화면 모드에서 쓰는 시작 시각
let holdUsingArduino = false;  // 이번 판을 아두이노가 재고 있는지
let holdRecords = [];          // 오차 기록 (줄어드는지 보여 주기 위함)

function holdTargetMs() {
  return parseInt(document.getElementById('hold-target').value, 10) * 1000;
}

function setHoldUi(state, btnText) {
  document.getElementById('hold-state').innerText = state;
  document.getElementById('hold-btn').innerText = btnText;
}

/** 시작 버튼과 "지금!" 버튼을 한 단추로 겸한다. */
async function holdAction() {
  if (holdRunning) {
    // 화면 모드에서 누른 것 — 아두이노 모드면 실제 버튼으로 눌러야 한다
    if (!holdUsingArduino) finishHold(Math.round(performance.now() - holdWebStartedAt));
    return;
  }

  document.getElementById('hold-result').classList.add('hidden');
  holdRunning = true;
  holdUsingArduino = false;

  // 아두이노가 있으면 그쪽이 소리를 내고 시간을 잰다
  try {
    const res = await fetch('/api/arduino/hold/start', { method: 'POST' });
    const data = await res.json();
    holdUsingArduino = !!data.sent;
  } catch (e) {
    holdUsingArduino = false;
  }

  if (holdUsingArduino) {
    setHoldUi('🔔 삑! 지금부터 세세요 — 아두이노 버튼을 누르면 끝', '아두이노 버튼을 누르세요');
  } else {
    holdWebStartedAt = performance.now();
    setHoldUi('▶️ 지금부터 세세요!', '⏹️ 지금!');
  }
}

/** 걸린 시간을 받아 오차를 계산하고 보여 준다. */
function finishHold(actualMs) {
  holdRunning = false;
  setHoldUi('다시 해볼까요?', '▶️ 다시 하기');

  const target = holdTargetMs();
  const errorMs = actualMs - target;
  const absMs = Math.abs(errorMs);

  document.getElementById('hold-result').classList.remove('hidden');
  document.getElementById('hold-actual').innerText = (actualMs / 1000).toFixed(2);
  document.getElementById('hold-goal').innerText = (target / 1000).toFixed(0);
  document.getElementById('hold-error').innerText =
      (errorMs >= 0 ? '+' : '−') + (absMs / 1000).toFixed(2) + '초';

  const grade = document.getElementById('hold-grade');
  if (absMs < 200) {
    grade.innerText = '🏆 시계 같은 정확함!';
    grade.className = 'text-sm font-black mt-1 text-amber-600';
  } else if (absMs < 500) {
    grade.innerText = '🎉 아주 정확해요!';
    grade.className = 'text-sm font-black mt-1 text-emerald-600';
  } else if (absMs < 1500) {
    grade.innerText = errorMs > 0 ? '🙂 조금 늦었어요' : '🙂 조금 빨랐어요';
    grade.className = 'text-sm font-black mt-1 text-purple-600';
  } else {
    grade.innerText = errorMs > 0 ? '🐢 많이 늦었어요' : '🐇 많이 빨랐어요';
    grade.className = 'text-sm font-black mt-1 text-rose-600';
  }

  recordHold(target, actualMs, errorMs);
  tryComplete(3);
}

/** 기록을 남겨 오차가 줄어드는지 눈으로 보게 한다. */
function recordHold(target, actualMs, errorMs) {
  holdRecords.unshift({ target, actualMs, errorMs });
  if (holdRecords.length > 5) holdRecords.pop();

  document.getElementById('hold-history').classList.remove('hidden');

  const list = document.getElementById('hold-list');
  list.innerHTML = '';

  holdRecords.forEach((r, i) => {
    const line = document.createElement('div');
    const sign = r.errorMs >= 0 ? '+' : '−';
    const abs = (Math.abs(r.errorMs) / 1000).toFixed(2);

    line.className = i === 0 ? 'text-purple-700 font-bold' : '';
    line.innerText = `목표 ${r.target / 1000}초 → ${(r.actualMs / 1000).toFixed(2)}초 (오차 ${sign}${abs}초)`;
    list.appendChild(line);
  });
}

/** 아두이노가 보낸 참기 게임 결과를 받아 온다. */
async function fetchHoldResults() {
  if (!holdRunning || !holdUsingArduino) return;

  try {
    const res = await fetch('/api/arduino/hold');
    if (!res.ok) return;

    const { results } = await res.json();
    if (results && results.length > 0) finishHold(results[0]);
  } catch (e) {
    // 서버가 잠깐 응답하지 않아도 다음 차례에 다시 물어본다
  }
}
setInterval(fetchHoldResults, 300);

'''

anchor2 = '/* ==========================================================================\n   🏆 우리 반 반응속도 순위표'
assert s.count(anchor2) == 1, '순위표 구역을 찾지 못했습니다'
s = s.replace(anchor2, JS + anchor2)

path.write_text(s)
print("3단계 화면에 참기 게임 추가")
