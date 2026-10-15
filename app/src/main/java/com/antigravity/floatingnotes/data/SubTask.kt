package com.antigravity.floatingnotes.data

import org.json.JSONArray
import org.json.JSONObject

data class SubTask(
    val id: String,
    val title: String,
    val isDone: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("isDone", isDone)
    }

    companion object {
        fun fromJson(obj: JSONObject): SubTask = SubTask(
            id = obj.optString("id", System.currentTimeMillis().toString()),
            title = obj.optString("title", ""),
            isDone = obj.optBoolean("isDone", false)
        )

        fun parseJsonList(jsonString: String): List<SubTask> {
            if (jsonString.isBlank() || jsonString == "[]") return emptyList()
            return try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<SubTask>()
                for (i in 0 until array.length()) {
                    list.add(fromJson(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun toJsonList(list: List<SubTask>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
