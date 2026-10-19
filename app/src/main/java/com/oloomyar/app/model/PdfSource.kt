package com.oloomyar.app.model

/** Immutable coordinates in the original PDF; preserves Persian, formulas and vector ink. */
data class PdfRegion(val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

data class PdfSource(val file: String, val regions: List<PdfRegion>) {
    companion object {
        fun parse(asset: String): PdfSource? {
            if (!asset.startsWith("pdf:")) return null
            val pieces = asset.removePrefix("pdf:").split('#', limit = 2)
            require(pieces.size == 2 && Regex("[A-Za-z0-9_-]+\\.pdf").matches(pieces[0])) { "Invalid source PDF" }
            val regions = pieces[1].split(';').map { entry ->
                val numbers = entry.split(',').map { it.toFloat() }
                require(numbers.size == 5 && numbers.all { it.isFinite() }) { "Invalid PDF region" }
                require(numbers[0] >= 1 && numbers[0] == numbers[0].toInt().toFloat())
                PdfRegion(numbers[0].toInt(), numbers[1], numbers[2], numbers[3], numbers[4]).also {
                    require(it.left >= 0 && it.top >= 0 && it.width > 0 && it.height > 0 && it.right <= 2000 && it.bottom <= 2000)
                }
            }
            require(regions.size in 1..16)
            return PdfSource(pieces[0], regions)
        }
    }
}
