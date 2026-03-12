package com.wearhouse.common.support.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    String key();

    String prefix() default "wearhouse:lock:";

    long waitTimeMs() default 1200L;

    long leaseTimeMs() default 3000L;

    boolean releaseAfterTransaction() default true;

    String failMessage() default "요청 처리 중입니다. 잠시 후 다시 시도해주세요.";
}
