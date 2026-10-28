package com.example.catalog.tests.api.config

import com.example.catalog.auth.AuthOverride
import com.example.catalog.auth.AuthProvider
import com.example.catalog.config.AuthType
import com.example.catalog.config.ConfigurationException
import com.example.catalog.config.TestConfig
import com.example.catalog.config.reportStep
import com.example.catalog.data.ProductTestData
import com.example.catalog.tags.ApiRegression
import com.example.catalog.tests.BaseApiTest
import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Owner
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

@ApiRegression
@Epic("Product Catalog API")
@Feature("Конфигурация и Авторизация")
@Story("Безопасность AuthProvider и защита эндпоинтов")
@Owner("SERYOGA-cloud")
@DisplayName("Конфигурация и Авторизация: регрессионные проверки провайдера аутентификации")
class ConfigAndAuthRegressionTest {

    @Nested
    @Tag("auth")
    @Tag("offline")
    @DisplayName("Автономные проверки провайдера аутентификации AuthProvider")
    inner class OfflineAuthProviderSafeguardTests {

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-31a: ошибка ConfigurationException при AUTH_TYPE=BEARER без заданного API_TOKEN")
        @Description("Проверяет, что при выборе режима AUTH_TYPE=BEARER без передачи токена API_TOKEN выбрасывается понятное исключение ConfigurationException.")
        fun bearerAuthTypeWithoutTokenThrowsConfigurationException() {
            val baseConfig = TestConfig.load(
                systemProps = mapOf("USE_LOCAL_MOCK" to "true"),
                envVars = emptyMap(),
                dotEnvFile = File("nonexistent.env"),
                overrideProfile = "local"
            )
            val bearerConfigWithoutToken = baseConfig.copy(
                authType = AuthType.BEARER,
                apiToken = null
            )
            val provider = AuthProvider(bearerConfigWithoutToken)

            reportStep("Проверить, что попытка применить Bearer-авторизацию без API_TOKEN завершается ошибкой ConfigurationException") {
                assertThat(provider.isAuthConfigured()).isTrue()
                assertThatThrownBy {
                    val dummySpec = io.restassured.RestAssured.given()
                    provider.applyAuth(dummySpec, AuthOverride.Default)
                }
                    .isInstanceOf(ConfigurationException::class.java)
                    .hasMessageContaining("AUTH_TYPE=BEARER requires a non-empty API_TOKEN")
            }
        }
    }

    @Nested
    @Tag("auth")
    @DisplayName("Проверки защищённого эндпоинта на стенде с авторизацией")
    inner class LiveAuthEndpointTests : BaseApiTest() {

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-31b: отклонение запроса с невалидным Bearer-токеном (401/403), если на стенде включена авторизация")
        @Description("Если на целевом стенде включена аутентификация (AUTH_TYPE != NONE), проверяет, что запрос с недействительным токеном отклоняется со статусом 401 или 403.")
        fun requestWithInvalidTokenIsRejectedWhenAuthIsEnabledOnServer() {
            requireApiReachable()
            assumeTrue(
                config.authType != AuthType.NONE,
                "Пропущено, так как AUTH_TYPE=NONE (в спецификации OpenAPI данного сервиса не объявлены securitySchemes)."
            )

            val response = reportStep("Отправить запрос GET /api/product/${ProductTestData.activeGtin} с заведомо невалидным Bearer-токеном") {
                apiClient.getProduct(
                    code = ProductTestData.activeGtin,
                    authOverride = AuthOverride.BearerToken("invalid-expired-token-000")
                )
            }

            reportStep("Проверить, что сервер вернул статус 401 Unauthorized или 403 Forbidden") {
                assertThat(response.statusCode).isIn(401, 403)
            }
        }
    }
}
