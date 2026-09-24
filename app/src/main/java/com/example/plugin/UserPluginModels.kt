package com.example.plugin

import org.json.JSONArray
import org.json.JSONObject

data class UserPluginProject(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "User",
    val description: String,
    val capabilities: Set<PluginCapability>,
    val sourceCode: String,
    val isEnabled: Boolean = true,
    val isValidated: Boolean = false,
    val lastBuildMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("version", version)
        json.put("author", author)
        json.put("description", description)
        val capsArray = JSONArray()
        capabilities.forEach { capsArray.put(it.name) }
        json.put("capabilities", capsArray)
        json.put("sourceCode", sourceCode)
        json.put("isEnabled", isEnabled)
        json.put("isValidated", isValidated)
        json.put("lastBuildMessage", lastBuildMessage)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): UserPluginProject? {
            return try {
                val json = JSONObject(jsonStr)
                val id = json.getString("id")
                val name = json.getString("name")
                val version = json.optString("version", "1.0")
                val author = json.optString("author", "User")
                val description = json.optString("description", "")
                val capsArray = json.optJSONArray("capabilities")
                val caps = mutableSetOf<PluginCapability>()
                if (capsArray != null) {
                    for (i in 0 until capsArray.length()) {
                        try {
                            caps.add(PluginCapability.valueOf(capsArray.getString(i)))
                        } catch (_: Exception) {}
                    }
                }
                val sourceCode = json.getString("sourceCode")
                val isEnabled = json.optBoolean("isEnabled", true)
                val isValidated = json.optBoolean("isValidated", false)
                val lastBuildMessage = json.optString("lastBuildMessage", "")
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = json.optLong("updatedAt", System.currentTimeMillis())

                UserPluginProject(
                    id = id,
                    name = name,
                    version = version,
                    author = author,
                    description = description,
                    capabilities = caps,
                    sourceCode = sourceCode,
                    isEnabled = isEnabled,
                    isValidated = isValidated,
                    lastBuildMessage = lastBuildMessage,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            } catch (e: Exception) {
                null
            }
        }

        fun createTemplate(id: String, name: String): String {
            val safeClassName = name.replace("[^a-zA-Z0-9]".toRegex(), "").ifEmpty { "MyCustomPlugin" }
            return """
package com.user.plugin

import com.example.plugin.*
import com.example.model.*

/**
 * Плагин для музыкального плеера Mmusic.
 * Реализует интерфейс MusicPlayerPlugin.
 */
class $safeClassName : MusicPlayerPlugin {
    override val id: String = "$id"
    override val name: String = "$name"
    override val version: String = "1.0.0"
    override val author: String = "User"
    override val description: String = "Пользовательский плагин расширения"
    
    override val requiredCapabilities: Set<PluginCapability> = setOf(
        PluginCapability.CONTROL_PLAYER,
        PluginCapability.UI_EXTENSION,
        PluginCapability.READ_METADATA
    )

    private var pluginContext: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        this.pluginContext = context
    }

    override fun onEnable() {
        // Регистрация действия в интерфейсе
        pluginContext?.ui?.registerAction(
            PluginAction(
                id = "${id}_action",
                pluginId = id,
                label = "$name: Быстрое действие",
                target = PluginActionTarget.PLAYER,
                onClick = { ctx ->
                    // Пользовательская реакция
                }
            )
        )
    }

    override fun onDisable() {
        pluginContext?.ui?.unregisterAction("${id}_action")
    }

    override fun onTrackChanged(song: Song?) {
        // Реакция на смену трека
    }

    override fun onPlaybackStateChanged(state: PlaybackState) {
        // Реакция на смену состояния воспроизведения
    }
}
""".trimIndent()
        }
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val summary: String,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

object UserPluginValidator {
    fun validate(
        id: String,
        name: String,
        version: String,
        sourceCode: String
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (id.isBlank()) {
            errors.add("Plugin ID не может быть пустым")
        } else if (!id.matches("^[a-zA-Z0-9._-]+$".toRegex())) {
            errors.add("Plugin ID может содержать только латинские буквы, цифры, '.', '_' и '-'")
        }

        if (name.isBlank()) {
            errors.add("Название плагина не может быть пустым")
        }

        if (version.isBlank()) {
            errors.add("Версия не может быть пустой (например, 1.0.0)")
        }

        if (sourceCode.isBlank()) {
            errors.add("Исходный код не может быть пустым")
            return ValidationResult(false, "Ошибка: пустой исходный код", errors)
        }

        // Check brackets balance with line and column tracking
        var openBraces = 0
        var openParens = 0
        var openBrackets = 0
        val lines = sourceCode.lines()

        for ((index, line) in lines.withIndex()) {
            val lineNum = index + 1
            for ((colIndex, ch) in line.withIndex()) {
                val colNum = colIndex + 1
                when (ch) {
                    '{' -> openBraces++
                    '}' -> {
                        openBraces--
                        if (openBraces < 0) {
                            errors.add("Compile error: Line $lineNum, Column $colNum: unexpected closing brace '}'")
                            openBraces = 0
                        }
                    }
                    '(' -> openParens++
                    ')' -> {
                        openParens--
                        if (openParens < 0) {
                            errors.add("Compile error: Line $lineNum, Column $colNum: unexpected closing parenthesis ')'")
                            openParens = 0
                        }
                    }
                    '[' -> openBrackets++
                    ']' -> {
                        openBrackets--
                        if (openBrackets < 0) {
                            errors.add("Compile error: Line $lineNum, Column $colNum: unexpected closing bracket ']'")
                            openBrackets = 0
                        }
                    }
                }
            }
        }

        if (openBraces > 0) {
            errors.add("Compile error: Line ${lines.size}, Column 1: unclosed curly brace '{' ($openBraces unclosed)")
        }
        if (openParens > 0) {
            errors.add("Compile error: Line ${lines.size}, Column 1: unclosed parenthesis '(' ($openParens unclosed)")
        }
        if (openBrackets > 0) {
            errors.add("Compile error: Line ${lines.size}, Column 1: unclosed square bracket '[' ($openBrackets unclosed)")
        }

        // Interface compliance checks
        if (!sourceCode.contains("MusicPlayerPlugin")) {
            errors.add("Compile error: class must implement interface 'MusicPlayerPlugin'")
        }

        if (!sourceCode.contains("class ")) {
            errors.add("Compile error: missing class declaration ('class ...')")
        }

        if (!sourceCode.contains("override fun onLoad")) {
            errors.add("Compile error: missing required method 'override fun onLoad(context: PluginContext)'")
        }

        if (!sourceCode.contains("override fun onEnable")) {
            errors.add("Compile error: missing required method 'override fun onEnable()'")
        }

        if (!sourceCode.contains("override fun onDisable")) {
            errors.add("Compile error: missing required method 'override fun onDisable()'")
        }

        if (!sourceCode.contains("package ")) {
            warnings.add("Рекомендуется объявить package (например, package com.user.plugin)")
        }

        val isValid = errors.isEmpty()
        val summary = if (isValid) {
            "Plugin validated successfully"
        } else {
            "Compile error (${errors.size} issues found)"
        }

        return ValidationResult(
            isValid = isValid,
            summary = summary,
            errors = errors,
            warnings = warnings
        )
    }
}
