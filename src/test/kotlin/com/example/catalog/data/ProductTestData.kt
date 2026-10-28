package com.example.catalog.data

import com.example.catalog.config.TestConfig
import com.example.catalog.models.Measure
import com.example.catalog.models.Product
import com.example.catalog.models.ProductStatus
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

/**
 * Provides deterministic, reproducible test data and builders strictly based on
 * the `Product` and `Measure` schemas in `docs/openapi.json`.
 */
object ProductTestData {

    val config: TestConfig
        get() = TestConfig.instance

    val activeGtin: String
        get() = config.testActiveGtin

    val activeNtin: String
        get() = config.testActiveNtin

    val inactiveGtin: String
        get() = config.testInactiveGtin

    val deactivatedNtin: String
        get() = config.testDeactivatedNtin

    const val GTIN_8_CODE = "48701238"
    const val GTIN_12_CODE = "012345678905"
    const val GTIN_14_CODE = "14870207314263"
    const val LARGE_INT64_PRODUCT_CODE = "4870207314266"
    const val MINIMAL_NULLABLE_PRODUCT_CODE = "4870207000001"
    const val UNKNOWN_GTIN = "9876543210987"
    const val UNKNOWN_NTIN = "9876543210988"

    fun sampleActiveProduct(
        gtin: String = activeGtin,
        ntinCode: String = activeNtin
    ): Product = Product(
        id = 105411556L,
        nameRu = "АНТИСКАЛАНТ GENESYS LF (25 кг)",
        nameKk = "ҚАЛАУҒА ҚАРСЫ GENESYS LF (25 кг)",
        gtin = gtin,
        ntinCode = ntinCode,
        isSocial = false,
        modified = 1792650036L,
        status = ProductStatus.ACTIVE,
        isMarkedEac = false,
        measure = Measure(
            id = 719L,
            name = "кг",
            shortName = "кг",
            code = "166"
        ),
        ntinIsDeactivated = false,
        ntinDeactivationReason = null,
        ntinDuplicateOfProduct = null
    )

    fun sampleInactiveProduct(
        gtin: String = inactiveGtin
    ): Product = Product(
        id = 104599L,
        nameRu = "Архивный товар (снят с производства)",
        nameKk = "Мұрағаттық тауар",
        gtin = gtin,
        ntinCode = "0290000054321",
        isSocial = false,
        modified = 1715000000000L,
        status = ProductStatus.INACTIVE,
        isMarkedEac = false,
        measure = Measure(
            id = 796L,
            name = "Штука",
            shortName = "шт",
            code = "796"
        ),
        ntinIsDeactivated = false,
        ntinDeactivationReason = null,
        ntinDuplicateOfProduct = null
    )

    fun sampleDeactivatedNtinProduct(
        ntinCode: String = deactivatedNtin,
        duplicateOfGtin: String = activeGtin
    ): Product = Product(
        id = 104610L,
        nameRu = "Дублирующая карточка молока 2.5%",
        nameKk = "Қайталанатын сүт картасы 2.5%",
        gtin = "4870207888888",
        ntinCode = ntinCode,
        isSocial = true,
        modified = 1729959000000L,
        status = ProductStatus.INACTIVE,
        isMarkedEac = true,
        measure = Measure(
            id = 112L,
            name = "Литр",
            shortName = "л",
            code = "112"
        ),
        ntinIsDeactivated = true,
        ntinDeactivationReason = "DUPLICATE_PRODUCT_CARD",
        ntinDuplicateOfProduct = duplicateOfGtin
    )

    fun sampleLargeInt64Product(): Product = Product(
        id = 40144006513L,
        nameRu = "0040144006513",
        nameKk = null,
        gtin = LARGE_INT64_PRODUCT_CODE,
        ntinCode = "0040144006513",
        isSocial = null,
        modified = null,
        status = ProductStatus.ACTIVE,
        isMarkedEac = null,
        measure = null,
        ntinIsDeactivated = null,
        ntinDeactivationReason = null,
        ntinDuplicateOfProduct = null
    )

    fun sampleMinimalNullableProduct(): Product = Product(
        id = 40144006643L,
        nameRu = "Товар с минимальным набором полей",
        nameKk = null,
        gtin = MINIMAL_NULLABLE_PRODUCT_CODE,
        ntinCode = "0040144006643",
        isSocial = null,
        modified = null,
        status = ProductStatus.ACTIVE,
        isMarkedEac = null,
        measure = null,
        ntinIsDeactivated = null,
        ntinDeactivationReason = null,
        ntinDuplicateOfProduct = null
    )

    @JvmStatic
    fun nonexistentCodesProvider(): Stream<Arguments> = Stream.of(
        Arguments.of(UNKNOWN_GTIN, "Несуществующий 13-значный код GTIN"),
        Arguments.of(UNKNOWN_NTIN, "Несуществующий 13-значный национальный код NTIN"),
        Arguments.of("9876543210989", "Несуществующий 13-значный штрихкод"),
        Arguments.of("98765432", "Несуществующий 8-значный код GTIN-8")
    )

    @JvmStatic
    fun malformedCodesProvider(): Stream<Arguments> = Stream.of(
        Arguments.of("ABC-INVALID-CODE", "code содержит латинские буквы и дефисы"),
        Arguments.of("487020731426A", "code содержит цифры и латинскую букву"),
        Arguments.of("товар12345", "code содержит кириллические символы"),
        Arguments.of("!@#$%^&*()", "code содержит специальные символы пунктуации"),
        Arguments.of("-4870207314266", "code содержит знак минус (отрицательное число)")
    )

    @JvmStatic
    fun securityPayloadsProvider(): Stream<Arguments> = Stream.of(
        Arguments.of("' OR '1'='1", "SQL-инъекция (тавтология OR '1'='1')"),
        Arguments.of("4870207314266; DROP TABLE product;--", "SQL-инъекция (stacked query DROP TABLE)"),
        Arguments.of("<script>alert(1)</script>", "XSS-инъекция с тегом <script>"),
        Arguments.of("${'$'}{jndi:ldap://127.0.0.1/a}", "Expression / JNDI-инъекция")
    )

    @JvmStatic
    fun boundaryLengthCodesProvider(): Stream<Arguments> = Stream.of(
        Arguments.of("X", 1, "Минимальная длина code: 1 символ"),
        Arguments.of("9876543", 7, "Длина на 1 меньше стандартного GTIN-8 (7 символов)"),
        Arguments.of("987654321098765", 15, "Длина на 1 больше стандартного GTIN-14 (15 символов)"),
        Arguments.of("9".repeat(255), 255, "Граничная длина 255 символов"),
        Arguments.of("9".repeat(1024), 1024, "Сверхдлинная строка 1024 символа в сегменте URL")
    )
}
