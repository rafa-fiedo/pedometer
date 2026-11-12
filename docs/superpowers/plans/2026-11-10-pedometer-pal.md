# Pedometer Pal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Pedometer Pal, a native Android pedometer showing today's steps, a per-day history, day/week/month totals and best-day records.

**Architecture:** Two Gradle modules. `:core` is pure Kotlin (totals, records, sensor-delta logic, goal rules) with JUnit tests. `:app` is the Android app: Room storage behind `StepRepository`, a `health` foreground service reading `Sensor.TYPE_STEP_COUNTER`, and three Compose screens whose display state comes from pure mapping functions.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (Material 3), Room, DataStore, kotlinx-datetime, coroutines, JUnit 4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-10-pedometer-design.md`

## Global Constraints

- Work on a branch named `build-app` created from `main`; do not commit to `main`.
- App name "Pedometer Pal". Application ID and namespace `app.pedometer`. `:core` package `app.pedometer.core`.
- `minSdk 29`, `compileSdk 35`, `targetSdk 35` (API 35 is the newest platform installed on the build machine).
- Java/Kotlin bytecode target 17. Do not use `jvmToolchain`; set `sourceCompatibility`/`targetCompatibility`/`jvmTarget` to 17 so both the system JDK 17 and Android Studio's JDK 21 can build.
- `:core` has no Android or `java.time` types in its public API; dates are `kotlinx.datetime.LocalDate` and `kotlinx.datetime.DayOfWeek`.
- No `INTERNET` permission. No analytics, no network libraries.
- Goal range 500 to 100,000, default 10,000.
- English UI copy exactly as listed in the Copy table. Sentence case, no exclamation marks.
- Versions below match what already builds on this machine (Android Studio 2024.3.2). If a listed version fails to resolve, use the nearest patch release that does and say so in the commit message.
- Shell is Git Bash on Windows. `adb` and `gradle` are not on `PATH`: use `./gradlew`, and `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"` for adb.
- SQLite on API 29 is 3.22, which has no `ON CONFLICT ... DO UPDATE`. Do not use that syntax or Room's `@Upsert` for `daily_steps`.

### Versions

| Item | Version |
|---|---|
| Gradle wrapper | 8.11.1 |
| Android Gradle Plugin (`com.android.application`) | 8.6.1 |
| Kotlin (`android`, `jvm`, `plugin.compose`) | 2.1.20 |
| KSP (`com.google.devtools.ksp`) | 2.1.20-1.0.32 |
| `androidx.compose:compose-bom` | 2024.12.01 |
| `androidx.core:core-ktx` | 1.15.0 |
| `androidx.activity:activity-compose` | 1.9.3 |
| `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | 2.8.7 |
| `androidx.navigation:navigation-compose` | 2.8.5 |
| `androidx.room:room-runtime`, `room-ktx`, `room-compiler` | 2.7.1 |
| `androidx.datastore:datastore-preferences` | 1.1.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.6.2 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `-test` | 1.10.1 |
| `junit:junit` | 4.13.2 |
| `org.robolectric:robolectric` | 4.14.1 |
| `androidx.test:core` | 1.6.1 |

Compose artifacts taken from the BOM: `ui`, `ui-tooling-preview`, `ui-tooling` (debug), `material3`, `material-icons-extended`.

### Copy

| Where | Text |
|---|---|
| Tabs | "Today", "History", "Scores" |
| Today, under the count | "of 10,000 steps" (goal formatted with grouping) |
| Today tiles | "This week", "This month" |
| Goal edit icon description | "Change goal" |
| Goal dialog | Title "Daily goal"; field label "Steps"; error "Enter a number from 500 to 100,000"; buttons "Save", "Cancel" |
| Notifications-off hint | "Notifications are off, so the step count won't show in the notification bar." |
| Permission screen | Title "Count your steps"; body "Pedometer Pal needs access to physical activity to count steps. Your steps stay on this phone."; button "Allow access" |
| Permission refused | Body "Without physical activity access, Pedometer Pal can't count steps."; button "Allow access", or "Open settings" when Android no longer shows the dialog |
| No sensor | Title "This phone can't count steps"; body "Pedometer Pal needs a built-in step sensor, and this phone doesn't have one." |
| Battery dialog | Title "Keep counting in the background"; body "Some phones stop background apps to save battery. Allow Pedometer Pal to ignore battery saving so it doesn't miss steps."; buttons "Allow", "Not now" |
| History empty | "Your days will appear here" |
| Scores | Section titles "Totals", "Records"; rows "Today", "This week", "This month"; cards "Best day this month", "Best day this year"; no record "–" |
| Notification | Title "6,482 steps today"; text "Goal: 10,000"; channel name "Step count" |

## Review Focus

1. Rows dated after today (the phone's clock was set back): totals, records and history ignore them and nothing crashes. Test in Task 3.
2. Goal input that is empty, non-numeric, decimal, negative or too long to fit an `Int`: rejected with the dialog error, never a crash. Test in Task 2.
3. The first of January: the week total still includes late-December days, while "this month" and "best day this year" do not. Test in Task 3.
4. A repeated or lower sensor value on the same boot, a negative value, or a jump larger than an `Int`: adds 0 (or a clamped value), never a negative count. Test in Task 2.
5. The step write and the sensor baseline are saved together, so a crash between them cannot double-count: both live in one Room transaction. Test in Task 4.

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (generated)
- Create: `local.properties` (not committed)
- Create: `core/build.gradle.kts`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/app/pedometer/ui/MainActivity.kt`, `app/src/main/java/app/pedometer/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/ic_walk.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/values/colors.xml`

**Interfaces:**
- Produces: modules `:core` (plugin `org.jetbrains.kotlin.jvm`) and `:app`; `app.pedometer.ui.MainActivity`; `PedometerPalTheme(content: @Composable () -> Unit)` using dynamic colour on Android 12+ and the default Material 3 light/dark schemes otherwise; drawable `R.drawable.ic_walk`.

- [ ] **Step 1: Create the branch and the Gradle wrapper**

```bash
git checkout -b build-app
```

Write `settings.gradle.kts` (plugin repositories `google()`, `mavenCentral()`, `gradlePluginPortal()`; `rootProject.name = "PedometerPal"`; `include(":app", ":core")`), then generate the wrapper from the cached distribution:

```bash
"$(ls -d ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle | head -1)" wrapper --gradle-version 8.11.1 --distribution-type bin
```

- [ ] **Step 2: Write the build files**

- `gradle/libs.versions.toml`: every entry from the Versions table.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx2g`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`.
- `local.properties`: one line, `sdk.dir=` pointing at `%LOCALAPPDATA%\Android\Sdk` with backslashes and the colon escaped.
- `.gitignore`: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`, `captures/`.
- `core/build.gradle.kts`: Kotlin JVM library; `api` kotlinx-datetime; `testImplementation` junit and `kotlin("test")`.
- `app/build.gradle.kts`: plugins application, kotlin-android, kotlin-compose, ksp; `buildFeatures { compose = true; buildConfig = true }`; `versionCode 1`, `versionName "1.0"`; `testOptions.unitTests.isIncludeAndroidResources = true`; dependencies `project(":core")` plus everything in the Versions table (Room compiler via `ksp`; junit, robolectric, `androidx.test:core`, coroutines-test and `kotlin("test")` as `testImplementation`).

- [ ] **Step 3: Write the minimal app**

- `AndroidManifest.xml`: application label `@string/app_name` ("Pedometer Pal"), icon `@mipmap/ic_launcher`, theme `Theme.PedometerPal` (parent `android:Theme.Material.Light.NoActionBar`), one exported launcher activity `.ui.MainActivity`.
- `MainActivity`: `enableEdgeToEdge()`, then `setContent { PedometerPalTheme { Text("Pedometer Pal") } }`.
- `ic_walk.xml`: 24dp vector, `android:tint="?attr/colorControlNormal"`, white fill, path data:
  `M13.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM9.8,8.9L7,23h2.1l1.8,-8 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3C14.8,12 16.8,13 19,13v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1L6,8.3V13h2V9.6l1.8,-0.7`
- `ic_launcher.xml`: adaptive icon, background colour `#1D9E75`, foreground an inset (25%) of `ic_walk` tinted white.

- [ ] **Step 4: Verify the build**

Run: `./gradlew :core:build :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Scaffold Pedometer Pal project with core and app modules"
```

---

### Task 2: Goal rules and sensor accumulator (`:core`)

**Files:**
- Create: `core/src/main/kotlin/app/pedometer/core/Goal.kt`
- Create: `core/src/main/kotlin/app/pedometer/core/StepAccumulator.kt`
- Test: `core/src/test/kotlin/app/pedometer/core/GoalTest.kt`, `StepAccumulatorTest.kt`

**Interfaces:**
- Produces:
  - `object Goal { const val MIN = 500; const val MAX = 100_000; const val DEFAULT = 10_000; fun isValid(value: Int): Boolean; fun parse(text: String): Int? }`
  - `data class AccumulatorState(val lastSensorValue: Long, val lastBootCount: Int)`
  - `data class AccumulatorResult(val stepsToAdd: Int, val state: AccumulatorState)`
  - `object StepAccumulator { fun apply(previous: AccumulatorState?, sensorValue: Long, bootCount: Int): AccumulatorResult }`

- [ ] **Step 1: Write the failing tests**

```kotlin
class GoalTest {
    @Test fun parsesValidGoals() {
        assertEquals(10_000, Goal.parse("10000"))
        assertEquals(8_000, Goal.parse(" 8000 "))
        assertEquals(500, Goal.parse("500"))
        assertEquals(100_000, Goal.parse("100000"))
    }
    @Test fun rejectsInvalidGoals() {
        for (text in listOf("499", "100001", "", "abc", "12.5", "-600", "10,000", "99999999999999999999")) {
            assertNull(Goal.parse(text), text)
        }
    }
    @Test fun validatesRange() {
        assertTrue(Goal.isValid(500)); assertTrue(Goal.isValid(100_000))
        assertFalse(Goal.isValid(499)); assertFalse(Goal.isValid(100_001))
    }
}

class StepAccumulatorTest {
    private val prev = AccumulatorState(lastSensorValue = 500, lastBootCount = 3)
    private fun check(previous: AccumulatorState?, value: Long, boot: Int, add: Int, last: Long) {
        val r = StepAccumulator.apply(previous, value, boot)
        assertEquals(add, r.stepsToAdd)
        assertEquals(AccumulatorState(last, boot), r.state)
    }
    @Test fun firstReadingSetsBaseline() = check(null, 500, 3, add = 0, last = 500)
    @Test fun sameBootAddsDifference() = check(prev, 525, 3, add = 25, last = 525)
    @Test fun repeatedValueAddsNothing() = check(prev, 500, 3, add = 0, last = 500)
    @Test fun rebootAddsFullValue() = check(prev, 40, 4, add = 40, last = 40)
    @Test fun rebootWithHigherValueStillAddsFullValue() = check(prev, 900, 4, add = 900, last = 900)
    @Test fun lowerValueOnSameBootRebaselines() = check(prev, 480, 3, add = 0, last = 480)
    @Test fun negativeValueIsTreatedAsZero() = check(prev, -5, 4, add = 0, last = 0)
    @Test fun hugeJumpIsClamped() = check(AccumulatorState(0, 3), 5_000_000_000, 3, add = Int.MAX_VALUE, last = 5_000_000_000)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test`
Expected: compilation failure, unresolved references `Goal` and `StepAccumulator`.

- [ ] **Step 3: Implement `Goal` and `StepAccumulator`**

`Goal.parse` trims, accepts ASCII digits only, and returns the value only when `isValid`. `StepAccumulator.apply` follows the four rules in the spec's `StepAccumulator` section, after coercing a negative `sensorValue` to 0.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test`
Expected: `BUILD SUCCESSFUL`, 11 tests passed.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "Add goal rules and step sensor accumulator"
```

---

### Task 3: Totals, records and history (`:core`)

**Files:**
- Create: `core/src/main/kotlin/app/pedometer/core/StepStats.kt`
- Test: `core/src/test/kotlin/app/pedometer/core/StepStatsTest.kt`

**Interfaces:**
- Produces:
  - `data class DayCount(val date: LocalDate, val steps: Int)`
  - `data class StepSummary(val today: Int, val thisWeek: Int, val thisMonth: Int, val bestDayThisMonth: DayCount?, val bestDayThisYear: DayCount?)`
  - `data class HistoryDay(val date: LocalDate, val steps: Int, val goalReached: Boolean)`
  - `object StepStats { fun summarize(days: List<DayCount>, today: LocalDate, firstDayOfWeek: DayOfWeek): StepSummary; fun history(days: List<DayCount>, today: LocalDate, goal: Int): List<HistoryDay> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
class StepStatsTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    private val today = d("2026-11-10") // a Tuesday
    private val november = listOf(
        DayCount(d("2026-11-04"), 7_395), DayCount(d("2026-11-05"), 9_640),
        DayCount(d("2026-11-06"), 8_115), DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-08"), 4_310), DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    @Test fun totalsWithMondayWeek() {
        val s = StepStats.summarize(november, today, DayOfWeek.MONDAY)
        assertEquals(6_482, s.today); assertEquals(17_686, s.thisWeek); assertEquals(60_016, s.thisMonth)
    }
    @Test fun weekStartingSunday() =
        assertEquals(21_996, StepStats.summarize(november, today, DayOfWeek.SUNDAY).thisWeek)
    @Test fun records() {
        val days = november + DayCount(d("2026-06-14"), 18_342) + DayCount(d("2025-12-30"), 20_000)
        val s = StepStats.summarize(days, today, DayOfWeek.MONDAY)
        assertEquals(DayCount(d("2026-11-07"), 12_870), s.bestDayThisMonth)
        assertEquals(DayCount(d("2026-06-14"), 18_342), s.bestDayThisYear)
    }
    @Test fun tiesGoToEarliestDay() {
        val days = november + DayCount(d("2026-11-03"), 12_870)
        assertEquals(d("2026-11-03"), StepStats.summarize(days, today, DayOfWeek.MONDAY).bestDayThisMonth?.date)
    }
    @Test fun noDataOrOnlyZeros() {
        for (days in listOf(emptyList(), listOf(DayCount(today, 0)))) {
            val s = StepStats.summarize(days, today, DayOfWeek.MONDAY)
            assertEquals(StepSummary(0, 0, 0, null, null), s)
        }
    }
    @Test fun futureRowsAreIgnored() {
        val days = november + DayCount(d("2026-11-11"), 99_999)
        val s = StepStats.summarize(days, today, DayOfWeek.MONDAY)
        assertEquals(60_016, s.thisMonth); assertEquals(12_870, s.bestDayThisMonth?.steps)
        assertEquals(d("2026-11-10"), StepStats.history(days, today, 10_000).first().date)
    }
    @Test fun firstOfJanuary() {
        val days = listOf(DayCount(d("2025-12-29"), 5_000), DayCount(d("2025-12-31"), 9_000), DayCount(d("2026-01-01"), 3_000))
        val s = StepStats.summarize(days, d("2026-01-01"), DayOfWeek.MONDAY)
        assertEquals(17_000, s.thisWeek); assertEquals(3_000, s.thisMonth)
        assertEquals(DayCount(d("2026-01-01"), 3_000), s.bestDayThisYear)
    }
    @Test fun historyIsNewestFirstWithGapsAndGoalMarks() {
        val days = listOf(DayCount(d("2026-11-07"), 12_870), DayCount(d("2026-11-09"), 10_000), DayCount(d("2026-11-10"), 6_482))
        assertEquals(
            listOf(
                HistoryDay(d("2026-11-10"), 6_482, false), HistoryDay(d("2026-11-09"), 10_000, true),
                HistoryDay(d("2026-11-08"), 0, false), HistoryDay(d("2026-11-07"), 12_870, true),
            ),
            StepStats.history(days, today, 10_000),
        )
    }
    @Test fun historyRunsThroughTodayAndIsEmptyWithoutRecords() {
        assertEquals(4, StepStats.history(listOf(DayCount(d("2026-11-07"), 100)), today, 10_000).size)
        assertEquals(emptyList(), StepStats.history(emptyList(), today, 10_000))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests "*StepStatsTest"`
Expected: compilation failure, unresolved reference `StepStats`.

- [ ] **Step 3: Implement `StepStats`**

Both functions first drop rows dated after `today`; input order is not assumed. A best day requires more than 0 steps.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test`
Expected: `BUILD SUCCESSFUL`, 20 tests passed.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "Add step totals, records and history calculations"
```

---

### Task 4: Storage and repository (`:app`)

**Files:**
- Create: `app/src/main/java/app/pedometer/data/Entities.kt` (`DailyStepsEntity`, `CounterStateEntity`)
- Create: `app/src/main/java/app/pedometer/data/StepDao.kt`, `AppDatabase.kt`, `SettingsStore.kt`, `StepRepository.kt`
- Create: `app/src/main/java/app/pedometer/TodaySource.kt`, `app/src/main/java/app/pedometer/PedometerApp.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`android:name=".PedometerApp"`)
- Test: `app/src/test/java/app/pedometer/data/StepRepositoryTest.kt`

**Interfaces:**
- Consumes: `DayCount`, `Goal`, `StepAccumulator`, `AccumulatorState` from Tasks 2 and 3.
- Produces:
  - Tables `daily_steps(date TEXT PRIMARY KEY, steps INTEGER NOT NULL)` and `counter_state(id INTEGER PRIMARY KEY, last_sensor_value INTEGER NOT NULL, last_boot_count INTEGER NOT NULL)` (single row, `id = 0`); database file `pedometer.db`, version 1, `exportSchema = false`.
  - `class StepRepository(dao: StepDao, settings: SettingsStore)` with
    `val days: Flow<List<DayCount>>` (ascending by date),
    `val goal: Flow<Int>`,
    `val batteryPromptShown: Flow<Boolean>`,
    `suspend fun recordReading(sensorValue: Long, bootCount: Int, date: LocalDate)`,
    `suspend fun addSteps(date: LocalDate, steps: Int)`,
    `suspend fun setGoal(goal: Int)` (throws `IllegalArgumentException` unless `Goal.isValid(goal)`),
    `suspend fun markBatteryPromptShown()`.
  - `class SettingsStore(dataStore: DataStore<Preferences>)`; the app instance uses a preferences file named `settings`.
  - `class TodaySource { val today: StateFlow<LocalDate>; fun refresh() }` and top-level `fun currentLocalDate(): LocalDate` (system clock and time zone).
  - `class PedometerApp : Application() { val repository: StepRepository; val todaySource: TodaySource }`.

- [ ] **Step 1: Write the failing test**

Robolectric test (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`) with an in-memory Room database (`allowMainThreadQueries()`) and a DataStore created by `PreferenceDataStoreFactory` over a `TemporaryFolder` file. Helper `suspend fun steps(date: String): Int` reads `repo.days.first()` and returns that date's steps or 0.

```kotlin
@Test fun firstReadingAddsNothingThenDifferencesAccumulate() = runTest {
    repo.recordReading(500, 3, d("2026-11-10")); assertEquals(0, steps("2026-11-10"))
    repo.recordReading(525, 3, d("2026-11-10")); assertEquals(25, steps("2026-11-10"))
    repo.recordReading(530, 3, d("2026-11-10")); assertEquals(30, steps("2026-11-10"))
}
@Test fun rebootAddsStepsSinceBoot() = runTest {
    repo.recordReading(100, 1, d("2026-11-10")); repo.recordReading(130, 1, d("2026-11-10"))
    repo.recordReading(12, 2, d("2026-11-10")); assertEquals(42, steps("2026-11-10"))
}
@Test fun stepsGoToTheDatePassedIn() = runTest {
    repo.recordReading(100, 1, d("2026-11-10")); repo.recordReading(130, 1, d("2026-11-10"))
    repo.recordReading(150, 1, d("2026-11-11"))
    assertEquals(30, steps("2026-11-10")); assertEquals(20, steps("2026-11-11"))
}
@Test fun baselineIsSavedWithTheSteps() = runTest {
    repo.recordReading(100, 1, d("2026-11-10")); repo.recordReading(130, 1, d("2026-11-10"))
    assertEquals(CounterStateEntity(id = 0, lastSensorValue = 130, lastBootCount = 1), dao.counterState())
}
@Test fun addStepsSums() = runTest {
    repo.addSteps(d("2026-11-07"), 400); repo.addSteps(d("2026-11-07"), 100); assertEquals(500, steps("2026-11-07"))
}
@Test fun goalDefaultsAndPersists() = runTest {
    assertEquals(10_000, repo.goal.first())
    repo.setGoal(8_000); assertEquals(8_000, repo.goal.first())
    assertFailsWith<IllegalArgumentException> { repo.setGoal(100) }
}
@Test fun batteryPromptFlag() = runTest {
    assertFalse(repo.batteryPromptShown.first()); repo.markBatteryPromptShown(); assertTrue(repo.batteryPromptShown.first())
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*StepRepositoryTest"`
Expected: compilation failure, unresolved reference `StepRepository`.

- [ ] **Step 3: Implement the data layer**

- `StepDao` is an abstract class. Adding steps is two statements, `INSERT OR IGNORE INTO daily_steps(date, steps) VALUES(:date, 0)` then `UPDATE daily_steps SET steps = steps + :steps WHERE date = :date`, wrapped in a `@Transaction` method `addSteps(date: String, steps: Int)`.
- `@Transaction open suspend fun record(sensorValue: Long, bootCount: Int, date: String)` reads `counter_state`, calls `StepAccumulator.apply`, adds the steps and writes the new state. `StepRepository.recordReading` delegates to it.
- Dates are stored as ISO strings (`LocalDate.toString()` / `LocalDate.parse`).
- `TodaySource.refresh()` sets `today` to `currentLocalDate()`; it is a no-op when the date has not changed.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 7 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Add Room storage, settings and step repository"
```

---

### Task 5: Step counting service

**Files:**
- Create: `app/src/main/java/app/pedometer/counting/StepCounterService.kt`, `StepNotification.kt`, `BootReceiver.kt`, `Rollover.kt` (function `nextRolloverTrigger` and `RolloverReceiver`)
- Create: `app/src/main/java/app/pedometer/ui/Formatting.kt`
- Create: `app/src/debug/AndroidManifest.xml`, `app/src/debug/java/app/pedometer/debug/DebugStepsReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/app/pedometer/counting/RolloverTest.kt`, `app/src/test/java/app/pedometer/ui/FormattingTest.kt`

**Interfaces:**
- Consumes: `PedometerApp.repository`, `StepRepository.recordReading/addSteps/days/goal`, `currentLocalDate()`.
- Produces:
  - `class StepCounterService : Service()` with `companion object { const val ACTION_FLUSH = "app.pedometer.action.FLUSH"; fun hasStepSensor(context: Context): Boolean; fun canCount(context: Context): Boolean; fun start(context: Context) }`. `canCount` is true when the sensor exists and `ACTIVITY_RECOGNITION` is granted; `start` does nothing unless `canCount`.
  - `fun nextRolloverTrigger(now: ZonedDateTime): ZonedDateTime`.
  - `fun formatSteps(steps: Int, locale: Locale): String`, `formatLongDate`, `formatDayRow`, `formatMonthTitle`, `formatRecordDate` (each `(date: LocalDate, locale: Locale): String`, taking a kotlinx `LocalDate`).
  - Debug builds only: `app.pedometer.debug.DebugStepsReceiver`, action `app.pedometer.DEBUG_ADD_STEPS`, extras `steps` (int, required) and `date` (ISO string, default today); it calls `repository.addSteps`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class RolloverTest {
    private fun at(time: String) = ZonedDateTime.parse("${time}+01:00[Europe/Paris]")
    @Test fun beforeTriggerUsesSameDay() =
        assertEquals(at("2026-11-10T23:58:00"), nextRolloverTrigger(at("2026-11-10T10:00:00")))
    @Test fun atOrAfterTriggerUsesNextDay() {
        val next = at("2026-11-11T23:58:00")
        assertEquals(next, nextRolloverTrigger(at("2026-11-10T23:58:00")))
        assertEquals(next, nextRolloverTrigger(at("2026-11-10T23:59:30")))
    }
}

class FormattingTest {
    private val date = LocalDate.parse("2026-11-10")
    @Test fun steps() { assertEquals("6,482", formatSteps(6_482, Locale.US)); assertEquals("0", formatSteps(0, Locale.US)) }
    @Test fun dates() {
        assertEquals("Tuesday, 10 November", formatLongDate(date, Locale.US))
        assertEquals("Tue 10", formatDayRow(date, Locale.US))
        assertEquals("November 2026", formatMonthTitle(date, Locale.US))
        assertEquals("Sat 7 November", formatRecordDate(LocalDate.parse("2026-11-07"), Locale.US))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*RolloverTest" --tests "*FormattingTest"`
Expected: compilation failure, unresolved references.

- [ ] **Step 3: Implement the formatting and rollover functions**

Patterns: `EEEE, d MMMM`, `EEE d`, `MMMM yyyy`, `EEE d MMMM`; `formatSteps` uses `NumberFormat.getIntegerInstance(locale)`.

- [ ] **Step 4: Implement the service, receivers and manifest entries**

- Manifest permissions: `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`. Service declared with `android:foregroundServiceType="health"`, not exported. `BootReceiver` (action `BOOT_COMPLETED`) and `RolloverReceiver` not exported.
- Service start: create the channel (`id = "steps"`, `IMPORTANCE_LOW`), call `ServiceCompat.startForeground` with notification id 1 and type `FOREGROUND_SERVICE_TYPE_HEALTH` on API 34+ (0 below), register the `TYPE_STEP_COUNTER` listener with `SENSOR_DELAY_NORMAL`, schedule the rollover alarm, return `START_STICKY`.
- Readings: the listener sends `event.values[0].toLong()` into a `Channel<Long>(Channel.CONFLATED)`; a single coroutine consumes it and calls `repository.recordReading(value, Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0), currentLocalDate())`. One consumer keeps readings in order.
- Notification: ongoing, silent, small icon `ic_walk`, content intent opens `MainActivity`, copy from the Copy table. Updates come from `combine(repository.days, repository.goal)` reduced to today's steps and the goal, then `distinctUntilChanged().conflate().collect { notify(it); delay(5_000) }`, so the latest value always arrives and updates are at most one per 5 seconds.
- Rollover: `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, nextRolloverTrigger(now), pendingIntent)` targeting `RolloverReceiver`, which calls `context.startService` with `ACTION_FLUSH` inside a `try/catch (IllegalStateException)`. On `ACTION_FLUSH` the service acquires a 5-second partial wake lock, calls `sensorManager.flush(listener)` and schedules the next alarm.
- `onStartCommand` is idempotent: the app calls `start` on every resume, so a second call must not register a second listener, consumer or notification collector.
- `BootReceiver` calls `StepCounterService.start(context)`.
- `onDestroy`: unregister the listener, cancel the coroutine scope.

- [ ] **Step 5: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`; lint reports 0 errors (suppress `BatteryLife` on the battery permission with `tools:ignore`).

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "Add foreground step counting service with reboot and midnight handling"
```

---

### Task 6: Startup flow and navigation shell

**Files:**
- Create: `app/src/main/java/app/pedometer/ui/AppRoot.kt` (gate and bottom navigation)
- Create: `app/src/main/java/app/pedometer/ui/onboarding/StartupGate.kt`, `PermissionScreen.kt`, `NoSensorScreen.kt`, `BatteryPrompt.kt`
- Modify: `app/src/main/java/app/pedometer/ui/MainActivity.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/app/pedometer/ui/onboarding/StartupGateTest.kt`

**Interfaces:**
- Consumes: `StepCounterService.hasStepSensor/canCount/start`, `StepRepository.batteryPromptShown/markBatteryPromptShown`, `TodaySource.refresh`.
- Produces:
  - `enum class StartupScreen { NO_SENSOR, PERMISSION, MAIN }`
  - `fun startupScreen(hasSensor: Boolean, hasActivityPermission: Boolean, skipSensorCheck: Boolean): StartupScreen`
  - `@Composable fun AppRoot(skipSensorCheck: Boolean)`; inside `MAIN`, a `Scaffold` with a `NavigationBar` and a `NavHost` with routes `today`, `history`, `scores` (start `today`), each showing a placeholder `Text` of its tab name until Tasks 7 and 8 replace them.
  - `@Composable fun NotificationsOffHint()` returning nothing when notifications are allowed (used by Task 7).

- [ ] **Step 1: Write the failing test**

```kotlin
class StartupGateTest {
    @Test fun noSensorWins() = assertEquals(StartupScreen.NO_SENSOR, startupScreen(false, true, false))
    @Test fun missingPermission() = assertEquals(StartupScreen.PERMISSION, startupScreen(true, false, false))
    @Test fun ready() = assertEquals(StartupScreen.MAIN, startupScreen(true, true, false))
    @Test fun skipFlagBypassesOnlyTheSensorCheck() {
        assertEquals(StartupScreen.PERMISSION, startupScreen(false, false, true))
        assertEquals(StartupScreen.MAIN, startupScreen(false, true, true))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*StartupGateTest"`
Expected: compilation failure, unresolved reference `startupScreen`.

- [ ] **Step 3: Implement the gate and screens**

- `MainActivity` passes `skipSensorCheck = BuildConfig.DEBUG && intent.getBooleanExtra("skip_sensor_check", false)`. In `onResume` it calls `todaySource.refresh()` and `StepCounterService.start(this)`. Between `onStart` and `onStop` it registers a receiver for `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` that calls `todaySource.refresh()`.
- `PermissionScreen` requests `ACTIVITY_RECOGNITION`, then `POST_NOTIFICATIONS` on API 33+. After a refusal it shows the refused copy; the button reads "Open settings" and opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` when `shouldShowRequestPermissionRationale` is false after a denial. Granting activity access calls `StepCounterService.start` and moves to `MAIN`.
- `BatteryPrompt`: shown once in `MAIN` when `batteryPromptShown` is false and the app is not already exempt (`PowerManager.isIgnoringBatteryOptimizations`). Either button calls `markBatteryPromptShown()`; "Allow" also launches `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:app.pedometer`.
- Tab icons from `material-icons-extended`: `DirectionsWalk`, `CalendarMonth`, `EmojiEvents`.

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Add startup gate, permission flow, battery prompt and navigation shell"
```

---

### Task 7: Today screen and goal editing

**Files:**
- Create: `app/src/main/java/app/pedometer/ui/today/TodayUiState.kt`, `TodayViewModel.kt`, `TodayScreen.kt`, `GoalDialog.kt`
- Modify: `app/src/main/java/app/pedometer/ui/AppRoot.kt` (route `today`)
- Test: `app/src/test/java/app/pedometer/ui/today/TodayUiStateTest.kt`

**Interfaces:**
- Consumes: `StepStats.summarize`, `Goal.parse`, `formatSteps`, `formatLongDate`, `StepRepository.days/goal/setGoal`, `TodaySource.today`, `NotificationsOffHint`.
- Produces:
  - `data class TodayUiState(val stepsText: String, val goalText: String, val progress: Float, val dateLabel: String, val weekText: String, val monthText: String, val goal: Int)`
  - `fun todayUiState(days: List<DayCount>, goal: Int, today: LocalDate, firstDayOfWeek: DayOfWeek, locale: Locale): TodayUiState`
  - `class TodayViewModel(repository: StepRepository, todaySource: TodaySource) : ViewModel() { val state: StateFlow<TodayUiState?>; fun setGoal(goal: Int) }` with `companion object { val Factory: ViewModelProvider.Factory }`. The first day of the week is `WeekFields.of(Locale.getDefault()).firstDayOfWeek`.

- [ ] **Step 1: Write the failing test**

```kotlin
class TodayUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    private val days = listOf(DayCount(d("2026-11-07"), 12_870), DayCount(d("2026-11-09"), 11_204), DayCount(d("2026-11-10"), 6_482))
    @Test fun mapsTotalsAndLabels() {
        val s = todayUiState(days, 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US)
        assertEquals("6,482", s.stepsText); assertEquals("of 10,000 steps", s.goalText)
        assertEquals(0.6482f, s.progress, 0.0001f); assertEquals("Tuesday, 10 November", s.dateLabel)
        assertEquals("17,686", s.weekText); assertEquals("30,556", s.monthText); assertEquals(10_000, s.goal)
    }
    @Test fun progressStopsAtFull() =
        assertEquals(1f, todayUiState(days, 5_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US).progress)
    @Test fun emptyDataShowsZero() {
        val s = todayUiState(emptyList(), 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US)
        assertEquals("0", s.stepsText); assertEquals(0f, s.progress)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*TodayUiStateTest"`
Expected: compilation failure, unresolved reference `todayUiState`.

- [ ] **Step 3: Implement the state mapping, ViewModel, screen and dialog**

- Layout as in the approved mockup: a circular progress ring with the count and `goalText` inside, the date label with the "Change goal" icon button beside it, two tiles ("This week", "This month"), then `NotificationsOffHint()`.
- `GoalDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit)`: number keyboard, field prefilled with `initial`; "Save" calls `Goal.parse` and shows the error from the Copy table under the field when it returns null, otherwise calls `onSave`. The error clears when the text changes.
- The screen collects `state` with `collectAsStateWithLifecycle` and shows nothing while it is null.

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Add Today screen with goal ring and goal editing"
```

---

### Task 8: History and Scores screens

**Files:**
- Create: `app/src/main/java/app/pedometer/ui/history/HistoryUiState.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`
- Create: `app/src/main/java/app/pedometer/ui/scores/ScoresUiState.kt`, `ScoresViewModel.kt`, `ScoresScreen.kt`
- Modify: `app/src/main/java/app/pedometer/ui/AppRoot.kt` (routes `history`, `scores`)
- Test: `app/src/test/java/app/pedometer/ui/history/HistoryUiStateTest.kt`, `app/src/test/java/app/pedometer/ui/scores/ScoresUiStateTest.kt`

**Interfaces:**
- Consumes: `StepStats.history/summarize`, the formatting functions, `StepRepository.days/goal`, `TodaySource.today`.
- Produces:
  - `data class HistoryRow(val label: String, val stepsText: String, val goalReached: Boolean)`, `data class HistoryMonth(val title: String, val rows: List<HistoryRow>)`, `fun historyMonths(days: List<DayCount>, goal: Int, today: LocalDate, locale: Locale): List<HistoryMonth>`
  - `data class RecordUi(val stepsText: String, val dateText: String)`, `data class ScoresUiState(val todayText: String, val weekText: String, val monthText: String, val bestThisMonth: RecordUi?, val bestThisYear: RecordUi?)`, `fun scoresUiState(days: List<DayCount>, today: LocalDate, firstDayOfWeek: DayOfWeek, locale: Locale): ScoresUiState`
  - `HistoryViewModel` and `ScoresViewModel`, same constructor and `Factory` shape as `TodayViewModel`, each exposing `val state: StateFlow<...?>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class HistoryUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    @Test fun groupsByMonthNewestFirst() {
        val days = listOf(DayCount(d("2026-10-30"), 10_000), DayCount(d("2026-11-01"), 4_310))
        assertEquals(
            listOf(
                HistoryMonth("November 2026", listOf(HistoryRow("Mon 2", "0", false), HistoryRow("Sun 1", "4,310", false))),
                HistoryMonth("October 2026", listOf(HistoryRow("Sat 31", "0", false), HistoryRow("Fri 30", "10,000", true))),
            ),
            historyMonths(days, 10_000, d("2026-11-02"), Locale.US),
        )
    }
    @Test fun emptyWithoutRecords() = assertEquals(emptyList(), historyMonths(emptyList(), 10_000, d("2026-11-02"), Locale.US))
}

class ScoresUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    @Test fun mapsTotalsAndRecords() {
        val days = listOf(DayCount(d("2026-06-14"), 18_342), DayCount(d("2026-11-07"), 12_870), DayCount(d("2026-11-09"), 11_204), DayCount(d("2026-11-10"), 6_482))
        assertEquals(
            ScoresUiState("6,482", "17,686", "30,556", RecordUi("12,870", "Sat 7 November"), RecordUi("18,342", "Sun 14 June")),
            scoresUiState(days, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US),
        )
    }
    @Test fun noRecordsYet() {
        val s = scoresUiState(emptyList(), d("2026-11-10"), DayOfWeek.MONDAY, Locale.US)
        assertEquals("0", s.todayText); assertNull(s.bestThisMonth); assertNull(s.bestThisYear)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*HistoryUiStateTest" --tests "*ScoresUiStateTest"`
Expected: compilation failure, unresolved references.

- [ ] **Step 3: Implement the mappings, ViewModels and screens**

- History: a `LazyColumn` with a month title per group and plain bordered rows (label left, steps right, a check icon with content description "Goal reached" when `goalReached`). With no months, the centred empty text from the Copy table.
- Scores: "Totals" with three rows, then "Records" with two cards showing steps and date, or "–" when the record is null.

- [ ] **Step 4: Run tests, lint and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 lint errors.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "Add History and Scores screens"
```

---

### Task 9: End-to-end check on the emulator

**Files:** none created; fixes go to the file that owns the defect.

**Interfaces:**
- Consumes: `DebugStepsReceiver`, the `skip_sensor_check` launch extra, the debug APK.

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew clean :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 lint errors.

- [ ] **Step 2: Boot the emulator and install**

```bash
SDK="$LOCALAPPDATA/Android/Sdk"; ADB="$SDK/platform-tools/adb.exe"
"$SDK/emulator/emulator.exe" -list-avds
```

Start the listed AVD in the background (`"$SDK/emulator/emulator.exe" -avd <name> -no-snapshot-save -no-boot-anim`), wait until `"$ADB" shell getprop sys.boot_completed` prints `1`, then:

```bash
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
```

If no AVD boots, stop here and report this task as not verified; do not claim the UI was checked.

- [ ] **Step 3: Check the no-sensor screen**

```bash
"$ADB" shell am start -n app.pedometer/.ui.MainActivity
"$ADB" shell uiautomator dump /sdcard/ui.xml && "$ADB" shell cat /sdcard/ui.xml
```

Expected: if the emulator has no step sensor, the dump contains `This phone can't count steps`. If it does have one, the permission screen (`Count your steps`) appears instead; note which.

- [ ] **Step 4: Check the three screens with seeded data**

```bash
"$ADB" shell pm grant app.pedometer android.permission.ACTIVITY_RECOGNITION
"$ADB" shell pm grant app.pedometer android.permission.POST_NOTIFICATIONS
"$ADB" shell am force-stop app.pedometer
"$ADB" shell am start -n app.pedometer/.ui.MainActivity --ez skip_sensor_check true
"$ADB" shell am broadcast -n app.pedometer/.debug.DebugStepsReceiver -a app.pedometer.DEBUG_ADD_STEPS --ei steps 6482
"$ADB" shell am broadcast -n app.pedometer/.debug.DebugStepsReceiver -a app.pedometer.DEBUG_ADD_STEPS --ei steps 12870 --es date <ISO date three days before the emulator's today>
```

Dismiss the battery dialog with "Not now", then dump the UI on each tab. Expected:
- Today: `6,482`, `of 10,000 steps`, `This week`, `This month`.
- History: four rows, newest first, the oldest showing `12,870` with `Goal reached`, the two in between showing `0`.
- Scores: `Best day this month` or `Best day this year` shows `12,870`.

Then change the goal to `5000` in the dialog and confirm `of 5,000 steps`; enter `12` and confirm the error `Enter a number from 500 to 100,000`.

- [ ] **Step 5: Commit any fixes and report**

Commit fixes with a message naming the defect. Report what was verified on the emulator and state plainly that real step counting, the notification, reboot and overnight behaviour still need the owner's on-phone checks listed in the spec's Testing section.
