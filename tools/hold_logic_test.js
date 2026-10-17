// 참기 게임의 오차 계산과 평가가 맞는지 확인한다.
// 화면 없이 논리만 떼어 검사한다.

function grade(absMs, errorMs) {
  if (absMs < 200) return '시계 같은 정확함';
  if (absMs < 500) return '아주 정확';
  if (absMs < 1500) return errorMs > 0 ? '조금 늦음' : '조금 빠름';
  return errorMs > 0 ? '많이 늦음' : '많이 빠름';
}

var cases = [
  // 목표(ms), 실제(ms), 기대 오차 표시, 기대 평가
  [10000, 10000, '+0.00초', '시계 같은 정확함'],
  [10000, 10150, '+0.15초', '시계 같은 정확함'],
  [10000,  9850, '−0.15초', '시계 같은 정확함'],
  [10000, 10400, '+0.40초', '아주 정확'],
  [10000,  9600, '−0.40초', '아주 정확'],
  [10000, 10800, '+0.80초', '조금 늦음'],
  [10000,  9200, '−0.80초', '조금 빠름'],
  [10000, 12500, '+2.50초', '많이 늦음'],
  [10000,  7000, '−3.00초', '많이 빠름'],
  [ 5000,  5100, '+0.10초', '시계 같은 정확함'],
  [15000, 14000, '−1.00초', '조금 빠름']
];

var pass = 0;
cases.forEach(function (c) {
  var target = c[0], actual = c[1];
  var errorMs = actual - target;
  var absMs = Math.abs(errorMs);

  var shown = (errorMs >= 0 ? '+' : '−') + (absMs / 1000).toFixed(2) + '초';
  var g = grade(absMs, errorMs);

  var ok = (shown === c[2]) && (g === c[3]);
  if (ok) {
    pass++;
  } else {
    print('  불일치 목표' + target + ' 실제' + actual +
          ' -> ' + shown + ' / ' + g +
          '  (기대 ' + c[2] + ' / ' + c[3] + ')');
  }
});

print((pass === cases.length ? '통과' : '주의') + ' : 오차 계산과 평가 ' + pass + '/' + cases.length);

// 기록 목록이 최근 5개만 남는지
var records = [];
for (var i = 1; i <= 8; i++) {
  records.unshift({ n: i });
  if (records.length > 5) records.pop();
}
print(records.length === 5 && records[0].n === 8
  ? '통과 : 기록은 최근 5개만, 최신이 맨 위'
  : '실패 : 기록 관리가 잘못됨');
