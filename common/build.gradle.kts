plugins {
    `java-library`
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.11"))
    api("org.springframework:spring-web")
    api("org.springframework:spring-webmvc")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("jakarta.validation:jakarta.validation-api")
    api("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
}
