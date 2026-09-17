plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.iaaops"
version = "1.0.0-rc.1"
description = "IAA 投放运营中台 · 业务后端"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // 与 spring-boot-testcontainers 4.1.1 依赖的 Testcontainers 版本对齐
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // JWT 签发与校验（Nimbus）
    implementation("org.springframework.security:spring-security-oauth2-jose")
    // Argon2PasswordEncoder 依赖 BouncyCastle；口令哈希需与现有 Python 侧兼容
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Boot 4 把各技术的自动配置拆成独立模块：只放 flyway-core 不会触发自动迁移，
    // 必须用 starter-flyway（它带上 spring-boot-flyway 自动配置）。
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // v1 解析媒体后台导出的 .xlsx；媒体 API 自动采集尚未实现
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 把 MockMvc 测试支撑拆成独立模块，starter-test 不再自带
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // 模块边界校验：Spring Modulith 当前版本线尚未跟进 Boot 4.1，改用与框架解耦的 ArchUnit
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
