# Round Widget and Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A refresh-on-tap for the widgets (arrow on 2 × 1 to 4 × 1, the whole round widget) and a new round 1 × 1 widget with a goal ring on a transparent background.

**Architecture:** One refresh routine, shared by a Glance `ActionCallback`, reads the sensor once and redraws every widget through the existing `StepEffects.refreshNow()`. The round widget is a second `GlanceAppWidget` with its own receiver and provider XML. Its circle and ring are drawn into a `Bitmap` with Android's `Canvas` (Glance has no progress ring), and the footprints and count are Glance views on top.

**Tech Stack:** Kotlin 2.1.20, Glance 1.1.1 (+ glance-appwidget-testing), Robolectric 4.14.1, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-17-round-widget-and-refresh-design.md`

## Global Constraints

- A refresh waits at most 5 seconds for a sensor reading, then always redraws every widget (both widget classes).
- Without activity permission, a refresh only redraws.
- The round widget: picker name "Steps round 1 × 1", starts at 1 × 1, not resizable. Transparent outside the circle. Circle 56dp, or the cell's smaller side if that is less. Ring starts at the top and goes clockwise, full at or past the goal.
- Six-digit counts (text longer than 5 characters, e.g. "12,870") use a smaller font in the circle: 10sp instead of 12sp.
- The square 1 × 1 layout keeps opening the app and gets no arrow.
- Every user-visible string goes in `strings.xml`. Run Gradle as `./gradlew`; the suite is `:core:test :app:testDebugUnitTest`.

## Review Focus

- A refresh while the phone gives no sensor reading: the widgets must still redraw (Task 1 test).
- Progress at 0 and at or above 1: an empty ring and a full ring, no overdraw or gap (Task 3 test).
- The `StepEffects` redraw reaches the round widget too, not only the rectangular one. Glance's `updateAll` can't be observed in a JVM test, so this rests on `PedometerApp` calling `updateAllWidgets` (code review), plus a check on the phone.
- Existing placed widgets: the four existing receivers and their XML are unchanged, so placed widgets keep working (Task 3 test).
- Dark mode: the ring and circle colours come from the widget theme, not fixed colours (code review; the colours are passed in, as covered by the Task 3 bitmap test).

---

### Task 1: The refresh routine and action

**Files:**
- Create: `app/src/main/java/app/pedometer/widget/WidgetRefresh.kt`
- Test: `app/src/test/java/app/pedometer/widget/WidgetRefreshTest.kt`

**Interfaces:**
- Produces:
  - `suspend fun refreshSteps(canCount: Boolean, recordOnce: suspend () -> Boolean, redraw: suspend () -> Unit)`. When `canCount` is true it calls `recordOnce()` first, ignoring the result, and then always calls `redraw()`. An exception from `recordOnce` (other than cancellation) is logged and the redraw still happens.
  - `class RefreshWidgetAction : ActionCallback`. `onAction` calls `refreshSteps(StepCounterService.canCount(app), { app.recorder.recordOnce(5_000) }, { app.effects.refreshNow() })`, where `app` is the `PedometerApp`.

- [ ] **Step 1: Write the failing tests** (plain JUnit with `runBlocking`, recording calls in a list)
  - `readsThenRedraws`: with `canCount = true` and `recordOnce` returning true, the calls are `["record", "redraw"]`.
  - `redrawsEvenWithoutAReading`: `recordOnce` returns false → the calls are `["record", "redraw"]`.
  - `redrawsWhenReadingFails`: `recordOnce` throws `IllegalStateException` → `redraw` is still called, and `refreshSteps` doesn't throw.
  - `withoutPermissionOnlyRedraws`: `canCount = false` → the calls are `["redraw"]`.
- [ ] **Step 2: Run them and check they fail**
  - Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.widget.WidgetRefreshTest"`
  - Expected: compile failure, `Unresolved reference: refreshSteps`.
- [ ] **Step 3: Implement `WidgetRefresh.kt`**
- [ ] **Step 4: Run them and check they pass**
- [ ] **Step 5: Commit** — `git commit -m "Add a widget refresh routine"`

---

### Task 2: Refresh arrow on the 2 × 1 to 4 × 1 widgets

**Files:**
- Create: `app/src/main/res/drawable/ic_refresh.xml` (the Material "refresh" 24dp vector, white fill, tinted at use)
- Modify: `app/src/main/java/app/pedometer/widget/PedometerWidget.kt` (`WidgetContent`), `strings.xml`
- Test: `app/src/test/java/app/pedometer/widget/WidgetContentTest.kt`

**Interfaces:**
- Consumes: `RefreshWidgetAction` (Task 1).
- Produces: `const val REFRESH_TAG = "refresh"`. In `WidgetContent`, widths TWO to FOUR end with a 20dp `Image(ic_refresh)`. It is tinted `primary`, has the content description `widget_refresh`, carries `GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>())` and `semantics { testTag = REFRESH_TAG }`, and has 8dp spacing before it. Width ONE has no arrow.
- String: `<string name="widget_refresh">Refresh steps</string>`

- [ ] **Step 1: Write the failing tests** (in `WidgetContentTest`)
  - `widerLayoutsHaveARefreshArrow`: for TWO, THREE and FOUR, `onNode(hasTestTag(REFRESH_TAG))` exists and `assertHasRunCallbackClickAction(RefreshWidgetAction::class.java)` passes.
  - `oneCellHasNoRefreshArrow`: for ONE, `onNode(hasTestTag(REFRESH_TAG)).assertDoesNotExist()`.
- [ ] **Step 2: Run them and check they fail**
  - Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.widget.WidgetContentTest"`
  - Expected: compile failure on `REFRESH_TAG`.
- [ ] **Step 3: Add the arrow, the drawable and the string**
- [ ] **Step 4: Run them and check they pass**
- [ ] **Step 5: Commit** — `git commit -m "Add a refresh arrow to the wider widgets"`

---

### Task 3: The round 1 × 1 widget

**Files:**
- Create:
  - `app/src/main/java/app/pedometer/widget/RoundStepsWidget.kt`
  - `app/src/main/java/app/pedometer/widget/GoalRing.kt`
  - `app/src/main/res/xml/pedometer_widget_round.xml`
- Modify: `AndroidManifest.xml`, `strings.xml`, `app/src/main/java/app/pedometer/PedometerApp.kt` (the `refreshWidget` lambda)
- Test:
  - `app/src/test/java/app/pedometer/widget/GoalRingTest.kt`
  - `RoundWidgetContentTest.kt`
  - `WidgetProvidersTest.kt` (extend)

**Interfaces:**
- Consumes: `widgetState` / `WidgetState` (existing), `RefreshWidgetAction` (Task 1), `R.drawable.ic_footprints`.
- Produces:
  - `fun drawGoalRing(sizePx: Int, progress: Float, circleColor: Int, trackColor: Int, ringColor: Int): Bitmap`. An ARGB_8888 bitmap: a filled circle across the whole bitmap, and a ring of stroke `sizePx * 0.07` inset by half the stroke. The track is drawn in full; the ring arc runs from −90° sweeping `360 × progress.coerceIn(0, 1)` with round caps. Nothing is drawn outside the circle, so the corners stay transparent.
  - `fun roundStepsFontSp(stepsText: String): Int` returns 12, or 10 when `stepsText.length > 5`.
  - `@Composable fun RoundWidgetContent(state: WidgetState, ring: Bitmap)`:
    - an outer `Box` filling the cell, centred, clickable with `actionRunCallback<RefreshWidgetAction>()`, with no background;
    - an inner `Box` of size `min(56dp, smaller cell side)` with `Image(ImageProvider(ring))` filling it;
    - a centred `Column` holding the footprints (18dp, tinted `primary`) and the steps `Text` (Medium weight, `onSurface`, `roundStepsFontSp`).
  - `class RoundStepsWidget : GlanceAppWidget` (`SizeMode.Single`). It reads days, goal and week start like `PedometerWidget`, builds the ring bitmap at 56dp × density inside `provideContent` from `GlanceTheme.colors` (`widgetBackground` for the circle, `surfaceVariant` for the track, `primary` for the ring), and renders `RoundWidgetContent`.
  - `class RoundStepsWidgetReceiver : GlanceAppWidgetReceiver`.
  - `PedometerApp`'s `refreshWidget` calls `updateAll` on both `PedometerWidget()` and `RoundStepsWidget()`. Expose this as `suspend fun updateAllWidgets(context: Context)` in `RoundStepsWidget.kt`, so it can be tested.
- Provider XML attributes: `minWidth="40dp"`, `minHeight="40dp"`, `targetCellWidth="1"`, `targetCellHeight="1"`, `resizeMode="none"`, `updatePeriodMillis="0"`, and the same `description` and `initialLayout` as the others.
- String: `<string name="widget_name_round">Steps round 1 × 1</string>`

- [ ] **Step 1: Write the failing tests**
  - `GoalRingTest` (Robolectric, NATIVE graphics). Draw at 100 px with circle white, track gray and ring blue.
    - `halfProgressFillsTheRightHalf`: at 0.5, the pixel at the ring's right middle `(96, 50)` is blue and the left middle `(4, 50)` is gray.
    - `centreIsTheCircleAndCornersAreTransparent`: `(50, 50)` is white; the alpha at `(1, 1)` is 0.
    - `progressIsClamped`: at 1.7, `(4, 50)` is blue (a full ring); at −0.2, `(96, 50)` is gray.
  - `RoundWidgetContentTest` (`runGlanceAppWidgetUnitTest`, size 70 × 70 dp):
    - `showsTodaysSteps`: `hasText("6,482")` exists and `"of 10,000"` does not.
    - `tappingRefreshes`: the root clickable node `assertHasRunCallbackClickAction(RefreshWidgetAction::class.java)`.
    - `sixDigitCountsUseTheSmallerFont`: `roundStepsFontSp("12,870") == 10` and `roundStepsFontSp("6,482") == 12`.
  - `WidgetProvidersTest`:
    - `thePickerListsFourEntries` becomes `thePickerListsFiveEntries`, adding `RoundStepsWidgetReceiver`.
    - New `roundEntryIsAFixedOneByOne`: `targetCellWidth == 1`, `targetCellHeight == 1`, `resizeMode == 0` (none).
    - New `roundReceiverDrawsTheRoundWidget`: `glanceAppWidget is RoundStepsWidget`.
    - The four existing entries stay as they are.
- [ ] **Step 2: Run them and check they fail**
  - Run: `./gradlew :app:testDebugUnitTest --tests "app.pedometer.widget.*"`
  - Expected: compile failure on `drawGoalRing`.
- [ ] **Step 3: Implement the ring, the content, the widget and receiver, the provider XML, the manifest entry, the string, and `updateAllWidgets` (used by `PedometerApp`)**
- [ ] **Step 4: Run the full suite, lint and build**
  - Run: `./gradlew :core:test :app:testDebugUnitTest lintDebug assembleDebug`
  - Expected: BUILD SUCCESSFUL, no failures, no `severity="Error"` in the lint report.
- [ ] **Step 5: Visual check** — render the round widget's ring bitmap and the content to a PNG with a throwaway Robolectric test (not committed), and look at it.
- [ ] **Step 6: Commit** — `git commit -m "Add a round 1x1 widget with a goal ring"`
