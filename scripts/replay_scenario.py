#!/usr/bin/env python3
"""시나리오의 운전 경로대로 에뮬레이터 위치를 1초마다 옮기고, 와이퍼 단계를 바꾼다.

    python3 scripts/replay_scenario.py rain-commute [--speed 2] [--from 300] [--no-wiper] [--gear]

- 위치: `adb emu geo fix`로 보낸다. FusedLocation이 이 값을 따라간다 (ADR 0005).
- 와이퍼: 가상 차량 도구(http://127.0.0.1:8088)로 보낸다. 브라우저에서 도구 탭을 열어 둬야 받는다.
  body 값은 일부만 보내면 차 상태가 꼬이므로, 매번 현재 상태 전체를 읽어 와이퍼만 바꿔 보낸다.
- 앱의 시나리오 재생과 같은 속도로 같이 시작해야 화면과 위치가 맞는다.
"""
import argparse
import json
import math
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONSOLE = "http://127.0.0.1:8088"
# 시나리오 와이퍼 단계 → 가상 차량 도구 모드
WIPER_MODE = {"OFF": "Off", "INTERMITTENT": "Auto", "LOW": "Low", "HIGH": "High"}


def adb():
    path = shutil.which("adb") or os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
    if not os.path.exists(path):
        sys.exit("adb를 찾을 수 없습니다")
    return path


def distance(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def point_at(line, meters):
    """ScenarioEngine과 같은 방식으로 경로 위 [meters] 지점을 구한다"""
    walked = 0.0
    for a, b in zip(line, line[1:]):
        leg = distance(a, b)
        if walked + leg >= meters and leg > 0:
            t = (meters - walked) / leg
            return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        walked += leg
    return tuple(line[-1])


def console(method, path, params=None):
    url = CONSOLE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(url, method=method)
    with urllib.request.urlopen(req, timeout=3) as r:
        return json.loads(r.read() or b"{}")


def set_wiper(level):
    """현재 body 전체를 읽고 앞 와이퍼만 바꿔 보낸다 (도구의 bodySnapshot과 같은 필드)"""
    b = console("GET", "/vehicle/state")["body"]
    locks = b["door_locks"]
    body = {
        "door_fl": int(b["doors"]["front_left"]), "door_fr": int(b["doors"]["front_right"]),
        "door_rl": int(b["doors"]["rear_left"]), "door_rr": int(b["doors"]["rear_right"]),
        "doors_locked": int(all(locks[k] for k in ("front_left", "front_right", "rear_left", "rear_right"))),
        "window_fl": round(b["windows"]["front_left"] or 0), "window_fr": round(b["windows"]["front_right"] or 0),
        "window_rl": round(b["windows"]["rear_left"] or 0), "window_rr": round(b["windows"]["rear_right"] or 0),
        "frunk": int(b["closures"]["frunk"]), "trunk": int(b["closures"]["trunk"]),
        "front_wiper": WIPER_MODE[level], "rear_wiper": b["wipers"]["rear"]["mode"],
        "ambient_mode": b["ambient_light"].get("mode") or "On", "ambient_color": b["ambient_light"]["color"],
        "ambient_brightness_pct": round(b["ambient_light"].get("brightness_pct", 100)),
    }
    res = console("PUT", "/vehicle/body", body)
    if "emulator_inactive" in json.dumps(res):
        print("  ! 가상 차량 도구 탭이 열려 있지 않아 와이퍼를 못 바꿨습니다")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("scenario", help="assets/scenarios 안의 파일 이름 (확장자 제외)")
    p.add_argument("--speed", type=float, default=1.0, help="재생 배속")
    p.add_argument("--from", dest="start", type=float, default=0.0, help="시작 시점(초)")
    p.add_argument("--no-wiper", action="store_true", help="와이퍼는 바꾸지 않는다")
    p.add_argument("--gear", action="store_true", help="시작할 때 D단, 끝나면 P단으로 바꾼다")
    args = p.parse_args()

    with open(os.path.join(ROOT, "app/src/main/assets/scenarios", args.scenario + ".json"), encoding="utf-8") as f:
        s = json.load(f)
    route = next(r for r in s["routes"] if r["id"] == s["driveRouteId"])
    line, duration = route["line"], route["durationSeconds"]
    length = sum(distance(a, b) for a, b in zip(line, line[1:]))
    wipers = sorted(s.get("vehicle", []), key=lambda k: k["atSeconds"])
    use_console = not args.no_wiper or args.gear
    if use_console:
        subprocess.run([adb(), "forward", "tcp:8088", "tcp:8088"], check=False, capture_output=True)

    print(f"{s['title']}: {route['name']} {length:.0f}m, {duration}s, {args.speed}배속")
    if args.gear:
        console("PUT", "/vehicle/gear", {"gear": "Drive"})

    wiper_sent = None
    began = time.monotonic()
    while True:
        t = min(args.start + (time.monotonic() - began) * args.speed, duration)
        lat, lng = point_at(line, length * t / duration)
        # geo fix는 경도를 먼저 받는다
        subprocess.run([adb(), "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], check=False, capture_output=True)

        level = next((k["wiper"] for k in reversed(wipers) if k["atSeconds"] <= t), None)
        if not args.no_wiper and level and level != wiper_sent:
            try:
                set_wiper(level)
                print(f"  {t:5.0f}s 와이퍼 {level}")
            except (urllib.error.URLError, KeyError) as e:
                print(f"  ! 와이퍼 변경 실패: {e}")
            wiper_sent = level

        print(f"\r{t:5.0f}s  {lat:.5f}, {lng:.5f}", end="", flush=True)
        if t >= duration:
            break
        time.sleep(1.0)

    print("\n도착")
    if args.gear:
        console("PUT", "/vehicle/gear", {"gear": "Park"})


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n중단")
