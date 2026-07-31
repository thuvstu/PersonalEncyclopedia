// 📁 app/src/main/java/com/thuvstu/personalencyclopedia/ui/component/UiSchemaRenderer.kt
package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

@Composable
fun UiSchemaRenderer(
    schemaJson: String,
    answers: Map<String, String>,
    onAnswerChange: (String, String) -> Unit
) {
    val element = try {
        Json.parseToJsonElement(schemaJson)
    } catch (e: Exception) {
        Text("Invalid UI Schema", color = MaterialTheme.colorScheme.error)
        return
    }
    RenderElement(element, answers, onAnswerChange)
}

@Composable
private fun RenderElement(
    element: JsonElement,
    answers: Map<String, String>,
    onAnswerChange: (String, String) -> Unit
) {
    if (element !is JsonObject) return
    val type = element["type"]?.jsonPrimitive?.contentOrNull ?: return

    when (type) {
        "column" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                element["children"]?.jsonArray?.forEach { child ->
                    RenderElement(child, answers, onAnswerChange)
                }
            }
        }
        "text" -> {
            val content = element["content"]?.jsonPrimitive?.contentOrNull ?: ""
            Text(content, style = MaterialTheme.typography.bodyLarge)
        }
        "input" -> {
            val id = element["id"]?.jsonPrimitive?.contentOrNull ?: return
            val label = element["label"]?.jsonPrimitive?.contentOrNull ?: ""
            OutlinedTextField(
                value = answers[id] ?: "",
                onValueChange = { onAnswerChange(id, it) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        "multipleChoice" -> {
            val id = element["id"]?.jsonPrimitive?.contentOrNull ?: return
            val options = element["options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val selected = answers[id]
            Column {
                options.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selected == opt,
                            onClick = { onAnswerChange(id, opt) }
                        )
                        Text(opt, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}