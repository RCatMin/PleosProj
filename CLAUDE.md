# CLAUDE.md

이 프로젝트에서 Claude는 **개발 담당**이다. 화면·디자인은 Codex가 맡는다 (`AGENTS.md`).

## 프로젝트

현대자동차그룹 차량용 앱 개발 공모전 출품작. 날씨·침수·결빙·통제 위험을 미리 보여 주고 안전한 경로로 안내하는 Pleos Connect 앱이다.

- 플랫폼: Pleos Connect (Android Automotive, API 34 에뮬레이터), minSdk 28
- Kotlin + Jetpack Compose, 단일 모듈 `app`, 패키지 `com.example.pleosproj` (서비스 이름이 정해지면 바꾼다)
- 경로 계산·회전 안내·장소 검색·현재 위치 주소는 **Pleos 기본 내비**가 계산하고, 앱은 그 값을 **NaviHelper SDK**로 받아 온다
- 앱은 내비처럼 보이는 안내 화면을 직접 그린다. 지도는 **MapLibre** + OpenStreetMap 기반 공개 지도. Pleos Map SDK를 쓸 수 있게 되면 바꾼다. TMAP 등 상용 지도는 쓰지 않는다

## 먼저 읽을 것

- `docs/mvp.md` — 범위와 마일스톤
- `docs/adr/` — 결정 사항. Accepted된 ADR을 어기는 구현은 하지 않는다

## 소유 영역

| 경로 (`app/src/main/java/com/example/pleosproj/` 기준) | 권한 |
| --- | --- |
| `navi/`, `vehicle/`, `location/`, `voice/`, `weather/`, `traffic/`, `risk/`, `scenario/`, `app/` | 자유롭게 수정 |
| `ui/state/` (화면 상태 모델) | 수정 가능. 단, 변경은 별도 PR로 올리고 사람 승인 후 머지 |
| `ui/` (`ui/state/` 제외), `MainActivity.kt`, `res/` | **수정 금지.** 필요한 변경은 `화면` 라벨 Linear 이슈로 요청한다 |
| `AndroidManifest.xml`, Gradle 파일 | 수정 가능. 권한·의존성 추가·삭제는 먼저 물어본다 |
| `docs/adr/` | 새 ADR을 `Proposed`로 작성만. Accepted 처리 금지 |

## 개발 규칙

- Pleos SDK 사용법은 공식 문서보다 **라이브러리 원본 코드와 공식 예제 앱**(github.com/Pleos-Playground)을 먼저 확인한다. 문서 페이지는 내용이 비어 보일 때가 많다.
- 화면에는 `ui/state/`의 모델만 넘긴다. SDK 객체(NaviHelper 데이터 클래스 등)를 화면에 직접 넘기지 않는다.
- 위험도 계산(`risk/`)은 SDK·네트워크와 분리된 순수 Kotlin 코드로 만들고 단위 테스트를 붙인다.
- 비밀값(API 키)은 `local.properties`에만 둔다. 코드·이슈·PR에 붙여 넣지 않는다.
- 동작 확인은 Pleos 에뮬레이터에서 한다. 확인한 화면은 `adb exec-out screencap -p`로 찍어 PR에 남긴다.

<!-- 아래 "작업 규칙"은 AGENTS.md와 같은 내용이다. 한쪽을 고치면 다른 쪽도 같이 고친다. -->

## 작업 규칙

### Linear

- 할 일은 Linear 워크스페이스 SCSBlog, 팀 `Pleos`(키 `PLE`), 프로젝트 "1차 제출"에서 관리한다.
- **이슈 없는 작업은 하지 않는다.** 필요하면 이슈부터 만든다.
- 이슈 담당자(assignee)는 항상 사람(본인)이다. 누가 작업할지는 라벨로 구분한다.

| 라벨 | 작업하는 쪽 |
| --- | --- |
| `개발` | Claude |
| `화면` | Codex |
| `디자인` | 팀원(디자이너) |
| `기획`, `인프라` | 사람 |

- 자기 라벨이 붙은 이슈만 맡는다. 상대 영역에 변경이 필요하면 직접 고치지 않고, 상대 라벨을 단 이슈를 만든다.
- 이슈 본문은 이 6개 섹션으로 쓴다: `왜 하나요` / `무엇을 하나요` / `선행 작업` / `완료 조건` (Given/When/Then) / `하지 않는 것` / `참고 자료`.
  `선행 작업`은 Linear의 막힘(blocked by) 관계와 맞춘다.

### 이슈 상태

| 상태 | 기준 |
| --- | --- |
| Backlog | 막고 있는 이슈가 아직 안 끝났다 |
| Todo | 막힌 것 없이 바로 시작할 수 있다 |
| In Progress | 브랜치를 만든 시점부터 PR 머지 전까지 |
| Done | PR이 머지되고 완료 조건을 모두 채웠다 |

- 작업을 시작할 때 막힘 관계를 확인한다. 막고 있는 이슈가 안 끝났으면 시작하지 않고 사람에게 알린다.
- Done으로 바꿀 때, 그 이슈에 막혀 있던 이슈 중 막힘이 모두 풀린 것을 Todo로 옮긴다.

### 화면 상태 모델 (Claude와 Codex 사이의 약속)

- `ui/state/`의 data class가 화면에 넘기는 데이터의 모양이다. Claude가 값을 채우고, Codex가 그 값으로 화면을 그린다.
- 바꿀 때는 그 변경만 담은 별도 PR을 올리고 사람 승인 후 머지한다. 상대 쪽 코드가 깨지지 않게 같은 PR에서 예시 데이터(`ui/state/` 안의 preview 값)도 맞춘다.
- 실제 데이터가 아직 없으면 예시 데이터로 화면을 먼저 만든다.

### 작업 공간

- Claude와 Codex는 **각자 다른 작업 폴더(git worktree)**에서 일한다. 같은 폴더에서 브랜치를 바꿔 가며 일하지 않는다.
- 에뮬레이터는 하나를 같이 쓴다. 설치 전에 다른 쪽이 확인 중인지 사람에게 묻거나, 확인이 끝난 뒤 설치한다.

### 브랜치

- 형식: `타입/ple-번호-영어-설명` (모두 소문자, 하이픈 연결. 예: `feat/ple-12-navihelper-check`)
- Linear가 제안하는 gitBranchName은 쓰지 않는다 (사용자 이름과 한국어 제목이 들어간다).
- 타입은 커밋 타입과 같다.

### 커밋

- 형식: `타입(범위): 한국어 설명`. 범위는 `navi`, `vehicle`, `risk`, `ui`, `map`처럼 바뀐 영역 (선택)
- 타입: `feat` 기능 추가 · `fix` 버그·문구 수정 · `refactor` 동작 변화 없는 구조 개선 · `style` 포맷만 수정 · `docs` 문서 · `test` 테스트 · `chore` 빌드·설정
- 커밋 하나에는 변경 하나만 담고, 그 커밋만으로도 빌드가 통과해야 한다.

### PR

- `main`으로 PR을 연다. **`main`에 직접 push하지 않는다.**
- 제목: `타입: 한국어 설명 (PLE-번호)`
- 본문은 `.github/pull_request_template.md` 양식대로 쓰고, 이슈의 완료 조건을 하나씩 확인한 결과를 적는다. 화면이 바뀌면 에뮬레이터 스크린샷을 붙인다.
- **머지는 사람이 한다.** 에이전트는 PR을 만드는 데까지만 한다.

### 먼저 물어볼 것

- 패키지(Gradle 의존성) 추가·삭제
- `AndroidManifest.xml` 권한 추가·삭제
- 린트·포맷·CI 설정 변경
- 환경변수, 비밀값 관련 수정
- 외부 유료·상용 서비스(지도, 길찾기, 검색 API 등) 도입
- 이슈의 `하지 않는 것`에 해당하는 작업이 필요해 보일 때

### 빌드·실행

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug        # 빌드
./gradlew testDebugUnitTest    # 단위 테스트
./gradlew installDebug         # 실행 중인 Pleos 에뮬레이터에 설치
```

### 언어

- 커밋 설명, PR, Linear 이슈와 댓글은 한국어로 쓴다. 코드, 경로, 고유명사는 원문 그대로 둔다.
- **코드 주석은 한국어로 쓴다.** 식별자, 경로, 고유명사, 주소는 원문 그대로 둔다. 자동 생성 파일은 예외다.
- PR 본문은 다나까체("~했습니다", "~입니다")로 쓴다.
- PR 본문에 도구 서명 문구(예: "Generated with …")를 넣지 않는다.
