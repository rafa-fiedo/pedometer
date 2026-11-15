# Pedometer Pal: calories, distance and a first-day-of-week setting

Date: 2026-11-15
Status: draft, waiting for the owner's approval
Builds on the earlier specs in `docs/superpowers/specs/`. Everything there still holds
unless this document changes it.

## Purpose

The owner wants to see roughly how many calories their walking burns, based on their own
height and weight. They also want to choose whether weeks start on Monday or Sunday,
because people differ and the phone's region doesn't always match what they want.

## Decisions made with the owner

| Topic | Decision |
|---|---|
| How calories are worked out | From the owner's height and weight, entered once in Settings |
| What else it gives | Distance (km), shown next to calories |
| Where it shows | Today: one new line under the ring. Scores: the chart's summary line. History and the widget stay as they are |
| Before height and weight are entered | No guess: Today shows a link to Settings instead |
| Units | cm, kg and km only for now |
| First day of the week | A setting: Phone default / Monday / Sunday |

## Approaches considered

- **Height and weight in Settings (chosen).** Personal enough to be useful, and it gives
  distance for free.
- **A fixed estimate per step.** Nothing to enter, but 30–40% off for heavier or lighter
  people.
- **Also counting walking speed.** Needs steps recorded through the day rather than daily
  totals; a much bigger change for a small gain.

For building it there is one sensible way: the two numbers and the week setting are
stored with the other settings, and the maths is plain code in the `:core` module next to
the existing step maths, so it can be tested on its own. Storing them in the step
database instead would mix settings with daily counts for no benefit.

## The calculation

- Step length = height × 0.415.
- Distance (km) = steps × step length.
- Calories (kcal) = 0.5 × weight (kg) × distance (km). These are walking calories on top
  of what the body burns at rest, as most step counters show.
- Example: 10,000 steps at 178 cm and 72 kg → 7.4 km and 266 kcal.
- Shown as whole kcal and km to one decimal, formatted for the phone's language
  ("266 kcal · 7.4 km").
- Allowed values: height 100–250 cm, weight 30–300 kg, whole numbers. Anything else is
  refused in the dialog.

## Settings

New sections between Goal and Notifications:

```
You
  Height                  178 cm        (or "Not set")
  Weight                  72 kg         (or "Not set")
Week
  First day of the week   Phone default (Monday)
```

- Height and weight open a number dialog like the daily goal's.
- First day of the week opens a choice of three: Phone default (showing which day that
  is), Monday, Sunday. The default is Phone default, so nothing changes for existing users.

## Today

Under "of 10,000 steps", one line: "266 kcal · 7.4 km" for today. If height or weight is
missing, the line is instead a link, "Add your height and weight to see calories", which
opens the Settings tab.

## Scores

The chart's summary line becomes "53,400 total · 7,629 a day · 1,420 kcal" (calories for
the whole period shown, at 178 cm and 72 kg). A selected day reads "Tue 10 Nov · 6,482 steps · 172 kcal".
Without height and weight, the lines stay as they are today.

## First day of the week

The chosen day applies everywhere a week appears: the Today "This week" total, the
widget's week total and the Scores week chart. A change shows at once, including on the
widget.

## Calls made for the owner (correct any of these)

- Step length factor 0.415 and calorie factor 0.5: common published rules of thumb; good
  for a rough figure, not medical accuracy.
- Calories also in the selected-day line on Scores, so the two lines match.
- Distance shown only on Today, not on Scores, to keep the summary line short.
- "Not set" height or weight is allowed (the owner can clear them by leaving the field
  empty), and calories disappear again.

## Not included

Miles, pounds or feet; calories on History or the widget; a calorie goal; resting
calories; sex or age in the formula; any change to how steps are counted.
