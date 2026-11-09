# PLE-48 최종 화면 캡처

2026-11-09, Pleos API 34 에뮬레이터 2560×1440에서 확인했습니다. 코드 커밋은 `077e236`이며, `adb exec-out screencap -p` 원본입니다. PLE-46의 이전 배치와 구분하기 위해 최종 화면은 이 폴더에 모았습니다.

| 파일 | 확인한 내용 |
| --- | --- |
| [snow-compare.png](snow-compare.png) | 지도 위 경로 이름 제거, 하단 가로 카드와 후보별 예보 4개 |
| [snow-compare-alternative.png](snow-compare-alternative.png) | 성수대교를 선택했을 때 선·선택 표시·해당 후보 예보 변경 |
| [snow-briefing.png](snow-briefing.png) | 목적지·도착·소요 시간, 위험 요약·날씨, 하단 경로 비교·안내 시작 |
| [snow-guidance.png](snow-guidance.png) | 흰 회전 카드, 누락된 거리별 날씨 처리, 오른쪽 진행 막대와 하단 조작 |
| [snow-guidance-alert.png](snow-guidance-alert.png) | 결빙 경고와 날씨·회전 카드·진행 막대가 겹치지 않는 배치 |
| [snow-guidance-night.png](snow-guidance-night.png) | 야간 화면의 글자·아이콘·진행 막대 대비 |
| [rain-excluded.png](rain-excluded.png) | 가로로 움직이는 후보 3개, 통제 후보 선택 차단 |
| [live-compare.png](live-compare.png) | 실제 양주역 경로의 지점별 통과 시각·거리·예보·기상청 근거 |
| [live-briefing.png](live-briefing.png) | 실제 지금·10km 앞·20km 앞·목적지 날씨 4장 |
| [live-guidance.png](live-guidance.png) | 실제 거리별 날씨와 IC·쉼터·TG 시설, 회전·교통 진행 막대 |
| [live-guidance-moved.png](live-guidance-moved.png) | 약 378m 재생 후 시설 거리·회전 안내 갱신 |
| [live-other-routes.png](live-other-routes.png) | 안내 중 경로 비교와 ‘지금 길로 계속 가기’ 복귀 |

실제 API 요청·안내 시작·재탐색 사이의 시각과 거리 값은 수집 시점과 엔진 결과에 따라 달라집니다. 화면은 전달받은 값을 표시합니다. 위치 재생 프로세스와 시연 재생을 종료한 뒤 앱은 겨울 시연의 경로 비교에 둡니다. 에뮬레이터 전원과 해상도는 변경하지 않았습니다.
