package moe.antimony.hoshi.features.reader

internal fun sanitizeReaderResource(mediaType: String, bytes: ByteArray): ByteArray {
    if (!mediaType.substringBefore(';').trim().equals("text/css", ignoreCase = true)) {
        return bytes
    }
    return sanitizeReaderCss(bytes.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
}

internal fun sanitizeReaderCss(css: String): String =
    epubPrivateDeclarationRegex.replace(css) { match ->
        val indent = match.groups["indent"]?.value.orEmpty()
        val property = match.groups["property"]?.value.orEmpty().lowercase()
        val value = match.groups["value"]?.value?.trim().orEmpty()
        replacementDeclarations(indent, property, value)
    }

private val epubPrivateDeclarationRegex =
    Regex("""(?im)^(?<indent>[ \t]*)-epub-(?<property>[^:;{}\r\n]+)[ \t]*:[ \t]*(?<value>[^;{}\r\n]*)[ \t]*;[ \t]*(?:\r?\n)?""")

private fun replacementDeclarations(indent: String, property: String, value: String): String =
    when (property) {
        // Hoshi controls page direction globally; nested EPUB writing-mode rules can crash Android WebView.
        "writing-mode" -> ""
        "line-break" -> declarations(
            indent,
            "-webkit-line-break" to value,
            "line-break" to value,
        )
        "word-break" -> declarations(
            indent,
            "word-break" to value,
        )
        "hyphens" -> declarations(
            indent,
            "-webkit-hyphens" to value,
            "hyphens" to value,
        )
        "text-underline-position" -> declarations(
            indent,
            "text-underline-position" to value,
        )
        "text-combine" -> declarations(
            indent,
            "-webkit-text-combine" to value,
            "text-combine-upright" to if (value.equals("horizontal", ignoreCase = true)) "all" else value,
        )
        "text-orientation" -> declarations(
            indent,
            "-webkit-text-orientation" to value,
            "text-orientation" to value,
        )
        "text-emphasis-style" -> declarations(
            indent,
            "-webkit-text-emphasis-style" to value,
            "text-emphasis-style" to value,
        )
        "text-emphasis-color" -> declarations(
            indent,
            "-webkit-text-emphasis-color" to value,
            "text-emphasis-color" to value,
        )
        else -> ""
    }

private fun declarations(indent: String, vararg pairs: Pair<String, String>): String =
    pairs.joinToString(separator = "\n", postfix = "\n") { (property, value) ->
        "$indent$property: $value;"
    }
