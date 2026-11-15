# Pedometer Pal: step charts on the Scores tab

Date: 2026-11-15
Status: draft, waiting for the owner's approval
Builds on: `docs/superpowers/specs/2026-11-10-pedometer-design.md` and
`docs/superpowers/specs/2026-11-12-settings-notifications-widget-design.md`.
Everything there still holds unless this document changes it.

## Purpose

The owner wants to see their steps as graphs: a week day by day, a month
from the 1st to its last day, and a year from 1 January to 31 December.
Today the app has only numbers (totals and records), so there is no way to
see a pattern at a glance.

## Decisions made with the owner

| Topic | Decision |
|---|---|
| What each chart shows | Every day of the period: week = 7 days, month = 1st to last day, year = 1 January to 31 December |
| Where the charts live | At the top of the Scores tab (option A); still 4 tabs |
| Week and month | Bars, one per day |
| Year | A thin line, one point per day (365 or 366), because bars would be too thin to read |

## Approaches considered

- **A. Charts on top of Scores (chosen).** Keeps the four tabs; the chart
  takes over the job of the week and month totals, so Scores becomes
  "charts and records".
- **B. A new Charts tab.** Most room for the chart, but five tabs make the
  bottom bar crowded and the labels small.
- **C. Chart above the History list.** Chart and day list together, but
  the chart is squeezed, and a one-period chart sits awkwardly on top of a
  list that scrolls through every day.

For the drawing itself, a chart library was rejected: the charts are
simple, and drawing them with Compose's Canvas avoids a new dependency.

## The Scores tab

Top to bottom:

1. Title "Scores".
2. A Week / Month / Year switch. Opens on Week.
3. The period row: `‹  10–16 Nov  ›`, `‹  November 2026  ›`, `‹  2026  ›`.
   The arrows move one period back or forward.
4. The chart.
5. One line under the chart: the period's total and its average a day
   ("53,400 total · 7,629 a day"). When a day is selected, the line shows
   that day instead ("Thu 12 Nov · 10,402 steps").
6. The Records section as today: best day this month, best day this year.

The Totals section (Today, This week, This month) is removed: today's
count is on the Today tab, and the week and month totals are now the
chart's summary line for the current week and month.

## The charts

Shared by all three:

- **Goal line:** a dashed horizontal line at the current daily goal, with
  the goal written at its end. Past days are judged against the current
  goal, as History already does.
- **Scale:** from 0 to the larger of the goal and the period's best day,
  plus a little headroom, so the goal line is always visible.
- **Future days:** have no bar or point. Days before the app's first
  counted day are drawn as 0, as History does with gaps.
- **Selecting a day:** tapping a bar (or a spot on the year line, which
  picks the nearest day) selects that day and shows it in the summary
  line; tapping it again, or changing period or view, clears it.

Week: 7 bars, labelled with the day's first letter. Bars that reached the
goal are in the accent colour, the others in a muted colour.

Month: a bar for each day, labelled at 1, 10, 20 and the last day. Same
colours as the week.

Year: a thin line through one point per day from 1 January to 31 December,
labelled with month initials along the bottom. One colour; the goal line
shows which stretches were above the goal.

## Navigation between periods

- Opens on the current week, month or year.
- The forward arrow stops at the current period.
- The back arrow stops at the period that holds the first counted day;
  with no steps at all, both arrows do nothing.
- Each view keeps its own position while the app is open (switching Week
  → Month → Week returns to the same week). Nothing is saved after the app
  closes.

## Calls made for the owner (correct any of these)

- **Week start:** the owner said Monday to Sunday. The chart follows the
  phone's first day of the week, the same as the existing "This week"
  total, which is Monday unless the phone's region starts weeks on Sunday.
  Reason: the week chart and the week total should always agree.
- **Average a day:** divides by the days of the period that have already
  started (including today), not counting days before the first counted
  day. Reason: the current week shouldn't look worse just because it
  isn't over.
- **Year line colour:** one colour, not split above and below the goal.
  Reason: a two-colour line at this thinness is hard to read.
- **Totals section removed** from Scores (see above).

## Accessibility

The chart is one element for screen readers, read as the summary line
("November 2026: 186,000 total, 8,450 a day"). The arrows and the switch
have labels.

## Building it

- The chart data (the days of a period, the period label, navigation
  limits, totals and average) is plain logic in the `:core` module, with
  unit tests, alongside `StepStats`.
- The drawing is a Compose `Canvas` in the app module; Robolectric UI
  tests check the switch, the arrows, the summary line and selecting a day.
- Left to the builder: exact sizes, spacing and colours within the app's
  existing theme, and the period label formats for the phone's language.

## Not included

Comparing two periods, week or month totals as separate bars, saving the
chosen view, charts on the widget, exporting data, any change to the Today
or History tabs.
