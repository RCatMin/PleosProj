#!/usr/bin/env python3
"""우리 안내 엔진이 고른 경로를 따라 에뮬레이터 위치를 움직인다 (PLE-37).

    adb shell am broadcast -n com.example.pleosproj/.app.NaviProbeReceiver --es cmd guide_start
    python3 scripts/replay_route.py [--speed-kmh 40] [--from 0]

앱이 남긴 `files/route.json`(고른 경로선)을 가져와서, 그 길을 따라 1초마다 `adb emu geo fix`로 위치를 옮긴다.
FusedLocation이 이 위치를 따라가므로 안내 화면·음성이 실제로 움직인다.
"""
import argparse
import json
import math
import os
import shutil
import subprocess
import sys
import time

PACKAGE = "com.example.pleosproj"


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
    walked = 0.0
    for a, b in zip(line, line[1:]):
        leg = distance(a, b)
        if walked + leg >= meters and leg > 0:
            t = (meters - walked) / leg
            return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        walked += leg
    return tuple(line[-1])


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--speed-kmh", type=float, default=40.0, help="달리는 속도")
    p.add_argument("--from", dest="start", type=float, default=0.0, help="경로 시작점부터 몇 m에서 출발할지")
    p.add_argument("--user", default="10", help="앱이 설치된 안드로이드 사용자 (Pleos 에뮬레이터는 10)")
    args = p.parse_args()

    out = subprocess.run([adb(), "shell", "run-as", PACKAGE, "--user", args.user, "cat", "files/route.json"],
                         capture_output=True, text=True)
    if out.returncode != 0 or not out.stdout.strip().startswith("["):
        sys.exit(f"경로 파일을 가져오지 못했습니다. 먼저 guide_start를 보내세요.\n{out.stderr.strip()}")
    line = json.loads(out.stdout)
    length = sum(distance(a, b) for a, b in zip(line, line[1:]))
    speed = args.speed_kmh / 3.6
    print(f"경로 {length:.0f}m, {args.speed_kmh:.0f}km/h, 약 {length / speed / 60:.1f}분")

    m = args.start
    while True:
        lat, lng = point_at(line, m)
        subprocess.run([adb(), "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], capture_output=True)
        print(f"\r{m:6.0f}m  {lat:.5f}, {lng:.5f}", end="", flush=True)
        if m >= length:
            break
        m = min(m + speed, length)
        time.sleep(1.0)
    print("\n도착")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n중단")
