# Pedometer Pal: round 1 × 1 widget and refreshing the widget on demand

Date: 2026-11-17
Status: draft, waiting for the owner's approval
Builds on the earlier specs in `docs/superpowers/specs/`. Everything there still holds
unless this document changes it.

## Purpose

The owner wants a 1 × 1 widget that sits on the home screen like an app icon: a circle the
size of an icon on a transparent background, with the footprints and today's steps inside
and a ring that fills towards the goal. They also find the widget updates too rarely and
want a way to refresh it.

## Why the widget is slow today

With "Show step count" off (the default since the last change), steps are collected in
the background at most every 15 minutes, and Android often stretches that to an hour or
more while the phone is idle. The widget only changes when steps are collected or the app
is opened. Android allows nothing faster than 15 minutes without the permanent
notification, so an interval setting cannot help; a refresh on tap can.

## Decisions made with the owner

| Topic | Decision |
|---|---|
| Round widget style | B: a circle with the footprints and today's steps, and a goal ring around the edge |
| Round widget in the picker | A new, fifth entry; the square 1 × 1 stays |
| Refreshing | A refresh arrow on the 2 × 1 to 4 × 1 widgets; tapping the round widget refreshes it |

## Approaches considered

- **Refresh on tap (chosen).** Reads the step sensor there and then and redraws within a
  few seconds; works in both counting modes.
- **An update-interval setting.** Can't go below Android's 15-minute floor, so it can't make
  the widget feel live.
- **Live updates with the step count switched on.** Already possible; costs the permanent
  notification. Stays available as it is.

For the goal ring: Glance (the widget toolkit) has no ring that shows progress, so the ring
and the circle are drawn into a small picture each time the widget updates, and the
footprints and number are placed on top. The alternative, a set of ready-made ring
pictures in fixed steps, would make the ring jump in coarse steps.

## The round widget

- Picker name: "Steps round 1 × 1". Starts at 1 × 1; not resizable.
- Everything outside the circle is transparent.
- The circle is 56dp across (a typical app icon), centred in the cell, smaller if the cell is
  smaller. Inside: the footprints on top, today's steps below ("6,354"), shrinking the text
  for 6-digit counts so it fits.
- Ring: a thin ring just inside the circle's edge, a faint full track plus the filled part
  for today's share of the goal, starting at the top and going clockwise; full once the goal
  is reached.
- Colours follow the widget's existing theme colours (light circle, primary-coloured ring
  and footprints), so it matches the other widgets in light and dark mode.
- Tapping it refreshes the count (see below) instead of opening the app.

## Refreshing

- The 2 × 1, 3 × 1 and 4 × 1 layouts get a small refresh arrow at their right end. Tapping
  the arrow refreshes; tapping anywhere else still opens the app. The square 1 × 1 has no
  room for an arrow and keeps opening the app.
- A refresh reads the step sensor once (waiting up to 5 seconds for a reading), saves any
  new steps, then redraws every widget. If no reading arrives, the widgets are redrawn with
  what is already saved, so the tap always does something visible.
- If the app may not read the sensor (no activity permission), a refresh just redraws.

## Calls made for the owner (correct any of these)

- 56dp circle, matching a common app icon size; launchers differ, so it may look a little
  bigger or smaller than icons on some phones.
- Six-digit step counts use a smaller font inside the circle.
- The round widget is fixed at 1 × 1 (a round widget stretched wider would look odd).
- No refresh arrow on the square 1 × 1.

## Not included

Changes to the counting interval, a refresh button inside the app, round widgets larger than
1 × 1, showing calories on any widget.
