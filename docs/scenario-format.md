# 시나리오 파일 형식

시연용 시나리오는 `app/src/main/assets/scenarios/<id>.json`에 둔다. 앱은 `ScenarioPlayer.load(context, id)`로 읽고, 재생기는 이 값으로 안내 화면 상태를 만든다 (ADR 0005).

시나리오 속 위험 지점, 통제 시각, 날씨는 시연을 위해 만든 값이다. 실제 기록이 아니다. 겨울 시나리오(`snow-commute`)의 결빙 사고 다발지역만 도로교통공단 실제 통계(2021~2025년)에서 가져왔고, 경로선은 카카오모빌리티 실제 경로다. 소요 시간과 날씨는 눈 오는 날을 가정해 정했다.

장거리 폭우 시나리오(`rain-long-drive`)도 경로선은 카카오모빌리티 실제 경로이고 위험 지점은 실제 도로 위에 있지만, 그곳의 침수 우려와 통제, 날씨는 모두 가정이다. 실제 침수나 통제 기록이 아니다. 자세한 내용은 맨 아래에 적었다.

앱의 시연 모드는 `ScenarioPlayer.list(context)`로 시나리오 목록을 받는다.

## 전체 구조

```json
{
  "id": "rain-commute",
  "title": "도심 폭우 퇴근길",
  "summary": "강남역에서 삼성역까지, 호우경보 속 침수·통제 구간을 피하는 길로 안내해요",
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
| `summary` | 시연 모드에서 시나리오를 고를 때 보여 줄 한 줄 설명 |
| `startTime` | 출발 시각 (`HH:mm`). 도착 예정 시각 계산에 쓴다. `"now"`라고 쓰면 시나리오를 불러온 시각(분 단위)이 출발 시각이 된다 |
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

  OSRM 공개 서버(OpenStreetMap 기반 무료 길찾기)를 개발할 때만 부른다.
  카카오모빌리티 경로로 만들려면 `scripts/kakao_route.py`를 쓴다 (겨울 시나리오). `local.properties`의 `KAKAO_REST_KEY`를 읽는다.
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
- `statistical: true`는 결빙 사고 다발지역처럼 실제 통계에서 가져온 지점이다. 경로 카드 요약에서 "결빙 사고가 잦은 곳 N곳"처럼 개수로 말한다.
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

시연할 때는 `--demo` 하나로 시작한다. 앱에서 시나리오의 브리핑이나 경로 비교 화면을 띄워 두고 실행하면, 스크립트가 "안내 시작"을 누르고 차량을 D단으로 바꾼 뒤 4배속으로 위치·와이퍼·차량 속도를 같이 움직인다. 끝나거나 Ctrl+C로 멈추면 속도 0, P단으로 돌려놓는다.

```bash
python3 scripts/replay_scenario.py rain-commute --demo
```

차량 속도는 경로 평균 속도(거리 ÷ 주행 시간)를 보낸다. 가상 차량 도구 탭은 페달 값으로 속도를 계속 다시 계산하므로 스크립트는 가속 페달을 늘 0으로 보낸다. 손으로 `accelerator`를 0이 아닌 값으로 보내면 탭이 혼자 계속 가속하고, 그때는 탭을 새로 고쳐야 멈춘다.

와이퍼·기어·속도를 바꾸려면 가상 차량 도구(http://127.0.0.1:8088) 탭을 브라우저에 열어 둔다. 앱과 스크립트는 같은 배속으로 같이 시작해야 화면과 위치가 맞는다.

## 장거리 폭우 시나리오 (`rain-long-drive`)

수원역에서 서울역까지 경수대로·시흥대로로 약 40km를 1시간(3600초)에 달린다. 18시에 출발해 19시에 도착한다. 시연 영상에 쓸 장면을 늘리려고 만든 시나리오라 위험 지점을 다섯 곳 두었다. 4배속으로 재생하면 15분쯤 걸린다.

후보 경로는 세 개다.

- 수원광명·서부간선 (54분): 가장 빠르지만 서부간선로 안양천 구간이 18시 20분부터 통제되는 것으로 가정해, 그곳을 지날 때는 이미 막혀 있어 후보에서 빠진다.
- 경수대로·시흥대로 (60분): 추천 경로이자 실제로 달리는 경로다.
- 과천·올림픽대로 (68분): 가장 빠른 경로보다 20% 넘게 오래 걸려 추천하지 않는다.

주행 경로에서 지나는 위험 지점과 그곳을 지나는 시각은 다음과 같다. 경고는 1.2km 앞(약 2분 전)부터 뜬다.

- 7분 30초, 덕영대로 율전동 구간 (`yuljeon-downpour`): 비가 거세지는 구간. 6분에 호우경보와 시간당 40mm 비로 바뀐다.
- 21분 35초, 호계지하차도 (`hogye-underpass`): 지하차도 침수 우려.
- 35분 25초, 석수역 부근 안양천변 도로 (`seoksu-anyangcheon`): 하천변 물 고임 우려.
- 44분, 신대방동 저지대 도로 (`sindaebang-low`): 배수가 느린 저지대 침수 우려.
- 54분, 여의대방로 여의도 구간 (`yeouidaebang-visibility`): 50분부터 시간당 65mm로 비가 가장 세져 앞이 잘 보이지 않는 구간.

지하차도와 도로 이름은 실제 이름이지만, 그곳이 이날 위험하다는 것은 시연을 위한 가정이다. 시나리오 요약에도 "시연용 가정"이라고 적었다.

재생은 다른 시나리오와 같다. 앱에서 이 시나리오를 고르고 4배속으로 시작할 때 위치 스크립트도 같은 배속으로 돌린다. 특정 위험 지점을 보려면 진행 막대로 그 시각 2분 전쯤으로 옮긴다.

```bash
python3 scripts/replay_scenario.py rain-long-drive --speed 4
python3 scripts/replay_scenario.py rain-long-drive --speed 4 --from 1170   # 호계지하차도 2분 전
```

경로선을 다시 받을 때는 `python3 scripts/kakao_route.py rain-long-drive`를 쓴다. `RainLongDriveScenarioTest`가 길이, 도착 시각, 추천 경로, 위험 지점 다섯 곳의 위치와 경고를 확인한다.

## 김포공항 폭우 시나리오 (`rain-airport-drive`)

원티드그라운드(송파구 백제고분로37길 22)에서 김포공항까지 양재대로·남부순환로로 약 37km를 1시간(3600초)에 달린다. `startTime`이 `"now"`라서 시연을 시작한 시각이 출발 시각이 되고, 도착 예정은 그 1시간 뒤로 나온다.

후보 경로는 세 개다. 한강변 두 길이 모두 통제되어 남부순환로 길만 갈 수 있는 상황이다.

- 올림픽대로 (50분): 노량진 구간이 출발 15분 뒤부터 통제되는 것으로 가정해, 그곳을 지날 때는 막혀 있어 후보에서 빠진다.
- 양재대로·남부순환로 (60분): 추천 경로이자 실제로 달리는 경로다.
- 강변북로 (52분): 이촌 구간이 출발 20분 뒤부터 통제되는 것으로 가정해 후보에서 빠진다.

주행 경로에서 지나는 위험 지점과 그곳을 지나는 시각은 다음과 같다. 경고는 1.2km 앞(약 2분 전)부터 뜬다.

- 8분, 일원지하차도 (`irwon-underpass`): 지하차도 침수 우려. 5분에 호우경보와 시간당 40mm 비로 바뀐다.
- 24분, 사당역 사거리 부근 저지대 도로 (`sadang-low`): 저지대 침수 우려.
- 33분, 신림역 부근 도림천변 도로 (`sillim-dorimcheon`): 하천변 물 고임 우려.
- 44분, 개봉지하차도 (`gaebong-underpass`): 지하차도 침수 우려.
- 51분, 남부순환로 신월동 구간 (`sinwol-visibility`): 48분부터 시간당 65mm로 비가 가장 세져 앞이 잘 보이지 않는 구간.

지하차도와 도로 이름은 실제 이름이고 경로선은 카카오모빌리티 실제 경로다. 그곳의 침수 우려와 한강변 통제, 날씨는 시연을 위한 가정이며 실제 기록이 아니다. 시나리오 요약에도 "시연용 가정"이라고 적었다.

```bash
python3 scripts/replay_scenario.py rain-airport-drive --speed 4
python3 scripts/replay_scenario.py rain-airport-drive --speed 4 --from 1310   # 사당역 저지대 2분 전
```

경로선을 다시 받을 때는 `python3 scripts/kakao_route.py rain-airport-drive`를 쓴다. `RainAirportDriveScenarioTest`가 출발·도착 시각, 추천 경로, 위험 지점 위치와 경고를 확인한다.
