package com.example.catalog.config

import java.io.File
import java.net.URI
import java.util.Properties

enum class AuthType {
    NONE,
    BEARER,
    BASIC,
    API_KEY
}

/**
 * Centralized, immutable configuration for Product Catalog API tests.
 *
 * Resolution priority:
 * 1. JVM System Properties (`-DBASE_URL=...`, `-DSWAGGER_URL=...`, `-DAPI_TOKEN=...`)
 * 2. Environment Variables (`BASE_URL`, `SWAGGER_URL`, `API_TOKEN`)
 * 3. Optional local `.env` file in the project root (if present, git-ignored)
 * 4. Non-environment-specific profile defaults (`config/local.properties` or `config/ci.properties`)
 *
 * No real server addresses, Swagger URLs, or credentials are hardcoded or defaulted.
 * When `USE_LOCAL_MOCK=false` (the default), both `BASE_URL` and `SWAGGER_URL` must be
 * explicitly configured, or a descriptive [ConfigurationException] is thrown immediately.
 */
data class TestConfig(
    val profile: String,
    val baseUrl: String,
    val swaggerUrl: String,
    val authType: AuthType,
    val apiToken: String?,
    val apiHeaderName: String,
    val apiUsername: String?,
    val apiPassword: String?,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val allowDestructiveTests: Boolean,
    val useLocalMock: Boolean,
    val testActiveGtin: String,
    val testActiveNtin: String,
    val testInactiveGtin: String,
    val testDeactivatedNtin: String
) {
    companion object {
        const val LOCAL_MOCK_PLACEHOLDER_URL = "http://localhost:0"
        const val LOCAL_SPEC_PATH = "docs/openapi.json"

        val instance: TestConfig by lazy { load() }

        fun load(
            systemProps: Map<String, String> = System.getProperties()
                .entries
                .associate { it.key.toString() to it.value.toString() },
            envVars: Map<String, String> = System.getenv(),
            dotEnvFile: File = File(".env"),
            overrideProfile: String? = null
        ): TestConfig {
            val isCiEnv = envVars["CI"]?.equals("true", ignoreCase = true) == true
            val resolvedProfile = overrideProfile
                ?: systemProps["TEST_PROFILE"]
                ?: envVars["TEST_PROFILE"]
                ?: if (isCiEnv) "ci" else "local"

            val dotEnvProps = loadDotEnvFile(dotEnvFile)
            val fileProps = loadProfileProperties(resolvedProfile)

            fun resolve(vararg keys: String, default: String? = null): String? {
                for (key in keys) {
                    val fromSys = systemProps[key]?.trim()
                    if (!fromSys.isNullOrEmpty()) return fromSys

                    val fromEnv = envVars[key]?.trim()
                    if (!fromEnv.isNullOrEmpty()) return fromEnv

                    val fromDotEnv = dotEnvProps[key]?.trim()
                    if (!fromDotEnv.isNullOrEmpty()) return fromDotEnv

                    val fromFile = fileProps.getProperty(key)?.trim()
                    if (!fromFile.isNullOrEmpty()) return fromFile
                }
                return default
            }

            val useLocalMock = resolve("USE_LOCAL_MOCK", "api.use-local-mock", default = "false").toBoolean()
            val rawBaseUrl = resolve("BASE_URL", "api.base-url", "API_BASE_URL")
            val rawSwaggerUrl = resolve("SWAGGER_URL", "api.swagger-url", "API_SWAGGER_URL")

            val validatedBaseUrl = if (useLocalMock && rawBaseUrl.isNullOrBlank()) {
                LOCAL_MOCK_PLACEHOLDER_URL
            } else {
                validateBaseUrl(rawBaseUrl, resolvedProfile)
            }

            val validatedSwaggerUrl = if (useLocalMock && rawSwaggerUrl.isNullOrBlank()) {
                LOCAL_SPEC_PATH
            } else {
                validateSwaggerUrl(rawSwaggerUrl, resolvedProfile)
            }

            val rawAuthType = resolve("AUTH_TYPE", "api.auth-type", default = "NONE")!!.uppercase()
            val authType = runCatching { AuthType.valueOf(rawAuthType) }.getOrElse {
                throw ConfigurationException(
                    "Unsupported AUTH_TYPE='$rawAuthType'. Allowed values: ${AuthType.entries.joinToString()}"
                )
            }

            val connectTimeoutMs = resolve("CONNECT_TIMEOUT_MS", "api.connect-timeout-ms", default = "5000")!!.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: throw ConfigurationException("CONNECT_TIMEOUT_MS must be a positive integer.")

            val readTimeoutMs = resolve("READ_TIMEOUT_MS", "api.read-timeout-ms", default = "15000")!!.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: throw ConfigurationException("READ_TIMEOUT_MS must be a positive integer.")

            return TestConfig(
                profile = resolvedProfile,
                baseUrl = validatedBaseUrl.trimEnd('/'),
                swaggerUrl = validatedSwaggerUrl,
                authType = authType,
                apiToken = resolve("API_TOKEN", "api.token", "api.x-api-key", "API_X_API_KEY"),
                apiHeaderName = resolve("API_HEADER_NAME", "api.header-name", default = "X-API-Key")!!,
                apiUsername = resolve("API_USERNAME", "api.username"),
                apiPassword = resolve("API_PASSWORD", "api.password"),
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
                allowDestructiveTests = resolve("ALLOW_DESTRUCTIVE_TESTS", default = "false").toBoolean(),
                useLocalMock = useLocalMock,
                testActiveGtin = resolve("TEST_ACTIVE_GTIN", default = "1234567890123")!!,
                testActiveNtin = resolve("TEST_ACTIVE_NTIN", default = "0200208079406")!!,
                testInactiveGtin = resolve("TEST_INACTIVE_GTIN", default = "4870207999999")!!,
                testDeactivatedNtin = resolve("TEST_DEACTIVATED_NTIN", default = "0290000099999")!!
            )
        }

        fun validateBaseUrl(rawBaseUrl: String?, profile: String = "local"): String {
            if (rawBaseUrl.isNullOrBlank()) {
                throw ConfigurationException(
                    "Configuration error: Required parameter BASE_URL is not set (profile '$profile'). " +
                        "Please set the BASE_URL environment variable or pass -DBASE_URL=<api_base_url> via command line."
                )
            }

            val uri = try {
                URI(rawBaseUrl.trim())
            } catch (ex: Exception) {
                throw ConfigurationException(
                    "Configuration error: BASE_URL '$rawBaseUrl' is not a valid URI (${ex.message}).",
                    ex
                )
            }

            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                throw ConfigurationException(
                    "Configuration error: BASE_URL '$rawBaseUrl' must use http:// or https:// scheme."
                )
            }

            if (uri.host.isNullOrBlank()) {
                throw ConfigurationException(
                    "Configuration error: BASE_URL '$rawBaseUrl' must include a valid host."
                )
            }

            return rawBaseUrl.trim()
        }

        /**
         * Validates `SWAGGER_URL`, which may be:
         * - an HTTP(S) URL to an OpenAPI JSON/YAML document (e.g. `https://api.example.test/v3/api-docs`),
         * - an HTTP(S) URL to a Swagger UI page (e.g. `https://docs.example.test/swagger-ui/index.html`),
         * - or a local file/classpath path to an OpenAPI JSON/YAML file (e.g. `docs/openapi.json`).
         */
        fun validateSwaggerUrl(rawSwaggerUrl: String?, profile: String = "local"): String {
            if (rawSwaggerUrl.isNullOrBlank()) {
                throw ConfigurationException(
                    "Configuration error: Required parameter SWAGGER_URL is not set (profile '$profile'). " +
                        "Please set the SWAGGER_URL environment variable or pass -DSWAGGER_URL=<swagger_or_openapi_url_or_path> via command line."
                )
            }

            val trimmed = rawSwaggerUrl.trim()
            if (trimmed.contains("://")) {
                val uri = try {
                    URI(trimmed)
                } catch (ex: Exception) {
                    throw ConfigurationException(
                        "Configuration error: SWAGGER_URL '$trimmed' is not a valid URI (${ex.message}).",
                        ex
                    )
                }
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https" && scheme != "file") {
                    throw ConfigurationException(
                        "Configuration error: SWAGGER_URL '$trimmed' must use http://, https://, or file:// scheme, or be a valid local file path."
                    )
                }
                if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
                    throw ConfigurationException(
                        "Configuration error: SWAGGER_URL '$trimmed' must include a valid host."
                    )
                }
                return trimmed
            }

            val localFile = File(trimmed)
            val classpathResource = Thread.currentThread().contextClassLoader.getResource(trimmed)
            if (!localFile.exists() && classpathResource == null) {
                throw ConfigurationException(
                    "Configuration error: SWAGGER_URL '$trimmed' is neither an http(s):// URL nor an existing local file."
                )
            }

            return trimmed
        }

        private fun loadDotEnvFile(file: File): Map<String, String> {
            if (!file.exists() || !file.isFile) return emptyMap()
            return file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim().removePrefix("export ").trim()
                    val value = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    key to value
                }
        }

        private fun loadProfileProperties(profile: String): Properties {
            val props = Properties()
            val resourcePath = "config/$profile.properties"
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            if (stream != null) {
                stream.use { props.load(it) }
            }
            return props
        }
    }
}

class ConfigurationException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
