package com.example.catalog.assertions

import com.example.catalog.config.RestAssuredConfig
import com.example.catalog.config.reportStep
import com.example.catalog.models.Product
import com.example.catalog.models.ProductStatus
import com.fasterxml.jackson.databind.JsonNode
import io.restassured.module.jsv.JsonSchemaValidator
import io.restassured.response.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions

/**
 * Централизованные контрактные и бизнес-проверки для ответов Product Catalog API.
 */
object ProductAssertions {

    private const val PRODUCT_SCHEMA_PATH = "schemas/product-schema.json"
    private const val ENDPOINT_GET_PRODUCT = "GET /api/product/{code}"

    val ALLOWED_PRODUCT_FIELDS = setOf(
        "id",
        "name_ru",
        "name_kk",
        "gtin",
        "ntin_code",
        "is_social",
        "modified",
        "status",
        "is_markedeac",
        "measure",
        "ntin_isdeactivated",
        "ntin_deactivationreason",
        "ntin_duplicateofproduct"
    )

    val ALLOWED_MEASURE_FIELDS = setOf(
        "id",
        "name",
        "short_name",
        "code"
    )

    fun assertStatusCode(
        response: Response,
        expectedStatus: Int,
        scenario: String = "выполнение запроса к API"
    ) {
        reportStep("Проверяем, что HTTP-статус ответа равен $expectedStatus ($scenario)") {
            assertThat(response.statusCode)
                .withFailMessage(
                    "HTTP-статус не соответствует ожиданию (%s). Должен вернуться HTTP <%s>, а вернулся HTTP <%s>. Тело ответа: %s",
                    scenario,
                    expectedStatus,
                    response.statusCode,
                    response.body.asString().take(500)
                )
                .isEqualTo(expectedStatus)
        }
    }

    fun assertStatusCodeIn(
        response: Response,
        vararg expectedStatuses: Int,
        scenario: String = "выполнение запроса к API"
    ) {
        reportStep("Проверяем, что HTTP-статус ответа входит в ${expectedStatuses.toList()} ($scenario)") {
            assertThat(response.statusCode)
                .withFailMessage(
                    "HTTP-статус не соответствует ожиданию (%s). Ожидался один из %s, но вернулся HTTP <%s>. Тело ответа: %s",
                    scenario,
                    expectedStatuses.toList(),
                    response.statusCode,
                    response.body.asString().take(500)
                )
                .isIn(*expectedStatuses.toTypedArray())
        }
    }

    fun assertNotServerError(response: Response, scenario: String = "проверка отсутствия ошибки 5xx") {
        assertThat(response.statusCode)
            .withFailMessage(
                "Сервер не должен возвращать ошибку 5xx (%s), но вернул HTTP <%s>. Тело ответа: %s",
                scenario,
                response.statusCode,
                response.body.asString().take(500)
            )
            .isLessThan(500)
    }

    fun assertMatchesProductSchema(response: Response) {
        reportStep("Валидируем JSON-ответ по схеме OpenAPI ($PRODUCT_SCHEMA_PATH)") {
            response.then().assertThat().body(
                JsonSchemaValidator.matchesJsonSchemaInClasspath(PRODUCT_SCHEMA_PATH)
            )
        }
    }

    fun extractProduct(response: Response): Product {
        return reportStep("Десериализуем JSON-ответ в модель Product") {
            val rawBody = response.body.asString()
            assertThat(rawBody)
                .withFailMessage("Контракт API нарушен: тело ответа $ENDPOINT_GET_PRODUCT пустое, ожидался JSON-объект Product.")
                .isNotBlank()
            RestAssuredConfig.strictObjectMapper.readValue(rawBody, Product::class.java)
        }
    }

    fun assertValidProductResponse(
        response: Response,
        expectedCode: String? = null,
        expectedStatus: ProductStatus? = null
    ): Product {
        assertStatusCode(response, 200, "Получение карточки продукта ${expectedCode ?: ""}".trim())
        assertMatchesProductSchema(response)
        val product = extractProduct(response)
        if (expectedCode != null) {
            assertProductMatchesRequestedCode(product, expectedCode)
        }
        if (expectedStatus != null) {
            reportStep("Проверяем, что статус продукта равен $expectedStatus") {
                assertThat(product.status)
                    .withFailMessage(
                        "Контракт API нарушен: ожидался статус продукта '%s', но получено '%s'.",
                        expectedStatus,
                        product.status
                    )
                    .isEqualTo(expectedStatus)
            }
        }
        return product
    }

    fun assertMeasureFieldsValid(product: Product) {
        val measure = product.measure ?: return
        reportStep("Проверяем поля вложенного справочника единицы измерения Measure") {
            SoftAssertions().apply {
                assertThat(measure.id)
                    .withFailMessage("Контракт API нарушен: поле 'measure.id' должно быть положительным числом.")
                    .isNotNull()
                    .isPositive()
                assertThat(measure.name)
                    .withFailMessage("Контракт API нарушен: поле 'measure.name' не должно быть пустым.")
                    .isNotBlank()
                assertThat(measure.shortName)
                    .withFailMessage("Контракт API нарушен: поле 'measure.short_name' не должно быть пустым.")
                    .isNotBlank()
                assertThat(measure.code)
                    .withFailMessage("Контракт API нарушен: поле 'measure.code' не должно быть пустым.")
                    .isNotBlank()
            }.assertAll()
        }
    }

    fun assertNtinDeactivationCoherence(product: Product) {
        reportStep("Проверяем бизнес-правило деактивации NTIN (ntin_isdeactivated, ntin_deactivationreason, ntin_duplicateofproduct)") {
            SoftAssertions().apply {
                assertThat(product.ntinIsDeactivated)
                    .withFailMessage("Ожидался признак ntin_isdeactivated = true для деактивированного NTIN.")
                    .isTrue()
                assertThat(product.ntinDeactivationReason)
                    .withFailMessage("При ntin_isdeactivated=true поле ntin_deactivationreason не должно быть пустым.")
                    .isNotBlank()
                assertThat(product.ntinDuplicateOfProduct)
                    .withFailMessage("При ntin_isdeactivated=true поле ntin_duplicateofproduct должно содержать код основной карточки.")
                    .isNotBlank()
            }.assertAll()
        }
    }

    fun assertProductMatchesRequestedCode(product: Product, requestedCode: String) {
        reportStep("Проверяем соответствие полей Product запрошенному коду '$requestedCode'") {
            SoftAssertions().apply {
                assertThat(product.id)
                    .withFailMessage(
                        "Контракт API нарушен: поле 'id' в ответе $ENDPOINT_GET_PRODUCT не должно быть null согласно Swagger-схеме Product."
                    )
                    .isNotNull()

                assertThat(listOfNotNull(product.gtin, product.ntinCode))
                    .withFailMessage(
                        "Контракт API нарушен: ни 'gtin' ('%s'), ни 'ntin_code' ('%s') в ответе $ENDPOINT_GET_PRODUCT не совпадают с запрошенным code='%s'.",
                        product.gtin,
                        product.ntinCode,
                        requestedCode
                    )
                    .contains(requestedCode)

                assertThat(product.status)
                    .withFailMessage(
                        "Контракт API нарушен: поле 'status' в ответе $ENDPOINT_GET_PRODUCT должно принимать значение из enum [ACTIVE, INACTIVE], но получено '%s'.",
                        product.status
                    )
                    .isIn(ProductStatus.ACTIVE, ProductStatus.INACTIVE)
            }.assertAll()
        }
    }

    fun assertNoUndocumentedFields(response: Response) {
        reportStep("Проверяем отсутствие незадокументированных полей в объектах Product и Measure") {
            val rootNode: JsonNode = RestAssuredConfig.lenientObjectMapper.readTree(response.body.asString())
            assertThat(rootNode.isObject)
                .withFailMessage("Контракт API нарушен: корневой ответ $ENDPOINT_GET_PRODUCT должен быть JSON-объектом.")
                .isTrue()

            val rootFields = rootNode.fieldNames().asSequence().toSet()
            val unknownRootFields = rootFields - ALLOWED_PRODUCT_FIELDS

            val measureNode = rootNode.get("measure")
            val unknownMeasureFields = if (measureNode != null && !measureNode.isNull && measureNode.isObject) {
                measureNode.fieldNames().asSequence().toSet() - ALLOWED_MEASURE_FIELDS
            } else {
                emptySet()
            }

            SoftAssertions().apply {
                assertThat(unknownRootFields)
                    .withFailMessage(
                        "Контракт API нарушен: объект 'Product' в ответе $ENDPOINT_GET_PRODUCT содержит поля, которых нет в Swagger-схеме: %s",
                        unknownRootFields
                    )
                    .isEmpty()

                if (measureNode != null && !measureNode.isNull) {
                    assertThat(measureNode.isObject)
                        .withFailMessage("Контракт API нарушен: поле 'measure' должно быть JSON-объектом согласно Swagger-схеме Measure.")
                        .isTrue()
                    assertThat(unknownMeasureFields)
                        .withFailMessage(
                            "Контракт API нарушен: объект 'Measure' в ответе $ENDPOINT_GET_PRODUCT содержит поля, которых нет в Swagger-схеме: %s",
                            unknownMeasureFields
                        )
                        .isEmpty()
                }
            }.assertAll()
        }
    }
}
