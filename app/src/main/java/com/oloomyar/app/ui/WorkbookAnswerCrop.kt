package com.oloomyar.app.ui

import android.graphics.Bitmap

/** Tight display crop of the original black answer key. Never rewrites or resamples its ink. */
internal fun cropWorkbookAnswer(source: Bitmap): Bitmap {
    val pixels=IntArray(source.width*source.height)
    source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
    var left=source.width;var right=-1;var top=source.height;var bottom=-1
    pixels.forEachIndexed { index, pixel ->
        val r=(pixel ushr 16) and 255;val g=(pixel ushr 8) and 255;val b=pixel and 255
        // The blue page-rule is not answer content. Keep all dark neutral ink,
        // including thin fraction bars, decimal marks and Persian letter dots.
        if (maxOf(r,g,b)<210 && maxOf(r,g,b)-minOf(r,g,b)<40) {
            val x=index%source.width;val y=index/source.width
            left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)
        }
    }
    if (right<left) return source
    val padding=16
    left=(left-padding).coerceAtLeast(0);right=(right+padding).coerceAtMost(source.width-1)
    top=(top-padding).coerceAtLeast(0);bottom=(bottom+padding).coerceAtMost(source.height-1)
    return Bitmap.createBitmap(source,left,top,right-left+1,bottom-top+1)
}
