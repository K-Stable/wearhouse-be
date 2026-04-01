# Product Service `@ReadTx` vs `@Transactional(readOnly = true)` 벤치마크 (2026-03-31)

## 1) 목적
`product-service` 조회 메서드 중 Lazy 컬렉션(`images`, `options`) 접근이 있는 경로에서,
- 현재(`@ReadTx`: `readOnly + SUPPORTS`)와
- 변경안(`@Transactional(readOnly = true)`)
의 성능/안정성 차이를 실제 실행으로 비교했다.

대상 메서드:
- `BuyerProductQueryService#getBuyerProducts`
- `BuyerProductQueryService#getBuyerProductDetail`
- `SellerProductQueryService#getSellerProducts`
- `SellerProductQueryService#getSellerProduct`

## 2) 테스트 조건
- 실행: `./gradlew :product:test --tests "com.wearhouse.product.performance.ProductQueryServiceFlowBenchmarkTest" --rerun-tasks --no-daemon --console=plain`
- 데이터: 상품 30개 시드(옵션 2개 + 이미지 3개/상품)
- 워밍업: 각 flow 50회
- 측정: 각 flow 400회
- 환경: `@DataJpaTest`, `spring.jpa.open-in-view=false`, 외부 클라이언트 mock
- 결과 소스: `product/build/test-results/test/TEST-com.wearhouse.product.performance.ProductQueryServiceFlowBenchmarkTest.xml`

## 3) Before/After 결과

| Flow | Before avg(ms) | After avg(ms) | Delta | Before p95 | After p95 | Delta | Before p99 | After p99 | Delta | Before TPS | After TPS | Delta |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| buyer.getBuyerProductDetail | 57.338 | 26.862 | -53.2% | 216.796 | 149.362 | -31.1% | 799.117 | 436.646 | -45.4% | 17.4 | 37.2 | +113.8% |
| buyer.getBuyerProducts | 54.383 | 50.759 | -6.7% | 279.509 | 267.708 | -4.2% | 630.930 | 602.108 | -4.6% | 18.4 | 19.7 | +7.1% |
| seller.getSellerProduct | 38.253 | 37.686 | -1.5% | 179.774 | 201.902 | +12.3% | 458.310 | 543.614 | +18.6% | 26.1 | 26.5 | +1.5% |
| seller.getSellerProducts | 24.227 | 81.146 | +235.0% | 130.346 | 379.848 | +191.4% | 398.333 | 1507.177 | +278.3% | 41.1 | 12.3 | -70.1% |

요약:
- Buyer 상세/목록은 개선.
- Seller 단건은 큰 차이 없음.
- Seller 목록은 이번 측정에서 큰 폭으로 악화.

## 4) 병목 근거(실제 로그 계수)
동일 테스트 XML에서 SQL 패턴을 계수:
- `from product_image ... where product_id=?`: **41,400회**
- `from product_option ... where product_id=?`: **18,900회**
- seller 목록 기본 product 조회: **900회**

해석:
- 목록/상세 매핑 과정에서 `images`, `options` Lazy 접근이 대량 발생(N+1 패턴).
- 트랜잭션 애노테이션 변경만으로는 근본 병목(N+1) 제거가 되지 않음.

## 5) 결론
1. `@ReadTx -> @Transactional(readOnly=true)` 변경은 **경로별 효과가 다름**.
2. 현재 병목 핵심은 트랜잭션 타입보다, 목록 응답 조립 시 발생하는 **Lazy N+1 쿼리**.
3. 다음 개선 우선순위:
   - seller/buyer 목록용 전용 조회(Projection)로 이미지/옵션 필요 필드만 한번에 조회
   - `images`/`options` batch fetch 전략 적용(`@BatchSize` 또는 벌크 조회)
   - 상세 API는 `EntityGraph`/fetch join 전략 재점검

## 6) 이번 변경 사항
적용 파일:
- `product/src/main/java/com/wearhouse/product/domain/service/buyer/BuyerProductQueryService.java`
- `product/src/main/java/com/wearhouse/product/domain/service/seller/SellerProductQueryService.java`

변경 내용:
- 아래 4개 메서드를 `@Transactional(readOnly = true)`로 변경
  - `getBuyerProducts`
  - `getBuyerProductDetail`
  - `getSellerProducts`
  - `getSellerProduct`
- 시즌 조회 3개 메서드는 기존 `@ReadTx` 유지.
