package com.reader343.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.reader343.domain.PageAppearance

@Immutable
class PageStyle private constructor(
    val paper: Color,
    val filter: ColorFilter?,
    val markBlend: BlendMode,
    private val markAlpha: Float,
) {
    fun mark(color: Color): Color = if (markAlpha < 1f) color.copy(alpha = markAlpha) else color

    companion object {
        val Normal = PageStyle(
            paper = Color.White,
            filter = null,
            markBlend = BlendMode.Multiply,
            markAlpha = 1f,
        )

        val Night = PageStyle(
            paper = Color(NIGHT_PAPER, NIGHT_PAPER, NIGHT_PAPER),
            filter = ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -NIGHT_SCALE, 0f, 0f, 0f, NIGHT_INK,
                        0f, -NIGHT_SCALE, 0f, 0f, NIGHT_INK,
                        0f, 0f, -NIGHT_SCALE, 0f, NIGHT_INK,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            ),
            markBlend = BlendMode.SrcOver,
            markAlpha = NIGHT_MARK_ALPHA,
        )

        val Sepia = PageStyle(
            paper = Color(SEPIA_R, SEPIA_G, SEPIA_B),
            filter = ColorFilter.colorMatrix(
                ColorMatrix().apply { setToScale(SEPIA_R, SEPIA_G, SEPIA_B, 1f) },
            ),
            markBlend = BlendMode.Multiply,
            markAlpha = 1f,
        )

        fun of(appearance: PageAppearance): PageStyle = when (appearance) {
            PageAppearance.Normal -> Normal
            PageAppearance.Night -> Night
            PageAppearance.Sepia -> Sepia
        }

        private const val NIGHT_INK = 225f
        private const val NIGHT_PAPER_LEVEL = 20f
        private const val NIGHT_SCALE = (NIGHT_INK - NIGHT_PAPER_LEVEL) / 255f
        private const val NIGHT_PAPER = NIGHT_PAPER_LEVEL / 255f
        private const val NIGHT_MARK_ALPHA = 0.35f
        private const val SEPIA_R = 0.957f
        private const val SEPIA_G = 0.925f
        private const val SEPIA_B = 0.847f
    }
}
