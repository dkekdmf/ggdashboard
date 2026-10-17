"""페이지 첫 실행 묶음을 "필요한 화면 요소가 있을 때만" 돌도록 바꾼다.

차시를 나눈 뒤로는 한 페이지에 없는 단계의 초기화까지 돌려서
조용히 실패하거나 콘솔에 오류가 쌓였다. 무엇이 무엇을 필요로 하는지
한자리에 적어 두면 읽기도 쉽고 실수도 줄어든다.
"""
import pathlib

OLD = """[refreshProgress, onSliderInput, updateRuleOptions, newMaze, resetRps, buildAllQuizzes, buildSurvey,
 buildMorseTable, loadRanking, changeSudokuSize,
 renderCode2, renderCode3, renderCode4, renderCode5, renderCode6].forEach(fn => {
  try {
    fn();
  } catch (e) {
    console.error(`초기화 실패: ${fn.name}`, e);
  }
});"""

NEW = """/* 무엇을 준비할지, 그리고 그것이 어떤 화면 요소를 필요로 하는지 적어 둔다.
   needs 가 이 페이지에 없으면(= 다른 차시의 단계면) 건너뛴다.
   needs 가 null 이면 어느 차시에서나 돌려야 하는 것이다. */
const INIT_TASKS = [
  { fn: refreshProgress,  needs: null },
  { fn: buildAllQuizzes,  needs: null },
  { fn: buildSurvey,      needs: 'survey-questions' },

  { fn: onSliderInput,    needs: 'val-r' },        // 1단계 무드등 슬라이더
  { fn: loadRanking,      needs: 'ranking-list' }, // 3단계 순위표
  { fn: updateRuleOptions, needs: 'rule-sensor' }, // 5단계 조건문
  { fn: changeSudokuSize, needs: 'sudoku-size' },  // 7단계 스도쿠
  { fn: newMaze,          needs: 'maze-grid' },    // 8단계 미로
  { fn: resetRps,         needs: 'rps-me' },       // 9단계 가위바위보
  { fn: buildMorseTable,  needs: 'morse-table' },  // 10단계 모스 부호

  { fn: renderCode2, needs: 'code-2' },
  { fn: renderCode3, needs: 'code-3' },
  { fn: renderCode4, needs: 'code-4' },
  { fn: renderCode5, needs: 'code-5' },
  { fn: renderCode6, needs: 'code-6' }
];

INIT_TASKS.forEach(task => {
  if (task.needs && !hasEl(task.needs)) return;   // 이 차시에 없는 단계

  try {
    task.fn();
  } catch (e) {
    console.error(`초기화 실패: ${task.fn.name}`, e);
  }
});"""

for name in ['index.html', 'lesson2.html']:
    path = pathlib.Path('gonggongplan/src/main/resources/static') / name
    text = path.read_text()

    assert text.count(OLD) == 1, f'{name}: 초기화 묶음을 찾지 못했습니다'
    path.write_text(text.replace(OLD, NEW))
    print(f"  {name}: 초기화를 필요 요소 기준으로 정리")
