# 0006. Pleos SDK를 쓸 수 없을 때의 대체

- 상태: Proposed
- 날짜: 2026-10-31

## 맥락

Pleos 개발자 콘솔에서 Vehicle State와 NaviHelper는 열려 있지만, FusedLocation, TextToSpeech, SpeechToText, Vehicle Control은 준비 중으로 나온다.

에뮬레이터에서 직접 써 보니 FusedLocation은 동작했고, TextToSpeech는 기기 내 모드에서 실제로 소리가 났다. SpeechToText는 초기화까지 됐다. Vehicle Control만 권한 오류로 막혀 있었다.

준비 중인 SDK가 실차나 마켓에서도 동작할지는 알 수 없다.

## 결정

위치와 음성은 Pleos SDK를 먼저 쓰고, 쓸 수 없으면 안드로이드 기본 기능으로 바꾼다.

- 내 위치와 반경 알림: Pleos FusedLocation, 안 되면 `LocationManager` (반경 알림은 거리 계산으로 직접 처리)
- 음성 출력: Pleos TextToSpeech, 안 되면 안드로이드 `TextToSpeech`
- 음성 입력: Pleos SpeechToText, 안 되면 `SpeechRecognizer`

기능마다 인터페이스를 하나 두고, 앱이 시작할 때 `isSupported()`와 초기화 결과를 보고 구현을 고른다. 어떤 구현을 골랐는지는 로그로 남긴다.

Pleos TextToSpeech는 초기화 콜백 안에서 바로 `speak()`를 부르면 소리 없이 무시된다. 메인 스레드로 넘겨서 호출한다.

Vehicle Control은 쓰지 않는다.

음성 출력이 Pleos와 안드로이드 기본 기능 모두 안 되면 소리 없이 화면 알림만 한다. Pleos 에뮬레이터에는 표준 안드로이드 TTS 엔진이 없어서, 에뮬레이터에서는 이 경우가 된다.

## 검토한 대안

Pleos SDK만 쓰면 SDK 활용 면에서는 가장 좋지만, 막히는 순간 경고 기능이 멈춘다.

처음부터 안드로이드 기본 기능만 쓰면 안정적이지만 Pleos SDK를 쓰는 의미가 사라진다.

## 결과

어떤 환경에서도 위치와 음성 경고가 동작한다. 대체 구현으로 바뀐 만큼 Pleos SDK 사용은 줄어든다.

구현이 두 벌이 되므로 인터페이스는 작게 유지한다. 콘솔에서 SDK가 열리면 대체 구현을 정리할 수 있다.
