plugins {
    `java-library`
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.11"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.1"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.cloud:spring-cloud-openfeign-core")
    api("io.jsonwebtoken:jjwt-api:0.12.6")
    api("org.springframework:spring-web")
    api("org.springframework:spring-webmvc")
    api("org.springframework:spring-tx")
    api("org.springframework:spring-context-support")
    api("org.springframework.data:spring-data-redis")
    api("org.springframework.security:spring-security-core")
    api("org.springframework.security:spring-security-web")
    api("org.springframework.kafka:spring-kafka")
    api("org.springframework:spring-aop")
    api("org.aspectj:aspectjweaver")
    api("org.redisson:redisson:3.52.0")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("jakarta.validation:jakarta.validation-api")
    api("jakarta.servlet:jakarta.servlet-api")
    api("jakarta.mail:jakarta.mail-api")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")


    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
