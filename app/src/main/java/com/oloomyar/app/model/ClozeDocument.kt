package com.oloomyar.app.model

sealed interface ClozeToken {
    data class Text(val value: String) : ClozeToken
    data class Blank(val index: Int, val id: String) : ClozeToken
}

/** Only printed blank positions become interactive; every surrounding character round-trips. */
data class ClozeDocument(val tokens: List<ClozeToken>) {
    fun toTemplate() = tokens.joinToString("") { when (it) {
        is ClozeToken.Text -> it.value
        is ClozeToken.Blank -> "{{${it.index}}}"
    } }
    companion object {
        fun parse(template: String, stepId: String): ClozeDocument {
            val pattern = Regex("""\{\{([0-9]+)\}\}""")
            var cursor = 0
            val tokens = buildList {
                pattern.findAll(template).forEach { match ->
                    add(ClozeToken.Text(template.substring(cursor, match.range.first)))
                    val index = match.groupValues[1].toInt()
                    add(ClozeToken.Blank(index, "$stepId-blank-$index"))
                    cursor = match.range.last + 1
                }
                add(ClozeToken.Text(template.substring(cursor)))
            }
            val document = ClozeDocument(tokens)
            require(document.toTemplate() == template) { "Cloze source round-trip failed" }
            return document
        }
    }
}
