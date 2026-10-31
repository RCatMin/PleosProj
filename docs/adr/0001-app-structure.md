# 0001. 앱 구조: 단일 모듈, Kotlin + Compose, 패키지별 소유 영역

- 상태: Proposed
- 날짜: 2026-10-31

## 맥락

개발 기간이 약 2주이고, 개발은 Claude(데이터·SDK)와 Codex(화면)가 나눠 맡는다 (10/30). 사람(개발자)은 Kotlin·안드로이드가 처음이다. 구조가 복잡하면 두 에이전트가 같은 파일을 건드리거나, 사람이 코드를 따라가기 어려워진다.

## 결정

- **단일 모듈 `app`**, Kotlin + Jetpack Compose, minSdk 28 (공모전 요구), Pleos Connect(API 34) 에뮬레이터 기준.
- **패키지로 소유 영역을 나눈다** (`app/src/main/java/com/example/pleosproj/` 기준):

| 패키지 | 내용 | 소유 |
| --- | --- | --- |
| `navi/` | NaviHelper 연결 | Claude |
| `vehicle/` | Vehicle SDK, android.car 차량 신호 | Claude |
| `location/` | 내 위치, 위험 지점 반경 알림 | Claude |
| `voice/` | 음성 출력·입력 | Claude |
| `weather/`, `traffic/` | 기상청·ITS 등 외부 데이터 | Claude |
| `risk/` | 위험도 계산 (순수 Kotlin, 단위 테스트) | Claude |
| `scenario/` | 시나리오 재생 | Claude |
| `app/` | Application, 연결 조립, 화면 상태를 만드는 ViewModel | Claude |
| `ui/state/` | **화면 상태 모델 (Claude·Codex 사이의 약속)** | 공동, 변경은 별도 PR + 사람 승인 |
| `ui/` (나머지), `MainActivity.kt`, `res/` | 화면, 테마, 지도 꾸미기 | Codex |

- 화면에는 `ui/state/`의 모델만 넘긴다. SDK 데이터 클래스를 화면에 직접 넘기지 않는다.
- 의존성 주입 라이브러리(Hilt 등)는 쓰지 않는다. `app/`에서 직접 조립한다.

## 검토한 대안

- 기능별 여러 모듈(`:navi`, `:risk`, `:ui` …) — 경계는 더 단단하지만, 2주 일정과 Gradle 설정 부담에 비해 이득이 작다.
- Hilt 의존성 주입 — 공식 예제는 쓰지만, 처음 보는 사람에게 동작이 숨겨지고 빌드 설정이 늘어난다.
- XML View 화면 — Compose가 Android Studio 기본 템플릿이고 Codex가 다루기 쉽다.

## 결과

- 두 에이전트가 같은 파일을 건드릴 일이 줄어든다. 충돌 지점은 `ui/state/` 하나다.
- 모듈 경계가 없어서 규칙(CLAUDE.md·AGENTS.md)으로만 지켜진다. 위반하면 PR 리뷰에서 걸러야 한다.
- 서비스 이름이 정해지면 패키지 이름(`com.example.pleosproj`)을 바꾼다. 마켓 등록 전에는 반드시 바꿔야 한다.
