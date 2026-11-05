# PLE-28 에뮬레이터 확인 화면

2560×1440 Pleos API 34 에뮬레이터, 차량 P 상태다. PNG는 `adb exec-out screencap -p` 원본이다.

| 파일 | 확인한 동작 |
| --- | --- |
| [close-guidance.png](./close-guidance.png) | 가까운 운전자 시점에서 반투명 3D 건물과 보라 경로·내 위치가 함께 보임 |
| [dragging-flat.png](./dragging-flat.png) | 5초 드래그 도중 캡처, 3D 건물 숨김 |
| [after-drag-3d.png](./after-drag-3d.png) | 드래그와 관성 이동 종료 후 3D 복귀 |
| [overview.png](./overview.png) | 위에서 보기의 낮은 줌·2D 지도와 전체 경로 |
| [after-turn.png](./after-turn.png) | 4배 재생으로 언주로 좌회전 통과, 경로·내 위치 가림 없음 |
| [continuous-turn.gif](./continuous-turn.gif) | 실제 4배 재생 녹화에서 좌회전 전후 8초 발췌, 앱 영역 640px·10fps 미리보기 |

CPU는 녹화와 별도로 측정했다. [측정 조건과 원본](../../performance/ple-28.md)을 참고한다.
