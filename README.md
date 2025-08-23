## Hierarchy-Cache

계층형 데이터(부모-자식 트리)를 빠르게 조회하기 위해 Redis에 부모/자식 집합(Set) 형태로 캐시하고,
이벤트 소싱 + 커서 + 분산락으로 정합성을 유지하는 샘플 프로젝트입니다.

### 배경과 문제점
- **깊은 재귀 조회 비용**: DB에서 트리를 하향식으로 재귀 탐색하면 N+1 쿼리 및 성능 저하가 발생합니다.
- **동시 수정 경쟁**: 다중 쓰레드/인스턴스가 동시에 읽고 쓰면 캐시와 DB 간 불일치가 생길 수 있습니다.
- **부분적 실패/유실**: 캐시 오염(플러시, 만료) 또는 커서 유실 시 캐시와 이벤트 처리 상태가 어긋날 수 있습니다.

### 목표
- **빠른 하위 노드 접근**: 루트에서 하위 전체(손자 포함) 집합을 O(1)에 가깝게 가져오기.
- **정합성 보장**: 이벤트 순서를 커서로 보장하고, 잠금으로 경쟁 상태 최소화.
- **자동 복구**: 커서/캐시가 오염되더라도 읽기 경로에서 자동 복구.

---

## 설계 요약

### 데이터 모델
- `HierarchyGroup`: `id(UUID)`, `tenantId`, `parent`, `children`
- `HierarchyGroupEvent`: `id(TSID)`, `targetId`, `fromId`, `toId` → `CREATE | UPDATE | DELETE`

### Redis 키 설계
- 프리픽스: `{tenant}:group`
- 부모 집합: `{tenant}:group:{groupId}:parents`
- 자식 집합: `{tenant}:group:{groupId}:children`
- 커서: `{tenant}:group:cursor` (마지막 처리 이벤트 ID)
- 락: `{tenant}:group:lock`

### 일관성 전략
- **이벤트 소싱**: 쓰기 작업은 `HierarchyGroupEvent` 테이블에 기록됩니다.
- **커서**: 마지막 처리 이벤트 ID를 Redis에 저장하여 미처리 이벤트만 정순으로 적용합니다.
- **분산락**: 테넌트 단위 락 키로 읽기 경로에서 단일 처리 보장(`SET NX PX`).
- **초기화/증분 갱신**: 최초 접근 시 전체 트리로 캐시를 초기화, 이후에는 커서 이후 이벤트만 반영.
- **폴백**: 장애/락 실패 시 DB 재귀로 결과를 생성하여 가용성을 확보합니다.

### 읽기 경로 개요
1) 테넌트 락 획득 실패 시 → DB 재귀 폴백 반환
2) 커서 기준 미반영 이벤트 조회
3) 캐시 미초기화면 전체 초기화, 아니면 이벤트를 순서대로 적용
4) 커서 업데이트, `{groupId}`의 자식 집합 반환

---

## 실행 방법

### 요구사항
- Java 21, Gradle, Redis(로컬 `localhost:6379`)

### 설정 파일
- `src/main/resources/application.yml`: H2 in‑memory, Redis 호스트/포트, JPA 설정

### 빌드/테스트/실행
```bash
./gradlew test       # 통합/캐시/리포지토리 테스트
./gradlew bootRun    # 애플리케이션 실행 (포트 8080)
```

### 샘플 API
- `GET /api/groups/{groupId}/children` → `{groupId}`의 하위 전체 집합 반환
```bash
curl http://localhost:8080/api/groups/<ROOT_GROUP_ID>/children
```

---

## 테스트 시나리오(요약)
- 대량 데이터 초기화 검증(H2 `data.sql`, 총 30,000 행)
- 캐시 초기화/증분 갱신, 생성/이동/삭제 반영 검증
- 커서 유실 후 읽기 시 자동 복구
- 락 미획득 시 DB 폴백
- 동시성 경쟁 상황에서 단일락 보장 및 결과 일관성 확인

---

## 한계와 개선 계획
- **오염/복구 관측성**: 복구 이벤트/커서 이동 로깅과 메트릭(성공/실패/지연) 추가 필요
- **에러 처리/로깅**: `printStackTrace()` 제거, 구조화 로깅 및 예외 분류 적용
- **초기화 비용**: 대용량 테넌트 최초 접근 시 전체 초기화 비용 큼 → 백그라운드 프리워밍/배치 초기화 고려
- **메모리/TTL 전략**: Set 키 수 증가에 따른 메모리 관리와 TTL/eviction 정책 재검토 필요
- **트랜잭션 경계**: 이벤트 기록과 캐시 반영 간 타이밍 이슈 최소화를 위한 아웃박스/리트라이나 이벤트 일괄 적용 전략 검토

---

## 구조 요약
- 애플리케이션: Spring Boot 3.5, Java 21, Spring Data JPA(H2), Spring Data Redis, Lombok, TSID
- 주요 컴포넌트: `HierarchyGroup(Read/Command)Service`, `RedisCacheService`, `RedisLockService`
- 데이터 소스: `data.sql`로 초기 데이터 로드(테스트/데모 목적)

필요 시 운영환경용 설정/모니터링/메트릭 예시를 추가해 드릴 수 있습니다.
