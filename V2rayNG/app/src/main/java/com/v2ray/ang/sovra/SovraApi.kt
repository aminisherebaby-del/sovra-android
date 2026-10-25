package com.v2ray.ang.sovra

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Error from the SOVRA API. [code] is the stable machine code the server sends (or a local one such as "network"). */
class SovraApiException(val code: String, message: String) : Exception(message)

data class SovraConfig(val name: String, val country: String, val link: String)

data class SovraServers(val configs: List<SovraConfig>, val version: String)

data class SovraAccount(
    val active: Boolean,
    val reason: String,
    val planType: String,
    val quotaBytes: Long,
    val usedBytes: Long,
    val remainingBytes: Long?,
    val daysLeft: Int?,
    val username: String = "",
    val subLink: String = "",
    val canStartTest: Boolean = false,
    val isTest: Boolean = false,
)

data class SovraAppConfig(
    val announcement: String,
    val maintenance: Boolean,
    val minVersion: String,
    val supportUrl: String,
    val testsEnabled: Boolean,
)

data class SovraUnlimitedTier(val users: Int, val price: Long)

data class SovraPlans(val pricePerGb: Long, val periodDays: Int, val unlimited: List<SovraUnlimitedTier>)

data class SovraOrderResult(val orderId: Long, val amount: Long, val cardNumber: String, val cardHolder: String)

data class SovraOrder(
    val id: Long,
    val kind: String,
    val gb: Int,
    val users: Int,
    val amount: Long,
    val status: String,
)

object SovraApi {
    const val BASE_URL = "https://api.sovra.ir"
    private const val TIMEOUT_MS = 15000

    /** Logs in with the user's subscription link. Returns the session token. Blocking: call from a background thread. */
    fun loginSub(sub: String): String {
        val body = JSONObject().put("sub", sub.trim()).toString()
        val json = request("POST", "/v1/auth/login-sub", null, body)
        val token = json.optString("token")
        if (token.isEmpty()) throw SovraApiException("bad_response", "پاسخ سرور نامعتبره")
        return token
    }

    /** Fetches the server list of the logged-in account. Blocking. */
    fun servers(token: String): SovraServers {
        val json = request("GET", "/v1/servers", token, null)
        val arr = json.optJSONArray("configs")
            ?: throw SovraApiException("bad_response", "پاسخ سرور نامعتبره")
        val list = ArrayList<SovraConfig>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val link = o.optString("link")
            if (link.isNotBlank()) {
                list.add(SovraConfig(o.optString("name"), o.optString("country"), link))
            }
        }
        if (list.isEmpty()) throw SovraApiException("no_configs", "سروری برای این اکانت پیدا نشد")
        return SovraServers(list, json.optString("version"))
    }

    /** Fetches usage and expiry of the logged-in account. Blocking. */
    fun me(token: String): SovraAccount {
        val json = request("GET", "/v1/me", token, null)
        val acc = json.optJSONObject("account")
            ?: throw SovraApiException("bad_response", "پاسخ سرور نامعتبره")
        return SovraAccount(
            active = acc.optBoolean("active"),
            reason = acc.optString("reason"),
            planType = acc.optString("plan_type"),
            quotaBytes = acc.optLong("quota_bytes"),
            usedBytes = acc.optLong("used_bytes"),
            remainingBytes = if (acc.isNull("remaining_bytes")) null else acc.optLong("remaining_bytes"),
            daysLeft = if (acc.isNull("days_left")) null else acc.optInt("days_left"),
            username = json.optString("username"),
            subLink = if (json.isNull("sub_link")) "" else json.optString("sub_link"),
            canStartTest = json.optBoolean("can_start_test"),
            isTest = json.optBoolean("is_test"),
        )
    }

    /** Public app config (announcement, maintenance, support). No auth needed. Blocking. */
    fun appConfig(): SovraAppConfig {
        val json = request("GET", "/v1/app-config", null, null)
        return SovraAppConfig(
            announcement = json.optString("announcement"),
            maintenance = json.optBoolean("maintenance"),
            minVersion = json.optString("min_version"),
            supportUrl = json.optString("support_url"),
            testsEnabled = json.optBoolean("tests_enabled", true),
        )
    }

    /** Plan catalogue. No auth needed. Blocking. */
    fun plans(): SovraPlans {
        val json = request("GET", "/v1/plans", null, null)
        val tiers = ArrayList<SovraUnlimitedTier>()
        val arr = json.optJSONArray("unlimited")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tiers.add(SovraUnlimitedTier(o.optInt("users"), o.optLong("price")))
            }
        }
        return SovraPlans(json.optLong("price_per_gb"), json.optInt("period_days", 30), tiers)
    }

    /** Registers a new username/password account. Returns the session token. Blocking. */
    fun register(username: String, password: String): String {
        val body = JSONObject().put("username", username.trim()).put("password", password).toString()
        val json = request("POST", "/v1/auth/register", null, body)
        val token = json.optString("token")
        if (token.isEmpty()) throw SovraApiException("bad_response", "پاسخ سرور نامعتبره")
        return token
    }

    /** Logs in with username/password. Returns the session token. Blocking. */
    fun login(username: String, password: String): String {
        val body = JSONObject().put("username", username.trim()).put("password", password).toString()
        val json = request("POST", "/v1/auth/login", null, body)
        val token = json.optString("token")
        if (token.isEmpty()) throw SovraApiException("bad_response", "پاسخ سرور نامعتبره")
        return token
    }

    /** Starts the one-time free test (24h / 512MB) for the logged-in user. Blocking. */
    fun startTest(token: String) {
        request("POST", "/v1/test", token, "{}")
    }

    /** Creates an order. kind is "volume" (uses gb) or "unlimited" (uses users). Blocking. */
    fun createOrder(token: String, kind: String, gb: Int, users: Int): SovraOrderResult {
        val body = JSONObject().put("kind", kind).put("gb", gb).put("users", users).toString()
        val json = request("POST", "/v1/orders", token, body)
        return SovraOrderResult(
            orderId = json.optLong("order_id"),
            amount = json.optLong("amount"),
            cardNumber = json.optString("card_number"),
            cardHolder = json.optString("card_holder"),
        )
    }

    /** The user's orders, newest first. Blocking. */
    fun orders(token: String): List<SovraOrder> {
        val json = request("GET", "/v1/orders", token, null)
        val arr = json.optJSONArray("orders") ?: return emptyList()
        val list = ArrayList<SovraOrder>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                SovraOrder(
                    id = o.optLong("id"),
                    kind = o.optString("kind"),
                    gb = o.optInt("gb"),
                    users = o.optInt("users"),
                    amount = o.optLong("amount"),
                    status = o.optString("status"),
                )
            )
        }
        return list
    }

    /** Uploads a receipt image (JPG/PNG/WEBP, max 5MB) for an order. Blocking. */
    fun uploadReceipt(token: String, orderId: Long, bytes: ByteArray, mime: String) {
        if (bytes.size > 5 * 1024 * 1024) throw SovraApiException("file_too_big", "حجم عکس بیشتر از ۵ مگابایته")
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val boundary = "SovraBoundary" + System.currentTimeMillis()
        val conn = URL(BASE_URL + "/v1/orders/" + orderId + "/receipt").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                val w = out.bufferedWriter(Charsets.UTF_8)
                w.write("--$boundary\r\n")
                w.write("Content-Disposition: form-data; name=\"file\"; filename=\"receipt.$ext\"\r\n")
                w.write("Content-Type: $mime\r\n\r\n")
                w.flush()
                out.write(bytes)
                w.write("\r\n--$boundary--\r\n")
                w.flush()
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (status !in 200..299) throw errorFrom(status, text)
        } catch (e: SovraApiException) {
            throw e
        } catch (e: IOException) {
            throw SovraApiException("network", "اتصال به سرور برقرار نشد. اینترنت رو چک کن و دوباره امتحان کن")
        } finally {
            conn.disconnect()
        }
    }

    /** Ends the session on the server. Errors are ignored: the local session is removed anyway. */
    fun logout(token: String) {
        try {
            request("POST", "/v1/auth/logout", token, "{}")
        } catch (e: SovraApiException) {
            // the local session is removed regardless
        }
    }

    private fun request(method: String, path: String, token: String?, body: String?): JSONObject {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (status in 200..299) return JSONObject(text)
            throw errorFrom(status, text)
        } catch (e: SovraApiException) {
            throw e
        } catch (e: IOException) {
            throw SovraApiException("network", "اتصال به سرور برقرار نشد. اینترنت رو چک کن و دوباره امتحان کن")
        } catch (e: JSONException) {
            throw SovraApiException("bad_response", "پاسخ سرور نامعتبره")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorFrom(status: Int, text: String): SovraApiException {
        try {
            val detail = JSONObject(text).opt("detail")
            if (detail is JSONObject) {
                return SovraApiException(
                    detail.optString("code", "http_$status"),
                    detail.optString("message", "خطا ($status)")
                )
            }
        } catch (e: Exception) {
            // not a JSON error body: fall through to the generic error
        }
        return SovraApiException("http_$status", "خطای سرور ($status)")
    }
}
