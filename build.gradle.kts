plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.4"
}

group = "com.lecture"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("com.password4j:password4j:1.8.2")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.retry:spring-retry:2.0.12")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat("1.35.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    json {
        target("src/**/*.json")
        gson()
            .indentWithSpaces(2)
            .sortByKeys()
        trimTrailingWhitespace()
        endWithNewline()
    }
    yaml {
        target("src/**/*.yaml", "src/**/*.yml")
        jackson()
            .yamlFeature("WRITE_DOC_START_MARKER", false)
            .yamlFeature("MINIMIZE_QUOTES", true)
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("markdown") {
        target("**/*.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("**/*.sh", "**/*.xml", "**/*.properties")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
