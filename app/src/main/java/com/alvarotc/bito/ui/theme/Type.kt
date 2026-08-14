package com.alvarotc.bito.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R

@OptIn(ExperimentalTextApi::class)
private fun outfitFont(weight: FontWeight) =
    Font(
        resId = R.font.outfit,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val Outfit =
    FontFamily(
        outfitFont(FontWeight.Normal),
        outfitFont(FontWeight.Medium),
        outfitFont(FontWeight.SemiBold),
        outfitFont(FontWeight.Bold),
    )

// Escala de la guía: el dato grande, la unidad pequeña — siempre.
val BitoTypography =
    Typography(
        // Número héroe (36-40 bold)
        displayLarge =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            ),
        // Título de pantalla (26 semibold)
        headlineLarge =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
            ),
        // Título de tarjeta (18 semibold)
        titleMedium =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
        // Cuerpo (15 regular)
        bodyLarge =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            ),
        // Meta/unidades (13-14 medium, tinta-suave)
        labelMedium =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
    )
