package com.aisoul.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// DESIGN.md §3 — shape tokens
val RadiusCard = RoundedCornerShape(24.dp)
val RadiusButton = RoundedCornerShape(16.dp)
val RadiusInput = RoundedCornerShape(14.dp)
val RadiusChip = RoundedCornerShape(999.dp)
val RadiusSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

// Spacing scale (4dp base): 4, 8, 12, 16, 24, 32, 48, 64
object Space {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s48 = 48.dp
    val s64 = 64.dp

    val screen = s24 // screen horizontal padding, never less
    val card = s24 // padding inside cards
    val stack = s32 // vertical gap between cards/sections
    val hero = s64 // above and below the hero element
}
