import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.allure)
    alias(libs.plugins.detekt)
}

group = "com.example.catalog"
version = "1.0.0"

repositories {
    mavenCentral()
}

val allureReportVersion = libs.versions.allure.report.get()
val allureJavaAdapterVersion = libs.versions.allure.java.get()

allure {
    version.set(allureReportVersion)
    report {
        configFile.set(layout.projectDirectory.file("config/allurerc.json"))
        singleFile.set(true)
    }
    adapter {
        allureJavaVersion.set(allureJavaAdapterVersion)
        frameworks {
            junit5 {
                enabled.set(true)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
}

tasks.named("installAllure3") {
    doFirst {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            val nodeBin = layout.buildDirectory.file("allure/node/bin/node").get().asFile
            val npmBin = layout.buildDirectory.file("allure/node/bin/npm").get().asFile
            val npmCli = layout.buildDirectory.file("allure/node/lib/node_modules/npm/bin/npm-cli.js").get().asFile

            if (npmBin.exists()) {
                npmBin.delete()
            }
            npmBin.parentFile?.mkdirs()
            npmBin.writeText(
                """
                #!/bin/sh
                exec "${nodeBin.absolutePath}" "${npmCli.absolutePath}" "$@"
                """.trimIndent()
            )
            npmBin.setExecutable(true)
        }
    }
}

tasks.named<io.qameta.allure.gradle.report.tasks.AllureReport>("allureReport") {
    resultsDirs.setFrom(layout.buildDirectory.dir("allure-results"))
    mustRunAfter(tasks.withType<Test>())
    doLast {
        val generatedSingleFile = layout.buildDirectory.file("reports/allure-report/allureReport/index.html").get().asFile
        val rootReportFile = layout.buildDirectory.file("reports/allure-report/index.html").get().asFile
        if (generatedSingleFile.exists()) {
            generatedSingleFile.copyTo(rootReportFile, overwrite = true)
        }
    }
}

tasks.named<io.qameta.allure.gradle.report.tasks.AllureServe>("allureServe") {
    resultsDirs.setFrom(layout.buildDirectory.dir("allure-results"))
    mustRunAfter(tasks.withType<Test>())
    actions.clear()
    doLast {
        val allureExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) {
            layout.buildDirectory.file("allure/commandline/bin/allure.bat").get().asFile
        } else {
            layout.buildDirectory.file("allure/commandline/bin/allure").get().asFile
        }
        val resultsDir = layout.buildDirectory.dir("allure-results").get().asFile
        val reportDir = layout.buildDirectory.dir("reports/allure-report/allureServe").get().asFile
        val config = layout.projectDirectory.file("config/allurerc.json").asFile

        val command = mutableListOf(
            allureExecutable.absolutePath,
            "generate",
            resultsDir.absolutePath,
            "--config",
            config.absolutePath,
            "--output",
            reportDir.absolutePath,
            "--open"
        )
        port.orNull?.let {
            command.addAll(listOf("--port", it.toString()))
        }

        val process = ProcessBuilder(command)
            .directory(projectDir)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                "Allure serve failed with exit code $exitCode. Command: ${command.joinToString(" ")}"
            )
        }
    }
}

dependencies {
    // JUnit 5
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.suite)
    testRuntimeOnly(libs.junit.platform.launcher)

    // REST Assured + Kotlin Extensions + JSON Schema Validator
    testImplementation(libs.rest.assured)
    testImplementation(libs.rest.assured.kotlin)
    testImplementation(libs.rest.assured.json.schema)

    // Jackson Kotlin + Databind
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)

    // Allure Reporting (JUnit 5 + REST Assured filter)
    testImplementation(platform(libs.allure.bom))
    testImplementation(libs.allure.junit5)
    testImplementation(libs.allure.rest.assured)

    // Assertions
    testImplementation(libs.assertj.core)

    // Local OpenAPI Contract Stub / Mock Server for offline & contract verification
    testImplementation(libs.wiremock.standalone)

    // Logging
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)
}

kotlin {
    jvmToolchain(21)
}

fun hasConfiguredTargetOrMock(): Boolean {
    val dotEnv = file(".env")
    val dotEnvHasBaseUrl = dotEnv.exists() && dotEnv.readLines().any { line ->
        val trimmed = line.trim()
        !trimmed.startsWith("#") &&
            (trimmed.startsWith("BASE_URL=") || trimmed.startsWith("USE_LOCAL_MOCK=true")) &&
            trimmed.substringAfter("=").trim().isNotEmpty()
    }
    return !System.getProperty("BASE_URL").isNullOrBlank() ||
        !System.getProperty("api.base-url").isNullOrBlank() ||
        !System.getenv("BASE_URL").isNullOrBlank() ||
        System.getProperty("USE_LOCAL_MOCK")?.equals("true", ignoreCase = true) == true ||
        System.getProperty("api.use-local-mock")?.equals("true", ignoreCase = true) == true ||
        System.getenv("USE_LOCAL_MOCK")?.equals("true", ignoreCase = true) == true ||
        dotEnvHasBaseUrl
}

fun Test.configureCommonTestSettings() {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")

    // Forward relevant system properties (-DBASE_URL=..., -Dapi.base-url=..., etc.) to test JVM
    val forwardedProps = listOf(
        "BASE_URL",
        "SWAGGER_URL",
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
        "TEST_DEACTIVATED_NTIN",
        "api.base-url",
        "api.swagger-url",
        "api.token",
        "api.x-api-key",
        "api.auth-type",
        "api.header-name",
        "api.username",
        "api.password",
        "api.connect-timeout-ms",
        "api.read-timeout-ms",
        "api.use-local-mock"
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
    val targetConfigured = hasConfiguredTargetOrMock()
    useJUnitPlatform {
        if (!includeTagsProp.isNullOrBlank()) {
            includeTags(*includeTagsProp.split(",").map { it.trim() }.toTypedArray())
        } else if (!targetConfigured) {
            // When no live stand URL or local mock flag is set, run stand-independent offline tests
            includeTags("offline")
        }
        if (!excludeTagsProp.isNullOrBlank()) {
            excludeTags(*excludeTagsProp.split(",").map { it.trim() }.toTypedArray())
        }
    }
}

val testSourceSet = sourceSets.named("test")

tasks.register<Test>("offlineTest") {
    group = "verification"
    description = "Runs stand-independent configuration and auth safeguard tests (@Tag(\"offline\"))."
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("offline")
    }
}

fun registerLayerTask(name: String, tagExpression: String, excludeDestructive: Boolean = false) {
    tasks.register<Test>(name) {
        group = "verification"
        description = "Runs tests matching tag expression: $tagExpression."
        testClassesDirs = testSourceSet.get().output.classesDirs
        classpath = testSourceSet.get().runtimeClasspath
        shouldRunAfter(tasks.named("test"))
        useJUnitPlatform {
            includeTags(tagExpression)
            if (excludeDestructive) {
                excludeTags("destructive")
            }
        }
    }
}

registerLayerTask("apiSmokeTest", "api & smoke", excludeDestructive = true)
registerLayerTask("apiRegressionTest", "api & regression", excludeDestructive = false)

tasks.register("smokeTest") {
    group = "verification"
    description = "Runs all smoke suites (P0)."
    dependsOn("apiSmokeTest")
}

tasks.register("regressionTest") {
    group = "verification"
    description = "Runs all regression suites."
    dependsOn("apiRegressionTest")
}
