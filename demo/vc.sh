#!/usr/bin/env bash
# Virtual Console을 브라우저 없이 조작. 먼저: adb forward tcp:8088 tcp:8088
# 사용: demo/vc.sh seat driver true | demo/vc.sh gear Drive | demo/vc.sh state
# 콘솔은 브라우저 heartbeat(/browser/session)가 없으면 emulator_inactive로 거부하므로 매 호출 전에 한 번 보냄
# 주의: 좌석 탑승 값은 앱(SDK)에 반대로 전달됨(docs/context.md 참고)
set -euo pipefail
V=http://127.0.0.1:8088
curl -sf -X POST "$V/browser/session?client=chinae-cli" >/dev/null
case "${1:-}" in
  seat)  curl -sf -X PUT "$V/vehicle/seat?kind=occupied&area=$2&value=$3" >/dev/null ;;  # area: driver|passenger|rear_left|rear_right
  gear)  curl -sf -X PUT "$V/vehicle/gear?gear=$2" >/dev/null ;;                       # Park|Reverse|Neutral|Drive
  state) curl -sf "$V/vehicle/state" | python -c "import json,sys;s=json.load(sys.stdin);print(s['seat']['occupied'],s['gear']['actual'])" ;;
  *) echo "usage: $0 seat <area> <true|false> | gear <Park|Drive|...> | state" >&2; exit 2 ;;
esac
