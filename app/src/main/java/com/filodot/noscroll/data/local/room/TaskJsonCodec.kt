package com.filodot.noscroll.data.local.room

import com.filodot.noscroll.core.model.TaskChoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object TaskJsonCodec {
    private val json = Json { isLenient = false }

    fun encodeChoices(choices: List<TaskChoice>): String = buildJsonArray {
        choices.forEach { choice ->
            add(
                buildJsonObject {
                    put("id", choice.id)
                    put("text", choice.text)
                },
            )
        }
    }.toString()

    fun decodeChoices(value: String): List<TaskChoice> =
        json.parseToJsonElement(value).jsonArray.map { element ->
            val objectValue = element.jsonObject
            require(objectValue.keys == setOf("id", "text")) {
                "Unexpected task choice fields"
            }
            TaskChoice(
                id = objectValue.getValue("id").jsonPrimitive.content,
                text = objectValue.getValue("text").jsonPrimitive.content,
            )
        }
}
