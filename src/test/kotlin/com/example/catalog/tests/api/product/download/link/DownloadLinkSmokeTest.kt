package com.example.catalog.tests.api.product.download.link

import com.example.catalog.assertions.ProductAssertions
import com.example.catalog.config.reportStep
import com.example.catalog.tags.ApiRegression
import com.example.catalog.tags.ApiSmoke
import com.example.catalog.tests.BaseApiTest
import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Owner
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@ApiSmoke
@ApiRegression
@Epic("Product Catalog API")
@Feature("API")
@Story("DELETE /api/product/download/link/")
@Owner("SERYOGA-cloud")
@DisplayName("DELETE /api/product/download/link/: smoke-проверки удаления ссылки выгрузки")
class DownloadLinkSmokeTest : BaseApiTest() {

    @Test
    @Tag("destructive")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-26: успешное удаление ссылки на выгрузку товаров (DELETE /api/product/download/link/)")
    @Description("Проверяет, что запрос DELETE /api/product/download/link/ очищает текущую ссылку на выгрузку товаров и возвращает статус 200 OK.")
    fun deleteDownloadLinkReturnsOk() {
        requireDestructiveOperationsAllowed()

        val response = reportStep("Отправить запрос DELETE /api/product/download/link/") {
            apiClient.deleteDownloadLink()
        }

        ProductAssertions.assertStatusCode(response, 200, "Удаление ссылки на выгрузку товаров")
    }
}
