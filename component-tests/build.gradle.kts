plugins {
    java
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // Same managed versions as the application module.
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
    }
}

dependencies {
    // The application under test: brings its compiled classes (compile) and its
    // full runtime classpath (Spring Boot, Kafka, etc.) transitively at runtime.
    testImplementation(project(":"))

    // Test-infrastructure libraries the test source compiles against. Root
    // declares these as `implementation`, so they are not exposed at compile
    // time to consumers and must be re-declared here.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
