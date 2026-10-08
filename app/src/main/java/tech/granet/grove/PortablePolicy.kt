package tech.granet.grove

import org.json.JSONObject

/** Portable computation adapter; Android authority is passed explicitly by the platform owner. */
internal object PortablePolicy {
    fun value(operation: String, args: JSONObject): Any? {
        val response = CoreBridge.portable(operation, args) ?: return null
        if (response.has("error")) return null
        return response.get("value")
    }
    fun bool(operation: String, args: JSONObject): Boolean? = value(operation, args) as? Boolean
    fun int(operation: String, args: JSONObject, range: IntRange): Int? =
        (value(operation, args) as? Number)?.toLong()?.takeIf { it in range.first.toLong()..range.last.toLong() }?.toInt()
}
