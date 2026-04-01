# Product 검색 성능 점검 (QueryDSL vs 비-QueryDSL) + N+1 제거

- 일시: 2026-04-01
- 모듈: `product-service`
- 목적:
  - 검색 시나리오에서 QueryDSL 사용 방식과 비-QueryDSL(derived query) 비교
  - 리스트 조회 N+1 제거 적용 및 회귀 방지

## 1) 적용한 코드 변경

### N+1 제거 (리스트 조회)
- 대상:
  - `BuyerProductQueryService#getBuyerProducts`
  - `SellerProductQueryService#getSellerProducts`
- 방식:
  - 1단계: `product` 목록만 커서 조건으로 조회 (기존 QueryDSL 유지)
  - 2단계: 조회된 `productIds`로 `product_image`, `product_option`를 `IN` 일괄 조회
  - 3단계: `productId` 기준으로 그룹핑 후 mapper에서 사용
- 효과:
  - 기존의 `product -> images/options` lazy 접근 반복을 제거
  - 요청당 SQL 개수를 `N+1` 패턴에서 `고정 개수` 패턴으로 전환

### 추가/변경 파일
- `product/src/main/java/com/wearhouse/product/domain/service/buyer/BuyerProductQueryService.java`
- `product/src/main/java/com/wearhouse/product/domain/service/seller/SellerProductQueryService.java`
- `product/src/main/java/com/wearhouse/product/buyer/mapper/BuyerProductResponseMapper.java`
- `product/src/main/java/com/wearhouse/product/seller/mapper/SellerProductResponseMapper.java`
- `product/src/main/java/com/wearhouse/product/domain/service/common/ProductImageUrlResolver.java`
- `product/src/main/java/com/wearhouse/product/domain/repository/ProductImageRepository.java`
- `product/src/main/java/com/wearhouse/product/domain/repository/ProductOptionRepository.java`
- `product/src/main/java/com/wearhouse/product/domain/entity/ProductImageEntity.java`
- `product/src/main/java/com/wearhouse/product/domain/entity/ProductOptionEntity.java`

## 2) QueryDSL vs 비-QueryDSL 비교 테스트

### 테스트 클래스
- `ProductQueryDslVsDerivedQueryBenchmarkTest`

### 측정 시나리오
1. 카테고리 + 정렬
   - QueryDSL: `findBuyerProductsByCursor(..., PRICE_HIGH, limit)`
   - Derived: `findByStatusAndCategoryOrderByPriceDescIdDesc(..., Pageable)`
2. 키워드만 검색
   - QueryDSL: `findSellerProductsByCursor(..., keyword, ..., limit)`
   - Derived: `findBySellerIdAndNameContainingIgnoreCaseOrderByIdDesc(..., Pageable)`

### 실행 결과

#### 카테고리 + 정렬
- `buyer.category+sort.querydsl`
  - avg: `0.300 ms`
  - p95: `0.546 ms`
  - p99: `1.540 ms`
  - throughput: `3340.8 req/s`
  - prepared/req: `1.000`
  - queryExec/req: `1.000`
- `buyer.category+sort.derived`
  - avg: `0.333 ms`
  - p95: `0.574 ms`
  - p99: `1.011 ms`
  - throughput: `3006.0 req/s`
  - prepared/req: `1.000`
  - queryExec/req: `1.000`

#### 키워드만 검색
- `seller.keyword.querydsl`
  - avg: `0.165 ms`
  - p95: `0.242 ms`
  - p99: `0.669 ms`
  - throughput: `6097.6 req/s`
  - prepared/req: `1.000`
  - queryExec/req: `1.000`
- `seller.keyword.derived`
  - avg: `0.215 ms`
  - p95: `0.331 ms`
  - p99: `0.607 ms`
  - throughput: `4672.9 req/s`
  - prepared/req: `1.000`
  - queryExec/req: `1.000`

### 해석
- 두 시나리오 모두 **요청당 SQL 1회**로 동작.
- QueryDSL이 dynamic 조건 조합을 유지하면서도 derived query 대비 평균/처리량에서 유리하거나 동급 성능.
- 즉, QueryDSL 도입 효과를 “가독성”이 아니라 **동적 조건 유지 + SQL 효율 유지**로 어필 가능.

## 3) N+1 회귀 방지 테스트

### 테스트 클래스
- `ProductSearchNPlusOneGuardTest`

### 검증 기준
- buyer list: query execution count `<= 3`
- seller list: query execution count `<= 4`

### 결과
- 테스트 통과 (`BUILD SUCCESSFUL`)
- 리스트 조회에서 N+1 형태의 폭증 쿼리 패턴 미발생 확인

## 4) 실행 커맨드

```bash
./gradlew :product:test \
  --tests "com.wearhouse.product.performance.ProductQueryDslVsDerivedQueryBenchmarkTest" \
  --tests "com.wearhouse.product.performance.ProductSearchNPlusOneGuardTest"
```

## 5) 포트폴리오 어필 문장 예시

- “상품 검색에 QueryDSL을 적용해 동적 조건(카테고리/정렬/키워드/커서)을 일관된 SQL 1회 실행 구조로 유지했습니다.”
- “리스트 조회에서 `product -> images/options` lazy 반복 접근을 제거하고, `productIds IN (...)` 일괄 조회로 N+1 문제를 구조적으로 해소했습니다.”
- “회귀 방지를 위해 query execution count 상한 테스트를 추가해, N+1 재유입을 CI에서 차단하도록 설계했습니다.”
