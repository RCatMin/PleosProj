# PLE-46 에뮬레이터 캡처

2026-11-09, Pleos API 34 에뮬레이터 2560×1440에서 코드 커밋 `d2dae05` 빌드를 확인했습니다. 모두 `adb exec-out screencap -p` 원본입니다.

| 파일 | 확인한 화면 |
| --- | --- |
| [highway-compare.png](highway-compare.png) | 추천 길의 출발·중간 두 지점·도착 예보와 통과 시각, 기상청 근거 |
| [highway-compare-alternative.png](highway-compare-alternative.png) | 가장 짧은 길로 선택을 바꿨을 때 해당 후보의 14.7km·29.4km 지점과 통과 시각·예보 |
| [highway-briefing.png](highway-briefing.png) | 지금·10km 앞·20km 앞·목적지의 실제 날씨 4장 |
| [highway-guidance-start.png](highway-guidance-start.png) | 성남IC·성남졸음쉼터·성남TG 등 시설과 거리별 실제 날씨 |
| [highway-guidance-moved.png](highway-guidance-moved.png) | 약 733m 이동 후 성남IC 2.8km → 2.1km 등 남은 거리 갱신 |
| [highway-rest-area.png](highway-rest-area.png) | 구리IC 1.5km·구리휴게소 3.0km |
| [highway-rest-area-moved.png](highway-rest-area-moved.png) | 추가 이동 후 구리IC 1.1km·구리휴게소 2.6km |
| [short-route-guidance.png](short-route-guidance.png) | 강남역 부근 → 삼성역 3.8km: 10km·20km 앞 정보 없음, 시설 카드 숨김 |
| [snow-compare.png](snow-compare.png) | 겨울 시연 경로별 예보와 시연 데이터 근거 |
| [snow-guidance.png](snow-guidance.png) | 겨울 시연의 현재 눈·영하 기온, 미제공 거리별 날씨와 빈 시설 목록 처리 |

화면 캡처와 UI 트리 수집 시점 사이에 위치 갱신이 한 차례 더 반영되어 거리 표시가 0.1km 다를 수 있습니다. 표의 거리는 캡처 기준입니다. 위치 재생은 모두 종료했으며, 앱은 시연 선택 목록에 둡니다.
