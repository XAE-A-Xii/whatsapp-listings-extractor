package com.privacy.whatsappdecryptor.ui.fold

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Galaxy Z Fold6, portrait, 420 dpi (density 2.625).
 *
 * Cover: 6.3 in, 968 × 2376 px → 369 × 905 dp.
 * Inner: 7.6 in, 1856 × 2160 px → 707 × 823 dp.
 *
 * 600 dp sits between those widths, same line Android uses for sw600dp.
 * Cover portrait is under it. Inner portrait is over it.
 */
object Fold6 {
    const val COVER_WIDTH_PX = 968
    const val COVER_HEIGHT_PX = 2376
    const val INNER_WIDTH_PX = 1856
    const val INNER_HEIGHT_PX = 2160
    const val DENSITY_DPI = 420
    const val WIDTH_BREAK_DP = 600
}

enum class FoldPosture {
    Cover,
    Inner
}

@Composable
@ReadOnlyComposable
fun foldPosture(configuration: Configuration = LocalConfiguration.current): FoldPosture {
    return if (configuration.screenWidthDp < Fold6.WIDTH_BREAK_DP) {
        FoldPosture.Cover
    } else {
        FoldPosture.Inner
    }
}
