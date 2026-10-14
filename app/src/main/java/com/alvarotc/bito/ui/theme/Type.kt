package com.alvarotc.bito.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.ui.sistema.Gabarito

// Escala de la guía: el dato grande, la unidad pequeña — siempre.
val BitoTypography =
    Typography(
        // Número héroe (36-40 bold)
        displayLarge =
            TextStyle(
                fontFamily = Gabarito,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            ),
        // Título de pantalla (26 semibold)
        headlineLarge =
            TextStyle(
                fontFamily = Gabarito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
            ),
        // Título de tarjeta (18 semibold)
        titleMedium =
            TextStyle(
                fontFamily = Gabarito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
        // Cuerpo (15 regular)
        bodyLarge =
            TextStyle(
                fontFamily = Gabarito,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            ),
        // Meta/unidades (13-14 medium, tinta-suave)
        labelMedium =
            TextStyle(
                fontFamily = Gabarito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
    )
