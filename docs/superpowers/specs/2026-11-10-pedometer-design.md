# Pedometer Pal for Android: design

Date: 2026-11-10
Status: approved

## Purpose

A simple personal pedometer for Android. It shows today's step count, a
per-day history, totals for the day, week and month, and two records: the
best day of the month and the best day of the year. An iPhone version may
follow later, so the non-Android-specific logic is kept portable.

## Decisions made with the owner

| Topic | Decision |
|---|---|
| Tech stack | Native Kotlin with Jetpack Compose |
| Background counting | Always-on foreground service with a permanent notification |
| Daily goal | Yes, adjustable, 10,000 steps by default |
| Audience | The owner's own phone first; no Play Store release yet |
| Data | Stays on the phone; no account, no network access |
| Language | English |

## Scope

### Included

- Today screen: today's steps, progress ring towards the goal, this week's
  and this month's totals, goal editing.
- History screen: one row per day, newest first, grouped by month, with a
  mark on days that reached the goal.
- Scores screen: totals (today, this week, this month) and records (best
  day this month, best day this year).
- Continuous counting while the app is closed, surviving reboots.
- First-run permission flow and a battery-saving exemption prompt.

### Not included

Distance, calories, charts, cloud backup, home-screen widgets, export,
multiple languages, per-day goal history, an iPhone build.

## Definitions

All dates are the phone's local calendar dates at the moment steps are
recorded.

- **Today:** steps recorded for the current local date.
- **This week:** sum from the first day of the current week through today.
  The first day of the week follows the phone's locale
  (`WeekFields.of(Locale.getDefault())`).
- **This month:** sum over the current calendar month through today.
- **Best day this month:** the day with the most steps in the current
  calendar month, today included. Ties go to the earliest day. If no day
  has any steps, the record shows a dash.
- **Best day this year:** the same rule over the current calendar year.
- **Goal reached:** a day's steps are greater than or equal to the current
  goal. Changing the goal re-evaluates the marks on past days; goals are
  not stored per day.
- **Goal range:** 500 to 100,000 steps, default 10,000.
- **History range:** from the first day with a record through today. Days
  in between with no record are shown as 0.

## Architecture

Two Gradle modules.

### `:core` (pure Kotlin, no Android dependencies)

Holds the logic that would be shared with an iPhone build. Dates use
`kotlinx-datetime` rather than `java.time`, so the module has no
JVM-only types in its API. The first day of the week is passed in by
`:app`, which reads it from the phone's locale.

- `DayCount(date: LocalDate, steps: Int)`: one day's total.
- `StepStats`: given a list of `DayCount`, today's date, the first day of
  the week and the goal, returns the totals, the two records and the
  goal-reached flags described under Definitions.
- `StepAccumulator`: turns raw sensor readings into steps to add.
  - Input: previous state (last sensor value, last boot count, both
    nullable) and a new reading (sensor value, current boot count).
  - Output: steps to add and the new state.
  - Rules:
    - No previous state: add 0 and store the reading as the baseline.
    - Boot count changed: the sensor restarted from zero, so add the full
      new sensor value.
    - Same boot and the value went up: add the difference.
    - Same boot and the value went down (should not happen): add 0 and
      re-baseline.

### `:app` (Android)

- **Storage**
  - Room database with two tables: `daily_steps(date TEXT PRIMARY KEY,
    steps INTEGER NOT NULL)`, date in ISO format (`2026-11-10`), and a
    single-row `counter_state` holding the accumulator state (last sensor
    value, last boot count). Each reading updates both in one
    transaction, so a crash cannot double-count or lose steps.
  - DataStore preferences: the goal and whether the battery prompt has
    been shown.
  - `StepRepository` is the only class the UI and the service use to read
    and write this data. It exposes the day list as a `Flow`, so the UI
    updates live while walking.
- **Step counting**
  - `StepCounterService`: foreground service of type `health`. It
    registers a listener on `Sensor.TYPE_STEP_COUNTER`, passes each
    reading through `StepAccumulator` with the current
    `Settings.Global.BOOT_COUNT`, and adds the result to the current
    date's row.
  - The service owns the permanent notification: low importance, silent,
    showing today's steps and the goal, refreshed at most once every
    5 seconds. Tapping it opens the app.
  - `BootReceiver` starts the service after `BOOT_COMPLETED`.
  - The app also starts the service every time it is opened, which covers
    the case where the system stopped it.
  - Day rollover: a non-exact alarm shortly before midnight (delivered
    between 23:48 and 23:58) wakes the phone and flushes the sensor, so
    steps walked before midnight are recorded on the day that is ending.
    Steps taken after the alarm, in the last minutes of a day, can still
    be recorded on the following day. A phone in deep sleep receives the
    alarm late, but it is lying still then and has no steps to flush.
- **UI (Jetpack Compose, Material 3)**
  - One activity with a bottom navigation bar: Today, History, Scores.
  - One ViewModel per screen, each reading from `StepRepository` and
    computing its display state with `StepStats`.
  - Goal editing is a dialog on the Today screen with a number field
    validated against the goal range.
  - Light and dark theme follow the system setting.
  - The screens re-read the current date when the app comes to the
    foreground and when the date changes while it is open, so "today"
    never shows yesterday's number.

## Permissions and first run

| Permission | Why | When asked |
|---|---|---|
| `ACTIVITY_RECOGNITION` | Required to read the step sensor | First launch |
| `POST_NOTIFICATIONS` | Show the permanent notification (Android 13+) | First launch |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` | Run the counting service | Granted at install |
| `RECEIVE_BOOT_COMPLETED` | Restart counting after a reboot | Granted at install |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Offer the battery-saving exemption | Granted at install |
| `WAKE_LOCK` | Keep the phone awake for a few seconds to record steps at the day rollover | Granted at install |

First-run flow:

1. A short screen explains that the app needs physical-activity access to
   count steps, then requests `ACTIVITY_RECOGNITION`.
2. On Android 13+, it requests `POST_NOTIFICATIONS`.
3. Once activity access is granted, the service starts and the Today
   screen appears.
4. One time only, the app offers to exempt itself from battery
   optimisation, with a short explanation and a "Not now" option.

The battery exemption permission is restricted on Google Play. That is
acceptable for a personal build and must be revisited before any store
release.

## Error and edge cases

- **No step sensor:** the app shows a full-screen message that this phone
  cannot count steps, and does not start the service.
- **Activity permission refused:** the app shows an explanation with a
  button that asks again, or opens the app's system settings if Android
  no longer shows the request dialog.
- **Notification permission refused:** counting still works; the Today
  screen shows a one-line hint that the notification is hidden.
- **Reboot:** handled by the boot-count rule in `StepAccumulator`. Steps
  taken between the reboot and the service restarting are still counted,
  because the sensor counts from boot.
- **Service stopped by the system:** counting resumes the next time the
  app is opened or the phone reboots. Steps taken in between are
  recovered where the sensor kept counting, since the sensor value is
  cumulative within one boot. Recovered steps are all recorded on the
  day counting resumes, even if some were walked on an earlier day.
- **Time zone or clock change:** steps are recorded under whatever the
  local date is at that moment; existing rows are not rewritten.

## Platform targets

- Minimum: Android 10 (API 29), the first version with the
  `ACTIVITY_RECOGNITION` runtime permission.
- Compile and target SDK: the newest stable SDK available in the Android
  Studio installation on the build machine, fixed in the implementation
  plan.
- App name: "Pedometer Pal". Application ID: `app.pedometer`; the ID can
  change before any store release.

## Testing

- **Automated (JUnit, `:core`):**
  - `StepStats`: totals across week and month boundaries, week start on
    Monday and on Sunday, records with ties and with no data, goal flags,
    gap days counted as 0.
  - `StepAccumulator`: first reading, normal increase, reboot, decreasing
    value.
- **Manual (on a real phone, by the owner):**
  - Walk and watch the Today count and the notification rise.
  - Close the app, walk, reopen: the steps are there.
  - Reboot, walk, open the app: no steps lost or double-counted.
  - Leave it overnight: yesterday's total appears in History and Today
    starts from 0.
  - Change the goal: ring and history marks update.
  - Refuse each permission once and check the messages.

The Android emulator cannot reproduce real walking, so on-device checks
are the only verification of the counting path.

## Path to iPhone

Not part of this work. If it is ever wanted, `:core` moves into a Kotlin
Multiplatform module unchanged, storage and screens can follow with
Room and Compose Multiplatform, and only the step source is written anew
for iOS. Building for iPhone requires a Mac.
