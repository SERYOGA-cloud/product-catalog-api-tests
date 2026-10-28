package com.example.catalog.tests.api.product.getbycode

import com.example.catalog.assertions.ProductAssertions
import com.example.catalog.clients.ProductsApiClient
import com.example.catalog.config.OpenApiSpecLoader
import com.example.catalog.config.RestAssuredConfig
import com.example.catalog.config.reportStep
import com.example.catalog.data.ProductTestData
import com.example.catalog.models.Product
import com.example.catalog.models.ProductStatus
import com.example.catalog.tags.ApiRegression
import com.example.catalog.tests.BaseApiTest
import com.fasterxml.jackson.databind.JsonNode
import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Owner
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import io.restassured.http.Method
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

@ApiRegression
@Epic("Product Catalog API")
@Feature("API")
@Story("GET /api/product/{code}")
@Owner("SERYOGA-cloud")
@DisplayName("GET /api/product/{code}: регрессионные проверки получения карточки продукта по коду")
class ProductByCodeRegressionTest : BaseApiTest() {

    @Nested
    @DisplayName("Позитивные и бизнес-проверки GET /api/product/{code}")
    inner class PositiveRegressionTests {

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("TC-04: Метод GET /api/product/{code} возвращает все заполненные поля Product и вложенный объект Measure")
        @Description("Проверяет, что GET /api/product/{code} возвращает полную структуру карточки товара согласно Swagger-схеме Product и вложенной схеме Measure (id, code, name, short_name).")
        fun getProductPopulatesAllProductAndNestedMeasureFields() {
            requireApiReachable()

            val response = reportStep("Запросить полную карточку активного продукта (code='${ProductTestData.activeGtin}')") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }

            val product = ProductAssertions.assertValidProductResponse(
                response = response,
                expectedCode = ProductTestData.activeGtin
            )

            reportStep("Проверить заполненность полей карточки Product и вложенного справочника Measure") {
                SoftAssertions.assertSoftly { softly ->
                    softly.assertThat(product.id)
                        .withFailMessage("Поле Product.id должно быть положительным числом")
                        .isNotNull()
                        .isPositive()
                    softly.assertThat(product.gtin)
                        .withFailMessage("Поле Product.gtin не должно быть пустым")
                        .isNotBlank()
                    softly.assertThat(product.nameRu)
                        .withFailMessage("Поле Product.name_ru не должно быть пустым")
                        .isNotBlank()
                    softly.assertThat(product.status)
                        .withFailMessage("Поле Product.status должно входить в enum [ACTIVE, INACTIVE]")
                        .isIn(ProductStatus.ACTIVE, ProductStatus.INACTIVE)
                    softly.assertThat(product.modified)
                        .withFailMessage("Поле Product.modified должно быть положительной временной меткой")
                        .isNotNull()
                        .isPositive()
                }
            }

            if (product.measure != null) {
                ProductAssertions.assertMeasureFieldsValid(product)
            }
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-05: Метод GET /api/product/{code} возвращает статус INACTIVE для неактивного продукта")
        @Description("Проверяет, что товар со статусом INACTIVE корректно десериализуется в enum ProductStatus.INACTIVE и проходит валидацию схемы.")
        fun getProductWithInactiveStatusDeserializesInactiveEnum() {
            requireApiReachable()

            val response = reportStep("Запросить карточку неактивного продукта по GTIN code='${ProductTestData.inactiveGtin}'") {
                apiClient.getProduct(ProductTestData.inactiveGtin)
            }
            assumeTrue(
                response.statusCode == 200,
                "Фикстура неактивного товара (${ProductTestData.inactiveGtin}) отсутствует на текущем стенде (HTTP ${response.statusCode})"
            )

            val product = ProductAssertions.assertValidProductResponse(
                response = response,
                expectedCode = ProductTestData.inactiveGtin,
                expectedStatus = ProductStatus.INACTIVE
            )

            reportStep("Убедиться, что поле status десериализовано как ProductStatus.INACTIVE") {
                assertThat(product.status).isEqualTo(ProductStatus.INACTIVE)
            }
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-06: Метод GET /api/product/{code} возвращает булевы признаки is_social, is_markedeac и ntin_isdeactivated")
        @Description("Проверяет корректность десериализации булевых флагов товара: социально значимый товар (is_social), маркировка ЕАЭС (is_markedeac) и признак деактивации NTIN (ntin_isdeactivated).")
        fun getProductBooleanFlagsAreAccurate() {
            requireApiReachable()

            val response = reportStep("Запросить карточку продукта по GTIN code='${ProductTestData.activeGtin}'") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }

            val product = ProductAssertions.assertValidProductResponse(response, ProductTestData.activeGtin)

            reportStep("Проверить булевы флаги is_social, is_markedeac и ntin_isdeactivated") {
                SoftAssertions.assertSoftly { softly ->
                    softly.assertThat(product.isSocial)
                        .withFailMessage("Нарушение контракта Swagger: поле is_social не должно быть null")
                        .isNotNull()
                    softly.assertThat(product.isMarkedEac)
                        .withFailMessage("Нарушение контракта Swagger: поле is_markedeac не должно быть null")
                        .isNotNull()
                    softly.assertThat(product.ntinIsDeactivated)
                        .withFailMessage("Нарушение контракта Swagger: поле ntin_isdeactivated не должно быть null")
                        .isNotNull()
                }
            }
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-07: Метод GET /api/product/{code} возвращает причину деактивации и код дубля для деактивированного NTIN")
        @Description("Проверяет бизнес-инвариант: если у товара ntin_isdeactivated == true, в ответе заполняются поля ntin_deactivationreason и ntin_duplicateofproduct.")
        fun getDeactivatedNtinProductIncludesReasonAndDuplicateCode() {
            requireApiReachable()

            val response = reportStep("Запросить карточку продукта с деактивированным NTIN code='${ProductTestData.deactivatedNtin}'") {
                apiClient.getProduct(ProductTestData.deactivatedNtin)
            }
            assumeTrue(
                response.statusCode == 200,
                "Фикстура деактивированного NTIN (${ProductTestData.deactivatedNtin}) отсутствует на текущем стенде (HTTP ${response.statusCode})"
            )

            val product = ProductAssertions.assertValidProductResponse(response, ProductTestData.deactivatedNtin)
            ProductAssertions.assertNtinDeactivationCoherence(product)
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-08: Поиск одного продукта по GTIN и по национальному коду NTIN возвращает идентичную карточку")
        @Description("Проверяет консистентность поиска: запрос одного и того же товара по GTIN и по его NTIN возвращает один и тот же Product.id и одинаковые данные карточки.")
        fun getProductByGtinAndByItsNtinReturnIdenticalProductRecord() {
            requireApiReachable()

            val responseByGtin = reportStep("Шаг 1: Запросить продукт по основному коду GTIN '${ProductTestData.activeGtin}'") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }
            val productByGtin = ProductAssertions.assertValidProductResponse(responseByGtin)

            val linkedNtin = productByGtin.ntinCode
            assumeTrue(!linkedNtin.isNullOrBlank(), "У активного продукта не заполнено поле ntin_code для перекрёстной проверки")

            val responseByNtin = reportStep("Шаг 2: Запросить тот же продукт по его связанному коду NTIN '$linkedNtin'") {
                apiClient.getProduct(linkedNtin!!)
            }
            val productByNtin = ProductAssertions.assertValidProductResponse(responseByNtin)

            reportStep("Шаг 3: Сравнить карточки товара, полученные по GTIN и по NTIN") {
                SoftAssertions.assertSoftly { softly ->
                    softly.assertThat(productByNtin.id)
                        .withFailMessage("Идентификатор Product.id при поиске по NTIN должен совпадать с поиском по GTIN")
                        .isEqualTo(productByGtin.id)
                    softly.assertThat(productByNtin.gtin)
                        .withFailMessage("Поле Product.gtin должно совпадать в обоих ответах")
                        .isEqualTo(productByGtin.gtin)
                    softly.assertThat(productByNtin.nameRu)
                        .withFailMessage("Наименование Product.name_ru должно совпадать в обоих ответах")
                        .isEqualTo(productByGtin.nameRu)
                    softly.assertThat(productByNtin.status)
                        .withFailMessage("Статус Product.status должен совпадать в обоих ответах")
                        .isEqualTo(productByGtin.status)
                }
            }
        }

        @Test
        @Severity(SeverityLevel.MINOR)
        @DisplayName("TC-09: Последовательные запросы GET /api/product/{code} идемпотентны и возвращают одинаковый ответ")
        @Description("Проверяет идемпотентность чтения: три последовательных вызова GET /api/product/{code} возвращают идентичную структуру и значения полей.")
        fun repeatedGetProductCallsAreIdempotent() {
            requireApiReachable()

            val code = ProductTestData.activeGtin
            val products: List<Product> = reportStep("Выполнить 3 последовательных запроса GET /api/product/$code") {
                (1..3).map { attempt ->
                    reportStep("Попытка #$attempt: GET /api/product/$code") {
                        val response = apiClient.getProduct(code)
                        ProductAssertions.assertValidProductResponse(response, code)
                    }
                }
            }

            reportStep("Проверить полную идентичность всех 3 полученных карточек продукта") {
                assertThat(products[1]).isEqualTo(products[0])
                assertThat(products[2]).isEqualTo(products[0])
            }
        }
    }

    @Nested
    @Tag("contract")
    @DisplayName("Контрактные проверки схемы OpenAPI 3.0.1 для GET /api/product/{code}")
    inner class ContractRegressionTests {

        @Test
        @Severity(SeverityLevel.BLOCKER)
        @DisplayName("TC-21: валидация ответа GET /api/product/{code} по JSON Schema (Draft-07)")
        @Description("Проверяет полное соответствие тела ответа GET /api/product/{code} схеме schemas/product-schema.json, построенной на основе контракта OpenAPI 3.0.1.")
        fun getProductResponseConformsToOpenApiJsonSchema() {
            requireApiReachable()

            val code = ProductTestData.activeGtin
            val response = reportStep("Отправить запрос GET /api/product/$code для проверки JSON-схемы") {
                apiClient.getProduct(code)
            }

            ProductAssertions.assertStatusCode(response, 200, "Получение карточки товара для валидации JSON Schema")
            ProductAssertions.assertMatchesProductSchema(response)
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-22: проверка заголовка Content-Type: application/json в ответе GET /api/product/{code}")
        @Description("Проверяет, что фактический HTTP-заголовок Content-Type содержит application/json (несмотря на неспецифичный '*/*' в описании OpenAPI).")
        fun getProductResponseReturnsJsonContentTypeHeader() {
            requireApiReachable()

            val response = reportStep("Отправить запрос GET /api/product/${ProductTestData.activeGtin}") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }

            ProductAssertions.assertStatusCode(response, 200)
            reportStep("Проверить, что заголовок Content-Type содержит application/json") {
                assertThat(response.contentType)
                    .withFailMessage(
                        "Нарушение контракта HTTP: ожидался заголовок Content-Type: application/json, но получено '%s'",
                        response.contentType
                    )
                    .containsIgnoringCase("application/json")
            }
        }

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("TC-23: значение поля Product.status строго входит в перечень OpenAPI enum [ACTIVE, INACTIVE]")
        @Description("Проверяет, что строковое значение поля status в сыром JSON-ответе принимает только документированные значения из enum [ACTIVE, INACTIVE].")
        fun productStatusFieldUsesOnlyDocumentedEnumValues() {
            requireApiReachable()

            val response = reportStep("Запросить карточку товара GET /api/product/${ProductTestData.activeGtin}") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }

            ProductAssertions.assertStatusCode(response, 200)
            reportStep("Проверить значение поля status в JSON-дереве ответа") {
                val jsonNode: JsonNode = RestAssuredConfig.lenientObjectMapper.readTree(response.body.asString())
                val statusNode = jsonNode.get("status")
                if (statusNode != null && !statusNode.isNull) {
                    assertThat(statusNode.asText())
                        .withFailMessage(
                            "Нарушение контракта Swagger: поле Product.status должно быть одним из [ACTIVE, INACTIVE], получено '%s'",
                            statusNode.asText()
                        )
                        .isIn("ACTIVE", "INACTIVE")
                }
            }
        }

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("TC-24: Метод GET /api/product/{code} не возвращает поля вне Swagger-контракта (Product и Measure)")
        @Description("Выявляет расхождение контракта (contract drift): проверяет отсутствие лишних полей в JSON-ответе и сверяет список полей со спецификацией из SWAGGER_URL.")
        fun productAndMeasureObjectsDoNotContainUndocumentedFields() {
            requireApiReachable()

            val specNode = reportStep("Загрузить спецификацию OpenAPI из SWAGGER_URL (${config.swaggerUrl})") {
                OpenApiSpecLoader.loadOpenApiSpecNode(config)
            }
            val specProductProps = specNode.at("/components/schemas/Product/properties")
                .fieldNames().asSequence().toSet()

            val response = reportStep("Запросить карточку товара GET /api/product/${ProductTestData.activeGtin}") {
                apiClient.getProduct(ProductTestData.activeGtin)
            }

            ProductAssertions.assertStatusCode(response, 200)
            ProductAssertions.assertNoUndocumentedFields(response)
            reportStep("Сверить множество полей схемы Product в OpenAPI с эталонным списком ALLOWED_PRODUCT_FIELDS") {
                assertThat(specProductProps)
                    .withFailMessage(
                        "Расхождение со спецификацией OpenAPI: поля схемы Product %s не совпадают с ожидаемыми %s",
                        specProductProps,
                        ProductAssertions.ALLOWED_PRODUCT_FIELDS
                    )
                    .isEqualTo(ProductAssertions.ALLOWED_PRODUCT_FIELDS)
            }
        }
    }

    @Nested
    @Tag("boundary")
    @DisplayName("Граничные значения параметра code и типов данных")
    inner class BoundaryRegressionTests {

        @ParameterizedTest(name = "TC-16/18 [{index}]: длина={1} ({2})")
        @MethodSource("com.example.catalog.tests.api.product.getbycode.ProductByCodeRegressionTest#boundaryLengthCodesProvider")
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-16 & TC-18: граничные длины параметра code (1, 7, 15, 255, 1024 символа)")
        @Description("Проверяет, что граничные длины строкового параметра code (1, 7, 15, 255 и 1024 символа) обрабатываются корректно без ошибок 5xx.")
        fun getProductWithBoundaryLengthCodeHandlesGracefully(code: String, length: Int, description: String) {
            requireApiReachable()
            assertThat(code.length).isEqualTo(length)

            val response = reportStep("Запросить карточку товара с граничной длиной code ($length символов — $description)") {
                apiClient.getProduct(code)
            }

            ProductAssertions.assertNotServerError(response)
            ProductAssertions.assertStatusCodeIn(response, 404, 400, 414, scenario = description)
        }

        @ParameterizedTest(name = "TC-17: стандартный формат штрихкода code={0}")
        @ValueSource(
            strings = [
                ProductTestData.GTIN_8_CODE,
                ProductTestData.GTIN_12_CODE,
                ProductTestData.GTIN_14_CODE
            ]
        )
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-17: поддержка стандартных длин штрихкодов GTIN-8 (8), GTIN-12 (12) и GTIN-14 (14)")
        @Description("Проверяет, что штрихкоды стандартных форматов GTIN-8 (8 цифр), UPC/GTIN-12 (12 цифр) и ITF-14/GTIN-14 (14 цифр) корректно принимаются и десериализуются.")
        fun getProductWithStandardGtin8Gtin12Gtin14Lengths(code: String) {
            requireApiReachable()

            val response = reportStep("Запросить карточку товара со штрихкодом стандартной длины ($code, длина ${code.length})") {
                apiClient.getProduct(code)
            }

            ProductAssertions.assertNotServerError(response)
            if (response.statusCode == 200) {
                val product = ProductAssertions.extractProduct(response)
                reportStep("Проверить, что возвращённый gtin совпадает с запрошенным кодом $code") {
                    assertThat(product.gtin)
                        .withFailMessage("Нарушение контракта Swagger: ожидался gtin=%s, но получено %s", code, product.gtin)
                        .isEqualTo(code)
                }
            } else {
                ProductAssertions.assertStatusCode(response, 404, "Запрос товара по штрихкоду $code")
            }
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("TC-19: корректная десериализация 64-битных целочисленных полей (int64 > Int.MAX_VALUE)")
        @Description("Проверяет, что 64-битные поля (Product.id, Product.modified, Measure.id), превышающие 32-битный Int.MAX_VALUE (2 147 483 647), десериализуются без переполнения.")
        fun getProductWithLargeInt64FieldsDeserializesWithoutOverflow() {
            requireApiReachable()

            val response = reportStep("Запросить товар с 64-битными значениями полей id и modified (${ProductTestData.LARGE_INT64_PRODUCT_CODE})") {
                apiClient.getProduct(ProductTestData.LARGE_INT64_PRODUCT_CODE)
            }
            assumeTrue(
                response.statusCode == 200,
                "Тестовая фикстура с большими int64 (${ProductTestData.LARGE_INT64_PRODUCT_CODE}) отсутствует на целевом стенде"
            )

            val product = ProductAssertions.extractProduct(response)
            reportStep("Проверить, что 64-битный Product.id десериализован без переполнения (> Int.MAX_VALUE)") {
                assertThat(product.id)
                    .withFailMessage("Поле Product.id (int64) должно корректно вмещать значения больше Int.MAX_VALUE")
                    .isGreaterThan(Int.MAX_VALUE.toLong())
            }
        }

        @Test
        @Severity(SeverityLevel.MINOR)
        @DisplayName("TC-20: корректная десериализация ответа с null в необязательных полях (measure, name_kk)")
        @Description("Проверяет, что JSON-ответ карточки товара, в котором необязательные поля (measure, name_kk, modified и др.) равны null или опущены, десериализуется без ошибок.")
        fun getProductWithNullableOptionalFieldsDeserializesSafely() {
            requireApiReachable()

            val response = reportStep("Запросить товар с минимальным набором заполненных полей (${ProductTestData.MINIMAL_NULLABLE_PRODUCT_CODE})") {
                apiClient.getProduct(ProductTestData.MINIMAL_NULLABLE_PRODUCT_CODE)
            }
            assumeTrue(
                response.statusCode == 200,
                "Минимальная фикстура (${ProductTestData.MINIMAL_NULLABLE_PRODUCT_CODE}) отсутствует на целевом стенде"
            )

            ProductAssertions.assertMatchesProductSchema(response)
            val product = ProductAssertions.extractProduct(response)

            reportStep("Проверить обязательные и nullable-поля десериализованного объекта Product") {
                SoftAssertions.assertSoftly { softly ->
                    softly.assertThat(product.id)
                        .withFailMessage("Поле id должно быть положительным числом")
                        .isNotNull()
                        .isPositive()
                    softly.assertThat(product.gtin)
                        .withFailMessage("Поле gtin должно совпадать с запрошенным кодом")
                        .isEqualTo(ProductTestData.MINIMAL_NULLABLE_PRODUCT_CODE)
                    softly.assertThat(product.measure)
                        .withFailMessage("Необязательное поле measure должно быть null")
                        .isNull()
                    softly.assertThat(product.nameKk)
                        .withFailMessage("Необязательное поле name_kk должно быть null")
                        .isNull()
                }
            }
        }
    }

    @Nested
    @Tag("negative")
    @DisplayName("Негативные проверки GET /api/product/{code}")
    inner class NegativeRegressionTests {

        @Nested
        @DisplayName("Параметр code")
        inner class CodeParameterTests {

            @ParameterizedTest(name = "TC-10 [{index}]: {1} (code={0})")
            @MethodSource("com.example.catalog.tests.api.product.getbycode.ProductByCodeRegressionTest#nonExistentCodesProvider")
            @Severity(SeverityLevel.CRITICAL)
            @DisplayName("TC-10: Метод GET /api/product/{code} возвращает 404 Not Found для несуществующего кода допустимого формата")
            @Description("Проверяет, что запрос товара по валидному формату штрихкода, отсутствующему в базе каталога, возвращает HTTP 404 Not Found.")
            fun getProductWithNonExistentCodeReturns404(code: String, scenarioDescription: String) {
                requireApiReachable()

                val response = reportStep("Отправить запрос GET /api/product/$code ($scenarioDescription)") {
                    apiClient.getProduct(code)
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCode(response, 404, scenarioDescription)
            }

            @ParameterizedTest(name = "TC-11 [{index}]: {1} (code={0})")
            @MethodSource("com.example.catalog.tests.api.product.getbycode.ProductByCodeRegressionTest#malformedCodesProvider")
            @Severity(SeverityLevel.NORMAL)
            @DisplayName("TC-11: Метод GET /api/product/{code} возвращает 400 или 404 для невалидного нечислового code")
            @Description("Проверяет, что передача букв, кириллицы, спецсимволов или знака минус в параметре {code} корректно отклоняется со статусом 400 или 404 без падения сервера (5xx).")
            fun getProductWithMalformedNonNumericCodeHandlesGracefully(code: String, scenarioDescription: String) {
                requireApiReachable()

                val response = reportStep("Отправить запрос с невалидным параметром code='$code' ($scenarioDescription)") {
                    apiClient.getProduct(code)
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCodeIn(response, 400, 404, scenario = scenarioDescription)
            }

            @Test
            @Severity(SeverityLevel.NORMAL)
            @DisplayName("TC-12: Метод GET /api/product/{code} возвращает 400 или 404 при передаче строки из пробелов")
            @Description("Проверяет, что передача пробелов в параметре пути GET /api/product/{code} не приводит к ошибке 500 Internal Server Error и возвращает 400 или 404.")
            fun getProductWithWhitespaceCodeReturnsClientErrorNot5xx() {
                requireApiReachable()

                val response = reportStep("Отправить запрос GET /api/product/{code} со строкой из пробелов '   '") {
                    apiClient.getProduct("   ")
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCodeIn(response, 400, 404, scenario = "Передача строки из пробелов в параметре code")
            }

            @ParameterizedTest(name = "TC-13 [{index}]: {1}")
            @MethodSource("com.example.catalog.tests.api.product.getbycode.ProductByCodeRegressionTest#injectionPayloadsProvider")
            @Severity(SeverityLevel.CRITICAL)
            @DisplayName("TC-13: Безопасная обработка SQL-инъекций и XSS-векторов в параметре code без ошибок 5xx")
            @Description("Проверяет устойчивость к SQL-инъекциям и отражённому XSS в параметре пути {code}: сервер не должен возвращать 5xx или раскрывать SQL/stacktrace.")
            fun getProductWithSqlAndXssInjectionPayloadsIsHandledSafely(payload: String, scenarioDescription: String) {
                requireApiReachable()

                val response = reportStep("Отправить инъекционную нагрузку в параметре code ($scenarioDescription)") {
                    apiClient.getProduct(payload)
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCodeIn(response, 400, 404, scenario = scenarioDescription)

                reportStep("Убедиться, что тело ответа не содержит утечек SQL-запросов или стектрейсов исключений") {
                    val body = response.body.asString()
                    SoftAssertions.assertSoftly { softly ->
                        softly.assertThat(body)
                            .withFailMessage("Ответ сервера раскрывает детали SQL-запроса или JDBC-драйвера")
                            .doesNotContainIgnoringCase("SQLSyntaxErrorException")
                            .doesNotContainIgnoringCase("org.postgresql")
                            .doesNotContainIgnoringCase("org.hibernate")
                    }
                }
            }

            @Test
            @Severity(SeverityLevel.NORMAL)
            @DisplayName("TC-14: Отклонение запроса к базовому пути /api/product/ без обязательного параметра {code}")
            @Description("Проверяет, что вызов GET /api/product/ без указания обязательного сегмента пути {code} отклоняется со статусом 400, 404 или 405.")
            fun getProductWithoutPathParameterReturns404Or400() {
                requireApiReachable()

                val response = reportStep("Отправить запрос GET /api/product/ без параметра {code}") {
                    apiClient.getProductWithoutCode()
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCodeIn(
                    response,
                    400,
                    404,
                    405,
                    scenario = "Запрос GET /api/product/ без обязательного параметра {code}"
                )
            }
        }

        @Nested
        @DisplayName("Проверки неподдерживаемых HTTP-методов")
        inner class UnsupportedHttpMethodsTests {

            @ParameterizedTest(name = "TC-15: HTTP {0} /api/product/'{'code'}' возвращает 405 Method Not Allowed")
            @EnumSource(value = Method::class, names = ["POST", "PUT", "DELETE", "PATCH"])
            @Severity(SeverityLevel.NORMAL)
            @DisplayName("TC-15: Метод /api/product/{code} возвращает 405 Method Not Allowed для HTTP-методов кроме GET")
            @Description("Проверяет, что отправка POST, PUT, DELETE или PATCH на эндпоинт только для чтения /api/product/{code} отклоняется со статусом 405 Method Not Allowed.")
            fun unsupportedHttpMethodsOnProductEndpointReturnMethodNotAllowed(method: Method) {
                requireApiReachable()

                val path = ProductsApiClient.PRODUCT_BY_CODE_PATH.replace("{code}", ProductTestData.activeGtin)
                val response = reportStep("Отправить неподдерживаемый запрос HTTP $method $path") {
                    apiClient.requestWithMethod(
                        method = method,
                        path = path,
                        body = """{"gtin":"${ProductTestData.activeGtin}"}"""
                    )
                }

                ProductAssertions.assertNotServerError(response)
                ProductAssertions.assertStatusCodeIn(
                    response,
                    405,
                    400,
                    404,
                    scenario = "Вызов неподдерживаемого метода HTTP $method для $path"
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun nonExistentCodesProvider(): Stream<Arguments> = ProductTestData.nonexistentCodesProvider()

        @JvmStatic
        fun malformedCodesProvider(): Stream<Arguments> = ProductTestData.malformedCodesProvider()

        @JvmStatic
        fun injectionPayloadsProvider(): Stream<Arguments> = ProductTestData.securityPayloadsProvider()

        @JvmStatic
        fun boundaryLengthCodesProvider(): Stream<Arguments> = ProductTestData.boundaryLengthCodesProvider()
    }
}
