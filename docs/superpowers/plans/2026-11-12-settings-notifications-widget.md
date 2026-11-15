# Settings, Notification Choices and Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the owner switch the permanent notification off, be told when the daily goal is reached, edit the goal in a Settings screen, and see the count on a resizable home-screen widget.

**Architecture:** One app-wide `StepRecorder` records every sensor reading in arrival order; the foreground service, a WorkManager catch-up job, the rollover receiver and the visible activity all read through it. `CountingController` puts the app in continuous or periodic mode from the "Show step count" setting. `StepEffects` runs after steps are recorded to refresh a Jetpack Glance widget and send the once-a-day goal notification.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose, Room, DataStore, WorkManager, Jetpack Glance, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-12-settings-notifications-widget-design.md` (builds on `docs/superpowers/specs/2026-11-10-pedometer-design.md`)

## Global Constraints

- Work on the existing branch `settings-widget`; do not commit to `main`.
- All Global Constraints of `docs/superpowers/plans/2026-11-10-pedometer-pal.md` still apply (IDs, SDK levels, bytecode 17, no `INTERNET`, English sentence-case copy, Git Bash commands, no SQLite upsert syntax).
- No new permissions in the app's own manifest. WorkManager's library manifest adds `ACCESS_NETWORK_STATE` to the merged manifest; that is accepted and left in place (it lets the library see whether a network exists and grants no network access; removing it risks a crash inside WorkManager).
- Defaults: "Show step count" on, "Tell me when I reach my goal" off.
- The existing 49 tests keep passing; the Room schema does not change.
- Catch-up interval 15 minutes; one-shot sensor timeout 10 seconds (5 seconds inside the rollover receiver); effects throttle 5 seconds.
- New versions: `androidx.work:work-runtime-ktx` and `work-testing` 2.9.1; `androidx.glance:glance-appwidget`, `glance-material3` and `glance-appwidget-testing` 1.1.1. If one fails to resolve, use the nearest patch release and say so in the commit message.

### Copy

| Where | Text |
|---|---|
| Today gear icon description | "Settings" |
| Settings title; back arrow description | "Settings"; "Back" |
| Section titles | "Goal", "Notifications" |
| Goal row | Title "Daily goal"; subtitle "10,000 steps" (goal with grouping) |
| Step count switch | Title "Show step count"; subtitle "Always-on notification. Counts continuously." |
| Goal alert switch | Title "Tell me when I reach my goal"; subtitle "One notification a day." |
| Note when step count is off | "With the step count off, steps are collected about every 15 minutes and whenever you open the app." |
| Goal notification | Title "Goal reached"; text "You walked 10,000 steps today."; channel name "Goal reached" |
| Widget | Picker name "Pedometer Pal"; description "Today's steps"; texts "of 10,000" and "this week" |

## Review Focus

1. The sensor reports nothing within the timeout (phone lying still): the run records nothing, returns normally, and later runs still work. Test in Task 3.
2. Two readers at once (the visible activity and the catch-up job, or a mode switch mid-walk): each step is counted once. Test in Task 3.
3. The goal notification with notifications blocked: nothing is sent and the day is not marked as sent, so it can still arrive later that day. Test in Task 4.
4. A stored "last goal alert date" that is not a valid date: treated as never sent, no crash. Test in Task 2.
5. Switching "Show step count" off and on while walking: the accumulator baseline carries across, so nothing is lost or doubled. Test in Task 3.

---

### Task 1: Goal alert rule (`:core`)

**Files:**
- Create: `core/src/main/kotlin/app/pedometer/core/GoalAlert.kt`
- Test: `core/src/test/kotlin/app/pedometer/core/GoalAlertTest.kt`

**Interfaces:**
- Produces: `object GoalAlert { fun shouldNotify(steps: Int, goal: Int, today: LocalDate, lastAlertDate: LocalDate?, enabled: Boolean): Boolean }`

- [ ] **Step 1: Write the failing test**

```kotlin
class GoalAlertTest {
    private val today = LocalDate.parse("2026-11-12")
    private fun check(steps: Int, last: LocalDate?, enabled: Boolean = true) =
        GoalAlert.shouldNotify(steps, 10_000, today, last, enabled)

    @Test fun belowGoalDoesNotNotify() = assertFalse(check(9_999, null))
    @Test fun atOrAboveGoalNotifies() { assertTrue(check(10_000, null)); assertTrue(check(12_870, null)) }
    @Test fun alreadySentTodayDoesNotNotifyAgain() = assertFalse(check(12_870, today))
    @Test fun sentYesterdayNotifiesAgain() = assertTrue(check(10_000, LocalDate.parse("2026-11-11")))
    @Test fun disabledNeverNotifies() = assertFalse(check(12_870, null, enabled = false))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:test --tests "*GoalAlertTest"`
Expected: compilation failure, unresolved reference `GoalAlert`.

- [ ] **Step 3: Implement `GoalAlert.shouldNotify`**

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:test`
Expected: `BUILD SUCCESSFUL`, 25 tests passed.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "Add once-a-day goal alert rule"
```

---

### Task 2: Notification settings storage

**Files:**
- Modify: `app/src/main/java/app/pedometer/data/SettingsStore.kt`, `app/src/main/java/app/pedometer/data/StepRepository.kt`
- Test: `app/src/test/java/app/pedometer/data/StepRepositoryTest.kt`

**Interfaces:**
- Produces, on `StepRepository` (delegating to `SettingsStore`):
  - `val showStepCount: Flow<Boolean>` (default `true`), `suspend fun setShowStepCount(show: Boolean)`
  - `val goalAlertEnabled: Flow<Boolean>` (default `false`), `suspend fun setGoalAlertEnabled(enabled: Boolean)`
  - `val lastGoalAlertDate: Flow<LocalDate?>`, `suspend fun markGoalAlertSent(date: LocalDate)`
- Preference keys: `show_step_count` (boolean), `goal_alert_enabled` (boolean), `last_goal_alert_date` (ISO string).

- [ ] **Step 1: Write the failing tests** (added to `StepRepositoryTest`; `dataStore` becomes a field so a test can write to it)

```kotlin
@Test fun notificationSwitchesDefaultAndPersist() = runTest {
    assertTrue(repo.showStepCount.first()); assertFalse(repo.goalAlertEnabled.first())
    repo.setShowStepCount(false); repo.setGoalAlertEnabled(true)
    assertFalse(repo.showStepCount.first()); assertTrue(repo.goalAlertEnabled.first())
}
@Test fun goalAlertDateDefaultsToNullAndPersists() = runTest {
    assertNull(repo.lastGoalAlertDate.first())
    repo.markGoalAlertSent(d("2026-11-12")); assertEquals(d("2026-11-12"), repo.lastGoalAlertDate.first())
}
@Test fun unreadableGoalAlertDateCountsAsNeverSent() = runTest {
    dataStore.edit { it[stringPreferencesKey("last_goal_alert_date")] = "not-a-date" }
    assertNull(repo.lastGoalAlertDate.first())
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*StepRepositoryTest"`
Expected: compilation failure, unresolved reference `showStepCount`.

- [ ] **Step 3: Implement the settings and repository members**

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Store the two notification switches and the goal alert date"
```

---

### Task 3: One recorder for every sensor reading

**Files:**
- Create: `app/src/main/java/app/pedometer/counting/StepSensorReader.kt`, `StepRecorder.kt`
- Modify: `app/src/main/java/app/pedometer/counting/StepCounterService.kt`, `app/src/main/java/app/pedometer/PedometerApp.kt`
- Test: `app/src/test/java/app/pedometer/counting/StepRecorderTest.kt`

**Interfaces:**
- Consumes: `StepRepository.recordReading(sensorValue: Long, bootCount: Int, date: LocalDate)`.
- Produces:
  - `class StepSensorReader(context: Context) { val hasSensor: Boolean; fun readings(): Flow<Long>; fun flush() }`. `readings()` is a conflated `callbackFlow` that registers a `TYPE_STEP_COUNTER` listener (`SENSOR_DELAY_NORMAL`) while collected and unregisters when collection ends; it emits nothing when there is no sensor. `flush()` flushes the currently registered listeners.
  - `class StepRecorder(reader: StepSensorReader, repository: StepRepository, scope: CoroutineScope, bootCount: () -> Int, today: () -> LocalDate = ::currentLocalDate, onRecorded: () -> Unit = {})` with `val recorded: SharedFlow<Long>` and `suspend fun recordOnce(timeoutMs: Long = 10_000): Boolean`.
  - `PedometerApp` gains `val appScope: CoroutineScope` (`SupervisorJob() + Dispatchers.Default`), `val sensorReader: StepSensorReader`, `val recorder: StepRecorder`. Boot count comes from `Settings.Global.BOOT_COUNT`.

Design that the signatures do not determine: `recorded` is `reader.readings()` with each value written to the repository first (a failed write is logged and does not end the stream), then `onRecorded()` called, shared with `SharingStarted.WhileSubscribed()` and no replay. Because one upstream collector does every write, readings are recorded in arrival order no matter how many callers collect. `recordOnce` is `withTimeoutOrNull(timeoutMs) { recorded.first() } != null`. The service keeps the stream alive by collecting `recorded` in its scope, and no longer owns a listener, channel or consumer; `ACTION_FLUSH` calls `sensorReader.flush()`.

- [ ] **Step 1: Write the failing test**

Robolectric (`@Config(sdk = [34])`), in-memory Room and temp-file DataStore as in `StepRepositoryTest`. Add a step sensor with `shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))`. Helper `emit(value: Long)` builds an event with `createSensorEvent(1, Sensor.TYPE_STEP_COUNTER)`, sets `values[0]`, and calls `sendSensorEventToListeners`. The recorder runs on the test scheduler (`scope = backgroundScope`, `UnconfinedTestDispatcher`), `bootCount = { 1 }`, `today = { d("2026-11-12") }`.

```kotlin
@Test fun liveReadingsAreRecorded() = runTest {
    val live = backgroundScope.launch { recorder.recorded.collect {} }
    emit(100); emit(130)
    assertEquals(30, steps("2026-11-12")); live.cancel()
}
@Test fun recordOnceTimesOutQuietlyWhenTheSensorIsSilent() = runTest {
    assertFalse(recorder.recordOnce(timeoutMs = 10_000))
    assertEquals(0, steps("2026-11-12"))
    assertTrue(shadowOf(sensorManager).listeners.isEmpty())
}
@Test fun recordOnceRecordsTheNextReading() = runTest {
    val once = async { recorder.recordOnce() }
    emit(100); assertTrue(once.await())
}
@Test fun twoReadersCountEachStepOnce() = runTest {
    val live = backgroundScope.launch { recorder.recorded.collect {} }
    emit(100)
    val once = async { recorder.recordOnce() }
    emit(130); once.await(); emit(150)
    assertEquals(50, steps("2026-11-12")); live.cancel()
}
@Test fun baselineCarriesAcrossSeparateSessions() = runTest {
    val first = async { recorder.recordOnce() }; emit(100); first.await()
    val live = backgroundScope.launch { recorder.recorded.collect {} }
    emit(130); live.cancel()
    val second = async { recorder.recordOnce() }; emit(150); second.await()
    assertEquals(50, steps("2026-11-12"))
}
@Test fun onRecordedRunsAfterEachWrite() = runTest { /* counter passed as onRecorded is 2 after emit(100); emit(130) with a live collector */ }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*StepRecorderTest"`
Expected: compilation failure, unresolved reference `StepRecorder`.

- [ ] **Step 3: Implement `StepSensorReader` and `StepRecorder`, add them to `PedometerApp`, and move `StepCounterService` onto the recorder**

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 failures, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Route every step sensor reading through one recorder"
```

---

### Task 4: Goal notification and step effects

**Files:**
- Create: `app/src/main/java/app/pedometer/counting/GoalNotification.kt`, `StepEffects.kt`, `app/src/main/java/app/pedometer/ui/LaunchIntent.kt`
- Modify: `app/src/main/java/app/pedometer/counting/StepNotification.kt` (use `appLaunchIntent`), `PedometerApp.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/app/pedometer/counting/StepEffectsTest.kt`

**Interfaces:**
- Consumes: `GoalAlert.shouldNotify`, `StepRepository.days/goal/goalAlertEnabled/lastGoalAlertDate/markGoalAlertSent`, `StepRecorder`'s `onRecorded` hook.
- Produces:
  - `fun appLaunchIntent(context: Context): Intent` (the MAIN/LAUNCHER intent now built inside `StepNotification`).
  - `object GoalNotification { const val NOTIFICATION_ID = 2; fun ensureChannel(context: Context); fun build(context: Context, goal: Int): Notification }`; channel id `goal`, `IMPORTANCE_DEFAULT`, auto-cancel, small icon `ic_walk`.
  - `class StepEffects(context: Context, repository: StepRepository, scope: CoroutineScope, today: () -> LocalDate = ::currentLocalDate, refreshWidget: suspend () -> Unit = {})` with `suspend fun refreshNow()` and `fun requestRefresh()`.
  - `PedometerApp.effects: StepEffects`; the app's `StepRecorder` is built with `onRecorded = effects::requestRefresh`.

Design: `refreshNow()` calls `refreshWidget()`, then sends the goal notification when `GoalAlert.shouldNotify` is true and `NotificationManagerCompat.areNotificationsEnabled()`, marking the date only after sending. `requestRefresh()` posts to a conflated channel drained by one coroutine that calls `refreshNow()` then delays 5 seconds. `StepEffects` also calls `requestRefresh()` whenever `goal` or `goalAlertEnabled` changes after their first value.

- [ ] **Step 1: Write the failing test**

Robolectric, same storage setup; `today = { d("2026-11-12") }`. `notifications()` is `shadowOf(notificationManager).allNotifications`.

```kotlin
@Test fun sendsOnceWhenTheGoalIsReached() = runTest {
    repo.setGoalAlertEnabled(true); repo.addSteps(d("2026-11-12"), 10_000)
    effects.refreshNow(); effects.refreshNow()
    assertEquals(1, notifications().size)
    assertEquals("Goal reached", notifications().single().extras.getString(Notification.EXTRA_TITLE))
    assertEquals("You walked 10,000 steps today.", notifications().single().extras.getString(Notification.EXTRA_TEXT))
    assertEquals(d("2026-11-12"), repo.lastGoalAlertDate.first())
}
@Test fun staysQuietBelowTheGoalOrWhenSwitchedOff() = runTest {
    repo.addSteps(d("2026-11-12"), 12_000); effects.refreshNow()            // switch off
    repo.setGoalAlertEnabled(true); repo.setGoal(20_000); effects.refreshNow() // below goal
    assertTrue(notifications().isEmpty())
}
@Test fun blockedNotificationsAreNotMarkedAsSent() = runTest {
    shadowOf(notificationManager).setNotificationsEnabled(false)
    repo.setGoalAlertEnabled(true); repo.addSteps(d("2026-11-12"), 10_000); effects.refreshNow()
    assertNull(repo.lastGoalAlertDate.first())
    shadowOf(notificationManager).setNotificationsEnabled(true); effects.refreshNow()
    assertEquals(1, notifications().size)
}
@Test fun refreshesTheWidgetEveryTime() = runTest { /* a counter passed as refreshWidget is 2 after two refreshNow() calls */ }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*StepEffectsTest"`
Expected: compilation failure, unresolved reference `StepEffects`.

- [ ] **Step 3: Implement `appLaunchIntent`, `GoalNotification`, `StepEffects` and the `PedometerApp` wiring**

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 failures, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Add goal-reached notification and post-recording effects"
```

---

### Task 5: Counting modes

**Files:**
- Create: `app/src/main/java/app/pedometer/counting/CountingController.kt`, `StepCatchUpWorker.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (WorkManager), `Rollover.kt`, `StepCounterService.kt`, `BootReceiver.kt`, `app/src/main/java/app/pedometer/ui/MainActivity.kt`, `app/src/main/java/app/pedometer/ui/AppRoot.kt`, `PedometerApp.kt`
- Test: `app/src/test/java/app/pedometer/counting/CountingControllerTest.kt`, `StepCatchUpWorkerTest.kt`

**Interfaces:**
- Consumes: `StepCounterService.canCount/start`, `StepRecorder.recordOnce/recorded`, `StepEffects.refreshNow`, `StepRepository.showStepCount`.
- Produces:
  - `enum class CountingMode { NONE, CONTINUOUS, PERIODIC }`, `fun countingMode(canCount: Boolean, showStepCount: Boolean): CountingMode`
  - `class CountingController(context: Context, repository: StepRepository) { suspend fun apply() }`; `PedometerApp.countingController`.
  - `class StepCatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker`; unique work name `step-catch-up`.
  - `fun scheduleRolloverAlarm(context: Context)` in `Rollover.kt` (the body now inside `StepCounterService.scheduleRollover`).

Behaviour:
- `apply()` by mode. `NONE`: stop the service, cancel the work. `CONTINUOUS`: cancel the work, `StepCounterService.start`. `PERIODIC`: `stopService`, `enqueueUniquePeriodicWork("step-catch-up", ExistingPeriodicWorkPolicy.KEEP, 15 minutes)`, one immediate run as unique one-time work `step-catch-up-now` (`ExistingWorkPolicy.REPLACE`), and `scheduleRolloverAlarm`. The immediate run is what gives the spec's reading after a reboot and after switching the step count off.
- `StepCatchUpWorker.doWork()`: when `canCount`, `recorder.recordOnce()` then `effects.refreshNow()`; always `Result.success()`.
- `RolloverReceiver`: uses `goAsync()`. Continuous mode: `startService(ACTION_FLUSH)` as now. Periodic mode: `recorder.recordOnce(5_000)`, `effects.refreshNow()`, `scheduleRolloverAlarm`.
- `BootReceiver`: `goAsync()` then `countingController.apply()`.
- `MainActivity`: `onResume` launches `countingController.apply()` in `lifecycleScope` instead of calling `StepCounterService.start`; while `STARTED` and `canCount`, it collects `recorder.recorded` so the count is live in either mode.
- `AppRoot`: `PermissionScreen`'s `onGranted` launches `countingController.apply()` instead of `StepCounterService.start`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class CountingControllerTest {
    @Test fun cannotCountMeansNoMode() {
        assertEquals(CountingMode.NONE, countingMode(canCount = false, showStepCount = true))
        assertEquals(CountingMode.NONE, countingMode(canCount = false, showStepCount = false))
    }
    @Test fun stepCountOnIsContinuous() = assertEquals(CountingMode.CONTINUOUS, countingMode(true, true))
    @Test fun stepCountOffIsPeriodic() = assertEquals(CountingMode.PERIODIC, countingMode(true, false))
}
```

`StepCatchUpWorkerTest` (Robolectric, `TestListenableWorkerBuilder<StepCatchUpWorker>`): with no step sensor, `doWork()` returns `Result.success()` and `app.repository.days.first()` is empty.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*CountingControllerTest" --tests "*StepCatchUpWorkerTest"`
Expected: compilation failure, unresolved references.

- [ ] **Step 3: Implement the controller, the worker and the five integrations above**

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 failures, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add periodic catch-up counting and switch modes from the setting"
```

---

### Task 6: Settings screen

**Files:**
- Create: `app/src/main/java/app/pedometer/ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `AppRoot.kt` (route `settings`, bottom bar hidden on it), `app/src/main/java/app/pedometer/ui/today/TodayScreen.kt` (gear icon, `onOpenSettings: () -> Unit`), `TodayViewModel.kt`, `app/src/main/java/app/pedometer/ui/NotificationsOffHint.kt`, `strings.xml`
- Test: `app/src/test/java/app/pedometer/ui/settings/SettingsUiStateTest.kt`, `app/src/test/java/app/pedometer/ui/AppUiTest.kt`

**Interfaces:**
- Consumes: `StepRepository` settings members, `CountingController.apply`, `GoalDialog`, `formatSteps`.
- Produces:
  - `data class SettingsUiState(val goalText: String, val goal: Int, val showStepCount: Boolean, val goalAlertEnabled: Boolean, val showPeriodicNote: Boolean)`
  - `fun settingsUiState(goal: Int, showStepCount: Boolean, goalAlertEnabled: Boolean, locale: Locale): SettingsUiState`
  - `class SettingsViewModel(repository: StepRepository, controller: CountingController)` with `val state: StateFlow<SettingsUiState?>`, `fun setGoal(goal: Int)`, `fun setShowStepCount(show: Boolean)` (saves, then `controller.apply()`), `fun setGoalAlertEnabled(enabled: Boolean)`, and a `Factory`.
  - `TodayViewModel.notificationsWanted: StateFlow<Boolean>` (either switch on); `NotificationsOffHint(wanted: Boolean, modifier: Modifier = Modifier)` shows nothing when `wanted` is false.

- [ ] **Step 1: Write the failing tests**

```kotlin
class SettingsUiStateTest {
    @Test fun mapsValues() = assertEquals(
        SettingsUiState("10,000 steps", 10_000, showStepCount = true, goalAlertEnabled = false, showPeriodicNote = false),
        settingsUiState(10_000, showStepCount = true, goalAlertEnabled = false, Locale.US),
    )
    @Test fun noteAppearsWhenTheStepCountIsOff() =
        assertTrue(settingsUiState(8_000, showStepCount = false, goalAlertEnabled = true, Locale.US).showPeriodicNote)
}
```

Add to `AppUiTest` (permission granted, battery prompt dismissed as in the existing test):

```kotlin
@Test fun settingsShowsTheRowsAndSavesTheStepCountSwitch() {
    // open with the "Settings" content description, then:
    waitForText("Daily goal")
    compose.onNodeWithText("10,000 steps").assertIsDisplayed()
    compose.onNodeWithText("Tell me when I reach my goal").assertIsDisplayed()
    compose.onNodeWithText("Show step count").performClick()
    waitForText("With the step count off, steps are collected about every 15 minutes and whenever you open the app.")
    assertEquals(false, runBlocking { app.repository.showStepCount.first() })
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsUiStateTest" --tests "*AppUiTest"`
Expected: compilation failure in `SettingsUiStateTest`; unresolved reference `settingsUiState`.

- [ ] **Step 3: Implement the state mapping, ViewModel, screen, navigation and hint change**

- The whole row toggles its switch (`Modifier.toggleable` with `Role.Switch`), so tapping the title works.
- Turning the goal alert on when `POST_NOTIFICATIONS` is missing on API 33+ launches the permission request first and saves `true` only if it is granted.
- "Daily goal" opens the existing `GoalDialog`.

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 failures, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Add Settings screen with goal and notification switches"
```

---

### Task 7: Home-screen widget

**Files:**
- Create: `app/src/main/java/app/pedometer/widget/WidgetState.kt`, `PedometerWidget.kt` (the `GlanceAppWidget`, its `WidgetContent` composable and `PedometerWidgetReceiver`), `app/src/main/res/xml/pedometer_widget_info.xml`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (Glance), `AndroidManifest.xml` (receiver), `PedometerApp.kt` (`refreshWidget`), `strings.xml`
- Test: `app/src/test/java/app/pedometer/widget/WidgetStateTest.kt`, `WidgetContentTest.kt`

**Interfaces:**
- Consumes: `StepStats.summarize`, `formatSteps`, `firstDayOfWeek`, `appLaunchIntent`, `StepEffects`' `refreshWidget` parameter.
- Produces:
  - `enum class WidgetWidth { ONE, TWO, THREE, FOUR }`, `fun widgetWidth(widthDp: Float): WidgetWidth` (below 130 → `ONE`, below 203 → `TWO`, below 276 → `THREE`, otherwise `FOUR`)
  - `data class WidgetState(val stepsText: String, val goalText: String, val progress: Float, val weekText: String)`, `fun widgetState(days: List<DayCount>, goal: Int, today: LocalDate, firstDayOfWeek: DayOfWeek, locale: Locale): WidgetState`
  - `@Composable fun WidgetContent(state: WidgetState, width: WidgetWidth)`; `class PedometerWidget : GlanceAppWidget`; `class PedometerWidgetReceiver : GlanceAppWidgetReceiver`
  - `PedometerApp` passes `refreshWidget = { PedometerWidget().updateAll(this) }` to `StepEffects`.

Widget specifics:
- `SizeMode.Responsive` with sizes 57×40, 130×40, 203×40 and 276×40 dp; the layout is chosen with `widgetWidth(LocalSize.current.width.value)`.
- Provider XML: `minWidth` 130dp, `minHeight` 40dp, `targetCellWidth` 2, `targetCellHeight` 1, `minResizeWidth` 40dp, `resizeMode="horizontal"`, `widgetCategory="home_screen"`, `initialLayout="@layout/glance_default_loading_layout"`, description string. Widths beyond four cells use the four-cell layout.
- `GlanceTheme` colours, 16dp corner radius, whole widget clickable with `actionStartActivity(appLaunchIntent(context))`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class WidgetStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    @Test fun mapsStepsGoalProgressAndWeek() {
        val days = listOf(DayCount(d("2026-11-09"), 11_204), DayCount(d("2026-11-10"), 6_482))
        assertEquals(
            WidgetState("6,482", "of 10,000", 0.6482f, "17,686"),
            widgetState(days, 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US),
        )
    }
    @Test fun progressStopsAtFullAndEmptyDataIsZero() {
        assertEquals(1f, widgetState(listOf(DayCount(d("2026-11-10"), 9_000)), 5_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US).progress)
        assertEquals(WidgetState("0", "of 10,000", 0f, "0"), widgetState(emptyList(), 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US))
    }
    @Test fun widthBreakpoints() {
        assertEquals(WidgetWidth.ONE, widgetWidth(57f)); assertEquals(WidgetWidth.ONE, widgetWidth(129f))
        assertEquals(WidgetWidth.TWO, widgetWidth(130f)); assertEquals(WidgetWidth.THREE, widgetWidth(203f))
        assertEquals(WidgetWidth.FOUR, widgetWidth(276f)); assertEquals(WidgetWidth.FOUR, widgetWidth(400f))
    }
}
```

`WidgetContentTest` uses `runGlanceAppWidgetUnitTest` with `state = WidgetState("6,482", "of 10,000", 0.65f, "17,686")`:
- `ONE`: "6,482" exists; "of 10,000" does not.
- `TWO`: "6,482" and "of 10,000" exist; "this week" does not.
- `FOUR`: "6,482", "of 10,000", "17,686" and "this week" exist.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*WidgetStateTest" --tests "*WidgetContentTest"`
Expected: compilation failure, unresolved references.

- [ ] **Step 3: Implement the state mapping, the Glance widget, the provider XML, the manifest receiver and the `refreshWidget` wiring**

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 failures, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add resizable home-screen widget"
```

---

### Task 8: Whole-suite check

**Files:** none created; fixes go to the file that owns the defect.

- [ ] **Step 1: Run everything from clean**

Run: `./gradlew clean :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 failures, 0 lint errors, `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 2: Confirm the manifest and the spec agree**

Run: `grep -c "uses-permission" app/src/main/AndroidManifest.xml`
Expected: `7` (unchanged). The merged debug manifest contains `PedometerWidgetReceiver`, has no `INTERNET` permission, and its only addition is WorkManager's `ACCESS_NETWORK_STATE`.

- [ ] **Step 3: Report**

State what was verified by tests and that the following still need the owner's phone, as listed in the spec's Testing section: the notification disappearing and returning, catch-up counting while the app is closed, the goal notification arriving once, adding and resizing the widget, and counting after a reboot with the step count off.
