# Transactional Read Overhead Benchmark (2026-03-30)

## 목적
- `@ReadTx(readOnly=true, propagation=SUPPORTS)`
- `@Transactional(readOnly=true)` (기본 `REQUIRED`)
- 트랜잭션 없는 조회(`NO_TX`)

위 3가지 조회 경로를 비교해, 조회 트래픽이 높은 구간(구매자 상품 목록/상품 상세)에서의 오버헤드 차이를 확인한다.

## 참고 기준
- 카카오페이 아티클: `JPA Transactional 잘 알고 쓰고 계신가요?`
- 핵심 가설: readOnly만으로는 트랜잭션 시작/커밋 오버헤드가 제거되지 않으며, propagation 선택이 중요하다.

## 테스트 1: 오버헤드 발생 횟수/확률
- 테스트 파일: `common/src/test/java/com/wearhouse/common/global/transactional/TransactionalPropagationBehaviorTest.java`
- 실행: `./gradlew :common:test --tests "*TransactionalPropagationBehaviorTest.measureOverheadOccurrenceRate" --rerun-tasks`
- 방식: 단건 호출 1,000회 반복 시, 각 호출에서 `setAutoCommit/commit/setReadOnly` 발생 여부 집계

### 결과
| Mode | occurrence | occurrence rate | avg setAutoCommit(false) | avg commit | avg setReadOnly |
|---|---:|---:|---:|---:|---:|
| `@WriteTx` | 1000/1000 | 100.00% | 1.00 | 1.00 | 0.00 |
| `@Transactional` | 1000/1000 | 100.00% | 1.00 | 1.00 | 0.00 |
| `@ReadTx` (`SUPPORTS`) | 0/1000 | 0.00% | 0.00 | 0.00 | 0.00 |
| `@Transactional(readOnly=true)` | 1000/1000 | 100.00% | 1.00 | 1.00 | 2.00 |
| `@Transactional(readOnly=true, SUPPORTS)` | 0/1000 | 0.00% | 0.00 | 0.00 | 0.00 |

## 테스트 2: Product 실제 조회 경로 성능
- 테스트 파일: `product/src/test/java/com/wearhouse/product/performance/ProductReadTransactionalPerformanceTest.java`
- 실행: `./gradlew :product:test --tests "*ProductReadTransactionalPerformanceTest" --rerun-tasks`
- 조건
  - `osiv=false`
  - H2 in-memory
  - seed data: released product 30건 (각 1 option)
  - 워밍업 후 측정
  - 리스트: 2,000회
  - 상세(findByIdAndStatus): 3,000회

### 2-1. buyer product detail (`findByIdAndStatus`)
| Mode | avg(ms) | p95(ms) | p99(ms) | throughput(req/s) |
|---|---:|---:|---:|---:|
| `TX_READ_ONLY_REQUIRED` | 3.302 | 8.299 | 30.457 | 302.5 |
| `NO_TX` | 4.415 | 11.697 | 43.886 | 226.3 |
| `READ_TX_SUPPORTS` | 7.617 | 24.816 | 151.548 | 130.4 |

### 2-2. buyer product list (`findBuyerProductsByCursor`)
| Mode | avg(ms) | p95(ms) | p99(ms) | throughput(req/s) |
|---|---:|---:|---:|---:|
| `READ_TX_SUPPORTS` | 1.446 | 3.212 | 27.097 | 688.0 |
| `NO_TX` | 1.459 | 3.528 | 15.962 | 684.5 |
| `TX_READ_ONLY_REQUIRED` | 1.976 | 4.548 | 30.426 | 505.4 |

## 해석
1. "오버헤드 발생 여부" 관점에서는 차이가 명확하다.
   - `@Transactional(readOnly=true)`는 호출마다 트랜잭션 제어 동작이 발생한다.
   - `@ReadTx(SUPPORTS)`는 단독 조회 호출에서 해당 동작이 발생하지 않는다.
2. 실제 조회 성능(H2 기준)은 쿼리 타입/데이터 분포에 따라 상대 순위가 바뀔 수 있다.
   - 즉, 어노테이션 자체보다 쿼리 패턴/캐시/실행계획 영향이 함께 반영된다.
3. 실서비스 검증은 MySQL + 네트워크 포함 환경에서 재측정이 필요하다.

## 결론
- 정책 레벨 권장:
  - 조회 경로 기본값: `@ReadTx` (`readOnly=true, SUPPORTS`)
  - 변경 경로: `@WriteTx` (`REQUIRED`)
- 성능 검증 레벨:
  - H2 결과는 방향성 확인용
  - 운영 반영 전에는 MySQL 환경에서 p95/p99 재검증 권장

## 테스트 3: MySQL/RDS 실측 오버헤드 (2026-03-30)
- 대상: `wearhouse-mysql` (RDS MySQL, ap-northeast-2)
- 실행 위치: 로컬 직결은 SG 제한으로 타임아웃되어, `wearhouse` 네임스페이스 임시 Pod에서 실행
- 쿼리 패턴: `SELECT id FROM benchmark_order WHERE transaction_id = ?` (`findById` 유사)
- 데이터: 임시 벤치 테이블 `benchmark_order` 30건 삽입 후 측정, 완료 후 테이블 삭제
- 반복:
  - 워밍업 400회
  - 실측 3000회
  - 2회 반복 실행
- 비교 모드:
  - `NO_TX_SUPPORTS_EQUIV` = 단일 SELECT (Spring `@ReadTx(SUPPORTS)` 단독 호출과 동등한 동작)
  - `TX_READ_ONLY_REQUIRED` = `START TRANSACTION READ ONLY -> SELECT -> COMMIT`

### 3-1. 실행별 결과
| Run | Mode | avg(ms) | p95(ms) | p99(ms) | throughput(req/s) | commit delta |
|---|---|---:|---:|---:|---:|---:|
| 1 | `NO_TX_SUPPORTS_EQUIV` | 1.723 | 1.855 | 2.188 | 579.0 | 0 |
| 1 | `TX_READ_ONLY_REQUIRED` | 5.424 | 6.403 | 11.052 | 184.1 | 3000 |
| 2 | `NO_TX_SUPPORTS_EQUIV` | 1.598 | 1.735 | 2.069 | 624.5 | 0 |
| 2 | `TX_READ_ONLY_REQUIRED` | 4.594 | 4.823 | 6.149 | 217.4 | 3000 |

### 3-2. 2회 평균
| Mode | avg(ms) | p95(ms) | p99(ms) | throughput(req/s) | commit delta(avg) |
|---|---:|---:|---:|---:|---:|
| `NO_TX_SUPPORTS_EQUIV` | 1.661 | 1.795 | 2.128 | 601.8 | 0 |
| `TX_READ_ONLY_REQUIRED` | 5.009 | 5.613 | 8.601 | 200.8 | 3000 |

### 3-3. 해석
1. RDS 실측에서 `TX_READ_ONLY_REQUIRED`는 `NO_TX_SUPPORTS_EQUIV` 대비 평균 지연이 약 `3.02x` 증가.
2. 처리량은 약 `66.6%` 감소 (`601.8 -> 200.8 req/s`).
3. `commit delta`가 `3000`으로 집계되어, 트랜잭션 제어 오버헤드가 호출마다 실제 발생함을 확인.
