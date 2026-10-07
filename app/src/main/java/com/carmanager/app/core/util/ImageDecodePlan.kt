package com.carmanager.app.core.util

/** Bitmap et page au plus à 2048 px de côté, sans agrandir les petites images. */
internal data class ImageDecodePlan(val sampleSize: Int, val width: Int, val height: Int) {
    companion object {
        const val MAX_LONG_EDGE = 2048
        fun from(width: Int, height: Int): ImageDecodePlan {
            require(width > 0 && height > 0) { "Image illisible ou dimensions invalides." }
            var sample = 1
            fun scaled(value: Int) = ((value.toLong() + sample - 1) / sample).toInt()
            while (maxOf(scaled(width), scaled(height)) > MAX_LONG_EDGE) sample *= 2
            return ImageDecodePlan(sample, scaled(width), scaled(height))
        }
    }
}
