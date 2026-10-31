# Architecture Decision Records

되돌리기 어렵거나, Claude(개발)와 Codex(화면) 양쪽에 영향을 주는 결정을 기록한다.

## 규칙

- 파일명: `NNNN-짧은-제목.md`, 번호는 증가만 한다
- 상태: `Proposed` → `Accepted` / `Rejected`, 이후 바뀌면 `Superseded by NNNN`
- **Accepted 처리는 사람만 한다.** 에이전트는 Proposed로 작성까지만 한다
- 이미 Accepted된 ADR은 고치지 않고, 새 ADR로 대체한다

## 목록

| 번호 | 제목 | 상태 |
| --- | --- | --- |
| [0001](./0001-app-structure.md) | 앱 구조: 단일 모듈, Kotlin + Compose, 패키지별 소유 영역 | Proposed |
| [0002](./0002-map-provider.md) | 지도: MapLibre + OpenStreetMap 기반 공개 지도 | Proposed |
| [0003](./0003-risk-scoring.md) | 위험도 계산 방식 | Proposed |
| [0004](./0004-built-in-navi-role.md) | 기본 내비와 역할 분담 (방법 1) | Proposed |
| [0005](./0005-route-line-and-demo.md) | 경로선 표시와 시연 방식 (C안) | Proposed |
| [0006](./0006-sdk-fallback.md) | Pleos SDK 대체 구조 | Proposed |

## 템플릿

```markdown
# NNNN. 제목

- 상태: Proposed
- 날짜: YYYY-MM-DD

## 맥락
어떤 문제가 있고, 왜 지금 결정해야 하는가.

## 결정
무엇을 하기로 했는가.

## 검토한 대안
- 대안 A — 채택하지 않은 이유

## 결과
이 결정으로 쉬워지는 것, 어려워지는 것, 나중에 다시 볼 조건.
```
