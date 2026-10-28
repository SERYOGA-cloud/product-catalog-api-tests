package com.example.catalog.tests.api.config

import com.example.catalog.config.ConfigurationException
import com.example.catalog.config.OpenApiSpecLoader
import com.example.catalog.config.TestConfig
import com.example.catalog.config.reportStep
import com.example.catalog.tags.ApiRegression
import com.example.catalog.tags.ApiSmoke
import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Owner
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

@ApiSmoke
@ApiRegression
@Tag("offline")
@Epic("Product Catalog API")
@Feature("Конфигурация и Авторизация")
@Story("Fail-Fast валидация параметров запуска")
@Owner("SERYOGA-cloud")
@DisplayName("Конфигурация стенда: smoke-проверки обязательных параметров BASE_URL и SWAGGER_URL")
class ConfigAndAuthSmokeTest {

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-32: ошибка ConfigurationException при отсутствии обязательных параметров BASE_URL или SWAGGER_URL")
    @Description(
        "Проверяет требование fail-fast: если при запуске без локального мока не заданы BASE_URL или SWAGGER_URL " +
            "(или указана некорректная схема URI), выбрасывается понятное исключение ConfigurationException."
    )
    fun missingOrBlankRequiredUrlsThrowClearConfigurationException() {
        reportStep("Проверить выброс ConfigurationException при пустом BASE_URL") {
            assertThatThrownBy {
                TestConfig.validateBaseUrl("", profile = "local")
            }
                .isInstanceOf(ConfigurationException::class.java)
                .hasMessageContaining("Required parameter BASE_URL is not set")
        }

        reportStep("Проверить выброс ConfigurationException при отсутствии BASE_URL (USE_LOCAL_MOCK=false)") {
            assertThatThrownBy {
                TestConfig.load(
                    systemProps = mapOf("SWAGGER_URL" to "docs/openapi.json", "USE_LOCAL_MOCK" to "false"),
                    envVars = emptyMap(),
                    dotEnvFile = File("nonexistent.env"),
                    overrideProfile = "local"
                )
            }
                .isInstanceOf(ConfigurationException::class.java)
                .hasMessageContaining("BASE_URL")
        }

        reportStep("Проверить выброс ConfigurationException при отсутствии SWAGGER_URL (USE_LOCAL_MOCK=false)") {
            assertThatThrownBy {
                TestConfig.load(
                    systemProps = mapOf("BASE_URL" to "https://api.catalog.example.test", "USE_LOCAL_MOCK" to "false"),
                    envVars = emptyMap(),
                    dotEnvFile = File("nonexistent.env"),
                    overrideProfile = "local"
                )
            }
                .isInstanceOf(ConfigurationException::class.java)
                .hasMessageContaining("Required parameter SWAGGER_URL is not set")
        }

        reportStep("Проверить отклонение неподдерживаемой схемы URI (ftp://)") {
            assertThatThrownBy {
                TestConfig.validateBaseUrl("ftp://api.catalog.example.test", profile = "local")
            }
                .isInstanceOf(ConfigurationException::class.java)
                .hasMessageContaining("must use http:// or https:// scheme")
        }

        reportStep("Проверить автоматическое определение URL JSON-спецификации по ссылке на страницу Swagger UI") {
            val candidates = OpenApiSpecLoader.deriveCandidateSpecUrlsFromSwaggerUi(
                "https://docs.catalog.example.test/context/swagger-ui/index.html"
            )
            assertThat(candidates).contains(
                "https://docs.catalog.example.test/context/v3/api-docs",
                "https://docs.catalog.example.test/v3/api-docs"
            )
        }
    }
}
