#!/usr/bin/env bash
# demo/receiver.sh — 로컬 웹훅 수신 덤프 실행 래퍼 (설계서 부록 B).
# 릴레이가 보낸 서명 웹훅을 받아 헤더·본문을 콘솔에 찍는다(WireMock 대체). 기본 9099 포트.
exec python3 "$(cd "$(dirname "$0")" && pwd)/webhook_receiver.py" "$@"
