# 시나리오 파일 형식

시연용 시나리오는 `app/src/main/assets/scenarios/<id>.json`에 둔다. 앱은 `ScenarioPlayer.load(context, id)`로 읽고, 재생기는 이 값으로 안내 화면 상태를 만든다 (ADR 0005).

시나리오 속 위험 지점, 통제 시각, 날씨는 시연을 위해 만든 값이다. 실제 기록이 아니다.

## 전체 구조

```json
{
  "id": "rain-commute",
  "title": "도심 폭우 퇴근길",
  "startTime": "18:10",
  "destinationName": "삼성역",
  "routes": [ ... ],
  "driveRouteId": "bongeunsa",
  "hazards": [ ... ],
  "weather": [ ... ],
  "vehicle": [ ... ]
}
```

| 필드 | 뜻 |
| --- | --- |
| `startTime` | 출발 시각 (`HH:mm`). 도착 예정 시각 계산에 쓴다 |
| `routes` | 후보 경로. 경로 비교 화면에 그대로 나온다 |
| `driveRouteId` | 실제로 달리는 경로. 보통 추천될 경로를 넣는다 |
| `hazards` | 위험 지점 |
| `weather` | 시간에 따른 날씨 |
| `vehicle` | 시간에 따른 차량 신호 (지금은 와이퍼만) |

시간은 모두 재생 시작부터 흐른 초다.

## 경로

```json
{
  "id": "bongeunsa",
  "name": "봉은사로",
  "durationSeconds": 1080,
  "tollWon": 0,
  "via": [[37.4986, 127.0296, 69], [37.5088, 127.0390, 70], ...],
  "line": [[37.498595, 127.029627], ...],
  "turns": [
    { "atMeters": 1229.0, "kind": "LEFT", "text": "언주로로 좌회전" }
  ]
}
```

- `via`는 경로가 지나갈 지점이다. `[위도, 경도, 달리는 방향(도, 북쪽 0)]`으로 쓰고 방향은 생략할 수 있다. 교차로 한가운데보다 도로 중간에 찍어야 반대 차로나 골목에 붙지 않는다.
- `line`과 `turns`는 `via`를 보고 스크립트가 채운다. 손으로 고치지 않는다.

  ```bash
  python3 scripts/osrm_route.py rain-commute
  ```

  OSRM 공개 서버(OpenStreetMap 기반 무료 길찾기)를 개발할 때만 부른다. 앱은 OSRM을 쓰지 않는다.
- `line`은 `[위도, 경도]` 목록이다.
- `turns[].atMeters`는 출발점에서 경로선을 따라 잰 거리다.
- `turns[].kind`는 `ui/state`의 `TurnKind` 이름이다 (`LEFT`, `RIGHT`, `ARRIVE` 등). 마지막에는 `ARRIVE`를 넣는다.
- 재생은 `durationSeconds` 동안 일정한 속도로 달린다고 본다.

## 위험 지점

```json
{
  "id": "seolleung-low",
  "kind": "FLOOD",
  "lat": 37.5045, "lng": 127.0490,
  "title": "선릉역 저지대 도로",
  "detail": "침수 이력이 있는 저지대예요.",
  "vulnerability": "PAST_FLOOD",
  "icingZone": false,
  "closedFromSeconds": 420
}
```

- `kind`는 `HazardKind` 이름이다.
- `vulnerability`는 `NONE`, `UNDERPASS`, `RIVERSIDE`, `PAST_FLOOD` 중 하나로, 위험도 계산의 취약 지점 점수가 된다.
- `closedFromSeconds`가 있으면 그 시각부터 통제된다. 경로 비교는 각 구간을 지날 예상 시각으로 판단하므로, 지금은 열려 있어도 지날 때 통제되는 경로는 후보에서 빠진다.
- 경로선에서 150m 안에 있는 지점만 그 경로의 위험으로 본다.

## 날씨와 차량 신호

```json
"weather": [
  { "atSeconds": 0,   "rainMm": 12, "tempC": 23, "warning": "ADVISORY" },
  { "atSeconds": 300, "rainMm": 45, "tempC": 22, "warning": "WARNING" }
],
"vehicle": [
  { "atSeconds": 0,   "wiper": "LOW" },
  { "atSeconds": 300, "wiper": "HIGH" }
]
```

- 각 값은 다음 항목이 나올 때까지 유지된다. `snow: true`로 눈을 표시할 수 있다.
- `warning`은 `NONE`, `ADVISORY`(주의보), `WARNING`(경보)이다.
- `wiper`는 `OFF`, `INTERMITTENT`, `LOW`, `HIGH`이다.

## 재생

앱 화면은 `ScenarioPlayer`가 1초마다 넘겨주는 상태로 그린다. 에뮬레이터 위치와 와이퍼는 스크립트로 같이 움직인다.

```bash
python3 scripts/replay_scenario.py rain-commute            # 위치 + 와이퍼
python3 scripts/replay_scenario.py rain-commute --speed 3  # 3배속
python3 scripts/replay_scenario.py rain-commute --from 400 --no-wiper
```

와이퍼를 바꾸려면 가상 차량 도구(http://127.0.0.1:8088) 탭을 브라우저에 열어 둔다. 앱과 스크립트는 같은 배속으로 같이 시작해야 화면과 위치가 맞는다.
