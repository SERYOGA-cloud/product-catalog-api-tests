package com.example.catalog.auth

import com.example.catalog.config.AuthType
import com.example.catalog.config.ConfigurationException
import com.example.catalog.config.TestConfig
import io.restassured.specification.RequestSpecification

sealed interface AuthOverride {
    data object Default : AuthOverride
    data object NoAuth : AuthOverride
    data class BearerToken(val token: String) : AuthOverride
    data class ApiKeyHeader(val headerName: String, val value: String) : AuthOverride
    data class BasicCredentials(val username: String, val password: String) : AuthOverride
}

/**
 * Applies authentication headers/credentials to a REST Assured [RequestSpecification]
 * based on [TestConfig] or an explicit per-request [AuthOverride].
 *
 * By default (`AUTH_TYPE=NONE`), no authentication headers are added, matching the
 * OpenAPI specification (`docs/openapi.json` has no `securitySchemes`).
 */
class AuthProvider(private val config: TestConfig = TestConfig.instance) {

    fun isAuthConfigured(): Boolean = config.authType != AuthType.NONE

    fun applyAuth(
        spec: RequestSpecification,
        override: AuthOverride = AuthOverride.Default
    ): RequestSpecification {
        return when (override) {
            is AuthOverride.NoAuth -> spec
            is AuthOverride.BearerToken -> spec.header("Authorization", "Bearer ${override.token}")
            is AuthOverride.ApiKeyHeader -> spec.header(override.headerName, override.value)
            is AuthOverride.BasicCredentials -> spec.auth().preemptive().basic(override.username, override.password)
            is AuthOverride.Default -> applyConfiguredAuth(spec)
        }
    }

    private fun applyConfiguredAuth(spec: RequestSpecification): RequestSpecification {
        return when (config.authType) {
            AuthType.NONE -> spec
            AuthType.BEARER -> {
                val token = config.apiToken?.takeIf { it.isNotBlank() }
                    ?: throw ConfigurationException(
                        "AUTH_TYPE=BEARER requires a non-empty API_TOKEN environment variable or -DAPI_TOKEN property."
                    )
                spec.header("Authorization", "Bearer $token")
            }
            AuthType.API_KEY -> {
                val token = config.apiToken?.takeIf { it.isNotBlank() }
                    ?: throw ConfigurationException(
                        "AUTH_TYPE=API_KEY requires a non-empty API_TOKEN environment variable or -DAPI_TOKEN property."
                    )
                spec.header(config.apiHeaderName, token)
            }
            AuthType.BASIC -> {
                val user = config.apiUsername?.takeIf { it.isNotBlank() }
                    ?: throw ConfigurationException(
                        "AUTH_TYPE=BASIC requires a non-empty API_USERNAME environment variable."
                    )
                val pass = config.apiPassword
                    ?: throw ConfigurationException(
                        "AUTH_TYPE=BASIC requires an API_PASSWORD environment variable."
                    )
                spec.auth().preemptive().basic(user, pass)
            }
        }
    }
}
