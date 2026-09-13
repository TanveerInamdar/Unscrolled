# FoxtrotAlpha (Reels Blocker)

Android app that blocks Instagram Reels and YouTube Shorts, stores local usage/health/calendar facts in Room, and optionally speaks a generated roast after a block. Package: `com.foxtrotalpha.reelsblocker`. minSdk 26, targetSdk 34.

There is no backend of our own. Device APIs write into a local SQLite database. Gemini, ElevenLabs, and Backboard are called only when the user hits those features.

## Architecture

```
AccessibilityService  -->  detect player UI  -->  GLOBAL_ACTION_BACK
                                              -->  insert block_events
                                              -->  Gemini line + ElevenLabs TTS (optional)

MainActivity onResume -->  ScreenTimeSync (UsageStats, 30 days)
                      -->  HealthConnectSync (7 days)
                      -->  CalendarSync (today)
                      -->  Room (foxtrot_alpha.db)
                      -->  Dashboard / Insights ViewModels (Flows)

Coach tab             -->  snapshot from Room  -->  Backboard thread
```

**Blocking.** `ReelsBlockerService` is an Accessibility Service scoped to Instagram and YouTube packages. On window state/content changes it walks the active tree with `ReelsDetector`. Instagram Reels are identified by a visible `clips_viewer_view_pager`; YouTube Shorts by `reel_player_page_container` (also ReVanced / YouTube Kids). Fallback is a selected Reels/Shorts tab. A match triggers BACK, a toast, a `block_events` row, then `VoiceRoastCoordinator` if audio is enabled. A 1.5s cooldown avoids double-fires.

**Storage.** Room database `foxtrot_alpha.db`:

| Table | Written by | Contents |
| --- | --- | --- |
| `block_events` | accessibility service, live | timestamp, date, package, detection reason |
| `daily_app_usage` | `ScreenTimeSync` | per-day, per-app foreground ms |
| `tracked_apps` | seed on first DB create | which packages count as unproductive |
| `daily_health_metrics` | `HealthConnectSync` | steps, active/total calories |
| `sleep_sessions` | `HealthConnectSync` | bedtime, wake, duration |
| `calendar_events` | `CalendarSync` | today's instances |

Unproductive time is computed at query time as usage joined to `tracked_apps.is_unproductive`. It is not stored as a pre-rolled total.

**UI.** `MainActivity` is a three-tab shell (Today, Coach, Insights) gated on usage-access permission. `DashboardViewModel` / `InsightsViewModel` observe Room. Settings toggles blocking, voice roast, and profanity, and deep-links into system permission screens.

## How data is fetched

Nothing is polled in the background. Each source is synced when the app is opened or resumed, and again on pull-to-refresh on Today and Insights. Missing permission skips that source; it does not crash.

**Screen time.** `UsageStatsManager.queryEvents()` over the last 30 days. Resume-to-pause intervals are summed per package per local calendar day (`ScreenTimeCollector`), then the table is replaced. Android does not keep a longer detailed event window than that.

**Health Connect.** If the Health Connect client is installed and any of steps / sleep / calories is granted, `aggregateGroupByPeriod` with `Period.ofDays(1)` fills metrics for 7 local days. Sleep uses `SleepSessionRecord` in the same window. Canonical totals are Health Connect aggregates, not Google Health's displayed numbers (those can differ when multiple step sources exist).

**Calendar.** `CalendarContract.Instances` for local today. Today's partition is deleted and re-inserted.

**Blocks.** Not a poll. Inserted at the moment BACK is issued.

**Voice roast (on block).** Reads today's Room snapshot (`RoastContextBuilder`: blocks, unproductive time, app minutes, steps, sleep, next calendar event). Gemini Flash Lite returns one short line. ElevenLabs synthesizes MP3; on TTS failure the device TTS speaks the same line. Requires `GEMINI_API_KEY` and `ELEVENLABS_API_KEY` in `local.properties`.

**Coach (on send).** First message of a thread prepends `DEVICE_CONTEXT_JSON` from Room (today stats, calendar, last 7 days). Later messages send only the user text. Backboard (`app.backboard.io`) holds the assistant, thread, and memory. Requires `BACKBOARD_API_KEY`.

## Run

### Requirements

- Android Studio (or JDK 17 + Android SDK)
- Physical device recommended. Accessibility + usage stats + Health Connect are unreliable or incomplete on most emulators.
- Health Connect installed on the device if you want steps/sleep/calories.

### Keys

Copy SDK path and keys into `local.properties` at the repo root (gitignored). Gradle injects them as `BuildConfig` fields.

```
sdk.dir=<Android SDK path>
GEMINI_API_KEY=
ELEVENLABS_API_KEY=
ELEVENLABS_VOICE_ID=pNInz6obpgDQGcFmaJgB
BACKBOARD_API_KEY=
```

Blocking and the dashboard work with empty keys. Voice roast and coach no-op or error until the relevant keys are set.

### Build and install

Android Studio: open the repo, select a device, Run.

CLI from the repo root:

```
.\gradlew.bat :app:installDebug
```

On macOS/Linux: `./gradlew :app:installDebug`.

Then:

1. Open **Reels Blocker**. Grant usage access (required gate).
2. Enable the accessibility service for this app. On a sideloaded debug build, Android may hide that toggle until Settings → Apps → Reels Blocker → overflow menu → Allow restricted settings.
3. Optional: Health Connect permissions (steps, sleep, calories) and calendar.
4. Keep blocking on. Open Instagram Reels or YouTube Shorts; the app should press BACK and log a block.

### Demo roast from a connected phone

Debug build + USB debugging. Pulls `foxtrot_alpha.db` via `adb run-as` and runs the same Gemini → ElevenLabs path to `tools/demo_output/demo_roast.mp3`:

```
python tools/demo_voice_roast.py
```

## Layout

```
app/src/main/java/com/foxtrotalpha/reelsblocker/
  ReelsBlockerService.kt     accessibility entry
  detector/ReelsDetector.kt  view-id / tab matching
  usage/                     UsageStats sync
  health/                    Health Connect sync + permission contract
  calendar/                  CalendarContract sync
  data/                      Room DB, DAOs, entities, IntegrationSync
  voice/                     Gemini, ElevenLabs, roast coordinator
  coach/                     Backboard client, snapshot, chat VM
  ui/                        Today / Insights dashboard
  settings/                  toggles and permission CTAs
tools/demo_voice_roast.py    offline roast using the phone DB
```
