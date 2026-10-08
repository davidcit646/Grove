package tech.granet.grove

import org.json.JSONArray
import org.json.JSONObject

/** Reject native protocol drift before callers decode a typed result. */
internal object NativeEnvelope {
    fun decode(operation: String, text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= 196_608) { "Portable output too large" }
        val response = JSONObject(text)
        require(response.get("version") is Int && response.getInt("version") == 1 &&
            (response.has("value") xor response.has("error"))) { "Malformed native policy response" }
        if (response.has("error")) {
            require(response.get("error") is String && response.getString("error").length <= 256)
            return response
        }
        val value = response.get("value")
        when (operation) {
            "config", "calculator", "grid", "crop", "gestureSession", "uninstall" -> require(value is JSONObject)
            "setup", "folder" -> require(value is JSONObject || value == JSONObject.NULL)
            "normalize", "phoneDigits", "diagnostic", "report" -> require(value is String)
            "pin", "settingsRank", "channels", "whatsApp" -> require(value is JSONArray)
            "gesture", "columns", "indexState", "delay", "request", "imageSample" -> require(value is Int || value is Long)
            "publication", "access", "drawerClose", "imageValid" -> require(value is Boolean)
            "rule" -> require(value is Boolean || value is Int || value is Long || value is JSONArray || value is JSONObject)
            else -> throw IllegalArgumentException("Unknown native result operation")
        }
        return response
    }
}
