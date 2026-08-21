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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
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
            state = state,
            onNext = viewModel::next,
            onBack = viewModel::back,
            onSkip = viewModel::skipStory,
            onSetName = viewModel::setName,
            onSetPersonality = viewModel::setPersonality,
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
 *
 * The persistent "Seguir" pill (mockups 7e/7f) lives HERE, below [PagerDots], not inside either
 * step's own content — both mockups pin it to the very bottom of the screen, under the dots, so it
 * has to sit outside the swipeable pager cell. NAME gates it on a non-blank trimmed name;
 * PERSONALITY has nothing to gate (a personality is always selected, defaulting to NEUTRA) so it's
 * always enabled. FIRST_HABIT keeps no button here — T8 owns that step's own "Crear y empezar".
 */
@Composable
private fun StoryPagerScaffold(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onSetName: (String) -> Unit,
    onSetPersonality: (Personality) -> Unit,
) {
    val step = state.step
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
                    StoryPageContent(PAGER_STEPS[page], state, onSetName, onSetPersonality)
                }
            }
        }
        PagerDots(
            total = PAGER_STEPS.size,
            activeIndex = pageIndex,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp),
        )
        when (step) {
            OnboardingStep.NAME ->
                PillButton(
                    text = stringResource(R.string.onb_continue),
                    onClick = onNext,
                    enabled = state.name.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).testTag("onb-continue"),
                )
            OnboardingStep.PERSONALITY ->
                PillButton(
                    text = stringResource(R.string.onb_continue),
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).testTag("onb-continue"),
                )
            else -> {}
        }
    }
}

@Composable
private fun StoryPageContent(
    step: OnboardingStep,
    state: OnboardingUiState,
    onSetName: (String) -> Unit,
    onSetPersonality: (Personality) -> Unit,
) {
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
        OnboardingStep.NAME -> NameStepContent(name = state.name, onNameChange = onSetName)
        OnboardingStep.PERSONALITY ->
            PersonalityStepContent(
                name = state.name,
                personality = state.personality,
                onSelect = onSetPersonality,
            )
        // T8 owns FIRST_HABIT's real content — a bare placeholder keeps the pager chrome (dots,
        // skip gating) honest until then.
        else ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(step.name, style = MaterialTheme.typography.titleMedium, color = Tinta)
            }
    }
}

/** 138dp/98dp keeps the same ~1.4 tint-circle-to-avatar ratio [WelcomeScene]'s own hero uses. */
private val HABI_HERO_CIRCLE_SIZE = 138.dp
private val HABI_HERO_AVATAR_SIZE = 98.dp
private val HABI_MINI_AVATAR_SIZE = 40.dp

/** The tinted-circle Habi hero shared by the NAME and PERSONALITY steps — same shape as [WelcomeScene]'s, just smaller and mood/personality-aware. */
@Composable
private fun HabiHeroCircle(
    personality: Personality,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(HABI_HERO_CIRCLE_SIZE).clip(CircleShape).background(HojaTinte), contentAlignment = Alignment.Center) {
        HabiAvatar(
            spec = HabiSpec(Mood.NORMAL, personality, EquippedSet()),
            modifier = Modifier.size(HABI_HERO_AVATAR_SIZE),
            // A genuinely fresh onboarding Habi has no data behind it yet — MoodEngine's own
            // fallback for "no decided periods" is NORMAL (see [com.alvarotc.bito.domain.MoodEngine]),
            // never RADIANT/WILTED/DRAMATIC, all of which claim real history that doesn't exist yet.
            animated = false,
        )
    }
}

/** Transparent M3 [TextField] colors so the wrapping [BitoCard] reads as the field's only surface — same idiom as [com.alvarotc.bito.ui.habitform.HabitFormScreen]'s `NameField`. */
@Composable
private fun onboardingFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = Hoja,
    )

/**
 * 7e: Habi hero, headline, name field, and — only once the trimmed name is non-empty — Habi's
 * reaction bubble, voiced NEUTRA ([R.string.onb_name_reaction]; no personality is picked yet).
 */
@Composable
private fun NameStepContent(
    name: String,
    onNameChange: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        HabiHeroCircle(personality = Personality.NEUTRA)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onb_name_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = Tinta,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.onb_name_question),
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        BitoCard(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().testTag("onb-name-field"),
                placeholder = { Text(stringResource(R.string.onb_name_hint), color = TintaSuave) },
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Tinta, fontWeight = FontWeight.Bold),
                colors = onboardingFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
        val trimmedName = name.trim()
        if (trimmedName.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SpeechBubble(
                speaker = stringResource(R.string.onb_name_speaker),
                text = stringResource(R.string.onb_name_reaction, trimmedName),
                modifier = Modifier.fillMaxWidth().testTag("onb-name-reaction"),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 7f: headline, big Habi wearing the SELECTED personality's face, a live bubble speaking that
 * personality's own voice ([HabiVoice.greetingRes] at [Mood.NORMAL] — a fresh Habi has no data
 * behind it yet, same reasoning as [HabiHeroCircle]) with the user's name interpolated, then the
 * three personality cards (mini-Habi + label each), selectable, hoja-bordered when active.
 */
@Composable
private fun PersonalityStepContent(
    name: String,
    personality: Personality,
    onSelect: (Personality) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_personality_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = Tinta,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        HabiHeroCircle(personality = personality)
        Spacer(Modifier.height(16.dp))
        val fallbackName = stringResource(R.string.habi_name_fallback)
        SpeechBubble(
            speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(personality))),
            text = stringResource(HabiVoice.greetingRes(Mood.NORMAL, personality), name.trim().ifEmpty { fallbackName }),
            modifier = Modifier.fillMaxWidth().testTag("onb-personality-bubble"),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Personality.entries.forEach { option ->
                PersonalityCard(
                    personality = option,
                    selected = option == personality,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onb_personality_hint),
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PersonalityCard(
    personality: Personality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Tarjeta)
            .border(1.dp, if (selected) Hoja else Borde, RoundedCornerShape(20.dp))
            .selectable(selected = selected, onClick = onClick)
            .testTag("onb-personality-card-${personality.name.lowercase(Locale.ROOT)}")
            .padding(vertical = 16.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HabiAvatar(
            spec = HabiSpec(Mood.NORMAL, personality, EquippedSet()),
            modifier = Modifier.size(HABI_MINI_AVATAR_SIZE),
            animated = false,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(HabiVoice.labelRes(personality)),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = Tinta,
            textAlign = TextAlign.Center,
        )
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
