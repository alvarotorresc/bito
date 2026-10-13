package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality

/**
 * La miniatura de 36 dp de cada artículo (pieza 1 §7): los colores, con Habi en pequeño teñido; los
 * accesorios y los disfraces, con su propia pieza pintada. Nada se dibuja por código. La miniatura
 * no habla: el nombre y el estado de la tarjeta ya lo dicen (sin esto, TalkBack leería «Habi,
 * doing okay» en cada color).
 */
@Composable
fun StoreItemThumb(
    item: CatalogItem,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(36.dp).clearAndSetSemantics { testTag = "store-thumb-${item.id}" }) {
        val fill = Modifier.fillMaxSize()
        when (item.category) {
            CustomizationCategory.BODY_COLOR ->
                HabiAvatar(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(bodyColor = item.id)), fill, animated = false)
            CustomizationCategory.EYE_COLOR ->
                HabiAvatar(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(eyeColor = item.id)), fill, animated = false)
            else -> HabiPieceImage(item.id, fill)
        }
    }
}
