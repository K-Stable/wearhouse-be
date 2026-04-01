package com.wearhouse.product.performance;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.support.querydsl.QuerydslConfig;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.buyer.mapper.BuyerProductResponseMapper;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.buyer.BuyerProductQueryService;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import com.wearhouse.product.domain.service.seller.SellerProductAccessValidator;
import com.wearhouse.product.domain.service.seller.SellerProductQueryService;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import com.wearhouse.product.seller.mapper.SellerProductResponseMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
@Import({
        QuerydslConfig.class,
        BuyerProductQueryService.class,
        SellerProductQueryService.class,
        SellerProductAccessValidator.class,
        ProductStockResolver.class,
        ProductImageUrlResolver.class,
        BuyerProductResponseMapper.class,
        SellerProductResponseMapper.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductQueryServiceFlowBenchmarkTest {

    private static final int SEED_COUNT = 30;
    private static final int ITERATIONS = 400;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSeasonRepository productSeasonRepository;

    @Autowired
    private BuyerProductQueryService buyerProductQueryService;

    @Autowired
    private SellerProductQueryService sellerProductQueryService;

    @MockitoBean
    private ProductInventoryClient productInventoryClient;

    @MockitoBean
    private S3StorageService s3StorageService;

    private Long randomProductId;
    private LoginUser seller;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productSeasonRepository.deleteAll();

        seller = new LoginUser(2001L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductSeasonEntity season = productSeasonRepository.save(ProductSeasonEntity.of(2001L, "2026-SS"));

        for (int i = 1; i <= SEED_COUNT; i++) {
            ProductEntity product = ProductEntity.of(
                    2001L,
                    "perf-product-" + i,
                    BigDecimal.valueOf(10000L + i),
                    Category.OUTER,
                    "details-" + i,
                    "size-guide-" + i,
                    "shipping-" + i,
                    ProductStatus.RELEASED,
                    season
            );
            product.addOption("M", "BLACK", 100, 0);
            product.addOption("L", "NAVY", 80, 1);
            product.addImage(ProductImageType.MAIN, "products/main/" + i + ".jpg", 0);
            product.addImage(ProductImageType.PREVIEW, "products/preview/" + i + "-1.jpg", 0);
            product.addImage(ProductImageType.DETAIL, "products/detail/" + i + "-1.jpg", 0);
            productRepository.save(product);
        }

        List<Long> ids = productRepository.findBuyerProductsByCursor(
                Category.OUTER,
                null,
                BuyerProductSortType.LATEST,
                SEED_COUNT
        ).stream().map(ProductEntity::getId).toList();
        randomProductId = ids.get(ThreadLocalRandom.current().nextInt(ids.size()));

        given(productInventoryClient.getAvailableQtyBulk(any())).willReturn(java.util.Map.of());
        given(s3StorageService.getImageUrl(any())).willAnswer(invocation -> invocation.getArgument(0));

        for (int i = 0; i < 50; i++) {
            safeRun(() -> buyerProductQueryService.getBuyerProducts(Category.OUTER, BuyerProductSortType.LATEST, null, 20));
            safeRun(() -> buyerProductQueryService.getBuyerProductDetail(randomProductId));
            safeRun(() -> sellerProductQueryService.getSellerProducts(seller, null, null, null, 20, null));
            safeRun(() -> sellerProductQueryService.getSellerProduct(seller, randomProductId));
        }
    }

    @Test
    void benchmarkQueryFlows() {
        BenchmarkResult buyerList = benchmark(
                "buyer.getBuyerProducts",
                () -> buyerProductQueryService.getBuyerProducts(Category.OUTER, BuyerProductSortType.LATEST, null, 20)
        );
        BenchmarkResult buyerDetail = benchmark(
                "buyer.getBuyerProductDetail",
                () -> buyerProductQueryService.getBuyerProductDetail(randomProductId)
        );
        BenchmarkResult sellerList = benchmark(
                "seller.getSellerProducts",
                () -> sellerProductQueryService.getSellerProducts(seller, null, null, null, 20, null)
        );
        BenchmarkResult sellerDetail = benchmark(
                "seller.getSellerProduct",
                () -> sellerProductQueryService.getSellerProduct(seller, randomProductId)
        );

        printSection("product query flow benchmark (open-in-view=false, no outer tx)",
                buyerList, buyerDetail, sellerList, sellerDetail);
    }

    private BenchmarkResult benchmark(String name, Runnable runnable) {
        long[] nanos = new long[ITERATIONS];
        AtomicInteger success = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();
        long startAll = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            try {
                runnable.run();
                success.incrementAndGet();
            } catch (Exception exception) {
                error.incrementAndGet();
            } finally {
                nanos[i] = System.nanoTime() - start;
            }
        }
        long elapsed = System.nanoTime() - startAll;
        return BenchmarkResult.of(name, nanos, elapsed, success.get(), error.get());
    }

    private void printSection(String title, BenchmarkResult... results) {
        System.out.println("\n=== " + title + " ===");
        System.out.println("flow | success | error | avg(ms) | p95(ms) | p99(ms) | throughput(req/s)");
        Arrays.stream(results)
                .sorted(Comparator.comparing(BenchmarkResult::name))
                .forEach(result -> System.out.printf(
                        Locale.ROOT,
                        "%s | %d | %d | %.3f | %.3f | %.3f | %.1f%n",
                        result.name(),
                        result.successCount(),
                        result.errorCount(),
                        toMs(result.avgNanos()),
                        toMs(result.p95Nanos()),
                        toMs(result.p99Nanos()),
                        result.throughputPerSecond()
                ));
    }

    private void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ignored) {
            // warm-up 단계에서는 실패를 무시한다.
        }
    }

    private double toMs(double nanos) {
        return nanos / 1_000_000.0;
    }

    private record BenchmarkResult(
            String name,
            double avgNanos,
            long p95Nanos,
            long p99Nanos,
            double throughputPerSecond,
            int successCount,
            int errorCount
    ) {
        private static BenchmarkResult of(
                String name,
                long[] nanos,
                long totalElapsedNanos,
                int successCount,
                int errorCount
        ) {
            long[] sorted = Arrays.copyOf(nanos, nanos.length);
            Arrays.sort(sorted);
            long total = 0L;
            for (long value : nanos) {
                total += value;
            }
            double avg = (double) total / nanos.length;
            long p95 = percentile(sorted, 0.95);
            long p99 = percentile(sorted, 0.99);
            double elapsedMillis = Math.max(1.0, Duration.ofNanos(totalElapsedNanos).toMillis());
            double throughput = (nanos.length * 1000.0) / elapsedMillis;
            return new BenchmarkResult(name, avg, p95, p99, throughput, successCount, errorCount);
        }

        private static long percentile(long[] sortedNanos, double percentile) {
            int index = (int) Math.ceil(percentile * sortedNanos.length) - 1;
            int normalized = Math.min(Math.max(index, 0), sortedNanos.length - 1);
            return sortedNanos[normalized];
        }
    }

}
