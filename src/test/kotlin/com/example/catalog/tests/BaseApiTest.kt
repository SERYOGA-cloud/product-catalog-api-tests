package com.example.catalog.tests

import com.example.catalog.clients.ProductsApiClient
import com.example.catalog.config.TestConfig
import com.example.catalog.extensions.LocalContractStubServer
import io.qameta.allure.Allure
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

/**
 * Base class for Product Catalog API tests.
 *
 * - Initializes [TestConfig] and [ProductsApiClient].
 * - If `USE_LOCAL_MOCK=true`, starts the local OpenAPI WireMock contract server.
 * - If `USE_LOCAL_MOCK=false`, checks reachability of `config.baseUrl` and records
 *   diagnostic details in Allure. If the target server is unreachable from the current
 *   network, marks the live HTTP test as blocked/skipped with a clear diagnostic explanation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseApiTest {

    protected lateinit var config: TestConfig
    protected lateinit var apiClient: ProductsApiClient
    protected lateinit var effectiveBaseUrl: String
    private var apiReachable: Boolean = false

    @BeforeAll
    fun setUpApiTestSuite() {
        config = TestConfig.instance
        if (config.useLocalMock) {
            effectiveBaseUrl = LocalContractStubServer.ensureStarted()
            apiReachable = true
        } else {
            effectiveBaseUrl = config.baseUrl
            apiReachable = LocalContractStubServer.isRemoteReachable(effectiveBaseUrl, config.connectTimeoutMs)
        }
        apiClient = ProductsApiClient(
            config = config,
            baseUrlOverride = effectiveBaseUrl
        )
    }

    @BeforeEach
    fun attachEnvironmentContext() {
        Allure.parameter("profile", config.profile)
        Allure.parameter("targetBaseUrl", effectiveBaseUrl)
        Allure.parameter("swaggerUrl", config.swaggerUrl)
        Allure.parameter("useLocalMock", config.useLocalMock)
    }

    protected fun requireApiReachable() {
        assumeTrue(
            apiReachable,
            "BLOCKED: Target API server '$effectiveBaseUrl' is unreachable from current network " +
                "(connectTimeout=${config.connectTimeoutMs}ms). Connect to corporate VPN or run with -DUSE_LOCAL_MOCK=true for contract stub verification."
        )
    }

    protected fun requireDestructiveOperationsAllowed() {
        requireApiReachable()
        assumeTrue(
            config.allowDestructiveTests || config.useLocalMock,
            "BLOCKED/SKIPPED: State-modifying DELETE operation requires ALLOW_DESTRUCTIVE_TESTS=true when targeting live environment '$effectiveBaseUrl'."
        )
    }
}
