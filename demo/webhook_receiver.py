#!/usr/bin/env python3
"""demo/webhook_receiver.py — 로컬 웹훅 수신 덤프 (WireMock 대체). 설계서 부록 B 실연용.

릴레이가 보낸 웹훅을 받아 헤더(X-InsuHR-*)와 본문을 콘솔에 찍는다. 서명 검증은 하지 않는다(데모).
페이로드에 주민번호·계좌 원문이 없다는 걸(9.3 원칙) 눈으로 확인하는 창이기도 하다.

실행: python3 demo/webhook_receiver.py   # 기본 9099 포트
"""
import json
import sys
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
sys.stdout.reconfigure(line_buffering=True)  # 파일로 리다이렉트해도 실시간으로 보이게


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length else ""
        ts = datetime.now().strftime("%H:%M:%S")
        print(f"\n── {ts}  POST {self.path}")
        for h in ("X-InsuHR-Event-Uuid", "X-InsuHR-Timestamp", "X-InsuHR-Signature"):
            if self.headers.get(h):
                print(f"   {h}: {self.headers.get(h)}")
        try:
            print("   body: " + json.dumps(json.loads(body), ensure_ascii=False))
        except ValueError:
            print("   body: " + body)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"ok":true}')

    def log_message(self, *args):  # 기본 액세스로그 억제 — 우리 포맷만 남긴다
        pass


if __name__ == "__main__":
    print(f"웹훅 수신 대기: http://localhost:{PORT}/hook  (Ctrl+C 종료)")
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
