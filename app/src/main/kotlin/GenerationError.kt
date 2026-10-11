package org.example.app

import kdesign.json
import kotlinx.serialization.json.*

class GenerationError(val status: Int, val errorCode: String, message: String) : RuntimeException(message)

// Never forward the raw upstream body: authentication errors can contain credential fragments.
fun generationError(status: Int, body: String, retryAfter: String? = null): GenerationError {
    val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
    fun field(name: String) = (error?.get(name) as? JsonPrimitive)?.contentOrNull
    val code = field("code") ?: field("type") ?: "unknown_error"
    val explanation = when(code) {
        "credit_balance_exhausted" -> "OpenAI API credits are exhausted. Add credits in the API billing settings before generating again."
        "project_spend_limit_exceeded" -> "The OpenAI project spend limit was reached. Check that project's API limits before generating again."
        "organization_spend_limit_exceeded" -> "The OpenAI organization spend limit was reached. Check the organization's API billing limits."
        "organization_usage_limit_exceeded" -> "The OpenAI organization usage limit was reached. Request a higher usage limit."
        "insufficient_quota" -> "OpenAI API quota is unavailable. Check credits, billing, and limits for the project owning OPENAI_API_KEY. Retrying alone will not fix this."
        "rate_limit_exceeded", "slow_down" -> "OpenAI temporarily rate-limited generation. " +
            (retryAfter?.toLongOrNull()?.takeIf {it>=0}?.let {"Wait at least $it seconds before trying again."} ?: "Wait before trying again.")
        else -> when {
            status==401 -> "OpenAI rejected the API key. Check OPENAI_API_KEY on the server and restart it after changing the key."
            status==403 || status==404 -> "OpenAI denied access to the requested model. Check OPENAI_IMAGE_MODEL and the API project's image-model permissions or verification requirements."
            status==429 -> if(field("type")=="insufficient_quota") "OpenAI API billing or quota blocked generation. Check the API project's billing and limits before retrying." else "OpenAI returned HTTP 429. Check API billing and rate limits; the response did not identify a recognized cause."
            status>=500 -> "OpenAI is temporarily unavailable. Try generating again later."
            else -> "OpenAI rejected the generation request (HTTP $status). Check the configured model and request settings."
        }
    }
    val safeCode=code.takeIf {it.matches(Regex("[a-z_]{1,80}"))} ?: "unknown_error"
    return GenerationError(status, safeCode, "$explanation ($safeCode)")
}
