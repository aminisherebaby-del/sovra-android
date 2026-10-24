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
