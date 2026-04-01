# Product 조회 트랜잭션 병목 점검 보고서 (2026-03-31)

## 1. 배경
`product-service`에서 `@ReadTx`(`readOnly + SUPPORTS`)를 사용하는 조회 메서드 중,
Lazy 컬렉션(`images`, `options`) 접근이 있는 경로에서 병목 가능성이 확인되어 실제 성능 비교를 진행했다.

비교 대상:
- 기존: `@ReadTx`
- 개선: `@Transactional(readOnly = true)`

## 2. 테스트 대상 메서드
- `BuyerProductQueryService#getBuyerProducts`
- `BuyerProductQueryService#getBuyerProductDetail`
- `SellerProductQueryService#getSellerProducts`
- `SellerProductQueryService#getSellerProduct`

## 3. 실행 조건
- 실행 명령:
  - `./gradlew :product:test --tests "com.wearhouse.product.performance.ProductQueryServiceFlowBenchmarkTest" --rerun-tasks --no-daemon --console=plain`
- 데이터:
  - 상품 30개 시드
  - 상품당 옵션 2개, 이미지 3개
- 워밍업:
  - flow별 50회
- 측정:
  - flow별 400회
- 환경:
  - `@DataJpaTest`
  - `spring.jpa.open-in-view=false`
  - 외부 클라이언트(`Inventory`, `S3`)는 mock

## 4. Before / After 비교

| Flow | Before avg(ms) | After avg(ms) | Delta | Before p95 | After p95 | Delta | Before p99 | After p99 | Delta | Before TPS | After TPS | Delta |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| buyer.getBuyerProductDetail | 57.338 | 26.862 | -53.2% | 216.796 | 149.362 | -31.1% | 799.117 | 436.646 | -45.4% | 17.4 | 37.2 | +113.8% |
| buyer.getBuyerProducts | 54.383 | 50.759 | -6.7% | 279.509 | 267.708 | -4.2% | 630.930 | 602.108 | -4.6% | 18.4 | 19.7 | +7.1% |
| seller.getSellerProduct | 38.253 | 37.686 | -1.5% | 179.774 | 201.902 | +12.3% | 458.310 | 543.614 | +18.6% | 26.1 | 26.5 | +1.5% |
| seller.getSellerProducts | 24.227 | 81.146 | +235.0% | 130.346 | 379.848 | +191.4% | 398.333 | 1507.177 | +278.3% | 41.1 | 12.3 | -70.1% |

요약:
- Buyer 상세/목록 경로는 개선 효과가 확인됨
- Seller 단건 경로는 큰 차이 없음
- Seller 목록 경로는 측정 구간에서 악화됨

## 5. 병목 근거 (실제 SQL 로그 계수)
테스트 결과 XML 로그 기반 계수:
- `from product_image ... where product_id=?`: **41,400회**
- `from product_option ... where product_id=?`: **18,900회**
- seller 목록 기본 product 조회: **900회**

해석:
- 응답 매핑 과정에서 Lazy 컬렉션 접근이 반복되며 N+1 형태가 크게 발생
- 트랜잭션 애노테이션 변경만으로는 근본 병목 해결이 어려움

## 6. 결론
1. `@ReadTx -> @Transactional(readOnly = true)` 변경은 경로별 효과가 다르다.
2. 현재 핵심 병목은 트랜잭션 종류보다 **N+1 쿼리 패턴**이다.
3. 우선순위 개선안:
   - 목록 API 전용 projection 조회로 필요한 필드만 일괄 조회
   - `images/options` 배치 로딩 전략 적용 (`@BatchSize` 또는 벌크 조회)
   - 상세 API의 `EntityGraph`/fetch join 로딩 전략 재검토

## 7. 반영 내용
변경 파일:
- `product/src/main/java/com/wearhouse/product/domain/service/buyer/BuyerProductQueryService.java`
- `product/src/main/java/com/wearhouse/product/domain/service/seller/SellerProductQueryService.java`

반영 사항:
- 아래 4개 메서드를 `@Transactional(readOnly = true)`로 변경
  - `getBuyerProducts`
  - `getBuyerProductDetail`
  - `getSellerProducts`
  - `getSellerProduct`
- 시즌 조회 3개 메서드는 `@ReadTx` 유지
