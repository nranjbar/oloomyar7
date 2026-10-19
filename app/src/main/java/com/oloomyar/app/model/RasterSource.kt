package com.oloomyar.app.model

/** Display a source pixel rectangle without shipping a second scientific bitmap. */
data class RasterSource(val file: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right-left
    val height get() = bottom-top
    val aspectRatio get() = width.toFloat()/height
    companion object {
        fun parse(asset: String): RasterSource? {
            if (!asset.startsWith("crop:")) return null
            return runCatching {
                val file=asset.substringAfter("crop:").substringBefore('#')
                require(file.matches(Regex("[A-Za-z0-9_-]+\\.webp")))
                val coordinates=asset.substringAfter('#').split(',').map { it.toInt() }
                require(coordinates.size==4)
                RasterSource(file,coordinates[0],coordinates[1],coordinates[2],coordinates[3]).also {
                    require(it.left>=0 && it.top>=0 && it.width>0 && it.height>0)
                }
            }.getOrNull()
        }
    }
}
