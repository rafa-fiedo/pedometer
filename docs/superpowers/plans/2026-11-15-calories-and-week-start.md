# Calories, Distance and Week Start Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Walking calories and distance from the owner's height and weight (on Today and Scores), plus a Phone default / Monday / Sunday first-day-of-week setting used everywhere a week appears.

**Architecture:** The maths (step length, distance, calories, input ranges, week-start resolution) is pure Kotlin in `:core`. Height, weight and week start are stored in the existing preferences `SettingsStore` and exposed through `StepRepository`. The view models for Settings, Today, Scores and the widget read them; screens only format.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose Material3, DataStore Preferences 1.1.1, kotlinx-datetime 0.6.2, Glance 1.1.1, JUnit 4, Robolectric 4.14.1.

**Spec:** `docs/superpowers/specs/2026-11-15-calories-and-week-start-design.md`

## Global Constraints

- Step length = height × 0.415; distance km = steps × step length; kcal = 0.5 × weight kg × km, rounded half up to a whole number.
- Height 100–250 cm, weight 30–300 kg, whole numbers only. An empty field clears the value.
- Calories show only when both height and weight are set.
- Units: cm, kg, km only. km shown with exactly one decimal, kcal as a whole number, both formatted for the phone's `Locale`.
- Week start default is Phone default, so behaviour is unchanged until the owner picks a day.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- Run Gradle as `./gradlew` from the repo root. Release-variant unit tests are not part of the suite (`:app:testDebugUnitTest` only).

## Review Focus

- Only one of height and weight set: no calories anywhere, and Today shows the link. Test in Task 5.
- A stored height or weight outside the allowed range (corrupt file, or a future range change): treated as not set. Test in Task 2.
- An unknown stored week-start value: falls back to Phone default. Test in Task 2.
- Changing the week start while the widget is on the home screen: it redraws without waiting for new steps. Test in Task 4.
- A language with a comma decimal separator: km reads "7,4", not "7.4". Test in Task 5.

---

### Task 1: Body size, walking maths and week start in `:core`

**Files:**
- Create: `core/src/main/kotlin/app/pedometer/core/Walking.kt`
- Test: `core/src/test/kotlin/app/pedometer/core/WalkingTest.kt`

**Interfaces:**
- Produces:
  - `data class BodySize(val heightCm: Int, val weightKg: Int)` with companion `HEIGHT_RANGE = 100..250`, `WEIGHT_RANGE = 30..300`, `fun of(heightCm: Int?, weightKg: Int?): BodySize?` (null unless both are present and in range), and `fun parseMeasure(text: String, range: IntRange): Int?` (trimmed digits only, in range, otherwise null; same rules as `Goal.parse`).
  - `data class WalkEstimate(val km: Double, val kcal: Int)`
  - `object Walking { fun estimate(steps: Int, body: BodySize): WalkEstimate }`
  - `enum class WeekStart { PHONE_DEFAULT, MONDAY, SUNDAY; fun resolve(phoneDefault: DayOfWeek): DayOfWeek }`

- [ ] **Step 1: Write the failing tests**

`WalkingTest`:
- `tenThousandStepsAtTheSpecExample`: `Walking.estimate(10_000, BodySize(178, 72))` → `km` within 0.001 of 7.387, `kcal == 266`.
- `largerTotalsRoundHalfUp`: `estimate(53_400, BodySize(178, 72)).kcal == 1_420`; `estimate(6_482, …).kcal == 172`; `estimate(17_686, …).kcal == 470`.
- `noStepsNoCalories`: `estimate(0, BodySize(178, 72)) == WalkEstimate(0.0, 0)`.
- `bodySizeNeedsBothValuesInRange`: `BodySize.of(178, 72) == BodySize(178, 72)`; `of(null, 72)`, `of(178, null)`, `of(99, 72)`, `of(178, 301)` are all null.
- `parseMeasureAcceptsOnlyWholeNumbersInRange`: `parseMeasure(" 178 ", HEIGHT_RANGE) == 178`; `parseMeasure("250", HEIGHT_RANGE) == 250`; `"99"`, `"251"`, `"17.5"`, `"-80"`, `"abc"` and `""` all give null.
- `weekStartResolves`: `PHONE_DEFAULT.resolve(SUNDAY) == SUNDAY`; `MONDAY.resolve(SUNDAY) == MONDAY`; `SUNDAY.resolve(MONDAY) == SUNDAY`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :core:test --tests "app.pedometer.core.WalkingTest"`
Expected: compile failure, `Unresolved reference: Walking`.

- [ ] **Step 3: Implement `Walking.kt` with the interfaces above**

Round kcal half up with `kotlin.math.round` on a non-negative value (or `Math.round`).

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/app/pedometer/core/Walking.kt core/src/test/kotlin/app/pedometer/core/WalkingTest.kt
git commit -m "Add walking calories, distance and week start maths"
```

---

### Task 2: Store height, weight and week start

**Files:**
- Modify: `app/src/main/java/app/pedometer/data/SettingsStore.kt`, `app/src/main/java/app/pedometer/data/StepRepository.kt`
- Test: `app/src/test/java/app/pedometer/data/StepRepositoryTest.kt`

**Interfaces:**
- Consumes: `BodySize`, `WeekStart` (Task 1).
- Produces, on both `SettingsStore` and `StepRepository`:
  - `val heightCm: Flow<Int?>` and `val weightKg: Flow<Int?>`: null when missing or outside the allowed range.
  - `val bodySize: Flow<BodySize?>` = `BodySize.of(height, weight)`.
  - `val weekStart: Flow<WeekStart>`: `PHONE_DEFAULT` when missing or unknown.
  - `suspend fun setHeightCm(value: Int?)` and `suspend fun setWeightKg(value: Int?)`: null removes the key.
  - `suspend fun setWeekStart(value: WeekStart)`.
- Preference keys: `height_cm` (int), `weight_kg` (int), `week_start` (string, the enum name).

- [ ] **Step 1: Write the failing tests** (in `StepRepositoryTest`, same setup as the existing settings tests)

- `heightAndWeightStartUnsetAndRoundTrip`: both flows start null and `bodySize` is null. After `setHeightCm(178)` and `setWeightKg(72)`, the flows give 178 and 72, and `bodySize` gives `BodySize(178, 72)`. After `setWeightKg(null)`, `weightKg` and `bodySize` are null.
- `outOfRangeStoredValuesCountAsUnset`: `setHeightCm(999)` → `heightCm.first() == null`.
- `weekStartDefaultsToPhoneAndRoundTrips`: `weekStart.first() == PHONE_DEFAULT`; after `setWeekStart(SUNDAY)` it is `SUNDAY`.
- `unknownWeekStartFallsBackToPhoneDefault`: write `"TUESDAY"` straight into the test DataStore under `week_start` → `weekStart.first() == PHONE_DEFAULT`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.data.StepRepositoryTest"`
Expected: compile failure on `setHeightCm`.

- [ ] **Step 3: Implement the flows and setters in `SettingsStore`, with pass-throughs in `StepRepository`**

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.data.StepRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/pedometer/data app/src/test/java/app/pedometer/data
git commit -m "Store height, weight and first day of the week"
```

---

### Task 3: Settings rows and dialogs

**Files:**
- Modify: `app/src/main/java/app/pedometer/ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `app/src/main/java/app/pedometer/ui/Formatting.kt`, `strings.xml`
- Create: `app/src/main/java/app/pedometer/ui/settings/MeasureDialog.kt`, `app/src/main/java/app/pedometer/ui/settings/WeekStartDialog.kt`
- Test: `app/src/test/java/app/pedometer/ui/settings/SettingsUiStateTest.kt`

**Interfaces:**
- Consumes: Task 2's repository flows and setters; `BodySize.parseMeasure` and its ranges (Task 1).
- Produces:
  - `fun dayName(day: DayOfWeek, locale: Locale): String` in `Formatting.kt` (full name, capitalised, e.g. "Monday").
  - `SettingsUiState` gains `heightCm: Int?`, `weightKg: Int?`, `weekStart: WeekStart` and `phoneDefaultDay: String` (`dayName(firstDayOfWeek(locale), locale)`).
  - `settingsUiState(goal, showStepCount, goalAlertEnabled, heightCm, weightKg, weekStart, locale)`.
  - `SettingsViewModel.setHeightCm(Int?)`, `setWeightKg(Int?)`, `setWeekStart(WeekStart)`.
  - `@Composable fun MeasureDialog(title: String, label: String, error: String, initial: Int?, range: IntRange, onSave: (Int?) -> Unit, onDismiss: () -> Unit)`. Blank input calls `onSave(null)`. Otherwise it saves `parseMeasure(text, range)`, or shows `error` when that returns null. Same structure as `GoalDialog`.
  - `@Composable fun WeekStartDialog(selected: WeekStart, phoneDefaultDay: String, onSelect: (WeekStart) -> Unit, onDismiss: () -> Unit)`. Three radio rows: Phone default (phoneDefaultDay), then `dayName(MONDAY)`, then `dayName(SUNDAY)`. Choosing a row calls `onSelect` and the screen closes the dialog.

Strings (exact):

```xml
<string name="settings_section_you">You</string>
<string name="settings_height">Height</string>
<string name="settings_weight">Weight</string>
<string name="settings_not_set">Not set</string>
<string name="settings_height_value">%1$d cm</string>
<string name="settings_weight_value">%1$d kg</string>
<string name="settings_height_field">Height in cm</string>
<string name="settings_weight_field">Weight in kg</string>
<string name="settings_height_error">Enter 100 to 250 cm, or leave it empty</string>
<string name="settings_weight_error">Enter 30 to 300 kg, or leave it empty</string>
<string name="settings_section_week">Week</string>
<string name="settings_week_start">First day of the week</string>
<string name="settings_week_phone_default">Phone default (%1$s)</string>
```

Layout: "You" and "Week" sections go between Goal and Notifications. Each row matches the existing goal row (title, then the value in `onSurfaceVariant`, clickable): Height → `settings_height_value` or `settings_not_set`; Weight likewise; First day of the week → `settings_week_phone_default(phoneDefaultDay)` for `PHONE_DEFAULT`, otherwise the day name. Reuse the goal dialog's Save / Cancel strings.

- [ ] **Step 1: Write the failing tests** (in `SettingsUiStateTest`, `Locale.US`)

- `bodyValuesPassThrough`: with height 178, weight null and `SUNDAY` → `heightCm == 178`, `weightKg == null`, `weekStart == SUNDAY`.
- `phoneDefaultDayIsNamed`: `Locale.US` → `phoneDefaultDay == "Sunday"`; `Locale.UK` → `"Monday"`.
- Update the existing tests' `settingsUiState` calls to pass `null, null, WeekStart.PHONE_DEFAULT`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.settings.*"`
Expected: compile failure on the new `settingsUiState` parameters.

- [ ] **Step 3: Implement the state, view model, dialogs, rows and strings as described above**

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.settings.*" --tests "app.pedometer.ui.AppUiTest"`
Expected: PASS. `AppUiTest`'s Settings test still finds "Daily goal" and the switches.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add height, weight and first day of the week to Settings"
```

---

### Task 4: Use the chosen week start everywhere

**Files:**
- Modify: `app/src/main/java/app/pedometer/ui/today/TodayViewModel.kt`, `app/src/main/java/app/pedometer/ui/scores/ScoresViewModel.kt`, `app/src/main/java/app/pedometer/widget/PedometerWidget.kt`, `app/src/main/java/app/pedometer/counting/StepEffects.kt`
- Test: `app/src/test/java/app/pedometer/counting/StepEffectsTest.kt`, `app/src/test/java/app/pedometer/ui/AppUiTest.kt`

**Interfaces:**
- Consumes: `StepRepository.weekStart` (Task 2); `WeekStart.resolve` (Task 1); `firstDayOfWeek(locale)`.
- Produces: no new API. Wherever `firstDayOfWeek(locale)` fed a week, it becomes `weekStart.resolve(firstDayOfWeek(locale))`:
  - The Today and Scores view models add `repository.weekStart` to their `combine`.
  - The widget reads `repository.weekStart.first()` up front and `collectAsState` inside `provideContent`, like `days` and `goal`.
  - `StepEffects` adds `repository.weekStart` to the goal / goal-alert settings observer, so a change redraws the widget.

- [ ] **Step 1: Write the failing tests**

- `StepEffectsTest.weekStartChangeRedrawsTheWidget`: record `widgetRefreshes.get()`, call `repo.setWeekStart(WeekStart.SUNDAY)`, then wait up to 5 s for the count to rise. It must rise.
- `AppUiTest.chosenWeekStartShapesTheScoresWeek`: grant activity access and call `runBlocking { app.repository.setWeekStart(WeekStart.MONDAY) }`, then launch the app, dismiss the battery prompt and open Scores. `waitForText(formatPeriodLabel(StepChart.periodContaining(ChartRange.WEEK, today, DayOfWeek.MONDAY), Locale.getDefault()))`, where `today = currentLocalDate()`. Robolectric's default locale is en-US, whose weeks start on Sunday, so Monday differs from the phone default.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.counting.StepEffectsTest" --tests "app.pedometer.ui.AppUiTest"`
Expected: both time out. A Sunday-start week and a Monday-start week containing the same day always start on different dates, so the label differs on every day of the week.

- [ ] **Step 3: Wire `weekStart` into the four places listed above**

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.counting.StepEffectsTest" --tests "app.pedometer.ui.AppUiTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Use the chosen first day of the week everywhere"
```

---

### Task 5: Calories and distance on Today and Scores

**Files:**
- Modify: `app/src/main/java/app/pedometer/ui/today/TodayUiState.kt`, `TodayViewModel.kt`, `TodayScreen.kt`, `app/src/main/java/app/pedometer/ui/AppRoot.kt`, `app/src/main/java/app/pedometer/ui/scores/ChartUi.kt`, `ScoresUiState.kt`, `ScoresViewModel.kt`, `ScoresScreen.kt`, `app/src/main/java/app/pedometer/ui/Formatting.kt`, `strings.xml`
- Test: `app/src/test/java/app/pedometer/ui/today/TodayUiStateTest.kt`, `app/src/test/java/app/pedometer/ui/scores/ChartUiTest.kt`, `ScoresContentTest.kt`, `app/src/test/java/app/pedometer/ui/AppUiTest.kt`

**Interfaces:**
- Consumes: `Walking.estimate`, `BodySize` (Task 1); `StepRepository.bodySize` (Task 2).
- Produces:
  - `fun formatKm(km: Double, locale: Locale): String` in `Formatting.kt` (exactly one decimal, e.g. "7.4").
  - `data class WalkUi(val kcalText: String, val kmText: String)`; `TodayUiState` gains `walk: WalkUi?` (null without a full `BodySize`).
  - `todayUiState(days, goal, today, firstDayOfWeek, locale, body: BodySize?)`.
  - `TodayScreen(onOpenSettings: () -> Unit, viewModel = …)`. `AppRoot` passes a lambda that navigates to the Settings tab with the same options as a bottom-bar tap.
  - `ChartUiState` gains `kcalText: String?` (the period total). `SelectedDay` gains `kcalText: String?`.
  - `chartUiState(data, selected, locale, body: BodySize?)`; `scoresUiState(…, selected, body: BodySize?)` passes it through. `ScoresViewModel` adds `repository.bodySize` to its `combine`.

Strings (exact):

```xml
<string name="today_walk">%1$s kcal · %2$s km</string>
<string name="today_add_body">Add your height and weight to see calories</string>
<string name="chart_summary_kcal">%1$s total · %2$s a day · %3$s kcal</string>
<string name="chart_selected_day_kcal">%1$s · %2$s steps · %3$s kcal</string>
```

Today: under the goal row, a `bodyMedium` line `today_walk(kcalText, kmText)`. When `walk == null`, show instead a `TextButton` with `today_add_body` that calls `onOpenSettings`. Scores: use the `_kcal` strings when `kcalText` is not null, otherwise the existing ones. The chart's screen-reader description stays as it is.

- [ ] **Step 1: Write the failing tests**

- `TodayUiStateTest.walkShowsWithFullBodySize`: 10,000 steps today, `BodySize(178, 72)`, `Locale.US` → `walk == WalkUi("266", "7.4")`.
- `TodayUiStateTest.noWalkWithoutBodySize`: `body = null` → `walk == null`.
- `TodayUiStateTest.kmUsesTheLocalesDecimalSeparator`: same data with `Locale("pl", "PL")` → `walk?.kmText == "7,4"`.
- `ChartUiTest.kcalForThePeriodAndTheSelectedDay`: the current week of `november` with `BodySize(178, 72)` and selected `2026-11-10` → `kcalText == "470"` and `selected?.kcalText == "172"`; with `body = null`, both are null.
- `ScoresContentTest.summaryIncludesCaloriesWithABodySize`: `scoresUiState(…, body = BodySize(178, 72))` → `"17,686 total · 8,843 a day · 470 kcal"` is displayed; with selected `2026-11-10` → `"Tue 10 Nov · 6,482 steps · 172 kcal"`.
- `AppUiTest.recordedStepsAppearOnAllThreeScreens`: after the Today checks, assert `"Add your height and weight to see calories"` is displayed. Then `runBlocking { app.repository.setHeightCm(178); app.repository.setWeightKg(72) }` and `waitForText("342 kcal · 9.5 km")` (12,870 steps).
- `AppUiTest.theCaloriesLinkOpensSettings`: grant access, launch, dismiss the battery prompt, click `"Add your height and weight to see calories"`, then `waitForText("Daily goal")`.
- Update existing `todayUiState`, `chartUiState` and `scoresUiState` calls in tests to pass `null` for `body`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.*"`
Expected: compile failure on `WalkUi` and the new parameters.

- [ ] **Step 3: Implement the Today and Scores changes as described above**

- [ ] **Step 4: Run the full suite, lint and build**

Run: `./gradlew :core:test :app:testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL, no test failures, and no `severity="Error"` in `app/build/reports/lint-results-debug.xml`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Show walking calories and distance on Today and Scores"
```
