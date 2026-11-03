#!/usr/bin/env python3
"""시나리오 경로선과 회전 지점을 OSRM 공개 서버로 만든다. 개발할 때만 쓰고, 앱은 OSRM을 부르지 않는다.

    python3 scripts/osrm_route.py rain-commute

경로마다 `via`(지나갈 [위도, 경도, 방향(선택)] 목록, 출발·도착 포함)를 적어 두면,
그 지점들을 차례로 지나는 도로 경로를 받아 `line`과 `turns`를 덮어쓴다.
`durationSeconds`는 시나리오 상황(퇴근길 폭우)에 맞춰 손으로 정하므로 건드리지 않는다.
"""
import json
import math
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OSRM = "https://router.project-osrm.org/route/v1/driving/"

# OSRM 회전 방향 → ui/state TurnKind
MODIFIER = {
    "left": "LEFT", "right": "RIGHT", "slight left": "KEEP_LEFT", "slight right": "KEEP_RIGHT",
    "sharp left": "SHARP_LEFT", "sharp right": "SHARP_RIGHT", "uturn": "U_TURN", "straight": "STRAIGHT",
}
ACTION = {"LEFT": "좌회전", "RIGHT": "우회전", "KEEP_LEFT": "왼쪽 방향", "KEEP_RIGHT": "오른쪽 방향",
          "SHARP_LEFT": "좌회전", "SHARP_RIGHT": "우회전", "U_TURN": "유턴", "STRAIGHT": "직진"}


def fetch(via):
    coords = ";".join(f"{p[1]},{p[0]}" for p in via)
    # 세 번째 값이 있으면 그 지점에서 달리는 방향(도). 반대 차로나 골목에 붙는 것을 막는다
    bearings = ";".join(f"{round(p[2]) % 360},40" if len(p) > 2 else "" for p in via)
    url = OSRM + coords + "?overview=full&geometries=geojson&steps=true&continue_straight=true&bearings=" + bearings
    # macOS 기본 파이썬은 SSL이 오래돼 연결이 거절되므로 curl로 받는다
    out = subprocess.run(["curl", "-s", "-m", "20", "-A", "PleosProj-scenario-tool", url],
                         check=True, capture_output=True, text=True).stdout
    d = json.loads(out)
    if d.get("code") != "Ok":
        sys.exit(f"OSRM 오류: {d.get('code')} {d.get('message')}")
    return d["routes"][0]


def distance(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def along(line, point):
    """경로선 꼭짓점 중 [point]에 가장 가까운 곳까지 경로선을 따라 잰 거리. 앱(RouteGeometry)과 같은 방식으로 잰다"""
    walked, best, best_d = 0.0, 0.0, float("inf")
    for i, p in enumerate(line):
        if i:
            walked += distance(line[i - 1], p)
        d = distance(p, point)
        if d < best_d:
            best, best_d = walked, d
    return best


def turns_of(route, line, destination):
    turns = []
    for leg in route["legs"]:
        for step in leg["steps"]:
            m = step["maneuver"]
            kind = None
            if m["type"] in ("turn", "end of road", "fork", "on ramp", "off ramp", "roundabout", "rotary"):
                kind = MODIFIER.get(m.get("modifier"))
            elif m["type"] == "continue" and m.get("modifier") not in (None, "straight"):
                kind = MODIFIER.get(m.get("modifier"))
            # 직진이나 경유지 통과는 안내하지 않는다
            if kind and kind != "STRAIGHT":
                name = step["name"] or "다음 길"
                lng, lat = m["location"]
                turns.append({"atMeters": round(along(line, (lat, lng)), 1), "kind": kind, "text": f"{name}로 {ACTION[kind]}"})
    total = sum(distance(a, b) for a, b in zip(line, line[1:]))
    turns.append({"atMeters": round(total, 1), "kind": "ARRIVE", "text": f"{destination} 도착"})
    return turns


def main():
    name = sys.argv[1]
    path = os.path.join(ROOT, "app/src/main/assets/scenarios", name + ".json")
    with open(path, encoding="utf-8") as f:
        s = json.load(f)
    for r in s["routes"]:
        if "via" not in r:
            continue
        route = fetch(r["via"])
        r["line"] = [[round(lat, 6), round(lng, 6)] for lng, lat in route["geometry"]["coordinates"]]
        r["turns"] = turns_of(route, r["line"], s["destinationName"])
        print(f"{r['id']}: {route['distance']:.0f}m, 점 {len(r['line'])}개")
        for t in r["turns"]:
            print(f"   {t['atMeters']:7.0f}m {t['kind']:<10} {t['text']}")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(s, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
