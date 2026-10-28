package com.example.catalog.tests.api.product.download.link

import com.example.catalog.assertions.ProductAssertions
import com.example.catalog.clients.ProductsApiClient
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
import io.restassured.http.Method
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@ApiRegression
@Epic("Product Catalog API")
@Feature("API")
@Story("DELETE /api/product/download/link/")
@Owner("SERYOGA-cloud")
@DisplayName("DELETE /api/product/download/link/: регрессионные проверки управления ссылкой выгрузки")
class DownloadLinkRegressionTest : BaseApiTest() {

    @Nested
    @DisplayName("Позитивные и бизнес-проверки DELETE /api/product/download/link/")
    inner class PositiveRegressionTests {

        @Test
        @Tag("destructive")
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-28: идемпотентность повторного вызова DELETE /api/product/download/link/ и сохранность поиска товаров")
        @Description("Проверяет, что два последовательных вызова DELETE /api/product/download/link/ идемпотентны (200 OK) и не влияют на выдачу карточек товаров.")
        fun repeatedDeleteDownloadLinkCallsAreIdempotentAndPreserveProductLookup() {
            requireDestructiveOperationsAllowed()

            val firstDelete = reportStep("Первый вызов DELETE /api/product/download/link/") {
                apiClient.deleteDownloadLink()
            }
            val secondDelete = reportStep("Повторный вызов DELETE /api/product/download/link/ (проверка идемпотентности)") {
                apiClient.deleteDownloadLink()
            }
            val productLookupAfterReset = reportStep("Запросить карточку товара GET /api/product/${ProductTestData.activeGtin} после сброса ссылки выгрузки") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }

            ProductAssertions.assertStatusCode(firstDelete, 200, "Первый сброс ссылки выгрузки")
            ProductAssertions.assertStatusCode(secondDelete, 200, "Повторный сброс ссылки выгрузки")
            ProductAssertions.assertStatusCode(productLookupAfterReset, 200, "Поиск товара после сброса ссылки выгрузки")
            val product = ProductAssertions.extractProduct(productLookupAfterReset)
            reportStep("Убедиться, что карточка товара по-прежнему возвращается корректно") {
                assertThat(product.gtin).isEqualTo(ProductTestData.activeGtin)
            }
        }
    }

    @Nested
    @DisplayName("Негативные проверки DELETE /api/product/download/link/")
    inner class NegativeRegressionTests {

        @Nested
        @DisplayName("Проверки неподдерживаемых HTTP-методов")
        inner class UnsupportedHttpMethodsTests {

            @ParameterizedTest(name = "TC-30: неподдерживаемый HTTP-метод {0} для /api/product/download/link/")
            @EnumSource(value = Method::class, names = ["GET", "POST", "PUT", "PATCH"])
            @Severity(SeverityLevel.MINOR)
            @DisplayName("TC-30: отклонение неподдерживаемых HTTP-методов (GET, POST, PUT, PATCH) на /api/product/download/link/")
            @Description("Проверяет, что вызов GET, POST, PUT и PATCH на эндпоинте /api/product/download/link/ отклоняется со статусом 405 Method Not Allowed (или 404).")
            fun unsupportedHttpMethodsOnDownloadLinkEndpointReturnMethodNotAllowed(method: Method) {
                requireApiReachable()

                val response = reportStep("Отправить запрос $method ${ProductsApiClient.DOWNLOAD_LINK_PATH}") {
                    apiClient.requestWithMethod(method, ProductsApiClient.DOWNLOAD_LINK_PATH)
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCodeIn(
                    response,
                    405,
                    404,
                    scenario = "Вызов неподдерживаемого метода $method для ${ProductsApiClient.DOWNLOAD_LINK_PATH}"
                )
            }
        }
    }
}
