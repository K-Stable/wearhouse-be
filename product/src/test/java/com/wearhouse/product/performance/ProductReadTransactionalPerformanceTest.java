package com.wearhouse.product.performance;

import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.support.querydsl.QuerydslConfig;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.jdbc.batch_size=100",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
@Import({QuerydslConfig.class, ProductReadTransactionalPerformanceTest.BenchmarkConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductReadTransactionalPerformanceTest {

    private static final int SEED_COUNT = 30;
    private static final int LIST_LIMIT = 20;
    private static final int LIST_ITERATIONS = 2_000;
    private static final int DETAIL_ITERATIONS = 3_000;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReadBenchmarkService benchmarkService;

    private Long randomReleasedProductId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        for (int i = 1; i <= SEED_COUNT; i++) {
            ProductEntity product = ProductEntity.of(
                    1000L + (i % 10),
                    "benchmark-product-" + i,
                    BigDecimal.valueOf(10_000L + i),
                    Category.OUTER,
                    "details-" + i,
                    "size-guide",
                    "shipping",
                    ProductStatus.RELEASED
            );
            product.addOption("M", "BLACK", 100, 0);
            productRepository.save(product);
        }

        List<Long> releasedIds = productRepository.findBuyerProductsByCursor(
                Category.OUTER,
                null,
                BuyerProductSortType.LATEST,
                SEED_COUNT
        ).stream().map(ProductEntity::getId).toList();

        randomReleasedProductId = releasedIds.get(ThreadLocalRandom.current().nextInt(releasedIds.size()));

        // warm-up JIT / query plan cache
        for (int i = 0; i < 400; i++) {
            benchmarkService.listNoTx(Category.OUTER, LIST_LIMIT);
            benchmarkService.listTransactionalReadOnly(Category.OUTER, LIST_LIMIT);
            benchmarkService.listReadTx(Category.OUTER, LIST_LIMIT);
            benchmarkService.detailNoTx(randomReleasedProductId);
            benchmarkService.detailTransactionalReadOnly(randomReleasedProductId);
            benchmarkService.detailReadTx(randomReleasedProductId);
        }
    }

    @Test
    void compareBuyerListReadPerformance() {
        BenchmarkResult noTx = measure("NO_TX", LIST_ITERATIONS,
                () -> benchmarkService.listNoTx(Category.OUTER, LIST_LIMIT));
        BenchmarkResult txReadOnly = measure("TX_READ_ONLY_REQUIRED", LIST_ITERATIONS,
                () -> benchmarkService.listTransactionalReadOnly(Category.OUTER, LIST_LIMIT));
        BenchmarkResult readTx = measure("READ_TX_SUPPORTS", LIST_ITERATIONS,
                () -> benchmarkService.listReadTx(Category.OUTER, LIST_LIMIT));

        printSection("buyer product list (/api/v1/buyer/products equivalent)", noTx, txReadOnly, readTx);
    }

    @Test
    void compareBuyerDetailReadPerformance() {
        BenchmarkResult noTx = measure("NO_TX", DETAIL_ITERATIONS,
                () -> benchmarkService.detailNoTx(randomReleasedProductId));
        BenchmarkResult txReadOnly = measure("TX_READ_ONLY_REQUIRED", DETAIL_ITERATIONS,
                () -> benchmarkService.detailTransactionalReadOnly(randomReleasedProductId));
        BenchmarkResult readTx = measure("READ_TX_SUPPORTS", DETAIL_ITERATIONS,
                () -> benchmarkService.detailReadTx(randomReleasedProductId));

        printSection("buyer product detail findByIdAndStatus (/api/v1/buyer/products/{id} core query)",
                noTx, txReadOnly, readTx);
    }

    private BenchmarkResult measure(String mode, int iterations, Runnable runnable) {
        long[] nanos = new long[iterations];
        long startAll = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            runnable.run();
            nanos[i] = System.nanoTime() - start;
        }
        long elapsed = System.nanoTime() - startAll;
        return BenchmarkResult.of(mode, nanos, elapsed, iterations);
    }

    private void printSection(String section, BenchmarkResult... results) {
        System.out.println("\n=== Product Read Benchmark: " + section + " ===");
        System.out.println("mode | avg(ms) | p95(ms) | p99(ms) | throughput(req/s)");
        Arrays.stream(results)
                .sorted(Comparator.comparing(BenchmarkResult::avgNanos))
                .forEach(result -> System.out.printf(
                        Locale.ROOT,
                        "%s | %.3f | %.3f | %.3f | %.1f%n",
                        result.mode(),
                        toMs(result.avgNanos()),
                        toMs(result.p95Nanos()),
                        toMs(result.p99Nanos()),
                        result.throughputPerSecond()
                ));
    }

    private double toMs(double nanos) {
        return nanos / 1_000_000.0;
    }

    private record BenchmarkResult(
            String mode,
            double avgNanos,
            long p95Nanos,
            long p99Nanos,
            double throughputPerSecond
    ) {
        private static BenchmarkResult of(String mode, long[] nanos, long totalElapsedNanos, int iterations) {
            long[] sorted = Arrays.copyOf(nanos, nanos.length);
            Arrays.sort(sorted);
            long total = 0L;
            for (long nano : nanos) {
                total += nano;
            }
            double avg = (double) total / iterations;
            long p95 = percentile(sorted, 0.95);
            long p99 = percentile(sorted, 0.99);
            double elapsedMillis = Math.max(1.0, Duration.ofNanos(totalElapsedNanos).toMillis());
            double throughput = (iterations * 1000.0) / elapsedMillis;
            return new BenchmarkResult(mode, avg, p95, p99, throughput);
        }

        private static long percentile(long[] sortedNanos, double percentile) {
            int index = (int) Math.ceil(percentile * sortedNanos.length) - 1;
            int normalized = Math.min(Math.max(index, 0), sortedNanos.length - 1);
            return sortedNanos[normalized];
        }
    }

    @TestConfiguration
    static class BenchmarkConfig {

        @Bean
        ProductReadBenchmarkService productReadBenchmarkService(ProductRepository productRepository) {
            return new ProductReadBenchmarkService(productRepository);
        }
    }

    @RequiredArgsConstructor
    static class ProductReadBenchmarkService {

        private final ProductRepository productRepository;

        List<ProductEntity> listNoTx(Category category, int limit) {
            return productRepository.findBuyerProductsByCursor(
                    category,
                    null,
                    BuyerProductSortType.LATEST,
                    limit
            );
        }

        @Transactional(readOnly = true)
        List<ProductEntity> listTransactionalReadOnly(Category category, int limit) {
            return productRepository.findBuyerProductsByCursor(
                    category,
                    null,
                    BuyerProductSortType.LATEST,
                    limit
            );
        }

        @ReadTx
        List<ProductEntity> listReadTx(Category category, int limit) {
            return productRepository.findBuyerProductsByCursor(
                    category,
                    null,
                    BuyerProductSortType.LATEST,
                    limit
            );
        }

        ProductEntity detailNoTx(Long productId) {
            return productRepository.findByIdAndStatus(productId, ProductStatus.RELEASED)
                    .orElseThrow();
        }

        @Transactional(readOnly = true)
        ProductEntity detailTransactionalReadOnly(Long productId) {
            return productRepository.findByIdAndStatus(productId, ProductStatus.RELEASED)
                    .orElseThrow();
        }

        @ReadTx
        ProductEntity detailReadTx(Long productId) {
            return productRepository.findByIdAndStatus(productId, ProductStatus.RELEASED)
                    .orElseThrow();
        }
    }
}
