import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.example.catalog"
version = "1.0.0"

repositories {
    mavenCentral()
}

val junitVersion = "5.11.3"
val restAssuredVersion = "5.5.0"
val jacksonVersion = "2.18.1"
val allureVersion = "2.29.0"
val assertjVersion = "3.26.3"
val slf4jVersion = "2.0.16"
val logbackVersion = "1.5.12"
val wiremockVersion = "3.9.2"

val allureCommandline by configurations.creating

dependencies {
    // Allure CLI for HTML report generation (compatible with Gradle 9+)
    allureCommandline("io.qameta.allure:allure-commandline:$allureVersion@zip")

    // JUnit 5
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // REST Assured + Kotlin Extensions + JSON Schema Validator
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("io.rest-assured:kotlin-extensions:$restAssuredVersion")
    testImplementation("io.rest-assured:json-schema-validator:$restAssuredVersion")

    // Jackson Kotlin + Databind
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Allure Reporting (JUnit 5 + REST Assured filter)
    testImplementation(platform("io.qameta.allure:allure-bom:$allureVersion"))
    testImplementation("io.qameta.allure:allure-junit5")
    testImplementation("io.qameta.allure:allure-rest-assured")

    // Assertions
    testImplementation("org.assertj:assertj-core:$assertjVersion")

    // Local OpenAPI Contract Stub / Mock Server for offline & contract verification
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")

    // Logging
    testImplementation("org.slf4j:slf4j-api:$slf4jVersion")
    testRuntimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
}

kotlin {
    jvmToolchain(21)
}

val unpackAllureCli by tasks.registering(Copy::class) {
    description = "Unpacks Allure commandline distribution for HTML report generation"
    group = "reporting"
    from({ zipTree(allureCommandline.singleFile) })
    into(layout.buildDirectory.dir("allure-commandline"))
}

tasks.register<Exec>("allureReport") {
    description = "Generates static HTML Allure report in build/reports/allure-report from build/allure-results"
    group = "reporting"
    dependsOn(unpackAllureCli)
    val resultsDir = layout.buildDirectory.dir("allure-results").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/allure-report").get().asFile
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val allureExecutable = layout.buildDirectory
        .file("allure-commandline/allure-$allureVersion/bin/${if (isWindows) "allure.bat" else "allure"}")
        .get()
        .asFile
    doFirst {
        if (!resultsDir.exists()) {
            resultsDir.mkdirs()
        }
    }
    commandLine(
        allureExecutable.absolutePath,
        "generate",
        resultsDir.absolutePath,
        "-o",
        reportDir.absolutePath,
        "--clean"
    )
}

fun Test.configureCommonTestSettings() {
    useJUnitPlatform()

    // Forward relevant system properties (-DBASE_URL=..., -Dtest.profile=..., etc.) to test JVM
    val forwardedProps = listOf(
        "BASE_URL",
        "API_TOKEN",
        "AUTH_TYPE",
        "API_HEADER_NAME",
        "API_USERNAME",
        "API_PASSWORD",
        "CONNECT_TIMEOUT_MS",
        "READ_TIMEOUT_MS",
        "ALLOW_DESTRUCTIVE_TESTS",
        "USE_LOCAL_MOCK",
        "TEST_PROFILE",
        "TEST_ACTIVE_GTIN",
        "TEST_ACTIVE_NTIN",
        "TEST_INACTIVE_GTIN",
        "TEST_DEACTIVATED_NTIN"
    )
    forwardedProps.forEach { key ->
        System.getProperty(key)?.let { value ->
            systemProperty(key, value)
        }
    }

    systemProperty(
        "allure.results.directory",
        layout.buildDirectory.dir("allure-results").get().asFile.absolutePath
    )

    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }

    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Test>().configureEach {
    configureCommonTestSettings()
}

tasks.named<Test>("test") {
    val includeTagsProp = System.getProperty("includeTags")
    val excludeTagsProp = System.getProperty("excludeTags")
    useJUnitPlatform {
        if (!includeTagsProp.isNullOrBlank()) {
            includeTags(*includeTagsProp.split(",").map { it.trim() }.toTypedArray())
        }
        if (!excludeTagsProp.isNullOrBlank()) {
            excludeTags(*excludeTagsProp.split(",").map { it.trim() }.toTypedArray())
        }
    }
}

val testSourceSet = sourceSets.named("test")

tasks.register<Test>("smokeTest") {
    description = "Runs fast, non-destructive smoke tests (P0)"
    group = "verification"
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    useJUnitPlatform {
        includeTags("smoke")
        excludeTags("destructive")
    }
}

tasks.register<Test>("regressionTest") {
    description = "Runs the full regression test suite (positive, negative, boundary, business, contract)"
    group = "verification"
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    shouldRunAfter(tasks.named("smokeTest"))
    useJUnitPlatform {
        includeTags("regression")
    }
}
