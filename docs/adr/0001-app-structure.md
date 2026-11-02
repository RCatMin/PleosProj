# 0001. 앱 구조

- 상태: Proposed
- 날짜: 2026-10-31

## 맥락

개발 기간은 2주 남짓이고, 데이터와 SDK 쪽은 Claude가, 화면은 Codex가 나눠 맡는다. 둘이 같은 파일을 고치면 충돌이 나고, Kotlin을 처음 다루는 사람이 코드를 따라가기도 어려워진다.

## 결정

모듈은 `app` 하나로 두고 Kotlin과 Jetpack Compose로 만든다. minSdk는 공모전 요구대로 28이다.

역할은 패키지로 나눈다.

| 패키지 | 내용 | 담당 |
| --- | --- | --- |
| `navi/` | NaviHelper 연결 | Claude |
| `vehicle/` | 차량 신호 | Claude |
| `location/` | 내 위치, 위험 지점 반경 알림 | Claude |
| `voice/` | 음성 출력과 입력 | Claude |
| `weather/`, `traffic/` | 기상청, 교통 정보 | Claude |
| `risk/` | 위험도 계산 | Claude |
| `scenario/` | 시나리오 재생 | Claude |
| `app/` | 앱 시작, 연결 조립, 화면 상태를 만드는 ViewModel | Claude |
| `ui/state/` | 화면에 넘기는 데이터 모양 | 함께 |
| 나머지 `ui/`, `MainActivity.kt`, `res/` | 화면, 테마, 지도 꾸미기 | Codex |

화면에는 `ui/state/`의 모델만 넘긴다. SDK의 데이터 클래스를 화면에서 바로 쓰지 않는다. `ui/state/`를 바꿀 때는 그 변경만 담은 PR로 올린다.

의존성 주입 라이브러리는 쓰지 않고 `app/`에서 직접 조립한다.

## 검토한 대안

기능마다 모듈을 나누면 경계가 확실해지지만, 일정에 비해 Gradle 설정이 많아진다.

Hilt는 공식 예제에서 쓰지만, 처음 보는 사람에게는 객체가 어디서 만들어지는지 숨겨진다.

## 결과

두 작업자가 부딪히는 곳이 `ui/state/` 하나로 줄어든다. 대신 경계가 규칙으로만 지켜지므로 PR에서 확인해야 한다.

패키지 이름 `com.example.pleosproj`는 서비스 이름이 정해지면 바꾼다. 마켓에 올리기 전에는 꼭 바꿔야 한다.
