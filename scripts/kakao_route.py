#!/usr/bin/env python3
"""시나리오 경로선과 회전 지점을 카카오모빌리티 길찾기로 만든다. 개발할 때만 쓴다.

    python3 scripts/kakao_route.py snow-commute

osrm_route.py와 같은 방식이다. 경로마다 `via`(지나갈 [위도, 경도] 목록, 출발·도착 포함)를 적어 두면
그 지점들을 차례로 지나는 경로를 받아 `line`과 `turns`를 덮어쓴다. `durationSeconds`는 시나리오 상황에 맞춰
손으로 정하므로 건드리지 않는다. 키는 local.properties의 KAKAO_REST_KEY를 쓴다.
"""
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from osrm_route import ROOT, along, distance  # noqa: E402

URL = "https://apis-navi.kakaomobility.com/v1/directions"


def key():
    with open(os.path.join(ROOT, "local.properties"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("KAKAO_REST_KEY="):
                return line.split("=", 1)[1].strip()
    sys.exit("local.properties에 KAKAO_REST_KEY가 없다")


def fetch(via):
    q = f"origin={via[0][1]},{via[0][0]}&destination={via[-1][1]},{via[-1][0]}&priority=RECOMMEND"
    if len(via) > 2:
        q += "&waypoints=" + "|".join(f"{p[1]},{p[0]}" for p in via[1:-1])
    out = subprocess.run(["curl", "-s", "-m", "20", "-H", "Authorization: KakaoAK " + key(), URL + "?" + q],
                         check=True, capture_output=True, text=True).stdout
    route = json.loads(out)["routes"][0]
    if route["result_code"] != 0:
        sys.exit(f"카카오모빌리티 오류: {route['result_code']} {route['result_msg']}")
    return route


def kind(text):
    """앱의 KakaoNaviParser.kind와 같은 규칙으로 안내 문구를 회전 종류로 바꾼다"""
    if "지하차도 진입" in text:
        return "STRAIGHT"
    if "유턴" in text:
        return "U_TURN"
    if "좌회전" in text:
        return "LEFT"
    if "우회전" in text:
        return "RIGHT"
    if "왼쪽" in text:
        return "KEEP_LEFT"
    if "오른쪽" in text:
        return "KEEP_RIGHT"
    return "STRAIGHT"


def main():
    name = sys.argv[1]
    path = os.path.join(ROOT, "app/src/main/assets/scenarios", name + ".json")
    with open(path, encoding="utf-8") as f:
        s = json.load(f)
    for r in s["routes"]:
        if "via" not in r:
            continue
        route = fetch(r["via"])
        line = []
        for sec in route["sections"]:
            for road in sec["roads"]:
                v = road["vertexes"]
                for i in range(0, len(v), 2):
                    p = [round(v[i + 1], 6), round(v[i], 6)]
                    if not line or line[-1] != p:
                        line.append(p)
        r["line"] = line
        turns = []
        for sec in route["sections"]:
            for g in sec["guides"]:
                k = kind(g["guidance"])
                # 출발·경유지·도착과 직진 안내는 회전으로 넣지 않는다
                if g["type"] in (0, 100, 101, 1000) or k == "STRAIGHT":
                    continue
                turns.append({"atMeters": round(along(line, (g["y"], g["x"])), 1), "kind": k,
                              "text": g["guidance"]})
        total = sum(distance(a, b) for a, b in zip(line, line[1:]))
        turns.append({"atMeters": round(total, 1), "kind": "ARRIVE", "text": f"{s['destinationName']} 도착"})
        r["turns"] = turns
        print(f"{r['id']}: {route['summary']['distance']}m 평소 {route['summary']['duration'] // 60}분, 점 {len(line)}개")
        for t in turns:
            print(f"   {t['atMeters']:7.0f}m {t['kind']:<10} {t['text']}")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(s, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
