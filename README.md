# Hierarchy-Cache

계층형 데이터 구조를 위한 Redis 기반 캐시 일관성 관리 전략

---

## 계층형 데이터

- root
  - child1 ... child10000
    - child2 ...

---

## 문제점

- 계층 구조 재귀 탐색으로 인한 성능 저하

---

## 목표

- 손자 객체까지 빠르게 접근 가능한 구조 확보
- 테넌트 단위 분산 락으로 충돌 방지

---

## 개선안1

---

## 개선안2

- CUD
  - 이벤트 소싱 테이블을 통한 변경 이력 기록
- R
  - 조회 시 미반영 이벤트 처리 → Redis 캐시 갱신
- 커서 갱신으로 순서 보장
- 락 기반 동기화로 다중 접근 제어
- Hash + JSON 구조 활용

### 구조

- Redis Hash 구조: `{tenant}:hierarchyGroup:{UUID}`
    - `parents`: 상위 UUID 목록 (JSON 문자열)
    - `children`: 하위 UUID 목록 (JSON 문자열)
- 캐시 갱신은 조회 시점에만 수행
- 커서(`tenant:cursor:{tenant}:{domain}`)와 테넌트 락(`lock:tenant:{tenant}:{domain}:{entityId}`)으로 정합성 보장

---

## TODO



---