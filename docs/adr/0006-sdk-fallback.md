# 0006. Pleos SDK 대체 구조

- 상태: Proposed
- 날짜: 2026-10-31

## 맥락

Pleos 개발자 콘솔에서 SDK 상태는 다음과 같다 (10/30 확인).

| SDK | 콘솔 | 에뮬레이터 (PLE-12) |
| --- | --- | --- |
| Vehicle State, NaviHelper | 열림 | 동작 |
| FusedLocation | 준비 중 | 동작 (GPS 조작 반영) |
| TextToSpeech | 준비 중 | ON_DEVICE·HYBRID 동작 (음성 출력 확인), SERVER는 오류 |
| SpeechToText | 준비 중 | 초기화 성공, 실제 인식은 미확인 |
| Vehicle Control | 준비 중 | `SecurityException`으로 막힘 |

공모전 문의는 받지 않아서(10/31), '준비 중' SDK를 출품작·실차·마켓에서 쓸 수 있는지 확인할 수 없다.

## 결정

- 위치·음성은 **Pleos SDK를 먼저 쓰고, 안 되면 안드로이드 기본 기능으로 자동으로 바꾼다.**

| 기능 | 1순위 | 대체 |
| --- | --- | --- |
| 내 위치·반경 알림 (`location/`) | Pleos FusedLocation | 안드로이드 `LocationManager` (반경 알림은 거리 계산으로 직접) |
| 음성 출력 (`voice/`) | Pleos TextToSpeech (ON_DEVICE) | 안드로이드 `TextToSpeech` |
| 음성 입력 (`voice/`) | Pleos SpeechToText (ON_DEVICE) | 안드로이드 `SpeechRecognizer` |

- 각 기능은 인터페이스 하나로 감싸고, 시작할 때 `isSupported()`·초기화 결과로 구현을 고른다. 어떤 구현을 쓰는지 로그에 남긴다.
- Vehicle Control(차량 제어)은 쓰지 않는다.
- Pleos TTS는 초기화 콜백 안에서 바로 `speak()`하면 조용히 무시된다. 메인 스레드로 넘겨 호출한다.

## 검토한 대안

- Pleos SDK만 쓰기 — 심사 기준 3에는 가장 좋지만, 막히면 앱의 핵심(경고)이 멈춘다.
- 처음부터 안드로이드 기본 기능만 쓰기 — 안정적이지만 Pleos SDK 활용 점수를 잃는다.

## 결과

- 어떤 상황에서도 위치·음성 경고가 동작한다.
- 대체 구현으로 바뀌면 그만큼 Pleos SDK 활용이 줄어든다. 기획서에는 1순위인 Pleos SDK 사용을 적는다.
- 구현이 두 벌이라 코드가 늘어난다. 인터페이스를 작게 유지한다.
- 다시 볼 조건: 콘솔에서 SDK가 열리면 대체 구현을 정리할 수 있다.
