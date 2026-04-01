package com.wearhouse.common.support.lock;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveLockKey(joinPoint, distributedLock);
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = tryAcquire(lock, distributedLock);
        if (!acquired) {
            throw new ErrorException(CommonErrorCode.BUSINESS_RULE_VIOLATION, distributedLock.failMessage());
        }

        boolean deferredUnlock = false;
        try {
            return joinPoint.proceed();
        } finally {
            if (distributedLock.releaseAfterTransaction()
                    && TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                registerUnlockAfterCompletion(lock);
                deferredUnlock = true;
            }
            if (!deferredUnlock) {
                unlockSafely(lock);
            }
        }
    }

    private String resolveLockKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int index = 0; index < args.length; index++) {
            context.setVariable("p" + index, args[index]);
            context.setVariable("a" + index, args[index]);
            if (parameterNames != null && parameterNames.length > index) {
                context.setVariable(parameterNames[index], args[index]);
            }
        }

        Object keyValue;
        try {
            keyValue = expressionParser.parseExpression(distributedLock.key()).getValue(context);
        } catch (Exception exception) {
            throw new ErrorException(CommonErrorCode.INVALID_INPUT_VALUE, "분산락 키 식을 평가할 수 없습니다.");
        }

        if (keyValue == null || String.valueOf(keyValue).isBlank()) {
            throw new ErrorException(CommonErrorCode.INVALID_INPUT_VALUE, "분산락 키 값이 비어 있습니다.");
        }
        return distributedLock.prefix() + keyValue;
    }

    private boolean tryAcquire(RLock lock, DistributedLock distributedLock) {
        try {
            if (distributedLock.leaseTimeMs() > 0) {
                return lock.tryLock(
                        distributedLock.waitTimeMs(),
                        distributedLock.leaseTimeMs(),
                        java.util.concurrent.TimeUnit.MILLISECONDS
                );
            }
            return lock.tryLock(distributedLock.waitTimeMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void registerUnlockAfterCompletion(RLock lock) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                unlockSafely(lock);
            }
        });
    }

    private void unlockSafely(RLock lock) {
        if (lock == null) {
            return;
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
