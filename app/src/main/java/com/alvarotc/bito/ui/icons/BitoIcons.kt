package com.alvarotc.bito.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Glyphs from Lucide (https://lucide.dev, ISC) ported as stroke vectors —
// tinted at use site via Icon(tint = …).
private fun lucide(
    name: String,
    vararg paths: String,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color(0xFF3C352B)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object BitoIcons {
    val Plus by lazy { lucide("plus", "M5 12h14", "M12 5v14") }
    val Minus by lazy { lucide("minus", "M5 12h14") }
    val Check by lazy { lucide("check", "M20 6 9 17l-5-5") }
    val X by lazy { lucide("x", "M18 6 6 18", "m6 6 12 12") }
    val ChevronDown by lazy { lucide("chevron-down", "m6 9 6 6 6-6") }
    val ChevronLeft by lazy { lucide("chevron-left", "m15 18-6-6 6-6") }
    val Flame by lazy {
        lucide(
            "flame",
            "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 " +
                "2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z",
        )
    }
    val Home by lazy { lucide("house", "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10") }
    val Pencil by lazy {
        lucide(
            "pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 " +
                ".623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }
    val Settings by lazy {
        lucide(
            "settings-2",
            "M20 7h-9",
            "M14 17H5",
            "M14 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M4 7a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }
    val Download by lazy { lucide("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3") }
    val Upload by lazy { lucide("upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m17 8-5-5-5 5", "M12 3v12") }
    val Trash by lazy {
        lucide(
            "trash-2",
            "M3 6h18",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
            "M10 11v6",
            "M14 11v6",
        )
    }
}
