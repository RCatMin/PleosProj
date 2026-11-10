#!/usr/bin/env python3
"""시나리오의 운전 경로대로 에뮬레이터 위치를 1초마다 옮기고, 와이퍼 단계를 바꾼다.

    python3 scripts/replay_scenario.py rain-commute [--speed 2] [--from 300] [--no-wiper] [--gear]
    python3 scripts/replay_scenario.py rain-commute --demo   # 안내 시작을 누르고 D단·속도·위치를 같이 움직인다

- 위치: `adb emu geo fix`로 보낸다. FusedLocation이 이 값을 따라간다 (ADR 0005).
- 와이퍼: 가상 차량 도구(http://127.0.0.1:8088)로 보낸다. 브라우저에서 도구 탭을 열어 둬야 받는다.
  body 값은 일부만 보내면 차 상태가 꼬이므로, 매번 현재 상태 전체를 읽어 와이퍼만 바꿔 보낸다.
- 앱의 시나리오 재생과 같은 속도로 같이 시작해야 화면과 위치가 맞는다. `--tap-start`를 주면 스크립트가
  화면의 "안내 시작"을 대신 눌러 시작 시점을 맞춘다.
- 차량 속도(`--drive-speed`): 가상 차량 도구에 경로 평균 속도를 보낸다. 도구 탭은 페달 값으로 속도를 계속 다시 계산하므로
  가속 페달은 늘 0으로 보낸다. 0이 아닌 값을 보내면 탭이 혼자 계속 가속한다.
"""
import argparse
import json
import math
import os
import re
import shutil
import signal
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


def set_speed(kph):
    """차량 속도를 보낸다. 가속·브레이크 페달은 0으로 둔다 (도구 탭이 페달 값으로 속도를 다시 계산하기 때문)"""
    console("PUT", "/vehicle/controls", {"speed": max(0, round(kph)), "accelerator": 0, "brake": 0})


def drive_speed(t, duration, average_kph):
    """시나리오 [t]초의 차량 속도(km/h). 평균 속도로 달리되 출발 20초 동안 올리고 도착 20초 전부터 내린다"""
    ramp = min(1.0, t / 20.0, max(0.0, duration - t) / 20.0)
    wobble = 3.0 * math.sin(t / 37.0)
    return max(0.0, (average_kph + wobble) * ramp)


def tap_start():
    """에뮬레이터 화면에서 "안내 시작" 버튼을 찾아 누른다. 없으면 False"""
    subprocess.run([adb(), "shell", "uiautomator", "dump", "/sdcard/ui.xml"], check=False, capture_output=True)
    xml = subprocess.run([adb(), "shell", "cat", "/sdcard/ui.xml"], check=False, capture_output=True, text=True).stdout
    m = re.search(r'text="안내 시작"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if not m:
        return False
    x1, y1, x2, y2 = map(int, m.groups())
    subprocess.run([adb(), "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=False)
    return True


def main():
    p = argparse.ArgumentParser()
    p.add_argument("scenario", help="assets/scenarios 안의 파일 이름 (확장자 제외)")
    p.add_argument("--speed", type=float, default=1.0, help="재생 배속")
    p.add_argument("--from", dest="start", type=float, default=0.0, help="시작 시점(초)")
    p.add_argument("--no-wiper", action="store_true", help="와이퍼는 바꾸지 않는다")
    p.add_argument("--gear", action="store_true", help="시작할 때 D단, 끝나면 P단으로 바꾼다")
    p.add_argument("--drive-speed", action="store_true", help="가상 차량 도구에 주행 속도를 보낸다. 끝나면 0으로 돌린다")
    p.add_argument("--tap-start", action="store_true", help='화면의 "안내 시작"을 눌러 앱과 같이 시작한다')
    p.add_argument("--demo", action="store_true", help="시연용: 4배속 + --gear + --drive-speed + --tap-start")
    args = p.parse_args()
    if args.demo:
        # 앱의 시연 재생은 4배속이다
        args.speed, args.gear, args.drive_speed, args.tap_start = 4.0, True, True, True

    with open(os.path.join(ROOT, "app/src/main/assets/scenarios", args.scenario + ".json"), encoding="utf-8") as f:
        s = json.load(f)
    route = next(r for r in s["routes"] if r["id"] == s["driveRouteId"])
    line, duration = route["line"], route["durationSeconds"]
    length = sum(distance(a, b) for a, b in zip(line, line[1:]))
    wipers = sorted(s.get("vehicle", []), key=lambda k: k["atSeconds"])
    average_kph = length / duration * 3.6
    use_console = not args.no_wiper or args.gear or args.drive_speed
    if use_console:
        subprocess.run([adb(), "forward", "tcp:8088", "tcp:8088"], check=False, capture_output=True)

    print(f"{s['title']}: {route['name']} {length:.0f}m, {duration}s, {args.speed}배속")
    if args.tap_start and not tap_start():
        sys.exit('화면에 "안내 시작" 버튼이 없습니다. 시연의 브리핑이나 경로 비교 화면을 띄운 뒤 다시 실행하세요')
    began = time.monotonic()
    # kill로 끝낼 때도 Ctrl+C와 같이 차를 세우고 끝낸다
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        if args.gear:
            console("PUT", "/vehicle/gear", {"gear": "Drive"})
        replay(args, line, length, duration, wipers, average_kph, began)
        print("\n도착")
    except KeyboardInterrupt:
        print("\n중단")
    finally:
        # 끝나거나 중간에 멈추면 차를 세운다
        try:
            if args.drive_speed:
                set_speed(0)
            if args.gear:
                console("PUT", "/vehicle/gear", {"gear": "Park"})
        except urllib.error.URLError as e:
            print(f"  ! 차량 상태를 되돌리지 못했습니다: {e}")


def replay(args, line, length, duration, wipers, average_kph, began):
    wiper_sent = None
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

        kph = drive_speed(t, duration, average_kph) if args.drive_speed else None
        if kph is not None:
            try:
                set_speed(kph)
            except urllib.error.URLError as e:
                print(f"  ! 속도 변경 실패: {e}")

        print(f"\r{t:5.0f}s  {lat:.5f}, {lng:.5f}" + (f"  {kph:3.0f}km/h" if kph is not None else ""), end="", flush=True)
        if t >= duration:
            break
        time.sleep(1.0)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n중단")
