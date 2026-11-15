# Pedometer Pal: settings, notification choices and a home-screen widget

Date: 2026-11-12
Status: approved
Builds on: `docs/superpowers/specs/2026-11-10-pedometer-design.md` (the
base spec). Everything there still holds unless this document changes it.

## Purpose

The permanent notification works as designed, but the owner does not want
it there all day. This change lets the owner switch it off, optionally be
told only when the daily goal is reached, edit the goal from a settings
screen, and see the count on a home-screen widget.

## Decisions made with the owner

| Topic | Decision |
|---|---|
| Notification control | Two independent switches: "Show step count" and "Tell me when I reach my goal" |
| Counting with the step count notification off | Periodic catch-up: no notification, steps collected about every 15 minutes and when the app is opened |
| Daily goal | Editable in Settings; the pencil on Today stays as a shortcut |
| Widget | One widget, one row tall, resizable from 1 to 4 cells wide, showing more as it widens |

## Scope

### Included

- A Settings screen reached from a gear icon on the Today screen.
- Two notification switches and the goal-reached notification.
- A second way of counting (periodic catch-up) used when the step count
  notification is off.
- A resizable home-screen widget.

### Not included

Widgets taller than one row, a choice of what the widget shows, reminder
notifications, per-day goals, any change to History or Scores.

## Settings

| Setting | Values | Default |
|---|---|---|
| Daily goal | 500 to 100,000 | 10,000 (unchanged) |
| Show step count | on / off | on (today's behaviour, also after updating) |
| Tell me when I reach my goal | on / off | off |

The four switch combinations:

| Show step count | Goal alert | Result |
|---|---|---|
| on | off | Permanent notification, continuous counting (as today) |
| on | on | As above, plus one alert a day when the goal is reached |
| off | on | No permanent notification; one alert a day when the goal is reached |
| off | off | No notifications at all |

## Counting modes

"Show step count" selects the counting mode. Both modes write through the
same repository and use the same accumulator, so switching modes never
loses or double-counts steps.

### Continuous (Show step count on)

Unchanged from the base spec: the `health` foreground service with the
permanent notification.

### Periodic catch-up (Show step count off)

- The foreground service is stopped and its notification disappears.
- A background job runs about every 15 minutes. Each run registers the
  step sensor, waits up to 10 seconds for its current cumulative value,
  records it, and unregisters.
- The same one-shot reading happens when the app is opened, after a
  reboot, and at the day rollover (the existing 23:48 to 23:58 alarm).
- While the app is on screen it listens to the sensor directly, so the
  Today count is live.
- Android defers background jobs while the phone lies idle. That delays
  catch-up only while no steps are being taken.

Accepted limits of this mode:

- The count, the widget and the goal alert can lag by up to about 15
  minutes.
- Steps walked between the last catch-up and midnight are recorded on the
  next day unless the rollover alarm catches them.
- Steps walked between the last catch-up and the phone being switched off
  are lost, because the sensor restarts from zero at boot.
- The sensor is hardware-counted on most phones, but some phones stop
  counting, or do not report a value on demand, while no app is
  listening. On such a phone this mode misses steps; continuous mode is
  the remedy.

### Switching

Changing "Show step count" takes effect immediately: turning it off stops
the service and schedules the background job; turning it on cancels the
job and starts the service. The same decision is re-applied at app start
and after a reboot.

## Goal-reached notification

- Sent the first time on a given day that today's steps are greater than
  or equal to the goal, at most once per calendar day.
- Checked every time steps are recorded, in either counting mode, and
  when the goal or the switch changes. Lowering the goal below today's
  count therefore sends it, if it has not been sent today.
- Title "Goal reached", text "You walked 10,000 steps today." with the
  goal formatted with grouping. Tapping it opens the app. It uses its own
  notification channel ("Goal reached", default importance, so it may
  sound), separate from the silent step count channel.
- Turning the switch on requests the notification permission on Android
  13+ if it is missing. If the permission is refused, the switch returns
  to off.

## Settings screen

- A gear icon at the top right of the Today screen opens it. It is a
  full screen with a back arrow; the bottom tabs are hidden while it is
  open.
- Rows: "Daily goal" (shows the current goal, opens the existing goal
  dialog), "Show step count" (switch) and "Tell me when I reach my goal"
  (switch), with the explanatory line under the switches when the step
  count is off.
- The "notifications are off" hint on Today is shown only when a
  notification switch is on and Android is blocking the app's
  notifications.

## Widget

- One widget in the launcher's widget list, named "Pedometer Pal". It is
  added at 2 × 1 and can be resized horizontally from 1 to 4 cells; it is
  always one row tall.
- Content by width:

| Width | Shows |
|---|---|
| 1 cell | Walking icon above today's steps |
| 2 cells | Icon, today's steps, "of 10,000" |
| 3 cells | The above plus a progress bar towards the goal |
| 4 cells | The above plus this week's total with the label "this week" |

- Tapping the widget opens the app.
- Colours follow the phone's light or dark theme (and its dynamic colours
  on Android 12+).
- It refreshes whenever steps are recorded, at most once every 5 seconds,
  and when the goal changes. In periodic mode that means about every 15
  minutes; after midnight it can show the previous day's count until the
  next catch-up.
- Before the activity permission is granted, or on a phone without a step
  sensor, it shows 0 and still opens the app when tapped.

## Architecture

Changes are confined to `:app`, plus one pure rule in `:core`.

- **`:core`**: `GoalAlert.shouldNotify(steps, goal, today, lastAlertDate,
  enabled)`, the once-a-day rule above.
- **Settings storage**: the existing DataStore gains "show step count",
  "goal alert enabled" and "last goal alert date".
- **`StepSensorReader`**: the one place that talks to the step sensor. It
  offers a one-shot reading with a timeout and a live stream of readings.
  The foreground service, the background job, the rollover receiver and
  the activity all use it, replacing the listener code now inside the
  service.
- **`StepEffects`**: called after steps are recorded or settings change.
  It refreshes the widget and sends the goal-reached notification when the
  rule says so. It is throttled to once every 5 seconds, with the latest
  state always delivered.
- **`CountingController`**: reads "show step count" and puts the app in
  the matching mode (service running, or background job scheduled). Called
  at app start, after boot and when the switch changes.
- **Background job**: WorkManager periodic work, 15-minute interval.
- **Widget**: Jetpack Glance, with one layout per width. Its display state
  comes from a pure mapping function, like the three screens.
- **Settings screen**: a Compose screen with its own ViewModel, on a new
  navigation route.

## Permissions

No new manifest permissions. `POST_NOTIFICATIONS` is now also requested
when the goal alert is switched on.

## Error and edge cases

- **Sensor gives no value within 10 seconds** (one-shot reading): the run
  records nothing and the next run tries again; no steps are lost on
  phones that keep counting, because the sensor value is cumulative.
- **Activity permission missing or no sensor**: neither counting mode
  starts; the switches still save their values.
- **Notification permission revoked later**: the goal alert is silently
  skipped and not marked as sent, so it can still arrive that day once
  notifications are allowed again.
- **Mode switched while the app is open**: the on-screen live listener
  keeps the count current across the switch.
- **Widget present but the app has not run since boot**: the widget shows
  the last stored count; the background job or the service updates it.

## Testing

- **Automated:**
  - `GoalAlert` rule: below goal, at goal, already sent today, sent
    yesterday, disabled.
  - Settings storage: defaults and persistence of the two switches and the
    alert date.
  - Widget display state: values and text for each of the four widths.
  - `CountingController` decision as a pure function of (can count, show
    step count).
  - Settings screen on the JVM: the rows are shown and a switch change is
    saved.
  - The existing 49 tests keep passing.
- **Manual (on the phone, by the owner):**
  - Turn "Show step count" off: the notification disappears; walk, wait up
    to 15 minutes or open the app, and the count has risen.
  - Turn it back on: the notification returns and no steps were lost or
    counted twice.
  - Turn on the goal alert, set a low goal, walk past it: one notification
    arrives, and no second one that day.
  - Add the widget, resize it through all four widths, tap it.
  - Reboot with the step count off and check counting resumes.
