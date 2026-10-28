package com.example.catalog.clients

import com.example.catalog.auth.AuthOverride
import com.example.catalog.auth.AuthProvider
import com.example.catalog.config.RestAssuredConfig
import com.example.catalog.config.TestConfig
import io.qameta.allure.Step
import io.restassured.RestAssured.given
import io.restassured.http.Method
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification

/**
 * HTTP client for the Product Catalog API (`catalog-controller`).
 *
 * Implements only the real endpoints defined in `docs/openapi.json`:
 * - `GET /api/product/{code}` (`operationId: getProduct`)
 * - `DELETE /api/product/download/time/` (`operationId: downloadTime`)
 * - `DELETE /api/product/download/link/` (`operationId: downloadLink`)
 *
 * Does not hide HTTP errors or embed test assertions; returns raw [Response] so tests
 * can assert status codes, headers, schemas, and payloads explicitly.
 */
class ProductsApiClient(
    private val config: TestConfig = TestConfig.instance,
    private val baseUrlOverride: String? = null,
    private val authProvider: AuthProvider = AuthProvider(config)
) {

    companion object {
        const val PRODUCT_BY_CODE_PATH = "/api/product/{code}"
        const val PRODUCT_BASE_PATH = "/api/product/"
        const val DOWNLOAD_TIME_PATH = "/api/product/download/time/"
        const val DOWNLOAD_LINK_PATH = "/api/product/download/link/"
    }

    private fun newRequest(
        authOverride: AuthOverride = AuthOverride.Default,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, Any> = emptyMap()
    ): RequestSpecification {
        val baseSpec = RestAssuredConfig.baseRequestSpec(
            baseUrlOverride = baseUrlOverride,
            config = config
        )
        val spec = given().spec(baseSpec)
        authProvider.applyAuth(spec, authOverride)
        if (headers.isNotEmpty()) {
            spec.headers(headers)
        }
        if (queryParams.isNotEmpty()) {
            spec.queryParams(queryParams)
        }
        return spec
    }

    @Step("GET /api/product/{code} (code={code})")
    fun getProduct(
        code: String,
        authOverride: AuthOverride = AuthOverride.Default,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, Any> = emptyMap()
    ): Response {
        val spec = newRequest(authOverride, headers, queryParams)
        return if (code.contains('{') || code.contains('}')) {
            val encodedSegment = java.net.URLEncoder.encode(code, Charsets.UTF_8).replace("+", "%20")
            spec.urlEncodingEnabled(false)
                .`when`()
                .get("/api/product/$encodedSegment")
                .andReturn()
        } else {
            spec.pathParam("code", code)
                .`when`()
                .get(PRODUCT_BY_CODE_PATH)
                .andReturn()
        }
    }

    @Step("GET /api/product/ (without code path parameter)")
    fun getProductWithoutCode(
        authOverride: AuthOverride = AuthOverride.Default
    ): Response {
        return newRequest(authOverride)
            .`when`()
            .get(PRODUCT_BASE_PATH)
            .andReturn()
    }

    @Step("DELETE /api/product/download/time/")
    fun deleteDownloadTime(
        authOverride: AuthOverride = AuthOverride.Default,
        headers: Map<String, String> = emptyMap()
    ): Response {
        return newRequest(authOverride, headers)
            .`when`()
            .delete(DOWNLOAD_TIME_PATH)
            .andReturn()
    }

    @Step("DELETE /api/product/download/link/")
    fun deleteDownloadLink(
        authOverride: AuthOverride = AuthOverride.Default,
        headers: Map<String, String> = emptyMap()
    ): Response {
        return newRequest(authOverride, headers)
            .`when`()
            .delete(DOWNLOAD_LINK_PATH)
            .andReturn()
    }

    @Step("Send {method} request to {path}")
    fun requestWithMethod(
        method: Method,
        path: String,
        body: Any? = null,
        authOverride: AuthOverride = AuthOverride.Default,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val spec = newRequest(authOverride, headers)
        if (body != null) {
            spec.body(body)
        }
        return spec.`when`().request(method, path).andReturn()
    }
}
