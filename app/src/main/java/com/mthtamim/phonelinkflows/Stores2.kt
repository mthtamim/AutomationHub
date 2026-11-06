package com.mthtamim.phonelinkflows

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small state-prefs stores added in v2.3: security log, scheduled
 *  messages and the peer battery history. */
object SecLog {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("phonelink_state", Context.MODE_PRIVATE)

    fun add(ctx: Context, text: String) {
        try {
            val arr = try { JSONArray(prefs(ctx).getString("sec_log", "[]")) }
                catch (e: Exception) { JSONArray() }
            arr.put(JSONObject().put("t", System.currentTimeMillis()).put("s", text))
            while (arr.length() > 60) arr.remove(0)
            prefs(ctx).edit().putString("sec_log", arr.toString()).apply()
        } catch (_: Exception) { }
    }

    fun all(ctx: Context): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        try {
            val arr = JSONArray(prefs(ctx).getString("sec_log", "[]"))
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                out.add(o.optLong("t") to o.optString("s"))
            }
        } catch (_: Exception) { }
        return out
    }
}
