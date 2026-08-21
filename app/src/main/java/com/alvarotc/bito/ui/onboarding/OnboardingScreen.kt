package com.alvarotc.bito.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import kotlinx.coroutines.flow.drop
import java.util.Locale

/** The 6 steps that live inside the dotted pager chrome — WELCOME renders full-screen, outside it. */
private val PAGER_STEPS =
    listOf(
        OnboardingStep.STORY_1,
        OnboardingStep.STORY_2,
        OnboardingStep.STORY_3,
        OnboardingStep.NAME,
        OnboardingStep.PERSONALITY,
        OnboardingStep.FIRST_HABIT,
    )

private val STORY_STEPS = setOf(OnboardingStep.STORY_1, OnboardingStep.STORY_2, OnboardingStep.STORY_3)

/**
 * The first-run flow's scaffold (mockups 7a-7g; this task builds 7a-7d, T7/T8 own the rest).
 * WELCOME (7a) is a standalone full-screen beat; every step after it rides the same 6-dot pager
 * chrome (the mockups' dots row), swipeable via [HorizontalPager] — see [StoryPagerScaffold] for
 * how a swipe settle reconciles back into [OnboardingViewModel]'s own step.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.step == OnboardingStep.WELCOME) {
        WelcomeScene(
            languageTag = state.languageTag,
            onSetLanguage = viewModel::setLanguage,
            onStart = viewModel::next,
        )
    } else {
        StoryPagerScaffold(
            step = state.step,
            onNext = viewModel::next,
            onBack = viewModel::back,
            onSkip = viewModel::skipStory,
        )
    }
}

@Composable
private fun WelcomeScene(
    languageTag: String?,
    onSetLanguage: (String?) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Papel).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(Modifier.size(200.dp).clip(CircleShape).background(HojaTinte), contentAlignment = Alignment.Center) {
            HabiAvatar(
                spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                modifier = Modifier.size(140.dp),
                animated = false,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, color = Tinta)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.onb_welcome_tagline), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        Spacer(Modifier.height(24.dp))
        InfoPill(icon = BitoIcons.Lock, text = stringResource(R.string.onb_welcome_offline))
        Spacer(Modifier.height(10.dp))
        InfoPill(icon = BitoIcons.Ban, text = stringResource(R.string.onb_welcome_no_accounts))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // languageTag null means "follow the system" (OnboardingUiState's own contract) — the
            // chip that matches the CURRENTLY RESOLVED locale reads as selected, same idea as
            // GeneralSectionCard's language row, just without a third "System" option to land on.
            // A system language outside {es, en} (locales_config only declares those two) falls
            // back to "en" — the base resource language — rather than leaving BOTH chips
            // unselected, which `resolved == "es"`/`resolved == "en"` alone would do.
            val resolved = (languageTag ?: Locale.getDefault().language).takeIf { it == "es" } ?: "en"
            LanguageChip(
                text = stringResource(R.string.onb_lang_es),
                selected = resolved == "es",
                onClick = { onSetLanguage("es") },
            )
            LanguageChip(
                text = stringResource(R.string.onb_lang_en),
                selected = resolved == "en",
                onClick = { onSetLanguage("en") },
            )
        }
        Spacer(Modifier.weight(1f))
        PillButton(
            text = stringResource(R.string.onb_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
        )
    }
}

@Composable
private fun InfoPill(
    icon: ImageVector,
    text: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Tarjeta)
            .border(1.dp, Borde, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Hoja, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Tinta)
    }
}

@Composable
private fun LanguageChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Tinta else Tarjeta)
            .then(if (selected) Modifier else Modifier.border(1.dp, Borde, CircleShape))
            // selectable (not plain clickable) so the chosen chip carries real "selected" semantics
            // — lets a test assert which one is highlighted instead of reading rendered colors.
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (selected) Tarjeta else TintaSuave)
    }
}

/**
 * STORY_1..FIRST_HABIT ride one [HorizontalPager] so the story pages are actually swipeable — the
 * mockups give 7b-7d no advance affordance besides «Saltar» (which only ever jumps to NAME), so a
 * button-only fallback would leave a swipe as the sole way through the middle of the story.
 *
 * Two-way sync (VM step <-> pager position) without the two fighting each other: [pageIndex] —
 * the step's own index into [PAGER_STEPS] — is used as a `key()` around the whole pager block. A
 * step change from OUTSIDE a swipe (Empezar's `next()`, Saltar's `skipStory()`) changes
 * [pageIndex], which re-keys the block and mounts a fresh [rememberPagerState] already
 * `initialPage`-ed at the right spot — no `animateScrollToPage` needed. A swipe instead changes
 * the pager's own `settledPage` first; the `snapshotFlow` below reconciles that into the VM with
 * one `onNext()`/`onBack()`, which changes [pageIndex] to match, which re-keys the pager at the
 * page it's already visually settled on (page 0 is a floor: nothing here ever swipes past
 * STORY_1 back to WELCOME).
 *
 * The `.drop(1)` is a defensive no-op, not a fix for a live bug: [rememberPagerState] seeds
 * `settledPage` synchronously from `initialPage`, so on every (re-)mount the very first value
 * `snapshotFlow` replays to a fresh collector already equals [pageIndex] — the `when` below matches
 * neither branch on it regardless. Dropping that first, always-matching replay just makes that
 * explicit in the code rather than leaning on the coincidence: the collector body is then only ever
 * reasoning about a LATER, genuine change to `settledPage` — i.e. an actual swipe — not about
 * whether the first one happens to be harmless.
 */
@Composable
private fun StoryPagerScaffold(
    step: OnboardingStep,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val pageIndex = PAGER_STEPS.indexOf(step).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().background(Papel)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
            if (step in STORY_STEPS) {
                TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = TintaSuave)) {
                    Text(stringResource(R.string.onb_skip), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(pageIndex) {
                val pagerState = rememberPagerState(initialPage = pageIndex) { PAGER_STEPS.size }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.drop(1).collect { settled ->
                        when {
                            settled > pageIndex -> onNext()
                            settled < pageIndex -> onBack()
                        }
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    StoryPageContent(PAGER_STEPS[page])
                }
            }
        }
        PagerDots(
            total = PAGER_STEPS.size,
            activeIndex = pageIndex,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp),
        )
    }
}

@Composable
private fun StoryPageContent(step: OnboardingStep) {
    when (step) {
        in STORY_STEPS ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                StoryScene(step, modifier = Modifier.fillMaxWidth().aspectRatio(1.4f))
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(storyTitleRes(step)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = Tinta,
                    textAlign = TextAlign.Center,
                )
                if (step == OnboardingStep.STORY_3) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.onb_story3_sub),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TintaSuave,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        // T7 (NAME/PERSONALITY) and T8 (FIRST_HABIT) own the real content of these steps — a bare
        // placeholder keeps the pager chrome (dots, skip gating) honest until then.
        else ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(step.name, style = MaterialTheme.typography.titleMedium, color = Tinta)
            }
    }
}

private fun storyTitleRes(step: OnboardingStep) =
    when (step) {
        OnboardingStep.STORY_1 -> R.string.onb_story1_title
        OnboardingStep.STORY_2 -> R.string.onb_story2_title
        OnboardingStep.STORY_3 -> R.string.onb_story3_title
        else -> error("not a story step: $step")
    }

@Composable
private fun PagerDots(
    total: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val active = i == activeIndex
            Box(
                Modifier
                    .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) Hoja else Borde),
            )
        }
    }
}
