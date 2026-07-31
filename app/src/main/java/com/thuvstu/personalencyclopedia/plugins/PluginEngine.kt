package com.thuvstu.personalencyclopedia.plugins

import android.content.Context
import android.util.Log
import com.thuvstu.personalencyclopedia.db.dao.PluginDao
import com.thuvstu.personalencyclopedia.db.entity.PluginEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginDao: PluginDao
) {
    companion object {
        private const val TAG = "PluginEngine"
        private const val PLUGINS_DIR = "plugins"

        val BUILTIN_MCQ_PLUGIN = """
            var plugin = {
                manifest: {
                    id: "builtin-mcq",
                    name: "Multiple Choice",
                    version: "1.0.0",
                    type: "quizType"
                },
                grade: function(answer, answerData) {
                    var correct = (answer === answerData.answer);
                    return { correct: correct, score: correct ? 1.0 : 0.0 };
                },
                renderSchema: function(questionData) {
                    return {
                        type: "column",
                        children: [
                            { type: "text", content: questionData.question },
                            {
                                type: "multipleChoice",
                                id: "answer",
                                options: questionData.choices
                            }
                        ]
                    };
                }
            };
        """.trimIndent()
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class GradeResult(
        val correct: Boolean,
        val score: Double
    )

    suspend fun installPlugin(pluginId: String, name: String, version: String, jsSource: String): Boolean {
        return try {
            val manifest = validatePlugin(jsSource) ?: run {
                Log.e(TAG, "Plugin validation failed: $pluginId")
                return false
            }

            val pluginsDir = File(context.filesDir, PLUGINS_DIR)
            pluginsDir.mkdirs()
            val scriptFile = File(pluginsDir, "$pluginId.js")
            scriptFile.writeText(jsSource)

            pluginDao.upsert(
                PluginEntity(
                    id = pluginId,
                    name = name,
                    version = version,
                    manifestJson = manifest,
                    scriptPath = scriptFile.absolutePath
                )
            )

            Log.i(TAG, "Plugin installed: $pluginId v$version")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Plugin install failed: $pluginId", e)
            false
        }
    }

    private fun validatePlugin(jsSource: String): String? {
        val rhino = RhinoContext.enter()
        return try {
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initStandardObjects()
            rhino.evaluateString(scope, jsSource, "plugin", 1, null)

            val checkScript = """
                (function() {
                    if (typeof plugin === 'undefined') return null;
                    if (!plugin.manifest || !plugin.grade || !plugin.renderSchema) return null;
                    return JSON.stringify(plugin.manifest);
                })()
            """.trimIndent()

            val result = rhino.evaluateString(scope, checkScript, "validate", 1, null)
            result?.toString()?.takeIf { it != "null" }
        } catch (e: Exception) {
            Log.e(TAG, "Plugin validation error", e)
            null
        } finally {
            RhinoContext.exit()
        }
    }

    suspend fun gradeWithPlugin(pluginId: String, userAnswer: String, answerDataJson: String): GradeResult? {
        val plugin = pluginDao.getById(pluginId) ?: return null
        val scriptFile = File(plugin.scriptPath)
        if (!scriptFile.exists()) return null

        val rhino = RhinoContext.enter()
        return try {
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initStandardObjects()
            rhino.evaluateString(scope, scriptFile.readText(), pluginId, 1, null)

            val escapedAnswer = userAnswer.replace("\\", "\\\\").replace("\"", "\\\"")
            val gradeCall = """
                (function() {
                    var result = plugin.grade("$escapedAnswer", $answerDataJson);
                    return JSON.stringify(result);
                })()
            """.trimIndent()

            val result = rhino.evaluateString(scope, gradeCall, "grade", 1, null)
            val resultStr = result?.toString() ?: return null
            val resultJson = json.parseToJsonElement(resultStr).jsonObject

            GradeResult(
                correct = resultJson["correct"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                score = resultJson["score"]?.jsonPrimitive?.content?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Plugin grade failed: $pluginId", e)
            null
        } finally {
            RhinoContext.exit()
        }
    }

    suspend fun getRenderSchema(pluginId: String, questionDataJson: String): String? {
        val plugin = pluginDao.getById(pluginId) ?: return null
        val scriptFile = File(plugin.scriptPath)
        if (!scriptFile.exists()) return null

        val rhino = RhinoContext.enter()
        return try {
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initStandardObjects()
            rhino.evaluateString(scope, scriptFile.readText(), pluginId, 1, null)

            val schemaCall = """
                (function() {
                    return JSON.stringify(plugin.renderSchema($questionDataJson));
                })()
            """.trimIndent()

            rhino.evaluateString(scope, schemaCall, "render", 1, null)?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Plugin renderSchema failed: $pluginId", e)
            null
        } finally {
            RhinoContext.exit()
        }
    }

    fun observePlugins(): Flow<List<PluginEntity>> = pluginDao.observeAll()

    suspend fun installBuiltinPlugins() {
        val existing = pluginDao.getById("builtin-mcq")
        if (existing != null) return

        installPlugin(
            pluginId = "builtin-mcq",
            name = "Multiple Choice",
            version = "1.0.0",
            jsSource = BUILTIN_MCQ_PLUGIN
        )
    }
}