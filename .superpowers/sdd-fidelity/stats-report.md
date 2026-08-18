# Stats/Records/Numbers fidelity fix — report

Branch `feat/streaks`, HEAD 9998fe7 base. Scope: `StatsScreen.kt`, `RecordsScreen.kt`,
`NumbersScreen.kt`, `DotHeatmap.kt` (one constant). Layout/sizing only, zero behavior/domain
changes — same callbacks, state, routes, testTags. `StreakChip` (shared with Today's
`HabitCards.kt`) was left untouched; StatsScreen's streak wall now uses a new local composable
instead of reusing it, so Today is unaffected.

## 1. Tu semana — dots

`WeekDayDot` (StatsScreen.kt): 10dp → **16dp solid**, gap bumped to 6dp (already 6dp, kept).
Color mapping now matches DotHeatmap's fixed canon exactly: FULFILLED/ACTIVITY solid Hoja ·
FAILED/EMPTY solid Borde (was a hollow TintaSuave ring) · FROZEN solid HojaTinte + 10dp snowflake
(was 6dp) · PAUSED solid TintaSuave full-size (was a shrunk 0.6× Borde dot — colors were
literally swapped with FAILED before this fix) · PENDING 2dp TintaSuave ring, full opacity (was
1dp at 40% alpha) · OFF stays its own small/faint speck, 6dp at 35% Borde alpha — deliberately
*not* bumped to DotHeatmap's new 40% (point 7 below), since the spec gives this component its own
literal number (35%) and it's a smaller, denser strip than the calendar grid.

Habit name: `bodyLarge` (already the 15sp token, no override needed) + `maxLines = 1` +
`TextOverflow.Ellipsis`, still `weight(1f)` so dots stay right-aligned as a block. Row now
`heightIn(min = 40.dp)`.

## 2. Rachas activas — muro de llamas with names

Old shape was a `StreakChip` (flame+count, no name) stacked over a name `Text` in a `Column` —
the name was present but tiny and secondary. New `StreakWallChip` (StatsScreen.kt, replaces
`StreakWallItem`): one pill per streak — `BrasaTinte` bg, `CircleShape`, padding 16dp
horizontal/10dp vertical, 16dp `Brasa` flame icon, count **20sp Bold Brasa**
(`displayLarge.copy(fontSize = 20.sp)`), habit name **14sp Medium Tinta**
(`labelMedium.copy(fontSize = 14.sp)`) — count reads first and big, name rides along small.
`LazyRow` gap 16dp → 12dp per spec. `streak-${habitId}` testTag kept, moved from the wrapping
`Column` to the pill `Row` itself — the existing ordering test (`getUnclippedBoundsInRoot().left`)
doesn't care about node type, verified green.

Spec-gap decision: an unbounded name inside a `LazyRow`-hosted pill would let one long habit name
blow the wall off-screen with no wrap/ellipsis, and defeat the "wall of chips" reading. Added
`maxLines = 1`, `TextOverflow.Ellipsis`, `widthIn(max = 140.dp)` on the name — not in the literal
spec text but the closest faithful call to keep multiple chips visible per screen. No clickable
modifier on the chip — brasa here stays emotional heat, never interaction, per the existing
`StreakChip` KDoc convention (now cross-referenced).

## 3. Días perfectos (accent card)

Hero total 40sp → **56sp Bold** (`displayLarge.copy(fontSize = 56.sp)` — `displayLarge` is
already Bold, so only the size needed overriding) on `Tarjeta` color, unchanged. "N este mes"
13sp → 14sp. `testTag("accent")` and structure untouched.

## 4. Teasers (Récords | Tus números)

`titleMedium` was already 18sp SemiBold and the chevron already 20dp (no change needed there).
Added `heightIn(min = 72.dp)` + `Alignment.CenterVertically` on the inner `Row` (not the outer
`BitoCard` modifier — `Surface`'s content `Box` doesn't propagate min constraints to its child
`Column`, so a min-height on the card modifier itself would not have stretched the row).
Deliberately **did not** add `maxLines`/ellipsis to the label: "Tus números" is close to the
half-card width budget at 18sp and risks truncating to "Tus núme…"; `heightIn(min = 72.dp)`
absorbs a one- or two-line label identically (both land at 72dp), so both teaser cards stay the
same height regardless of which label wraps. Covered by a new test (see Tests below).

## 5. Delta chip

Kept the existing `delta != null && delta >= 0` gating untouched. Padding 10dp/4dp → chip canon
14dp horizontal/8dp vertical; weight bumped `labelMedium` (Medium) → SemiBold via
`.copy(fontWeight = FontWeight.SemiBold)` (size already 13sp, no fontSize override needed).

## 6. RecordsScreen.kt

- Hero: flame icon 40dp → 32dp, number 40sp Tinta → **56sp Bold Brasa**
  (`displayLarge.copy(fontSize = 56.sp)`, color changed to `Brasa` per spec). Unit + habit-name
  lines under it left as-is (not named in the spec point).
- Record chip (`RecordLine`): padding 10dp/4dp → 14dp/8dp, added the missing 14dp `Brasa` flame
  icon (chip canon is flame+count; the old chip was text-only), weight bumped to SemiBold.

## 7. NumbersScreen.kt

Each tile: value `headlineLarge` (26sp SemiBold) → **28sp Bold**
(`headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold)` — both real overrides here,
`headlineLarge`'s default weight is SemiBold not Bold). Label stays the bare `labelMedium` token
(already 13sp TintaSuave, no override needed).

## 8. DotHeatmap.kt — OFF dots (shared component)

`OFF_DOT_SIZE` 10dp → **14dp**, alpha 0.35 → **0.40**. Touch cells (`aspectRatio(1f)`,
`heightIn(min = 44.dp)`, `weight(1f)`, 3dp inter-cell gap) are completely untouched — the dot
glyph is drawn independently inside the cell and doesn't affect its measured size.

## Tests

- `./gradlew ktlintCheck testDebugUnitTest` — green, gate run before and after the final pass.
- Final full gate `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` — **BUILD
  SUCCESSFUL**.
- `DetailScreenTest`'s `heatmap day cells meet the 44dp touch floor` test — passes **unchanged**
  (asserts ≥46dp on `heatmap-day-$today`), confirming the OFF-dot bump didn't touch cell geometry.
- `StatsScreenTest` — all 4 tests green, including one **new** test added because nothing in the
  build/lint/unit gate actually renders geometry at real widths:
  `teaser cards get dato-grande presence regardless of label length` — asserts
  `assertHeightIsAtLeast(72.dp)` on both `teaser-records` and `teaser-numbers`, exercising the
  point-4 fix and the "Tus números"-wraps-but-both-cards-stay-equal-height reasoning above. This
  is the one legitimate size-expectation test edit in this pass; no existing assertions were
  changed.
- No RecordsScreen/NumbersScreen/DotHeatmap-specific unit tests exist in this codebase to begin
  with (verified via search) — nothing there to update or break.

## Files touched

- `app/src/main/java/com/alvarotc/bito/ui/stats/StatsScreen.kt`
- `app/src/main/java/com/alvarotc/bito/ui/stats/RecordsScreen.kt`
- `app/src/main/java/com/alvarotc/bito/ui/stats/NumbersScreen.kt`
- `app/src/main/java/com/alvarotc/bito/ui/components/DotHeatmap.kt`
- `app/src/test/java/com/alvarotc/bito/ui/stats/StatsScreenTest.kt` (one new test, see above)

## Concerns / follow-ups for the architect

1. Two different OFF-dot faintness values now coexist by design: 35% alpha in the Stats week
   strip vs. 40% in the calendar heatmap. Both are literal spec numbers for their respective
   components; flagging in case the architect wants them collapsed to one value later.
2. RecordsScreen's per-row record chip gained a flame icon it didn't have before (chip canon is
   flame+count, the old chip was text-only) — a visible addition beyond pure resizing, called out
   here since the task was scoped as "layout/sizing only."
3. The streak-wall chip's `widthIn(max = 140.dp)` + ellipsis on the habit name is a spec-gap call
   (see point 2 above), not a literal instruction — flagging for visual sign-off on a real device
   with longer habit names.
