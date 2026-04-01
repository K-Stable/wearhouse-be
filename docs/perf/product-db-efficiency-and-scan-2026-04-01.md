# Product DB 효율 측정 리포트 (Rows Examined / Full Scan / SQL Count)

- 일시: 2026-04-01 (KST)
- 환경: EKS `wearhouse` + MySQL(RDS)
- 대상 API
  - Seller keyword-only: `GET /product-service/api/v1/seller/products?keyword=pants&limit=20`
  - Buyer category+sort: `GET /product-service/api/v1/buyer/products?category=TOP&sort=PRICE_HIGH&limit=20`
- 부하 조건: 각 시나리오 `200 요청 (10 VU x 20 iterations)`
- 수집 소스
  - `performance_schema.events_statements_summary_by_digest`
  - `EXPLAIN`, `EXPLAIN ANALYZE`
  - k6 summary

## 1) 핵심 결과

| 시나리오 | 성공/실패 | p95(ms) | p99(ms) | 요청당 SQL 수 | 요청당 Rows Examined | 요청당 Rows Sent | Examined/Sent 비율 |
|---|---:|---:|---:|---:|---:|---:|---:|
| seller keyword-only | 200 / 0 | 103.73 | 110.37 | 10.99 | 39.00 | 5.00 | 7.80 |
| buyer category+sort | 200 / 0 | 123.29 | 126.96 | 8.97 | 143.84 | 135.84 | 1.06 |

해석:
- `seller keyword-only`는 결과 row는 적지만(`5`) 검색 후보를 더 많이 훑는 패턴이라 `Examined/Sent`가 상대적으로 큼.
- `buyer category+sort`는 반환 row와 탐색 row가 거의 비슷해 효율 비율(1.06)이 양호함.

## 2) Full Table Scan 비율

`performance_schema` 집계 기준 (`SUM_NO_INDEX_USED`, `SUM_NO_GOOD_INDEX_USED`):

| 시나리오 | Full scan digest 수 / 전체 digest 수 | Full scan digest 비율 | Full scan select 건수 / 전체 select 건수 | Full scan select 비율 |
|---|---:|---:|---:|---:|
| seller keyword-only | 0 / 3 | 0.00% | 0 / 2,198 | 0.00% |
| buyer category+sort | 0 / 2 | 0.00% | 0 / 1,794 | 0.00% |

결론:
- 이번 측정 구간에서 풀스캔은 관측되지 않음.

## 3) EXPLAIN / EXPLAIN ANALYZE 근거

### Seller keyword-only

EXPLAIN:
- key: `idx_product_seller_id`
- rows: `39`
- filtered: `100.00`
- Extra: `Using where; Backward index scan`

EXPLAIN ANALYZE:
- index lookup rows(actual): `39`
- filter 후 rows(actual): `5`
- limit rows(actual): `5`

### Buyer category+sort

EXPLAIN:
- key: `idx_product_category_created_at`
- rows: `8`
- filtered: `100.00`
- Extra: `Using where; Using filesort`

EXPLAIN ANALYZE:
- index lookup rows(actual): `8`
- filter 후 rows(actual): `8`
- sort+limit rows(actual): `8`

## 4) raw 파일 위치

- `docs/perf/raw/db-efficiency-2026-04-01/summary.csv`
- `docs/perf/raw/db-efficiency-2026-04-01/*-digest.tsv`
- `docs/perf/raw/db-efficiency-2026-04-01/*-agg.tsv`
- `docs/perf/raw/db-efficiency-2026-04-01/explain-*.txt`
- `docs/perf/raw/db-efficiency-2026-04-01/*-k6-summary.json`

## 5) 측정 시 주의사항

- `rows examined/sent`는 해당 구간의 SELECT digest 집계를 요청 수로 나눈 값이라, 정확히 특정 단일 쿼리 1개만을 의미하지는 않음.
- 하지만 요청당 DB 효율 변화 추세(개선/악화) 비교에는 유효함.
