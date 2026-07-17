@file:OptIn(ExperimentalTextApi::class)

package com.aisoul.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aisoul.app.R

// DESIGN.md §2 — the 7 type roles. No ad-hoc sizes anywhere in the app.

private val w400 = FontWeight(400)
private val w500 = FontWeight(500)
private val w600 = FontWeight(600)
private val w650 = FontWeight(650)

val Satoshi = FontFamily(
    Font(R.font.satoshi_variable, weight = w400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.satoshi_variable, weight = w500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.satoshi_variable, weight = w600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.satoshi_variable, weight = w650, variationSettings = FontVariation.Settings(FontVariation.weight(650))),
)

val InterFamily = FontFamily(
    Font(R.font.inter_variable, weight = w400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, weight = w500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, weight = w600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

@Immutable
data class AiSoulTypography(
    val display: TextStyle = TextStyle(
        fontFamily = Satoshi, fontSize = 40.sp, fontWeight = w600,
        letterSpacing = (-0.04).em, lineHeight = 42.sp,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = Satoshi, fontSize = 28.sp, fontWeight = w600,
        letterSpacing = (-0.03).em, lineHeight = 31.sp,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = Satoshi, fontSize = 20.sp, fontWeight = w600,
        letterSpacing = (-0.02).em, lineHeight = 24.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = InterFamily, fontSize = 15.sp, fontWeight = w400,
        letterSpacing = (-0.01).em, lineHeight = 22.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = InterFamily, fontSize = 12.sp, fontWeight = w500,
        letterSpacing = 0.02.em, lineHeight = 16.sp,
    ),
    val overline: TextStyle = TextStyle(
        fontFamily = InterFamily, fontSize = 11.sp, fontWeight = w600,
        letterSpacing = 0.12.em, lineHeight = 13.sp,
    ),
    val dataHero: TextStyle = TextStyle(
        fontFamily = Satoshi, fontSize = 48.sp, fontWeight = w650,
        letterSpacing = (-0.04).em, lineHeight = 48.sp,
        fontFeatureSettings = "tnum",
    ),
    // code in chat: Inter is not mono; use platform mono for fenced blocks only
    val code: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = w400,
        lineHeight = 19.sp,
    ),
)

val LocalAiSoulTypography = staticCompositionLocalOf { AiSoulTypography() }
