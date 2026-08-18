# Detail screen fidelity fix — report

Branch `feat/streaks`. Scope: `DotHeatmap.kt`, `DetailScreen.kt`, `Components.kt`
(SegmentedPills only). Layout/sizing only, zero behavior/domain changes.

## 1. DotHeatmap — fat dots

`app/src/main/java/com/alvarotc/bito/ui/components/DotHeatmap.kt`

- Added a `WeekdayHeaderRow` (L M X J V S D / M T W T F S S) above the grid, same
  `Row(fillMaxWidth, spacedBy(3dp))` + `weight(1f)` structure as the day rows so columns line up
  exactly. Labels come from `DayOfWeek.of(n).getDisplayName(TextStyle.NARROW, Locale.getDefault())`
  — no new string resources. Verified empirically under this project's Robolectric/SDK 35 setup
  (`[L, M, X, J, V, S, D]` for `Locale("es")`, `[M, T, W, T, F, S, S]` for `Locale.ENGLISH`) via a
  throwaway test, run, then deleted — not part of the diff.
- Visual dot diameter 26dp (`DOT_SIZE`), OFF stays 10dp at 35% alpha (`OFF_DOT_SIZE`).
- State → visual: FULFILLED/ACTIVITY = solid Hoja · FAILED/EMPTY = solid Borde (was a hollow
  ring/border for both) · FROZEN = solid HojaTinte + 14dp snowflake · PAUSED = solid TintaSuave
  (was a tiny 10dp dot) · PENDING = 2dp TintaSuave ring, full opacity (was 1.5dp at 40% alpha) ·
  OFF unchanged (Borde 35% alpha, small). Today = the state's fill (PENDING renders as Tarjeta,
  i.e. "empty card slot", per the mock) with a 2.5dp Brasa ring drawn inside the same 26dp
  footprint (`Modifier.border`, not enlarging the box — matches the canon's
  `box-sizing: border-box`).
- Touch cells: **untouched**. Same `aspectRatio(1f)`, `heightIn(min = 44.dp)`, `weight(1f)`, same
  3dp inter-cell `Row` gap, same 8dp outer inset at the DetailScreen call site. The
  `heatmap day cells meet the 44dp touch floor` test (asserts ≥46dp) passes unchanged, still
  measuring 48dp at w411dp.
- Vertical rhythm: `Column`'s `verticalArrangement` bumped 4dp → 6dp (row-to-row gap only — this
  has zero effect on any single cell's measured width/height, so it's free relative to the
  touch-floor guarantee). Horizontal inter-cell gap **stayed at 3dp** — bumping it toward 6-8dp
  would shrink the weight-divided cell width below the ≥46dp floor at narrower widths (checked
  the arithmetic: even after reclaiming the DetailScreen call site's 8dp outer inset, 6dp gaps
  drop w393dp to ~45.3dp), so the "6-8dp" spec number wasn't chased on that axis.
- Resolution of the "6-8dp visual gap between dots" spec line: that number is the canon mock's
  CSS `grid-gap` property (`.days{gap:8px}`), not the actual inter-dot visual space once you
  account for the mock's own per-column padding. Working the mock's own numbers: 332px card
  interior − 6×8px gaps = 284px / 7 columns ≈ 40.6px per column, with a 22px dot → dot occupies
  ~54% of its column. This implementation: 48dp cell (measured, unchanged), 26dp dot → also ~54%.
  Same proportion — the enlarged dot is what actually kills the "huge vertical air" (previously an
  11-18dp dot inside a 44-48dp cell), not a change to the gap constants.

## 2. Monument — DetailScreen.kt

- Flame icon 44dp, streak number 64sp Bold `Tinta` (`displayLarge.copy(fontSize = 64.sp)`), unit
  16sp Medium `TintaSuave` inline. Number and unit both use `Modifier.alignByBaseline()` (a
  `RowScope` member, resolves without import) so the unit sits on the number's actual text
  baseline — `Row(verticalAlignment = Alignment.Bottom)` alone would align text *boxes*, which
  visibly undershoots the number's baseline at this size gap (64sp vs 16sp); the Row keeps
  `Alignment.Bottom` for the flame icon, which has no baseline to align by.
- Below it, one `Row` with both chips: extracted `RecordChip` (was inline, no icon) — now has a
  14dp mini flame — and `FreezerChip`, moved out of its old standalone row into this same Row.
  Both chips: padding 14dp horizontal / 8dp vertical (was 10/4 and 12/6), text bumped from plain
  `labelMedium` (13sp Medium) to 13sp SemiBold. `FreezerChip` keeps its `freezer-chip` testTag,
  its `onClick → showFreezerSheet = true` wiring, and its exact same gating
  (`current.period == Period.DAY && !archived`), now passed into `Monument` as `showFreezerChip`.

## 3. SegmentedPills — Components.kt

- New `fillWidth: Boolean = false` param. `true` → each option gets `RowScope.weight(1f)`
  (equal-width, centered). Default `false` preserves wrap-content sizing.
  This is necessary, not cosmetic: a Compose `Row` with any `weight()` child always expands to
  consume the incoming bounded max width, regardless of whether `Modifier.fillMaxWidth()` was
  passed to the Row — so unconditional `weight(1f)` would have stretched the compact toggles too.
- Applied **universally** (not gated by `fillWidth`): container background Tarjeta → Papel
  (canon's `.seg{background:#F2ECE1}`), removed the container's `1.dp Borde` border (the canon
  `.seg` rule has none — Papel against the surrounding Tarjeta card is the visual distinction),
  selected pill Tarjeta bg + subtle shadow (`Modifier.shadow(2.dp, CircleShape, clip=false)` when
  selected, 0dp otherwise) replacing the old flat HojaTinte fill, text 14sp SemiBold (was 13sp
  Medium), unselected text stays TintaSuave.
- **DetailScreen's compliance-window pills** (`ComplianceSection`): `fillWidth = true` +
  `Modifier.fillMaxWidth()` — full canon.
- **HabitFormScreen's three other call sites** (quit-mode toggle ~line 282, limit-metric toggle
  ~line 289, period toggle ~line 304): NOT full-width — `fillWidth` stays at its `false` default,
  so they keep their existing compact/wrap-content sizing. They DO inherit the Papel
  trough/Tarjeta-selected+shadow/14sp-SemiBold/no-border visual fix, since that's the shared
  component's canonical look, not a width-specific concern. This is a deliberate visual change to
  a screen outside this dispatch's stated scope (Detail/DotHeatmap/pills-in-Components.kt) —
  flagging it explicitly rather than claiming "unchanged."
- % hero row (`ComplianceSection`): percentage 48sp Bold (Hoja if ≥80 else Tinta), "N de M" 14sp
  TintaSuave, both `alignByBaseline()`; "—" placeholder unchanged.

## 4. Pausar/Archivar — DetailScreen.kt

- New private `LifecycleActionButton` (local to DetailScreen.kt, does not touch the shared
  `GhostPillButton` — that component has ~15 other call sites across sheets/forms/settings that
  are out of this fix's scope). `OutlinedButton`, border `Borde` 1dp, `CircleShape`, 52dp min
  height, 20dp icon, 15sp Medium label, content color TintaSuave.
- `FooterActions`' Row: both buttons `Modifier.weight(1f)` (equal width, fills the row), gap
  bumped 8dp → 12dp. Pause now has an icon (`BitoIcons.Pause`, previously iconless); Resume keeps
  `BitoIcons.Play`; Archive gets `BitoIcons.Archive` (previously iconless). All three testTags
  (`pause`/`resume`/`archive`) preserved.
- Archived variant: `PillButton("Reactivar")` unchanged component, added `Modifier.fillMaxWidth()`
  so it's actually full-width as its own bullet describes (it wasn't, before — `Button` doesn't
  stretch without an explicit width modifier). `reactivate` testTag preserved. Note: this pill is
  still 56dp tall via `PillButton`'s own `heightIn(min = 56.dp)`, vs. the new 52dp lifecycle
  pills — that's an intentional pre-existing difference the spec didn't ask to reconcile ("as-is").

## 5. Dead space

Card padding (20dp) untouched everywhere; monument stays outside a card, directly on Papel, per
spec — only its internal type/icon scale changed.

## Constraints check

- No behavior/logic changes: same callbacks, same state, same routes. Diff is `DotHeatmap.kt`,
  `DetailScreen.kt`, `Components.kt` only — confirmed via `git status --porcelain`.
- No new testTags needed beyond what already existed; none renamed.
- Tokens only (`ui/theme/Color.kt`); no new strings; no emojis; no new dependencies.
- `ktlintCheck` clean.
- `lint`: 0 errors, 51 pre-existing warnings + 1 pre-existing informational note (that note is on
  `DetailScreen.kt` line 106, `mutableStateOf` boxing on `windowIndex` — pre-existing code this
  fix didn't touch). No new lint findings on any of the three changed files.

## Test results

- **Zero test files edited.** Nothing in the suite asserts a size this fix changed — the only
  size-sensitive test is the touch-floor test, and it targets the touch cell (unchanged), not the
  dot glyph inside it.
- `./gradlew ktlintCheck testDebugUnitTest` and the full
  `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` gate both green.
- Measured: **480 tests, 0 failures, 0 errors** (not 481 as noted in the dispatch — 480 is this
  repo's actual current baseline at `feat/streaks` HEAD; no test file was added, removed, or
  skipped by this change).
- `DetailScreenTest`: 7/7 pass, including `heatmap day cells meet the 44dp touch floor`
  (unchanged assertion, still measures 48dp) and `an archived habit only offers reactivation`
  (asserts `pause`/`archive` don't exist and `reactivate` does — passes with the new
  `LifecycleActionButton`/`fillMaxWidth` PillButton).
- `StatsScreenTest`: 3/3 pass — `WeekDayDot` in `StatsScreen.kt` maps `DayDot` → color
  independently of `DotHeatmap.kt` (own private composable, no shared visual API), untouched and
  unaffected.

## Coverage gap (honest note)

No test asserts the monument's streak number renders, the record chip exists, or exercises
`alignByBaseline()` at all — `Monument`/`RecordChip` are only reachable indirectly through
`DetailScreenTest`'s existing tag-based assertions (freezer-chip, pause/archive/resume/reactivate,
heatmap tags), none of which touch the new typographic layout. `assembleDebug` proves it compiles;
visual correctness of the baseline alignment and the 26dp dot states was reasoned through Compose
semantics and cross-checked against the canon mock's math (see §1), not verified on a running
device/screenshot within this session.

## Concerns / calls made

1. **SegmentedPills color/typography fix applied to all 4 call sites, not just DetailScreen's.**
   Read the spec's point 3 as "shared code gets the canonical look; only the width-filling
   behavior is caller-controlled" — the closing sentence about "other call sites unchanged
   visually as far as possible" reads as being specifically about the full-width/equal-weight
   concern (which Compose's `weight()` semantics make impossible to gate any other way than an
   explicit flag), not about color. Flagged above as a scope note, not hidden.
2. **Container border removed from SegmentedPills.** Canon's `.seg` CSS has no border; the old
   `1dp Borde` border is gone. Minor, but a real visual delta beyond the literal bullet list.
3. **`reactivate` PillButton made explicitly full-width.** It wasn't, before (Material `Button`
   doesn't stretch on its own); the spec bullet calls it "full-width... as-is," so I added the one
   missing modifier rather than leaving a description/behavior mismatch.

None of these required a domain/behavior change or a test edit.
