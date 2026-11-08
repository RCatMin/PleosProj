# Architecture Decision Records

되돌리기 어렵거나 개발과 화면 양쪽에 영향을 주는 결정을 기록한다.

## 규칙

- 파일명: `NNNN-짧은-제목.md`, 번호는 증가만 한다
- 상태: `Proposed` → `Accepted` / `Rejected`, 이후 바뀌면 `Superseded by NNNN`
- **Accepted 처리는 사람만 한다.** 에이전트는 Proposed로 작성까지만 한다
- 이미 Accepted된 ADR은 고치지 않고, 새 ADR로 대체한다

## 목록

| 번호 | 제목 | 상태 |
| --- | --- | --- |
| [0001](./0001-app-structure.md) | 앱 구조 | Proposed |
| [0002](./0002-map-provider.md) | 지도 | Proposed |
| [0003](./0003-risk-scoring.md) | 위험도 계산 | Proposed |
| [0004](./0004-built-in-navi-role.md) | 기본 내비와 역할 나누기 | Superseded by 0007 |
| [0005](./0005-route-line-and-demo.md) | 경로선 표시와 시연 방식 | Superseded by 0007 |
| [0006](./0006-sdk-fallback.md) | Pleos SDK를 쓸 수 없을 때의 대체 | Proposed |
| [0007](./0007-own-routing.md) | 경로 계산과 장소 검색을 우리 앱이 한다 | Proposed (경로 계산 서비스는 0008로 대체) |
| [0008](./0008-kakao-mobility-routing.md) | 경로 계산은 카카오모빌리티 길찾기로 한다 | Proposed |

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
