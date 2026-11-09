# PLE-48 파란색 UI·위험 구간선 최종 화면

2026-11-09 사용자 첨부 사진 기준으로 복원한 최종 배치입니다. 최초 설치는 2026-11-09, 최종 검증은 2026-11-10입니다. 추가 수정 빌드를 실행 중인 Pleos API 34 에뮬레이터에 설치했습니다. 위험 원·이름표는 경로 구간선과 우측 하단 범례로 교체하고, 주야간 전환 버튼은 해·달과 라이트·다크 이름으로 구분했습니다. 모든 사진은 `adb exec-out screencap -p` 원본(2560×1440)입니다.

| 파일 | 확인한 내용 |
| --- | --- |
| [eta-search.png](eta-search.png) | 일반 목적지 검색의 ETA 제목 |
| [eta-gangnam-underpass.png](eta-gangnam-underpass.png) | 강남역 경로의 개포지하차도 취약 정보가 침수 범례로 표시되는 현상(PLE-49) |
| [rain-compare.png](rain-compare.png) | 왼쪽 세로 후보, 통제 후보 선택 차단, 예보 4개, 위험 구간선·범례 |
| [rain-compare-alternative.png](rain-compare-alternative.png) | 도곡로 선택 시 선·카드 강조 변경과 위험 표시 유지 |
| [rain-compare-night.png](rain-compare-night.png) | 야간 비교의 위험 색·범례와 해·라이트 전환 버튼 |
| [snow-compare.png](snow-compare.png) | 결빙 구간선과 범례, 후보 경로의 위험 위치 |
| [snow-guidance-night.png](snow-guidance-night.png) | 겨울 안내의 야간 범례·내 위치와 라이트 전환 버튼 |
| [rain-guidance.png](rain-guidance.png) | 파란 회전 카드·다음 회전·카드 안 도착 정보, 하단 가로 날씨·음성 전환·종료, 오른쪽 파란 진행 막대 |
| [rain-guidance-alert.png](rain-guidance-alert.png) | 파란 회전 카드 바로 아래 위험 경고, 하단 날씨·조작과 겹치지 않는 배치 |
| [rain-guidance-night.png](rain-guidance-night.png) | 야간의 경로 위험 구간선·범례와 해·라이트 버튼, 회전·경고·진행 막대 |
| [rain-briefing.png](rain-briefing.png) | ETA 제목, 시연 위험 요약·날씨·브리핑 배치 |
| [live-compare.png](live-compare.png) | 실제 양주역 경로의 왼쪽 선택 카드와 기상청 근거·거리·통과 시각·예보 4개 |
| [live-briefing.png](live-briefing.png) | ETA 제목, 강남역 실제 날씨 4장과 브리핑 출발 흐름 |
| [live-guidance.png](live-guidance.png) | 실제 IC·쉼터·TG·거리별 날씨 |
| [live-guidance-moved.png](live-guidance-moved.png) | 위치 재생 후 시설 거리와 회전 갱신 |

비교·안내의 주야간 왕복 전환, 폭우 경고 표시, 실제 경로 계획·안내와 약 18초 위치 재생을 확인했습니다. 단위 테스트 166개(실패·오류 0), `assembleDebug`, `installDebug`를 통과했습니다. 에뮬레이터의 전원·해상도는 변경하지 않았습니다. 실행값은 수집/계획 시점에 따라 다르며, 화면은 연결부가 전달한 값을 표시합니다.

앱 이름은 ETA로 변경했으며 `aapt2 dump badging`에서도 Android 앱 표시 이름이 ETA임을 확인했습니다. 침수 범례는 현재 실제 침수 발생 확인을 뜻하지 않으며, 취약 지점·발생 정보를 구분하는 연결부 수정은 [PLE-49](https://linear.app/scsblog/issue/PLE-49)로 남겼습니다.
