import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("org.springframework.boot") version "3.5.11" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

extra["springCloudVersion"] = "2025.0.1"

allprojects {
    group = "com.wearhouse"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    plugins.withId("io.spring.dependency-management") {
        extensions.configure<DependencyManagementExtension> {
            imports {
                mavenBom("org.springframework.cloud:spring-cloud-dependencies:${rootProject.extra["springCloudVersion"]}")
            }
        }
    }
}
