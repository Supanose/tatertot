# Tater Tots

A baby sleep tracker with a home-screen widget, built around **pre-settling**: it learns your baby's own wake windows and tells you when to start winding down for the next nap. The mascot, Tater, mirrors your baby's state: awake, getting tired, napping, or asleep for the night in his nightcap.

Version 0.1: Android only, one phone, data stays on the device.

## Getting it on your phone

1. Install **Android Studio** (free): https://developer.android.com/studio
2. **File → Open** and pick this `TaterTots` folder. The first Gradle sync downloads the build tools, which takes a few minutes.
   - If Studio offers to upgrade the Android Gradle Plugin, you can accept, or ignore it. The project builds as-is.
3. On your phone, turn on **Developer options → USB debugging**: tap *Settings → About phone → Build number* 7 times, then enable it. Plug the phone in.
4. Press the green **Run ▶** button. The app installs and opens.
   - Or use **Build → Build App Bundle(s) / APK(s) → Build APK(s)** and copy the `.apk` to your partner's phone to install it there.
5. To add the widget, long-press your home screen, choose **Widgets**, then **Tater Tots**, and drag it on. The widget is 4×2 and can be resized.

On first launch it imports George's history (8–19 Oct 2026) from `app/src/main/assets/seed.json`, so predictions work immediately. Set his birthday under **Settings**.

## What it does

- **Widget:** one tap for *Asleep* / *Awake* and *Feed*, a live "awake for" timer, the next nap time with a likely range, and a "start settling in mm:ss" countdown.
- **Settling reminder:** a notification *N* minutes before the predicted nap. The default is 15 minutes, and you can change it in Settings.
- **Today:** Tater, the current status, the prediction, big buttons, and today's timeline. Tap any entry to fix its times, location (cot, pram, cuddles…) or notes.
- **History:** day by day, with naps, total day sleep and feeds.
- **Insights:** typical wake window, nap lengths, day sleep per day, night wakes, and **how accurate the predictions have been**. It replays the last 7 days, so it updates whenever you correct a log.
- **Export CSV:** under Settings.

## How predictions work (`engine/Predictor.kt`)

- **Next nap:** the time he last woke, plus the **median of his last 8 wake windows from the past 7 days**. The range covers the middle half of those windows, with at least ±10 minutes. The first nap of the day uses morning windows once there are at least 3.
- **Wake-up:** the nap start plus the median of his recent nap lengths. The range is wider, because nap length is much harder to predict.
- **Not enough data yet:** it falls back to age-based rule-of-thumb wake windows.
- **Bedtime:** sleeps starting after 19:15 (changeable) count as night sleep, and no prediction is made overnight.

Backtested on George's data (13–19 Oct), next-nap predictions were off by about **12 minutes on average, with 79% within 15 minutes**. That's about the same as the ChatGPT predictions your partner was getting. Nap length is the harder part: wake-up predictions were off by about 13 minutes on average.

Run the engine tests with `./gradlew test` (or right-click `PredictorTest` → Run).

## Project layout

```
app/src/main/java/app/tatertots/
  engine/     Model.kt, Predictor.kt  ← pure Kotlin, unit-tested, no Android code
  data/       Store.kt                ← JSON file storage, seed import, CSV export
  widget/     SleepWidget.kt          ← home-screen widget (RemoteViews + Chronometers)
  system/     Reminders.kt            ← settle notification + widget refresh alarms
  ui/         Compose screens, Theme.kt (potato palette), Tater.kt (animated mascot)
app/src/main/res/drawable/tater_*.xml ← Tater vector art used by the widget and icon
```

## Known limits / ideas for v2

- **One phone only.** Syncing two phones would need Firebase or Supabase.
- Night wakes from the spreadsheet weren't imported, because the old data only had partial nights.
- Feeds are logged but not yet used for predictions.
- Reminders use battery-friendly alarms, so they can fire a few minutes late when the phone is in deep sleep.
- Possible extras: a daily AI-written summary, a smaller 2×1 widget, and widget themes.

Tater spots patterns; he isn't a doctor. Feed on demand, follow safe sleep guidance from The Lullaby Trust, and trust your instincts over any prediction.
