package com.oloomyar.app.model

/** Immutable coordinates in the original PDF; preserves Persian, formulas and vector ink. */
data class PdfRegion(val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

data class PdfSource(val file: String, val regions: List<PdfRegion>, val additions: List<PdfRegion> = emptyList(), val exclusions: List<PdfRegion> = emptyList()) {
    val displayRegions get() = regions.map { body -> additions.filter { it.page == body.page }.fold(body) { a,b ->
        a.copy(left=minOf(a.left,b.left),top=minOf(a.top,b.top),right=maxOf(a.right,b.right),bottom=maxOf(a.bottom,b.bottom))
    } }
    val aspectRatio: Float get() = displayRegions.let { bounds ->
        bounds.maxOf { it.width } / (bounds.sumOf { it.height.toDouble() }.toFloat()+5f*(bounds.size-1))
    }
    companion object {
        fun parse(asset: String): PdfSource? {
            if (!asset.startsWith("pdf:")) return null
            val pieces = asset.removePrefix("pdf:").split('#', limit = 2)
            require(pieces.size == 2 && Regex("[A-Za-z0-9_-]+\\.pdf").matches(pieces[0])) { "Invalid source PDF" }
            val sections = pieces[1].split('|')
            fun parseRegion(entry: String): PdfRegion {
                val numbers = entry.split(',').map { it.toFloat() }
                require(numbers.size == 5 && numbers.all { it.isFinite() }) { "Invalid PDF region" }
                require(numbers[0] >= 1 && numbers[0] == numbers[0].toInt().toFloat())
                return PdfRegion(numbers[0].toInt(), numbers[1], numbers[2], numbers[3], numbers[4]).also {
                    require(it.left >= 0 && it.top >= 0 && it.width > 0 && it.height > 0 && it.right <= 2000 && it.bottom <= 2000)
                }
            }
            val regions = sections[0].split(';').map(::parseRegion)
            val additions = sections.drop(1).filter { it.startsWith('+') }.map { parseRegion(it.drop(1)) }
            val exclusions = sections.drop(1).filter { it.startsWith('-') }.map { parseRegion(it.drop(1)) }
            require(sections.drop(1).all { it.startsWith('+') || it.startsWith('-') })
            require(regions.size in 1..16)
            require(additions.size + exclusions.size <= 50)
            require((additions + exclusions).all { figure -> regions.any { it.page == figure.page } })
            return PdfSource(pieces[0], regions, additions, exclusions)
        }
    }
}
