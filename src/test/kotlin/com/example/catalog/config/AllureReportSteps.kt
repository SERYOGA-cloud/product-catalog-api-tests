package com.example.catalog.config

import io.qameta.allure.Allure

/**
 * Wraps a logical test action or verification block in a named Allure step,
 * creating a clean two-level hierarchy in Allure reports:
 * 1. Business step in Russian (`reportStep("...")`)
 * 2. Technical HTTP step (`HTTP GET ...`) with request/cURL/response attachments.
 */
fun <T> reportStep(description: String, action: () -> T): T {
    val stepAction = Allure.ThrowableRunnable<T> { action() }
    return Allure.step(description, stepAction)
}
