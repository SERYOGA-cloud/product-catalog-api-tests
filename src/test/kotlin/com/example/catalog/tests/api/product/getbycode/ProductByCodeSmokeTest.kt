package com.example.catalog.tests.api.product.getbycode

import com.example.catalog.assertions.ProductAssertions
import com.example.catalog.config.reportStep
import com.example.catalog.data.ProductTestData
import com.example.catalog.models.ProductStatus
import com.example.catalog.tags.ApiSmoke
import com.example.catalog.tests.BaseApiTest
import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Owner
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@ApiSmoke
@Epic("Product Catalog API")
@Feature("API")
@Story("GET /api/product/{code}")
@Owner("SERYOGA-cloud")
@DisplayName("GET /api/product/{code}: smoke-проверки получения карточки продукта")
class ProductByCodeSmokeTest : BaseApiTest() {

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-01: Метод GET /api/product/{code} возвращает HTTP 200 и активный продукт по GTIN")
    @Description("Проверяет, что запрос GET /api/product/{code} по валидному 13-значному GTIN возвращает HTTP 200 OK, валидный JSON-объект Product со статусом ACTIVE и непустыми наименованиями.")
    fun getProductByValidActiveGtinReturnsOkAndActiveProduct() {
        requireApiReachable()

        val gtin = ProductTestData.activeGtin
        val response = reportStep("Запросить карточку продукта по валидному GTIN code='$gtin'") {
            apiClient.getProduct(gtin)
        }

        val product = ProductAssertions.assertValidProductResponse(
            response = response,
            expectedCode = gtin,
            expectedStatus = ProductStatus.ACTIVE
        )

        reportStep("Проверить обязательные поля карточки активного продукта") {
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(product.gtin)
                    .withFailMessage("Поле gtin в ответе должно совпадать с запрошенным кодом %s", gtin)
                    .isEqualTo(gtin)
                softly.assertThat(product.nameRu)
                    .withFailMessage("Поле name_ru не должно быть пустым для активного товара")
                    .isNotBlank()
            }
        }
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-02: Метод GET /api/product/{code} возвращает HTTP 200 и продукт по национальному коду NTIN")
    @Description("Проверяет, что параметр пути {code} поддерживает поиск не только по глобальному GTIN, но и по национальному номенклатурному коду NTIN.")
    fun getProductByValidActiveNtinReturnsOkAndMatchingNtin() {
        requireApiReachable()

        val ntin = ProductTestData.activeNtin
        val response = reportStep("Запросить карточку продукта по национальному коду NTIN code='$ntin'") {
            apiClient.getProduct(ntin)
        }

        val product = ProductAssertions.assertValidProductResponse(
            response = response,
            expectedCode = ntin,
            expectedStatus = ProductStatus.ACTIVE
        )

        reportStep("Проверить, что возвращённый объект содержит ожидаемый NTIN '$ntin'") {
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(product.ntinCode)
                    .withFailMessage("Поле ntin_code должно равняться '%s', но получено '%s'", ntin, product.ntinCode)
                    .isEqualTo(ntin)
                softly.assertThat(product.id)
                    .withFailMessage("Поле id должно быть положительным числом")
                    .isPositive()
            }
        }
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-03: Метод GET /api/product/{code} для несуществующего кода не вызывает ошибку сервера 5xx")
    @Description("Проверяет базовую отказоустойчивость: запрос несуществующего штрихкода возвращает клиентский статус 404 Not Found и не приводит к 500 Internal Server Error.")
    fun getProductWithNonExistentCodeDoesNotCauseServerError() {
        requireApiReachable()

        val unknownCode = ProductTestData.UNKNOWN_GTIN
        val response = reportStep("Отправить запрос GET /api/product/$unknownCode для несуществующего штрихкода") {
            apiClient.getProduct(unknownCode)
        }

        ProductAssertions.assertNotServerError(response)
        ProductAssertions.assertStatusCode(response, 404, "Запрос несуществующего кода продукта '$unknownCode'")
    }
}
