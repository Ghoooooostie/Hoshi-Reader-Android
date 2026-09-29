package moe.antimony.hoshi.features.dictionary

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.antimony.hoshi.R

internal data class DeinflectionGroupLabel(
    val label: String,
    val description: String,
)

/**
 * 词形还原（去活用）标签的本地化。
 *
 * 词典引擎返回的活用组名和说明是 Yomitan 的英文原文，这里只按当前语言资源做显示层替换：
 * 默认语言没有条目时返回空表，弹窗继续显示原英文；中文环境下替换为 `strings.xml` 里的中文。
 */
internal object DeinflectionLabels {
    private const val SEPARATOR = "::"

    /** 解析「组名::标签::说明」格式的资源条目，格式不符的条目直接忽略。 */
    fun parse(entries: Array<out String>): Map<String, DeinflectionGroupLabel> {
        val result = linkedMapOf<String, DeinflectionGroupLabel>()
        entries.forEach { entry ->
            val nameEnd = entry.indexOf(SEPARATOR)
            if (nameEnd <= 0) return@forEach
            val name = entry.substring(0, nameEnd).trim()
            val rest = entry.substring(nameEnd + SEPARATOR.length)
            val labelEnd = rest.indexOf(SEPARATOR)
            if (labelEnd < 0) return@forEach
            val label = rest.substring(0, labelEnd).trim()
            if (name.isEmpty() || label.isEmpty()) return@forEach
            val description = rest.substring(labelEnd + SEPARATOR.length)
            result[name] = DeinflectionGroupLabel(label = label, description = description)
        }
        return result
    }

    /** 供弹窗 JS 按组名查表的 JSON 对象，形如 { "passive": { "label": "被动", ... } }。 */
    fun javascriptObject(context: Context): String {
        val labels = parse(context.resources.getStringArray(R.array.deinflection_group_translations))
        if (labels.isEmpty()) return "{}"
        return buildJsonObject {
            labels.forEach { (name, label) ->
                put(
                    name,
                    buildJsonObject {
                        put("label", label.label)
                        if (label.description.isNotBlank()) {
                            put("description", label.description)
                        }
                    },
                )
            }
        }.toString()
    }
}
