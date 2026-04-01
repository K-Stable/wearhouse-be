package com.wearhouse.common.global.transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class TransactionalPropagationBehaviorTest {

    @Test
    void measureOverheadOccurrenceRate() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            TxScenarioService service = context.getBean(TxScenarioService.class);
            CountingDataSource dataSource = context.getBean(CountingDataSource.class);

            int requests = 1_000;

            OccurrenceStats writeTx = measureOccurrence(requests, service::writeTx, dataSource);
            OccurrenceStats txDefault = measureOccurrence(requests, service::transactionalDefault, dataSource);
            OccurrenceStats readTxSupports = measureOccurrence(requests, service::readTxSupports, dataSource);
            OccurrenceStats txReadOnlyDefault = measureOccurrence(requests, service::transactionalReadOnlyDefault, dataSource);
            OccurrenceStats txReadOnlySupports = measureOccurrence(
                    requests,
                    service::transactionalReadOnlySupports,
                    dataSource
            );

            System.out.println("\n=== overhead occurrence rate (single-call based) ===");
            printOccurrence("@WriteTx", writeTx);
            printOccurrence("@Transactional", txDefault);
            printOccurrence("@ReadTx(readOnly=true,SUPPORTS)", readTxSupports);
            printOccurrence("@Transactional(readOnly=true)", txReadOnlyDefault);
            printOccurrence("@Transactional(readOnly=true,SUPPORTS)", txReadOnlySupports);

            // REQUIRED는 매 호출마다 tx 제어 오버헤드 발생
            assertEquals(requests, writeTx.occurrenceCount());
            assertEquals(requests, txDefault.occurrenceCount());
            assertEquals(requests, txReadOnlyDefault.occurrenceCount());

            // SUPPORTS(read-only)는 단독 호출 시 tx 제어 오버헤드 미발생
            assertEquals(0, readTxSupports.occurrenceCount());
            assertEquals(0, txReadOnlySupports.occurrenceCount());
        }
    }

    @Test
    void compareReadOnlyPropagationBehavior() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            TxScenarioService service = context.getBean(TxScenarioService.class);
            CountingDataSource dataSource = context.getBean(CountingDataSource.class);

            int warmUpLoops = 300;
            int loops = 200;

            runLoop(service::writeTx, warmUpLoops);
            runLoop(service::transactionalDefault, warmUpLoops);
            runLoop(service::readTxSupports, warmUpLoops);
            runLoop(service::transactionalReadOnlyDefault, warmUpLoops);
            runLoop(service::transactionalReadOnlySupports, warmUpLoops);

            dataSource.reset();
            long writeTxNanos = runLoop(service::writeTx, loops);
            CounterSnapshot writeTx = dataSource.snapshot();

            dataSource.reset();
            long txDefaultNanos = runLoop(service::transactionalDefault, loops);
            CounterSnapshot txDefault = dataSource.snapshot();

            dataSource.reset();
            long readTxSupportsNanos = runLoop(service::readTxSupports, loops);
            CounterSnapshot readTxSupports = dataSource.snapshot();

            dataSource.reset();
            long txReadOnlyDefaultNanos = runLoop(service::transactionalReadOnlyDefault, loops);
            CounterSnapshot txReadOnlyDefault = dataSource.snapshot();

            dataSource.reset();
            long txReadOnlySupportsNanos = runLoop(service::transactionalReadOnlySupports, loops);
            CounterSnapshot txReadOnlySupports = dataSource.snapshot();

            // REQUIRED 계열은 tx 시작/커밋이 발생해야 한다.
            assertEquals(loops, writeTx.setAutoCommitFalse());
            assertEquals(loops, writeTx.commit());
            assertEquals(loops, txDefault.setAutoCommitFalse());
            assertEquals(loops, txDefault.commit());

            // @ReadTx(SUPPORTS) 는 단독 호출 시 tx를 시작하지 않는다.
            assertEquals(0, readTxSupports.setAutoCommitFalse());
            assertEquals(0, readTxSupports.commit());

            // @Transactional(readOnly=true) 기본 REQUIRED 이므로 tx 시작/커밋이 발생한다.
            assertEquals(loops, txReadOnlyDefault.setAutoCommitFalse());
            assertEquals(loops, txReadOnlyDefault.commit());

            // readOnly + SUPPORTS 는 단독 호출 시 tx를 시작하지 않는다.
            assertEquals(0, txReadOnlySupports.setAutoCommitFalse());
            assertEquals(0, txReadOnlySupports.commit());

            System.out.println("\n=== transactional behavior (kakaopay article style) ===");
            print("@WriteTx", writeTx, writeTxNanos, loops);
            print("@Transactional", txDefault, txDefaultNanos, loops);
            print("@ReadTx(readOnly=true,SUPPORTS)", readTxSupports, readTxSupportsNanos, loops);
            print("@Transactional(readOnly=true)", txReadOnlyDefault, txReadOnlyDefaultNanos, loops);
            print("@Transactional(readOnly=true,SUPPORTS)", txReadOnlySupports, txReadOnlySupportsNanos, loops);

            // 핵심: readOnly=true 만으로는 트랜잭션 제거가 안 된다.
            assertTrue(
                    txReadOnlyDefault.commit() > readTxSupports.commit(),
                    "readOnly=true(default REQUIRED)와 SUPPORTS 간 commit 호출 차이가 있어야 합니다."
            );
        }
    }

    private long runLoop(Runnable runnable, int loops) {
        long start = System.nanoTime();
        for (int i = 0; i < loops; i++) {
            runnable.run();
        }
        return System.nanoTime() - start;
    }

    private OccurrenceStats measureOccurrence(int requests, Runnable runnable, CountingDataSource dataSource) {
        int occurrenceCount = 0;
        long totalSetAutoCommitFalse = 0;
        long totalCommit = 0;
        long totalSetReadOnly = 0;

        for (int i = 0; i < requests; i++) {
            dataSource.reset();
            runnable.run();
            CounterSnapshot snapshot = dataSource.snapshot();

            totalSetAutoCommitFalse += snapshot.setAutoCommitFalse();
            totalCommit += snapshot.commit();
            totalSetReadOnly += snapshot.setReadOnly();

            boolean happened = snapshot.setAutoCommitFalse() > 0
                    || snapshot.commit() > 0
                    || snapshot.setReadOnly() > 0;
            if (happened) {
                occurrenceCount++;
            }
        }

        return new OccurrenceStats(
                requests,
                occurrenceCount,
                totalSetAutoCommitFalse,
                totalCommit,
                totalSetReadOnly
        );
    }

    private void print(String label, CounterSnapshot snapshot, long totalNanos, int loops) {
        System.out.printf(
                Locale.ROOT,
                "%s -> total=%.2fms, ns/op=%.2f, setAutoCommit(false)=%d, setAutoCommit(true)=%d, setReadOnly=%d, commit=%d, rollback=%d%n",
                label,
                totalNanos / 1_000_000.0,
                (double) totalNanos / loops,
                snapshot.setAutoCommitFalse(),
                snapshot.setAutoCommitTrue(),
                snapshot.setReadOnly(),
                snapshot.commit(),
                snapshot.rollback()
        );
    }

    private void printOccurrence(String label, OccurrenceStats stats) {
        System.out.printf(
                Locale.ROOT,
                "%s -> occurrence=%d/%d (%.2f%%), avg(setAutoCommitFalse)=%.2f, avg(commit)=%.2f, avg(setReadOnly)=%.2f%n",
                label,
                stats.occurrenceCount(),
                stats.requests(),
                stats.occurrenceRatePercent(),
                stats.avgSetAutoCommitFalse(),
                stats.avgCommit(),
                stats.avgSetReadOnly()
        );
    }

    private record CounterSnapshot(
            int setAutoCommitFalse,
            int setAutoCommitTrue,
            int setReadOnly,
            int commit,
            int rollback
    ) {
    }

    private record OccurrenceStats(
            int requests,
            int occurrenceCount,
            long totalSetAutoCommitFalse,
            long totalCommit,
            long totalSetReadOnly
    ) {
        double occurrenceRatePercent() {
            return requests == 0 ? 0.0 : (occurrenceCount * 100.0 / requests);
        }

        double avgSetAutoCommitFalse() {
            return requests == 0 ? 0.0 : (double) totalSetAutoCommitFalse / requests;
        }

        double avgCommit() {
            return requests == 0 ? 0.0 : (double) totalCommit / requests;
        }

        double avgSetReadOnly() {
            return requests == 0 ? 0.0 : (double) totalSetReadOnly / requests;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        CountingDataSource dataSource() {
            return new CountingDataSource();
        }

        @Bean
        PlatformTransactionManager transactionManager(CountingDataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TxScenarioService txScenarioService() {
            return new TxScenarioService();
        }
    }

    static class TxScenarioService {

        @WriteTx
        public void writeTx() {
            // no-op
        }

        @Transactional
        public void transactionalDefault() {
            // no-op
        }

        @ReadTx
        public void readTxSupports() {
            // no-op
        }

        @Transactional(readOnly = true)
        public void transactionalReadOnlyDefault() {
            // no-op
        }

        @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
        public void transactionalReadOnlySupports() {
            // no-op
        }
    }

    static class CountingDataSource implements DataSource {

        private final AtomicInteger setAutoCommitFalse = new AtomicInteger();
        private final AtomicInteger setAutoCommitTrue = new AtomicInteger();
        private final AtomicInteger setReadOnly = new AtomicInteger();
        private final AtomicInteger commit = new AtomicInteger();
        private final AtomicInteger rollback = new AtomicInteger();

        @Override
        public Connection getConnection() {
            return newConnectionProxy();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return newConnectionProxy();
        }

        private Connection newConnectionProxy() {
            final boolean[] autoCommit = {true};
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getAutoCommit".equals(name)) {
                            return autoCommit[0];
                        }
                        if ("setAutoCommit".equals(name)) {
                            boolean value = (boolean) args[0];
                            if (value) {
                                setAutoCommitTrue.incrementAndGet();
                            } else {
                                setAutoCommitFalse.incrementAndGet();
                            }
                            autoCommit[0] = value;
                            return null;
                        }
                        if ("setReadOnly".equals(name)) {
                            setReadOnly.incrementAndGet();
                            return null;
                        }
                        if ("commit".equals(name)) {
                            commit.incrementAndGet();
                            return null;
                        }
                        if ("rollback".equals(name)) {
                            rollback.incrementAndGet();
                            return null;
                        }
                        if ("isClosed".equals(name)) {
                            return false;
                        }
                        if ("close".equals(name)) {
                            return null;
                        }
                        if ("unwrap".equals(name)) {
                            Class<?> target = (Class<?>) args[0];
                            if (target.isInstance(proxy)) {
                                return proxy;
                            }
                            throw new SQLException("Not a wrapper for " + target);
                        }
                        if ("isWrapperFor".equals(name)) {
                            Class<?> target = (Class<?>) args[0];
                            return target.isInstance(proxy);
                        }

                        Class<?> returnType = method.getReturnType();
                        if (returnType.equals(Boolean.TYPE)) {
                            return false;
                        }
                        if (returnType.equals(Integer.TYPE)) {
                            return 0;
                        }
                        if (returnType.equals(Long.TYPE)) {
                            return 0L;
                        }
                        if (returnType.equals(Double.TYPE)) {
                            return 0d;
                        }
                        if (returnType.equals(Float.TYPE)) {
                            return 0f;
                        }
                        return null;
                    }
            );
        }

        void reset() {
            setAutoCommitFalse.set(0);
            setAutoCommitTrue.set(0);
            setReadOnly.set(0);
            commit.set(0);
            rollback.set(0);
        }

        CounterSnapshot snapshot() {
            return new CounterSnapshot(
                    setAutoCommitFalse.get(),
                    setAutoCommitTrue.get(),
                    setReadOnly.get(),
                    commit.get(),
                    rollback.get()
            );
        }

        @Override
        public PrintWriter getLogWriter() {
            return new PrintWriter(System.out);
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
