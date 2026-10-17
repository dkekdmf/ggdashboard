"""HTML 안의 <script> 내용만 /tmp/page.js 로 뽑아 놓는다.

page_load_check.js 로 검사하기 전에 먼저 실행한다.
겸사겸사 HTML 태그가 제대로 닫혔는지도 확인한다.

쓰는 법:
    python3 tools/extract_js.py                 # index.html (1교시)
    python3 tools/extract_js.py lesson2.html    # 다른 파일
"""
import html.parser
import json
import pathlib
import re
import sys

name = sys.argv[1] if len(sys.argv) > 1 else 'index.html'
path = pathlib.Path('gonggongplan/src/main/resources/static') / name
text = path.read_text()

# 마지막 <script>...</script> 덩어리가 우리 코드다 (앞쪽은 CDN 라이브러리)
script = re.search(r'<script>(.*)</script>', text, re.S).group(1)
pathlib.Path('/tmp/page.js').write_text(script)


class TagChecker(html.parser.HTMLParser):
    """열고 닫는 태그가 짝이 맞는지 대충 확인한다."""

    VOID = ('br', 'img', 'input', 'meta', 'link', 'hr', 'script')

    def __init__(self):
        super().__init__()
        self.stack = []

    def handle_starttag(self, tag, attrs):
        if tag not in self.VOID:
            self.stack.append(tag)

    def handle_endtag(self, tag):
        if self.stack and self.stack[-1] == tag:
            self.stack.pop()


checker = TagChecker()
checker.feed(text)
left = [t for t in checker.stack if t != 'script']

# 그 페이지에 실제로 있는 id 목록. 검사기가 "없는 요소"를 흉내 내는 데 쓴다.
ids = sorted(set(re.findall(r'id="([^"]+)"', text)))
pathlib.Path('/tmp/page_ids.json').write_text(json.dumps(ids, ensure_ascii=False))

print(f"[{name}] 자바스크립트 {len(script.splitlines())}줄 → /tmp/page.js")
print(f"  화면 요소 id {len(ids)}개 → /tmp/page_ids.json")
print("  닫히지 않은 태그:", left if left else "없음")
