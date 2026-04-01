package com.wearhouse.product.performance;

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
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
@Import(QuerydslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductQueryDslVsDerivedQueryBenchmarkTest {

    private static final int SEED_COUNT = 200;
    private static final int LIMIT = 20;
    private static final int WARM_UP = 200;
    private static final int ITERATIONS = 1_500;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        for (int i = 1; i <= SEED_COUNT; i++) {
            ProductEntity product = ProductEntity.of(
                    900001L,
                    "benchmark pants " + i,
                    BigDecimal.valueOf(10_000L + i),
                    i % 2 == 0 ? Category.BOTTOM : Category.OUTER,
                    "details-" + i,
                    "size-guide",
                    "shipping",
                    ProductStatus.RELEASED
            );
            productRepository.save(product);
        }

        for (int i = 0; i < WARM_UP; i++) {
            safeRun(() -> productRepository.findBuyerProductsByCursor(
                    Category.BOTTOM,
                    null,
                    BuyerProductSortType.PRICE_HIGH,
                    LIMIT
            ));
            safeRun(() -> productRepository.findByStatusAndCategoryOrderByPriceDescIdDesc(
                    ProductStatus.RELEASED,
                    Category.BOTTOM,
                    PageRequest.of(0, LIMIT)
            ));
            safeRun(() -> productRepository.findSellerProductsByCursor(
                    900001L,
                    null,
                    "pants",
                    null,
                    null,
                    LIMIT
            ));
            safeRun(() -> productRepository.findBySellerIdAndNameContainingIgnoreCaseOrderByIdDesc(
                    900001L,
                    "pants",
                    PageRequest.of(0, LIMIT)
            ));
        }
    }

    @Test
    void compareBuyerCategorySortQueryDslVsDerived() {
        BenchmarkResult queryDsl = benchmark(
                "buyer.category+sort.querydsl",
                () -> productRepository.findBuyerProductsByCursor(
                        Category.BOTTOM,
                        null,
                        BuyerProductSortType.PRICE_HIGH,
                        LIMIT
                )
        );

        BenchmarkResult derived = benchmark(
                "buyer.category+sort.derived",
                () -> productRepository.findByStatusAndCategoryOrderByPriceDescIdDesc(
                        ProductStatus.RELEASED,
                        Category.BOTTOM,
                        PageRequest.of(0, LIMIT)
                )
        );

        printSection("buyer category+sort query benchmark", queryDsl, derived);
    }

    @Test
    void compareSellerKeywordQueryDslVsDerived() {
        BenchmarkResult queryDsl = benchmark(
                "seller.keyword.querydsl",
                () -> productRepository.findSellerProductsByCursor(
                        900001L,
                        null,
                        "pants",
                        null,
                        null,
                        LIMIT
                )
        );

        BenchmarkResult derived = benchmark(
                "seller.keyword.derived",
                () -> productRepository.findBySellerIdAndNameContainingIgnoreCaseOrderByIdDesc(
                        900001L,
                        "pants",
                        PageRequest.of(0, LIMIT)
                )
        );

        printSection("seller keyword-only query benchmark", queryDsl, derived);
    }

    private BenchmarkResult benchmark(String name, Runnable runnable) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        long[] nanos = new long[ITERATIONS];
        int success = 0;
        int error = 0;
        long startedAt = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            long started = System.nanoTime();
            try {
                runnable.run();
                success++;
            } catch (Exception exception) {
                error++;
            } finally {
                nanos[i] = System.nanoTime() - started;
            }
        }

        long elapsed = System.nanoTime() - startedAt;
        return BenchmarkResult.of(
                name,
                nanos,
                elapsed,
                success,
                error,
                statistics.getPrepareStatementCount(),
                statistics.getQueryExecutionCount()
        );
    }

    private void printSection(String title, BenchmarkResult... results) {
        System.out.println("\n=== " + title + " ===");
        System.out.println("mode | success | error | avg(ms) | p95(ms) | p99(ms) | throughput(req/s) | prepared/req | queryExec/req");
        Arrays.stream(results)
                .sorted(Comparator.comparing(BenchmarkResult::avgNanos))
                .forEach(result -> System.out.printf(
                        Locale.ROOT,
                        "%s | %d | %d | %.3f | %.3f | %.3f | %.1f | %.3f | %.3f%n",
                        result.name(),
                        result.successCount(),
                        result.errorCount(),
                        toMs(result.avgNanos()),
                        toMs(result.p95Nanos()),
                        toMs(result.p99Nanos()),
                        result.throughputPerSecond(),
                        result.preparedPerRequest(),
                        result.queryExecutionPerRequest()
                ));
    }

    private void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ignored) {
            // warm-up 단계는 실패를 무시한다.
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
            int errorCount,
            long preparedStatementCount,
            long queryExecutionCount
    ) {
        private static BenchmarkResult of(
                String name,
                long[] nanos,
                long totalElapsedNanos,
                int successCount,
                int errorCount,
                long preparedStatementCount,
                long queryExecutionCount
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

            return new BenchmarkResult(
                    name,
                    avg,
                    p95,
                    p99,
                    throughput,
                    successCount,
                    errorCount,
                    preparedStatementCount,
                    queryExecutionCount
            );
        }

        private double preparedPerRequest() {
            int safeCount = Math.max(successCount, 1);
            return (double) preparedStatementCount / safeCount;
        }

        private double queryExecutionPerRequest() {
            int safeCount = Math.max(successCount, 1);
            return (double) queryExecutionCount / safeCount;
        }

        private static long percentile(long[] sortedNanos, double percentile) {
            int index = (int) Math.ceil(percentile * sortedNanos.length) - 1;
            int normalized = Math.min(Math.max(index, 0), sortedNanos.length - 1);
            return sortedNanos[normalized];
        }
    }
}
