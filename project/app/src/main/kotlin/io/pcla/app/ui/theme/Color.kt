package io.pcla.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The whole app reads its chrome colors from this singleton, which keeps two complete skins:
 *
 * - `apply(false)` — the 2026 dark skin: near-black olive + lime accent.
 * - `apply(true)`  — PCL's own palette: blue accent on light gray, white cards (ColorObject1-8).
 *
 * Switching a theme calls [apply] and then recreates the Activity; that is why the fields are
 * `var` instead of immutable. Cube colors below stay fixed: Minecraft blocks look the same in
 * both skins.
 */
object Ui {
    var isDark: Boolean = true
        private set

    var Bg = Color(0xFF0B0C09)
        private set
    var BgElevated = Color(0xFF10120D)
        private set
    var Surface = Color(0xFF161A12)
        private set
    var SurfaceHigh = Color(0xFF1E2318)
        private set
    var Stroke = Color(0xFF2A3023)
        private set

    /** Primary accent. Lime in the dark skin, PCL blue (#1370F3) in the light one. */
    var Lime = Color(0xFFC6F877)
        private set
    var LimeDeep = Color(0xFFAFE459)
        private set
    var OnLime = Color(0xFF18210A)
        private set

    var Text = Color(0xFFF2F5EA)
        private set
    var TextMuted = Color(0xFF9CA395)
        private set
    var TextFaint = Color(0xFF6D7566)
        private set

    var HeroTop = Color(0xFF1C2216)
        private set
    var HeroBottom = Color(0xFF111309)
        private set
    var HeroBorder = Color(0xFF2A3023)
        private set

    var RecoTop = Color(0xFF2A1F45)
        private set
    var RecoBottom = Color(0xFF15121F)
        private set
    var RecoBorder = Color(0xFF3B2C5E)
        private set

    var Red = Color(0xFFFF7A6B)
        private set

    // Fixed across themes.
    val Blue = Color(0xFF3F7DF6)
    val BlueDeep = Color(0xFF255BC4)
    val Orange = Color(0xFFE8873B)
    val Grass = Color(0xFF6FBE4A)
    val Sand = Color(0xFFD9C7A3)
    val Purple = Color(0xFF8E63F2)

    /** Swatches for the Settings theme picker; these must not change when the skin changes. */
    val GreenPaletteLime = Color(0xFFC6F877)
    val PclPaletteBlue = Color(0xFF1370F3)

    init {
        apply(light = false)
    }

    fun apply(light: Boolean) {
        isDark = !light
        if (light) {
            // PCL 原版色板：Application.xaml 的 ColorObject1/3/7/8 与 Gray1-6
            Bg = Color(0xFFF5F5F5)
            BgElevated = Color(0xFFFFFFFF)
            Surface = Color(0xFFFFFFFF)
            SurfaceHigh = Color(0xFFEAF2FE)
            Stroke = Color(0xFFD5E6FD)
            Lime = Color(0xFF1370F3)
            LimeDeep = Color(0xFF0B5BCB)
            OnLime = Color(0xFFFFFFFF)
            Text = Color(0xFF343D4A)
            TextMuted = Color(0xFF737373)
            TextFaint = Color(0xFF8C8C8C)
            HeroTop = Color(0xFFFFFFFF)
            HeroBottom = Color(0xFFEAF2FE)
            HeroBorder = Color(0xFFD5E6FD)
            RecoTop = Color(0xFFE0EAFD)
            RecoBottom = Color(0xFFF5F5F5)
            RecoBorder = Color(0xFF96C0F9)
            Red = Color(0xFFCE2111)
        } else {
            Bg = Color(0xFF0B0C09)
            BgElevated = Color(0xFF10120D)
            Surface = Color(0xFF161A12)
            SurfaceHigh = Color(0xFF1E2318)
            Stroke = Color(0xFF2A3023)
            Lime = Color(0xFFC6F877)
            LimeDeep = Color(0xFFAFE459)
            OnLime = Color(0xFF18210A)
            Text = Color(0xFFF2F5EA)
            TextMuted = Color(0xFF9CA395)
            TextFaint = Color(0xFF6D7566)
            HeroTop = Color(0xFF1C2216)
            HeroBottom = Color(0xFF111309)
            HeroBorder = Color(0xFF2A3023)
            RecoTop = Color(0xFF2A1F45)
            RecoBottom = Color(0xFF15121F)
            RecoBorder = Color(0xFF3B2C5E)
            Red = Color(0xFFFF7A6B)
        }
    }
}

/**
 * PCL's original palette, kept for the light skin's documentation. The live values are in [Ui]
 * after `apply(true)`; this object is not read by the UI.
 */
object Pcl {
    val Blue1 = Color(0xFF343D4A)
    val Blue2 = Color(0xFF0B5BCB)
    val Blue3 = Color(0xFF1370F3)
    val Blue4 = Color(0xFF4890F5)
    val Blue5 = Color(0xFF96C0F9)
    val Blue6 = Color(0xFFD5E6FD)
    val Blue7 = Color(0xFFE0EAFD)
    val Blue8 = Color(0xFFEAF2FE)

    val Gray1 = Color(0xFF404040)
    val Gray2 = Color(0xFF737373)
    val Gray3 = Color(0xFF8C8C8C)
    val Gray4 = Color(0xFFA6A6A6)
    val Gray5 = Color(0xFFCCCCCC)
    val Gray6 = Color(0xFFEBEBEB)
    val Gray7 = Color(0xFFF0F0F0)
    val Gray8 = Color(0xFFF5F5F5)

    val RedLight = Color(0xFFFF4C4C)
    val RedDark = Color(0xFFCE2111)
    val RedBack = Color(0x80FBDDDD)
}

object PclDark {
    val Blue2 = Color(0xFF1370F3)
    val Blue3 = Color(0xFF4890F5)
    val Blue8 = Color(0xFF16304F)
    val Gray8 = Color(0xFF1B1F24)
    val Surface = Color(0xFF22272E)
    val SurfaceHigh = Color(0xFF2A3038)
    val Text = Color(0xFFE6EAF0)
    val TextMuted = Color(0xFF9BA6B4)
    val Outline = Color(0xFF39424E)
}
