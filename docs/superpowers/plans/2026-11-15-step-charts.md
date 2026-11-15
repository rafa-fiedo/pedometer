# Step Charts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Week, month and year step charts at the top of the Scores tab, replacing its Totals section.

**Architecture:** The period maths and chart numbers are pure Kotlin in `:core` (`StepChart`). The app maps them to display text (`ChartUi.kt`), draws them with a Compose `Canvas` (`StepChartView.kt`), and the Scores view model holds the chosen view, how many periods back each view is, and the selected day.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (Material3 from BOM 2024.12.01), kotlinx-datetime 0.6.2, JUnit 4, Robolectric 4.14.1, compose ui-test-junit4.

**Spec:** `docs/superpowers/specs/2026-11-15-step-charts-design.md`

## Global Constraints

- No chart library. Drawing is Compose `Canvas`. The only new dependency is `androidx.compose.ui:ui-test-manifest` as `debugImplementation` (test hosting only).
- minSdk 29, compileSdk/targetSdk 35; run Gradle as `./gradlew` from the repo root.
- Every user-visible string lives in `app/src/main/res/values/strings.xml`.
- Dates are formatted with `java.time` patterns and the phone's `Locale`, like `ui/Formatting.kt`.
- The week starts on `firstDayOfWeek(locale)` (the phone's first day of the week), the same as the existing "This week" total.
- Colours come from `MaterialTheme.colorScheme`: bars that reached the goal `primary`, the others `surfaceVariant`; year line `primary`; goal line `onSurfaceVariant`, dashed.
- Past days are judged against the current goal.
- The Scores view opens on Week, at the current period. Each view keeps its own position while the app is open; nothing is saved after it closes.

## Review Focus

- A week that crosses a month or a year end: correct 7 days and a label naming both months (and both years). Tests in Task 1 and Task 2.
- Leap years: February 2028 has 29 days and 2028 has 366 points. Tests in Task 1.
- No steps recorded at all: no division by zero, average 0, both arrows disabled. Tests in Task 1 and Task 4.
- Rows dated after today (phone clock set back): treated as future days, left out of totals. Test in Task 1.
- A phone whose week starts on Sunday: week begins on Sunday and its axis starts with "S". Tests in Task 1 and Task 2.

---

### Task 1: Chart periods and numbers in `:core`

**Files:**
- Create: `core/src/main/kotlin/app/pedometer/core/StepChart.kt`
- Test: `core/src/test/kotlin/app/pedometer/core/StepChartTest.kt`

**Interfaces:**
- Consumes: `DayCount(date: LocalDate, steps: Int)` from `StepStats.kt`.
- Produces:
  - `enum class ChartRange { WEEK, MONTH, YEAR }`
  - `data class ChartPeriod(val range: ChartRange, val start: LocalDate, val endInclusive: LocalDate)`
  - `data class ChartDay(val date: LocalDate, val steps: Int?)` where `steps == null` means a future day
  - `data class ChartData(val period: ChartPeriod, val days: List<ChartDay>, val goal: Int, val total: Int, val averagePerDay: Int, val scaleMax: Int, val canGoBack: Boolean, val canGoForward: Boolean)`
  - `object StepChart` with:
    - `fun periodContaining(range: ChartRange, date: LocalDate, firstDayOfWeek: DayOfWeek): ChartPeriod`
    - `fun shift(period: ChartPeriod, by: Int, firstDayOfWeek: DayOfWeek): ChartPeriod` (`by` = whole periods, negative = back)
    - `fun chart(days: List<DayCount>, period: ChartPeriod, today: LocalDate, goal: Int): ChartData`

Rules `chart` must follow (from the spec):
- `days` has one entry for every date from `period.start` to `period.endInclusive`.
- Dates after `today` get `steps = null`, even if a row exists for them. Rows for the same date are summed. Past dates with no row get 0.
- `total` = the sum over non-null days.
- `averagePerDay` = `total` ÷ the number of days in the period that are `<= today` and `>=` the first counted day (the earliest row dated `<= today`), rounded half up. It is 0 when that count is 0.
- `scaleMax = (maxOf(goal, bestDayInPeriod) * 11 + 9) / 10` (10% headroom, rounded up).
- `canGoBack` = a first counted day exists and is `< period.start`.
- `canGoForward` = `period.endInclusive < today`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class StepChartTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    private val today = d("2026-11-10") // a Tuesday
    private val november = listOf( // same data as StepStatsTest
        DayCount(d("2026-11-04"), 7_395), DayCount(d("2026-11-05"), 9_640),
        DayCount(d("2026-11-06"), 8_115), DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-08"), 4_310), DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )
    private fun week(date: String, first: DayOfWeek = DayOfWeek.MONDAY) =
        StepChart.periodContaining(ChartRange.WEEK, d(date), first)

    @Test fun weekStartsOnThePhonesFirstDay() {
        assertEquals(ChartPeriod(ChartRange.WEEK, d("2026-11-09"), d("2026-11-15")), week("2026-11-10"))
        assertEquals(ChartPeriod(ChartRange.WEEK, d("2026-11-08"), d("2026-11-14")), week("2026-11-10", DayOfWeek.SUNDAY))
    }
    @Test fun weekAcrossTheYearEnd() =
        assertEquals(ChartPeriod(ChartRange.WEEK, d("2026-12-28"), d("2027-01-03")), week("2027-01-01"))
    @Test fun monthRunsFromTheFirstToTheLastDay() {
        assertEquals(d("2026-02-28"), StepChart.periodContaining(ChartRange.MONTH, d("2026-02-14"), DayOfWeek.MONDAY).endInclusive)
        assertEquals(ChartPeriod(ChartRange.MONTH, d("2028-02-01"), d("2028-02-29")),
            StepChart.periodContaining(ChartRange.MONTH, d("2028-02-10"), DayOfWeek.MONDAY))
    }
    @Test fun leapYearHas366Days() {
        val year = StepChart.periodContaining(ChartRange.YEAR, d("2028-06-01"), DayOfWeek.MONDAY)
        assertEquals(ChartPeriod(ChartRange.YEAR, d("2028-01-01"), d("2028-12-31")), year)
        assertEquals(366, StepChart.chart(emptyList(), year, d("2028-06-01"), 10_000).days.size)
    }
    @Test fun shiftMovesWholePeriods() {
        val m = DayOfWeek.MONDAY
        assertEquals(week("2026-11-02"), StepChart.shift(week("2026-11-10"), -1, m))
        assertEquals(ChartPeriod(ChartRange.MONTH, d("2026-10-01"), d("2026-10-31")),
            StepChart.shift(StepChart.periodContaining(ChartRange.MONTH, today, m), -1, m))
        assertEquals(ChartPeriod(ChartRange.YEAR, d("2027-01-01"), d("2027-12-31")),
            StepChart.shift(StepChart.periodContaining(ChartRange.YEAR, today, m), 1, m))
    }
    @Test fun currentWeek() {
        val c = StepChart.chart(november, week("2026-11-10"), today, 10_000)
        assertEquals(listOf(11_204, 6_482, null, null, null, null, null), c.days.map { it.steps })
        assertEquals(17_686, c.total)
        assertEquals(8_843, c.averagePerDay)  // two days so far
        assertEquals(12_325, c.scaleMax)      // 11,204 + 10%, rounded up
        assertTrue(c.canGoBack); assertFalse(c.canGoForward)
    }
    @Test fun pastWeekAveragesOnlyFromTheFirstCountedDay() {
        val c = StepChart.chart(november, week("2026-11-02"), today, 10_000)
        assertEquals(listOf(0, 0, 7_395, 9_640, 8_115, 12_870, 4_310), c.days.map { it.steps })
        assertEquals(8_466, c.averagePerDay)  // 42,330 over 5 days
        assertFalse(c.canGoBack); assertTrue(c.canGoForward)
    }
    @Test fun monthAverageRoundsHalfUp() {
        val c = StepChart.chart(november, StepChart.periodContaining(ChartRange.MONTH, today, DayOfWeek.MONDAY), today, 10_000)
        assertEquals(30, c.days.size)
        assertEquals(60_016, c.total)
        assertEquals(8_574, c.averagePerDay)  // 8,573.7
        assertFalse(c.canGoBack)
    }
    @Test fun nothingRecorded() {
        val c = StepChart.chart(emptyList(), week("2026-11-10"), today, 10_000)
        assertEquals(listOf(0, 0, null, null, null, null, null), c.days.map { it.steps })
        assertEquals(0, c.total); assertEquals(0, c.averagePerDay); assertEquals(11_000, c.scaleMax)
        assertFalse(c.canGoBack); assertFalse(c.canGoForward)
    }
    @Test fun rowsAfterTodayCountAsFuture() {
        val c = StepChart.chart(november + DayCount(d("2026-11-12"), 5_000), week("2026-11-10"), today, 10_000)
        assertNull(c.days[3].steps)
        assertEquals(17_686, c.total)
    }
    @Test fun rowsForTheSameDayAreAdded() {
        val c = StepChart.chart(listOf(DayCount(today, 100), DayCount(today, 50)), week("2026-11-10"), today, 10_000)
        assertEquals(150, c.days[1].steps)
    }
}
```

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :core:test --tests "app.pedometer.core.StepChartTest"`
Expected: compile failure, `Unresolved reference: StepChart`.

- [ ] **Step 3: Implement `StepChart.kt` with the interfaces and rules above**

Week start uses the same offset formula as `StepStats.summarize`. For the month end, use `start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)`.

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL. The existing core tests still pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/app/pedometer/core/StepChart.kt core/src/test/kotlin/app/pedometer/core/StepChartTest.kt
git commit -m "Add chart periods and numbers"
```

---

### Task 2: Chart display text and the new Scores state

**Files:**
- Create: `app/src/main/java/app/pedometer/ui/scores/ChartUi.kt`
- Modify: `app/src/main/java/app/pedometer/ui/scores/ScoresUiState.kt` (replace the three total fields with `chart`)
- Test: `app/src/test/java/app/pedometer/ui/scores/ChartUiTest.kt`, `app/src/test/java/app/pedometer/ui/scores/ScoresUiStateTest.kt` (rewrite)

**Interfaces:**
- Consumes: everything Task 1 produces; `formatSteps`, `formatMonthTitle`, `formatRecordDate`, `RecordUi` from the existing UI code.
- Produces:
  - `data class ChartBar(val date: LocalDate, val steps: Int?, val reachedGoal: Boolean)`
  - `data class AxisLabel(val index: Int, val text: String)` (`index` = position in `bars`)
  - `data class SelectedDay(val date: LocalDate, val dateText: String, val stepsText: String)`
  - `data class ChartUiState(val range: ChartRange, val periodLabel: String, val bars: List<ChartBar>, val axisLabels: List<AxisLabel>, val goal: Int, val goalText: String, val scaleMax: Int, val totalText: String, val averageText: String, val selected: SelectedDay?, val canGoBack: Boolean, val canGoForward: Boolean)`
  - `fun chartUiState(data: ChartData, selected: LocalDate?, locale: Locale): ChartUiState`
  - `fun formatPeriodLabel(period: ChartPeriod, locale: Locale): String`
  - `fun dayIndexAt(x: Float, width: Float, count: Int): Int` returns `floor(x / width * count)`, clamped to `0..count-1`
  - `data class ScoresUiState(val chart: ChartUiState, val bestThisMonth: RecordUi?, val bestThisYear: RecordUi?)`
  - `fun scoresUiState(days: List<DayCount>, today: LocalDate, goal: Int, firstDayOfWeek: DayOfWeek, locale: Locale, range: ChartRange, periodsBack: Int, selected: LocalDate?): ScoresUiState`. The period is `StepChart.shift(StepChart.periodContaining(range, today, firstDayOfWeek), -periodsBack, firstDayOfWeek)`.

Formats (these values are pinned by the tests below):
- Period label: week in one month `"d–d MMM"` (en dash), across months `"d MMM – d MMM"`, across years `"d MMM yyyy – d MMM yyyy"`; month uses `formatMonthTitle`; year is `"yyyy"`.
- Axis: week = every day, narrow weekday `"EEEEE"`; month = days 1, 10, 20 and the last day; year = the 1st of each month, narrow month `"MMMMM"`.
- `selected`: pattern `"EEE d MMM"`. It is null when the date is outside the period or is a future day.
- `reachedGoal` = `steps != null && steps >= goal`.

- [ ] **Step 1: Write the failing tests**

`ChartUiTest` (all `Locale.US`, data = the `november` list from Task 1, today `2026-11-10`, goal 10,000):
- `weekLabelAndAxis`: the current Monday week → `periodLabel == "9–15 Nov"`, axis texts `["M","T","W","T","F","S","S"]` at indexes 0..6, `totalText "17,686"`, `averageText "8,843"`, `goalText "10,000"`, `bars[0].reachedGoal == true`, `bars[1].reachedGoal == false`.
- `sundayWeekAxisStartsWithS`: the `SUNDAY` week → the first axis text is `"S"` and `periodLabel == "8–14 Nov"`.
- `labelsAcrossMonthsAndYears`: `formatPeriodLabel` on week `2026-11-30..2026-12-06` → `"30 Nov – 6 Dec"`; on week `2026-12-28..2027-01-03` → `"28 Dec 2026 – 3 Jan 2027"`; on month Nov 2026 → `"November 2026"`; on year 2026 → `"2026"`.
- `monthAxisMarksTheLastDay`: Nov 2026 axis → `[(0,"1"),(9,"10"),(19,"20"),(29,"30")]`; Feb 2026 → last label `(27,"28")`.
- `yearAxisHasMonthInitials`: year 2026 → 12 labels; the texts joined are `"JFMAMJJASOND"`; the labels at positions 1 and 2 have indexes 31 and 59.
- `selectedDayText`: selected `2026-11-10` → `SelectedDay(d("2026-11-10"), "Tue 10 Nov", "6,482")`; selected `2026-11-12` (future) → null; selected `2026-10-01` (outside the period) → null.
- `dayIndexAtClamps`: `dayIndexAt(0f,700f,7)==0`, `dayIndexAt(350f,700f,7)==3`, `dayIndexAt(699.9f,700f,7)==6`, `dayIndexAt(-5f,700f,7)==0`, `dayIndexAt(800f,700f,7)==6`.

`ScoresUiStateTest` (rewrite):
- `mapsChartAndRecords`: the existing `days` list from the current test, today `2026-11-10`, `WEEK`, `periodsBack = 0` → `chart.totalText == "17,686"`, records unchanged (`RecordUi("12,870","Sat 7 November")`, `RecordUi("18,342","Sun 14 June")`).
- `periodsBackMovesTheChart`: `MONTH`, `periodsBack = 1` → `chart.periodLabel == "October 2026"`.
- `noRecordsYet`: an empty list → `chart.totalText == "0"`, both records null.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.scores.*"`
Expected: compile failure on `chartUiState`, `ChartRange` and the new `scoresUiState` parameters.

- [ ] **Step 3: Implement `ChartUi.kt` and the new `ScoresUiState` / `scoresUiState`**

`ScoresViewModel` and `ScoresScreen` will not compile until Task 4. In this task, change `ScoresViewModel` to pass `goal` (combine with `repository.goal`), `ChartRange.WEEK`, `0` and `null`. In `ScoresScreen`, delete the Totals rows and `TotalRow` so the app builds; Task 4 adds the chart.

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.scores.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/pedometer/ui/scores app/src/test/java/app/pedometer/ui/scores
git commit -m "Map chart numbers to display text"
```

---

### Task 3: The chart drawing

**Files:**
- Create: `app/src/main/java/app/pedometer/ui/scores/StepChartView.kt`
- Modify: `gradle/libs.versions.toml` (add `androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }`), `app/build.gradle.kts` (`debugImplementation(libs.androidx.compose.ui.test.manifest)`)
- Test: `app/src/test/java/app/pedometer/ui/scores/StepChartViewTest.kt`

**Interfaces:**
- Consumes: `ChartUiState`, `ChartBar`, `AxisLabel`, `dayIndexAt` from Task 2.
- Produces: `@Composable fun StepChart(chart: ChartUiState, description: String, onSelect: (LocalDate?) -> Unit, modifier: Modifier = Modifier)` and `const val STEP_CHART_TAG = "step-chart"`.

Behaviour:
- The tap target is a `Canvas` 160.dp tall, filling the width, with `testTag(STEP_CHART_TAG)` and `semantics { contentDescription = description }`.
- Day `i` occupies the slot `[i*w/n, (i+1)*w/n)`.
- Week and month draw one bar per non-null day. A bar is 60% of its slot (week) or 70% (month), rounded at the top. Height is `steps / scaleMax`, and a day with 0 steps draws no bar.
- Year draws a 1.5.dp `Path` through the slot centres of the non-null days.
- The goal line is dashed at `goal / scaleMax`, with `goalText` drawn at its right end using `rememberTextMeasurer`.
- Axis labels are a `Box` under the canvas. Each `Text` is placed at its slot centre.
- A tap uses `detectTapGestures`. The tapped day is `bars[dayIndexAt(x, width, n)]`. If its `steps` is null, nothing happens. If it equals `chart.selected?.date`, call `onSelect(null)`, otherwise `onSelect(date)`.

- [ ] **Step 1: Write the failing tests**

Use Robolectric, `@Config(sdk = [34])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and `createComposeRule()`. Build the states with `chartUiState` from Task 2, using Task 1's `november` data and today `2026-11-10`.
- `tappingABarSelectsThatDay`: on the current week, `onNodeWithTag(STEP_CHART_TAG).performTouchInput { click(Offset(width * 1.5f / 7, centerY)) }` → the captured value is `2026-11-10`.
- `tappingAFutureDayDoesNothing`: click at `width * 5.5f / 7` → `onSelect` is never called.
- `tappingTheSelectedDayClearsIt`: with the state's selected day `2026-11-10`, the same click as the first test → the captured value is `null`.
- `yearTapPicksTheNearestDay`: on year 2026, click at `width * 9.5f / 365` → `2026-01-10`.
- `describedForScreenReaders`: `onNodeWithContentDescription("Week: 17,686 total, 8,843 a day").assertExists()` when that string is passed as `description`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.scores.StepChartViewTest"`
Expected: compile failure, `Unresolved reference: StepChart`.

- [ ] **Step 3: Add the manifest dependency and implement `StepChartView.kt` as described above**

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.scores.StepChartViewTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/app/pedometer/ui/scores/StepChartView.kt app/src/test/java/app/pedometer/ui/scores/StepChartViewTest.kt
git commit -m "Draw the step chart and select a day by tapping"
```

---

### Task 4: The Scores tab with the chart

**Files:**
- Modify: `app/src/main/java/app/pedometer/ui/scores/ScoresScreen.kt`, `ScoresViewModel.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/app/pedometer/ui/scores/ScoresContentTest.kt`, `app/src/test/java/app/pedometer/ui/AppUiTest.kt` (the Scores part of `recordedStepsAppearOnAllThreeScreens`)

**Interfaces:**
- Consumes: `scoresUiState`, `ScoresUiState` (Task 2); `StepChart` (Task 3).
- Produces:
  - `@Composable fun ScoresContent(state: ScoresUiState, onRange: (ChartRange) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSelect: (LocalDate?) -> Unit)`
  - `ScoresViewModel` methods `selectRange(range: ChartRange)`, `previous()`, `next()`, `select(date: LocalDate?)`

Strings to add (exact):

```xml
<string name="chart_week">Week</string>
<string name="chart_month">Month</string>
<string name="chart_year">Year</string>
<string name="chart_previous">Previous period</string>
<string name="chart_next">Next period</string>
<string name="chart_summary">%1$s total · %2$s a day</string>
<string name="chart_selected_day">%1$s · %2$s steps</string>
<string name="chart_description">%1$s: %2$s total, %3$s a day</string>
```

Remove `scores_totals` and `total_today`. Keep `total_this_week` and `total_this_month`, which the Today tab still uses.

Layout, top to bottom:
1. The "Scores" title.
2. A `SingleChoiceSegmentedButtonRow` with Week / Month / Year.
3. A row: an `IconButton` with `Icons.AutoMirrored.Filled.KeyboardArrowLeft` (content description `chart_previous`, `enabled = canGoBack`), then `periodLabel` centred, then the right arrow (content description `chart_next`, `enabled = canGoForward`).
4. `StepChart`, with `description = chart_description(periodLabel, totalText, averageText)`.
5. The summary line: `chart_selected_day(dateText, stepsText)` when a day is selected, otherwise `chart_summary(totalText, averageText)`.
6. The Records section, unchanged.

View model: it holds `MutableStateFlow` of `range` (starts at `WEEK`), `periodsBack: Map<ChartRange, Int>` (missing = 0) and `selected: LocalDate?`. It combines these with `repository.days`, `repository.goal` and `todaySource.today` into `scoresUiState`.
- `previous()` acts only when `state.value?.chart?.canGoBack == true`, and `next()` only when `canGoForward`.
- `selectRange`, `previous` and `next` clear `selected`.

- [ ] **Step 1: Write the failing tests**

`ScoresContentTest` (Robolectric, `createComposeRule`; the state comes from `scoresUiState` with Task 1's `november` data, today `2026-11-10`, `Locale.US`):
- `showsTheChartSummaryAndRecords`: `"9–15 Nov"`, `"17,686 total · 8,843 a day"` and `"Best day this month"` are displayed; there is no node with the text `"Totals"`.
- `aSelectedDayReplacesTheSummary`: with selected `2026-11-10`, `"Tue 10 Nov · 6,482 steps"` is displayed and `"17,686 total · 8,843 a day"` does not exist.
- `controlsCallBack`: clicking `"Month"` → `onRange(MONTH)`; clicking the `"Previous period"` node → `onPrevious` is called once; the `"Next period"` node `assertIsNotEnabled()`.

`AppUiTest.recordedStepsAppearOnAllThreeScreens`: replace the final `12,870` count check with:
- `"Best day this year"` is displayed;
- at least 2 nodes show `"12,870"`;
- clicking `"Month"` then `waitForText(formatMonthTitle(today, Locale.getDefault()))`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.ui.scores.ScoresContentTest" --tests "app.pedometer.ui.AppUiTest"`
Expected: compile failure, `Unresolved reference: ScoresContent`.

- [ ] **Step 3: Implement the strings, `ScoresContent`, the stateful `ScoresScreen` wrapper and the view model as described above**

- [ ] **Step 4: Run the full suite, lint and build**

Run: `./gradlew :core:test :app:testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL, no test failures, and no `severity="Error"` entries in `app/build/reports/lint-results-debug.xml`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Show week, month and year charts on the Scores tab"
```
