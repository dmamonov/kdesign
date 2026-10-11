package org.example.app

import kotlin.test.*

class GenerationErrorTest {
    @Test fun distinguishesQuotaFromRateLimits() {
        val quota = generationError(429, """{"error":{"code":"insufficient_quota"}}""")
        assertEquals(429, quota.status)
        assertContains(quota.message!!, "Retrying alone will not fix this")
        val rate = generationError(429, """{"error":{"code":"rate_limit_exceeded"}}""", "12")
        assertContains(rate.message!!, "Wait at least 12 seconds")
    }
    @Test fun specificBillingCodeTakesPrecedenceOverBroadType() {
        val error = generationError(429, """{"error":{"code":"project_spend_limit_exceeded","type":"insufficient_quota"}}""")
        assertContains(error.message!!, "project spend limit")
    }
    @Test fun neverEchoesSensitiveUpstreamMessages() {
        val error = generationError(401, """{"error":{"message":"Invalid key sk-secret","code":"invalid_api_key"}}""")
        assertFalse(error.message!!.contains("sk-secret"))
        assertContains(error.message!!, "API key")
    }
    @Test fun malformedErrorsHaveSafeFallbacks() {
        assertContains(generationError(503, "<html>Unavailable</html>").message!!, "temporarily unavailable")
        assertContains(generationError(429, "{}").message!!, "did not identify")
    }
}
