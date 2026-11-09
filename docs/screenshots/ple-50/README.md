# PLE-50 시연 촬영 조작 검증

2026-11-10 실행 중인 Pleos API 34 에뮬레이터에서 확인했습니다. 모든 이미지는 `adb exec-out screencap -p` 원본(2560×1440)입니다.

| 파일 | 확인한 내용 |
| --- | --- |
| [rain-compare.png](rain-compare.png) | 안내 중 다른 길 비교의 평면 지도와 ‘안내 시작’ 위 ‘이전으로 돌아가기’ 버튼입니다. |
| [winter-compare-return.png](winter-compare-return.png) | 겨울 15:45에서 들어온 비교 화면의 복귀 버튼·평면 지도·숨긴 시점 선택입니다. |
| [winter-compare-night.png](winter-compare-night.png) | 확대·전체 경로·주야간 조작 후에도 평면 비교를 유지합니다. |
| [winter-returned-guidance.png](winter-returned-guidance.png) | 15:45의 경고·위치·정지 상태와 원래 운전석 시점을 유지해 복귀합니다. |
| [rain-returned-guidance.png](rain-returned-guidance.png) | 폭우 08:25의 이전 안내·경고에 복귀합니다. |
| [live-compare-return.png](live-compare-return.png) | 실제 안내의 다른 길 비교에서도 복귀 버튼을 시작 버튼 위에 배치합니다. |
| [live-returned-guidance.png](live-returned-guidance.png) | 실제 안내 복귀 시 기존 경로의 진행률을 유지합니다. |
| [rain-start.png](rain-start.png) | 4배속 기본 재생과 진행 막대의 ‘눌러서 이동’ 안내입니다. |
| [rain-jump-alert.png](rain-jump-alert.png) | 호우 항목 클릭으로 08:25, 위험 지점 약 200m 전으로 이동하고 경고·위치·날씨·다음 회전이 갱신됩니다. |
| [rain-jump-night.png](rain-jump-night.png) | 야간에도 고정 4배속·장면 이동과 위험 경고·범례가 표시됩니다. |
| [rain-rewind.png](rain-rewind.png) | 막대 아래쪽을 눌러 02:42로 되감고 정지를 유지합니다. |
| [rain-running-jump.png](rain-running-jump.png) | 재생 중 막대 위쪽으로 이동한 뒤 정지한 화면입니다. |
| [rain-reset.png](rain-reset.png) | 도착까지 이동한 뒤 재시작·처음으로 조작으로 00:00에 정지합니다. |
| [rain-turn-jump.png](rain-turn-jump.png) | 오른쪽 회전 항목 클릭으로 03:46, 회전 약 200m 전으로 이동합니다. |
| [winter-jump-alert.png](winter-jump-alert.png) | 겨울 결빙 항목 클릭으로 15:45에 이동하며 ‘앞쪽 결빙 주의’와 약 200m 거리를 표시합니다. |
| [live-no-seek.png](live-no-seek.png) | 실제 안내에는 이동 안내가 없고 막대를 눌러도 현재 위치·진행률이 바뀌지 않습니다. |

폭우 시연의 시계는 실제 약 7.1초 동안 00:08→00:36으로 진행해 4배속을 확인했습니다. 일시정지 후 경고 항목으로 이동하고 경고를 확인한 뒤 뒤로 이동·재진입하면 같은 경고가 다시 나타납니다. 재생 중 이동은 재생을 이어가고 일시정지 중 이동은 정지를 유지합니다.

다른 길 비교에서 ‘이전으로 돌아가기’와 기기 뒤로 가기 모두 기존 안내로 복귀합니다. 정지 중 진입하면 같은 시각에서 정지하고, 재생 중 진입하면 비교 동안 멈췄다가 복귀 후 이어서 재생합니다. 비교는 기울기·방향을 고정하며 시점 선택을 숨기고, 복귀하면 기존 주행 시점을 복원합니다.
