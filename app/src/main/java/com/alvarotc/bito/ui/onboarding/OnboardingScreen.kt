package com.alvarotc.bito.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import com.alvarotc.bito.ui.habitform.HabitPreset
import com.alvarotc.bito.ui.habitform.labelRes
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.settings.AppLocale
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
 * M9 final review minor 5: [StoryPagerScaffold]'s `key(pageIndex)` remounts the whole pager on
 * EVERY step change, including one reconciled from a swipe settle — so the page the user is
 * already looking at (fully visible the instant the drag stopped) got its entrance fade replayed
 * from alpha 0, a visible blink rather than motion. This latch is what tells the freshly-mounted
 * page which case it's in: the settle collector calls [markSwipe] right before the onNext()/
 * onBack() that triggers the remount; the page reads-and-clears it exactly once via
 * [consumeSkipsFade] to seed its own `entered` flag already-true instead of animating from false.
 * Every other path into a fresh page — first mount, a button/skip tap — never calls [markSwipe],
 * so [consumeSkipsFade] defaults to false and the fade plays exactly as before.
 *
 * Plain Kotlin, not Compose state: nothing here needs to trigger recomposition on its own (the
 * step change that led here already will), so a bare `var` is enough and — unlike a
 * [androidx.compose.runtime.MutableState] — it's directly unit-testable with no compose rule.
 * `internal`, not private, for exactly that: same-module tests read it straight, no wider API leak.
 */
internal class OnboardingSwipeFadeLatch {
    private var swiped = false

    fun markSwipe() {
        swiped = true
    }

    /** Reads AND clears in one call — a second consume before another [markSwipe] returns false. */
    fun consumeSkipsFade(): Boolean {
        val result = swiped
        swiped = false
        return result
    }
}

/**
 * The first-run flow's scaffold (mockups 7a-7g; this task builds 7a-7d, T7/T8 own the rest).
 * WELCOME (7a) is a standalone full-screen beat; every step after it rides the same 6-dot pager
 * chrome (the mockups' dots row), swipeable via [HorizontalPager] — see [StoryPagerScaffold] for
 * how a swipe settle reconciles back into [OnboardingViewModel]'s own step.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // System back steps back one beat instead of exiting the app mid-flow -- but only past
    // WELCOME: there, the default behavior (exit) is exactly right, and OnboardingStep's own
    // enum order makes `> WELCOME` the correct floor check (WELCOME is ordinal 0). Disabling the
    // handler there, rather than wiring it unconditionally and relying on OnboardingViewModel.back
    // being a no-op at the first step, is what lets the system gesture actually fall through to
    // that default instead of being swallowed by a handler that intercepted it and did nothing.
    BackHandler(enabled = state.step > OnboardingStep.WELCOME, onBack = viewModel::back)
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
            onSetHabitName = viewModel::setHabitName,
            onSetHabitKind = viewModel::setHabitKind,
            onSetHabitTarget = viewModel::setHabitTarget,
            onFinish = viewModel::finish,
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
        Box(
            Modifier.scale(rememberHabiEntranceScale()).size(200.dp).clip(CircleShape).background(HojaTinte),
            contentAlignment = Alignment.Center,
        ) {
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
            // languageTag null means "follow the system" (OnboardingUiState's own contract), and an
            // out-of-set tag (a backup restored from a locale locales_config doesn't declare, say)
            // is treated the exact same way GeneralSectionCard's language row already treats it —
            // AppLocale.resolveDisplayLanguage falls through to the resolved system locale for
            // either case, rather than lighting a chip that doesn't match what's actually active.
            val resolved = AppLocale.resolveDisplayLanguage(languageTag)
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
 * always enabled. FIRST_HABIT's own "Crear y empezar"/"Empezar" pill lives here too — same bottom
 * slot, same reason (mockup 7g pins it under the dots), gated on nothing (a blank habit name is a
 * ruled skip path, not an error: [onFinish] itself completes onboarding either way).
 *
 * Motion (GUIA :60): each page's content fades in (alpha 0->1, 200ms ease-out) on composition —
 * [HorizontalPager] itself already supplies the horizontal slide for a real drag; a
 * button/skip-triggered advance re-keys the whole pager (see the block comment above), which has
 * no drag to ride, so the fade is the only cue on that path. A swipe settle ALSO re-keys the
 * pager (same remount), but there the content was already visible the instant the drag stopped —
 * replaying the fade from 0 there is a blink, not motion (M9 final review minor 5), which is what
 * [OnboardingSwipeFadeLatch] exists to skip; see its own KDoc for the mechanism.
 *
 * The NAME step's blank-name gate only ever blocks the FORWARD direction, never backward: the
 * pager's own `userScrollEnabled` stays `true` unconditionally (a per-direction scroll flag
 * doesn't exist on [HorizontalPager]), and the settle collector below is what actually enforces
 * it — a forward settle off a blank NAME calls `pagerState.animateScrollToPage` back to the
 * current page instead of `onNext()`, so the page genuinely does move under the user's finger and
 * then snaps back, rather than never moving at all. A backward settle off NAME always passes
 * through to `onBack()` regardless of the name field, same as any other page.
 */
@Composable
private fun StoryPagerScaffold(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onSetName: (String) -> Unit,
    onSetPersonality: (Personality) -> Unit,
    onSetHabitName: (String) -> Unit,
    onSetHabitKind: (HabitPreset) -> Unit,
    onSetHabitTarget: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val step = state.step
    val pageIndex = PAGER_STEPS.indexOf(step).coerceAtLeast(0)
    // Survives the key(pageIndex) remount below on purpose — see OnboardingSwipeFadeLatch's KDoc.
    val swipeFadeLatch = remember { OnboardingSwipeFadeLatch() }

    Column(Modifier.fillMaxSize().background(Papel)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
            if (step in STORY_STEPS) {
                TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = TintaSuave)) {
                    Text(stringResource(R.string.onb_skip), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().testTag("onb-pager")) {
            key(pageIndex) {
                val pagerState = rememberPagerState(initialPage = pageIndex) { PAGER_STEPS.size }
                // Read inside the settle collector below via .value, not `state` directly: that
                // coroutine is launched once per `pagerState` (i.e. once per pageIndex) and never
                // restarts on every keystroke, so a plain closure over `state` would keep whatever
                // name was live the moment the coroutine started -- rememberUpdatedState is what
                // keeps this reading the CURRENT name as the user types.
                val latestState = rememberUpdatedState(state)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.drop(1).collect { settled ->
                        when {
                            settled > pageIndex -> {
                                // Belt-and-braces for the NAME step's blank-name gate: the
                                // "Seguir" button below is disabled on a blank name, but a swipe
                                // bypasses buttons entirely. Blocking the whole pager's scroll to
                                // prevent that (the old approach) also blocked swiping BACKWARD
                                // out of NAME, which has nothing to do with the gate -- scroll
                                // stays enabled unconditionally now, and only a FORWARD settle off
                                // a still-blank name gets rejected, by snapping back to this same
                                // page instead of calling onNext(). Without this, dragging past
                                // NAME with an empty field would still advance the step, and
                                // finish() would persist userName = "" (every voiced string falls
                                // back to "campeón" forever, exactly what 7e exists to prevent).
                                if (step == OnboardingStep.NAME && latestState.value.name.trim().isEmpty()) {
                                    // Child coroutine, not a direct suspending call here: a fast
                                    // second drag preempts this via HorizontalPager's own
                                    // MutatorMutex, and if that CancellationException propagated
                                    // out of this collect{} it would kill the whole LaunchedEffect
                                    // -- soft-locking onboarding (pager free-scrolls, VM never
                                    // hears settledPage again). launch{} isolates that
                                    // cancellation to just the snap-back.
                                    launch { pagerState.animateScrollToPage(pageIndex) }
                                } else {
                                    // Marked BEFORE onNext(), not after: onNext() synchronously
                                    // updates the VM's StateFlow, whose recomposition is what
                                    // re-keys this whole block -- the freshly mounted page has to
                                    // find the latch already marked when it first composes.
                                    swipeFadeLatch.markSwipe()
                                    onNext()
                                }
                            }
                            settled < pageIndex -> {
                                swipeFadeLatch.markSwipe()
                                onBack()
                            }
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    // Only `page == pageIndex` is the page this remount landed on -- guards the
                    // consume so a neighbor page composed for any other reason (prefetch, mid-drag)
                    // can never eat the mark meant for the settled page. Consuming HERE, in the
                    // `remember` seed rather than a later effect, is what avoids a one-frame
                    // alpha-0 render: `entered` starts true already, so `fade` below never leaves 1.
                    var entered by remember { mutableStateOf(page == pageIndex && swipeFadeLatch.consumeSkipsFade()) }
                    val fade by
                        animateFloatAsState(
                            targetValue = if (entered) 1f else 0f,
                            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                            label = "onb-page-fade",
                        )
                    LaunchedEffect(Unit) { entered = true }
                    Box(Modifier.fillMaxSize().alpha(fade)) {
                        StoryPageContent(
                            PAGER_STEPS[page],
                            state,
                            onSetName,
                            onSetPersonality,
                            onSetHabitName,
                            onSetHabitKind,
                            onSetHabitTarget,
                        )
                    }
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
            OnboardingStep.FIRST_HABIT ->
                PillButton(
                    text =
                        stringResource(
                            if (state.habitName.trim().isNotEmpty()) R.string.onb_habit_create else R.string.onb_habit_start,
                        ),
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).testTag("onb-create-start"),
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
    onSetHabitName: (String) -> Unit,
    onSetHabitKind: (HabitPreset) -> Unit,
    onSetHabitTarget: (Int) -> Unit,
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
        OnboardingStep.FIRST_HABIT ->
            FirstHabitStepContent(
                habitName = state.habitName,
                habitKind = state.habitKind,
                habitTarget = state.habitTarget,
                onSetHabitName = onSetHabitName,
                onSetHabitKind = onSetHabitKind,
                onSetHabitTarget = onSetHabitTarget,
            )
        else -> error("unreachable: every OnboardingStep in PAGER_STEPS is handled above")
    }
}

/**
 * Habi's entrance on 7a/7e (GUIA spec :60 "el onboarding gana vida"): scale 0.9 -> 1, 200ms
 * ease-out — the same beat [com.alvarotc.bito.ui.celebration.CelebrationSheets]' `PerfectDaySheet`
 * already uses for its own Habi entrance. Not applied to 7f (PERSONALITY): the brief scopes this
 * motion to 7a/7e only.
 */
@Composable
private fun rememberHabiEntranceScale(): Float {
    var entered by remember { mutableStateOf(false) }
    val scale by
        animateFloatAsState(
            targetValue = if (entered) 1f else 0.9f,
            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            label = "onb-habi-scale",
        )
    LaunchedEffect(Unit) { entered = true }
    return scale
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
        HabiHeroCircle(personality = Personality.NEUTRA, modifier = Modifier.scale(rememberHabiEntranceScale()))
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

/** Motion (GUIA :60): the active dot's pill width animates in, 150ms, rather than snapping. */
@Composable
private fun PagerDots(
    total: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val active = i == activeIndex
            val width by
                animateDpAsState(
                    targetValue = if (active) 22.dp else 8.dp,
                    animationSpec = tween(150, easing = LinearOutSlowInEasing),
                    label = "onb-dot-width",
                )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) Hoja else Borde),
            )
        }
    }
}

/**
 * 7g: headline, habit-name field, preset pills (this task's own visual — checkmark + hoja border
 * on the selected pill, mockup-literal — but the exact same [HabitPreset] labels
 * [com.alvarotc.bito.ui.habitform.HabitFormScreen]'s own `PresetPills` use, via the shared
 * [labelRes] extension: no duplicated string keys, and any translation of preset_daily etc. stays
 * in sync between the form and onboarding automatically), the goal row (see [GoalRow] for exactly
 * how it mirrors the real form's per-preset target semantics — the QUIT trap this task's ledger
 * warning exists for), and the widget hint card. Left-aligned throughout, like the real form —
 * unlike NAME/PERSONALITY's centered hero layout, 7g's mockup reads as a form, not a story beat.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FirstHabitStepContent(
    habitName: String,
    habitKind: HabitPreset,
    habitTarget: Int,
    onSetHabitName: (String) -> Unit,
    onSetHabitKind: (HabitPreset) -> Unit,
    onSetHabitTarget: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_habit_title), style = MaterialTheme.typography.headlineLarge, color = Tinta)
        Spacer(Modifier.height(20.dp))
        BitoCard(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = habitName,
                onValueChange = onSetHabitName,
                modifier = Modifier.fillMaxWidth().testTag("onb-habit-name-field"),
                placeholder = { Text(stringResource(R.string.onb_habit_name_hint), color = TintaSuave) },
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Tinta, fontWeight = FontWeight.Bold),
                colors = onboardingFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitPreset.entries.forEach { preset ->
                HabitPresetPill(preset = preset, selected = preset == habitKind, onClick = { onSetHabitKind(preset) })
            }
        }
        Spacer(Modifier.height(16.dp))
        GoalRow(kind = habitKind, target = habitTarget, onAdjust = onSetHabitTarget)
        Spacer(Modifier.height(16.dp))
        WidgetHintCard()
        Spacer(Modifier.height(24.dp))
    }
}

/** Mockup style, not the real form's: checkmark + hoja border on the selected pill (the form itself
 * fills the selected pill with [HojaTinte] instead — different visual language for the same five
 * [HabitPreset] options, same [labelRes] strings underneath). */
@Composable
private fun HabitPresetPill(
    preset: HabitPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Tarjeta,
        border = BorderStroke(1.dp, if (selected) Hoja else Borde),
        modifier = Modifier.testTag("onb-habit-preset-${preset.name}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (selected) {
                Icon(BitoIcons.Check, contentDescription = null, tint = Hoja, modifier = Modifier.size(14.dp))
            }
            Text(
                stringResource(preset.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Tinta else TintaSuave,
            )
        }
    }
}

/**
 * The "objetivo"/"goal" row (mockup's "dato grande" pattern: small label left, big value right).
 *
 * Mirrors [com.alvarotc.bito.ui.habitform.HabitFormScreen]'s own `TargetSection`/`TargetStepper`
 * per preset, simplified to what onboarding actually captures (no unit/period/limit-metric fields
 * of its own):
 *  - DAILY_CHECK: the real form hides its whole target card — `toNewEntity` always pins this
 *    preset's target at 1 regardless of any state. 7g's mockup shows the row anyway (ground truth
 *    for THIS step), so it's rendered but as a static "1 <goal-daily-unit>" — never wired to
 *    [target] or [onAdjust], since the real value is never anything else.
 *  - QUANTITY / DURATION / WEEKLY_TIMES: the real form's `TargetStepper` is genuinely editable —
 *    mirrored here with a small +/- pair over [target]/[onAdjust]. WEEKLY_TIMES additionally caps
 *    at 7 (`HabitFormViewModel`'s own `MAX_WEEKLY_TIMES`/`clampTarget`) since a habit above that is
 *    one the real form can never create; [OnboardingViewModel.setHabitTarget] itself only floors
 *    at 1, so that upper clamp lives here, at the one call site that needs it.
 *  - QUIT: onboarding never exposes the real form's Total/Limit picker (no `setHabitQuitMode` in
 *    the VM contract — the ledger warning's "hide honestly" branch), so [OnboardingViewModel.finish]
 *    always builds a TOTAL/abstinence habit (target pinned to 0 by `toNewEntity`, unconditionally).
 *    Rather than hiding the row (silent) or showing a bare "0" (looks broken), it RE-LABELS: the
 *    value slot shows [R.string.quit_total] ("Del todo"/"Cold turkey") — the exact same string the
 *    real form's own Total/Limit segmented pills use for that mode — so switching to «Dejar de
 *    hacer» tells the user what kind of quit habit they're about to get, same as the real form's
 *    default TOTAL selection does.
 */
@Composable
private fun GoalRow(
    kind: HabitPreset,
    target: Int,
    onAdjust: (Int) -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.onb_habit_goal_label),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
                modifier = Modifier.weight(1f),
            )
            when (kind) {
                HabitPreset.DAILY_CHECK ->
                    GoalValue(value = "1", unit = stringResource(R.string.onb_habit_goal_daily))
                HabitPreset.QUIT ->
                    Text(
                        stringResource(R.string.quit_total),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Tinta,
                    )
                HabitPreset.QUANTITY, HabitPreset.DURATION, HabitPreset.WEEKLY_TIMES -> {
                    val unit =
                        when (kind) {
                            HabitPreset.DURATION -> stringResource(R.string.unit_min)
                            HabitPreset.WEEKLY_TIMES -> stringResource(R.string.onb_habit_goal_weekly)
                            else -> stringResource(R.string.onb_habit_goal_quantity)
                        }
                    val max = if (kind == HabitPreset.WEEKLY_TIMES) ONB_WEEKLY_TIMES_MAX else Int.MAX_VALUE
                    GoalStepChip(icon = BitoIcons.Minus, testTag = "onb-goal-minus") {
                        onAdjust((target - 1).coerceIn(1, max))
                    }
                    Spacer(Modifier.width(10.dp))
                    GoalValue(value = "$target", unit = unit)
                    Spacer(Modifier.width(10.dp))
                    GoalStepChip(icon = BitoIcons.Plus, testTag = "onb-goal-plus") {
                        onAdjust((target + 1).coerceIn(1, max))
                    }
                }
            }
        }
    }
}

/** `HabitFormViewModel.clampTarget`'s own WEEKLY_TIMES cap (`MAX_WEEKLY_TIMES`), mirrored here. */
private const val ONB_WEEKLY_TIMES_MAX = 7

@Composable
private fun GoalValue(
    value: String,
    unit: String,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.displayLarge, color = Tinta)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
    }
}

/** Same visual language as [com.alvarotc.bito.ui.habitform.HabitFormScreen]'s own `StepChip`. */
@Composable
private fun GoalStepChip(
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Papel)
            .border(1.dp, Borde, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Tinta, modifier = Modifier.size(14.dp))
    }
}

/**
 * 7g's widget hint (`onb_habit_widget_hint`, "widget de Bito"/"Bito widget" bolded) — same
 * split-and-style idiom [com.alvarotc.bito.ui.settings.SettingsScreen]'s `coloredCountLine` uses:
 * the RAW template (no format args passed to [stringResource]) is split on the literal "%1$s"
 * token so the substituted segment can carry its own [SpanStyle], instead of losing the token to
 * eager substitution.
 */
@Composable
private fun WidgetHintCard() {
    val template = stringResource(R.string.onb_habit_widget_hint)
    val bold = stringResource(R.string.onb_habit_widget_bold)
    val text =
        buildAnnotatedString {
            val parts = template.split("%1\$s")
            append(parts.getOrElse(0) { "" })
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            append(parts.getOrElse(1) { "" })
        }
    BitoCard(modifier = Modifier.fillMaxWidth().testTag("onb-widget-hint"), container = HojaTinte, border = HojaTinte) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(BitoIcons.Widget, contentDescription = null, tint = Tinta, modifier = Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = Tinta)
        }
    }
}
