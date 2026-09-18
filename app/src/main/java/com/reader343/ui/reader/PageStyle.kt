package com.reader343.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.reader343.domain.PageAppearance
import com.reader343.ui.theme.PaperInk
import com.reader343.ui.theme.PaperShell
import com.reader343.ui.theme.PaperSwatch

@Immutable
class PageStyle private constructor(
    val shell: Color,
    val paper: Color,
    val filter: ColorFilter?,
    val markBlend: BlendMode,
    private val markAlpha: Float,
) {
    fun mark(color: Color): Color = if (markAlpha < 1f) color.copy(alpha = markAlpha) else color

    companion object {
        val Normal = PageStyle(
            shell = PaperShell.Normal,
            paper = PaperSwatch.Normal,
            filter = paperFilter(paper = PaperSwatch.Normal, ink = PaperInk.Normal),
            markBlend = BlendMode.Multiply,
            markAlpha = 1f,
        )

        val Night = PageStyle(
            shell = Color.Unspecified,
            paper = PaperSwatch.Night,
            filter = paperFilter(paper = PaperSwatch.Night, ink = PaperInk.Night),
            markBlend = BlendMode.SrcOver,
            markAlpha = NIGHT_MARK_ALPHA,
        )

        val Sepia = PageStyle(
            shell = PaperShell.Sepia,
            paper = PaperSwatch.Sepia,
            filter = paperFilter(paper = PaperSwatch.Sepia, ink = PaperInk.Sepia),
            markBlend = BlendMode.Multiply,
            markAlpha = 1f,
        )

        fun of(appearance: PageAppearance): PageStyle = when (appearance) {
            PageAppearance.Normal -> Normal
            PageAppearance.Night -> Night
            PageAppearance.Sepia -> Sepia
        }

        private fun paperFilter(paper: Color, ink: Color): ColorFilter {
            fun row(channel: Int, paperLevel: Float, inkLevel: Float): FloatArray =
                FloatArray(COLUMNS).also {
                    it[channel] = paperLevel - inkLevel
                    it[OFFSET] = inkLevel * CHANNEL_MAX
                }
            return ColorFilter.colorMatrix(
                ColorMatrix(
                    row(0, paper.red, ink.red) +
                        row(1, paper.green, ink.green) +
                        row(2, paper.blue, ink.blue) +
                        floatArrayOf(0f, 0f, 0f, 1f, 0f),
                ),
            )
        }

        private const val COLUMNS = 5
        private const val OFFSET = 4
        private const val CHANNEL_MAX = 255f
        private const val NIGHT_MARK_ALPHA = 0.35f
    }
}
