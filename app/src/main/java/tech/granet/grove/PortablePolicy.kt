package tech.granet.grove

import org.json.JSONObject

/** Portable computation adapter; Android authority is passed explicitly by the platform owner. */
internal object PortablePolicy {
    fun value(operation: String, args: JSONObject): Any? {
        val response = CoreBridge.portable(operation, args) ?: return null
        if (response.has("error")) return null
        return response.get("value")
    }
    fun rule(name: String, vararg fields: Pair<String, Any?>): Any? = value("rule", JSONObject().put("rule", name).apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    })
    fun ruleBool(name: String, vararg fields: Pair<String, Any?>): Boolean? = rule(name, *fields) as? Boolean
    fun ruleInt(name: String, range: IntRange, vararg fields: Pair<String, Any?>): Int? =
        (rule(name, *fields) as? Number)?.toInt()?.takeIf { it in range }
    fun bool(operation: String, args: JSONObject): Boolean? = value(operation, args) as? Boolean
    fun int(operation: String, args: JSONObject, range: IntRange): Int? =
        (value(operation, args) as? Number)?.toLong()?.takeIf { it in range.first.toLong()..range.last.toLong() }?.toInt()
}
