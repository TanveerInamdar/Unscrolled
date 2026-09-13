# Reels Blocker (FoxtrotAlpha)

An Android app that blocks Instagram Reels using an Accessibility Service, while leaving Stories, Home feed, Explore, and DMs untouched. When a Reel is detected on screen, the app presses Back automatically and notifies the user.

## How it works

### Components

| Component | Role |
|---|---|
| `ReelsBlockerService` | AccessibilityService scoped to `com.instagram.android`. On window state/content changes, runs the detector on the root node and presses Back (`GLOBAL_ACTION_BACK`) when a Reel is detected, with a 1.5 s cooldown to avoid spamming back presses. |
| `ReelsDetector` | The detection logic (see below). |
| `MainActivity` | Dashboard: blocking on/off toggle, service status, shortcut to Accessibility settings, notification permission request (Android 13+). |
| `BlockerPreferences` | SharedPreferences-backed enable/disable toggle (default: enabled). |
| `BlockFeedback` | On each block: short vibration, toast, and an auto-dismissing notification, all including the block reason. |
| `AccessibilityUtils` | Checks whether the service is enabled and deep-links to Accessibility settings. |

The service config (`res/xml/accessibility_service_config.xml`) restricts events to Instagram only, listens for `typeWindowStateChanged | typeWindowContentChanged`, and sets `flagReportViewIds` — required for view-ID-based detection.

### Detection (v2, view-ID based)

**Key insight:** Instagram internally calls Reels **"clips"**. Every immersive Reels player — the Reels tab, For You / Explore reels, Suggested feeds, and reels opened from DMs or links — is hosted inside a view with the resource ID:

```
com.instagram.android:id/clips_viewer_view_pager
```

Stories, confusingly, use a completely different family of view IDs (`reel_viewer_*` — Instagram's internal name for stories is "reel"), so they can never match this check. This is the same approach used by maintained open-source blockers such as [Scrolless](https://github.com/duartebarbosadev/Scrolless), AntiScroll, and Curbox.

`ReelsDetector.evaluate()` blocks when either:

1. **`REEL_PLAYER_VISIBLE`** — a `clips_viewer_view_pager` node is found via `findAccessibilityNodeInfosByViewId()` that is `isVisibleToUser` **and** has non-zero on-screen bounds. The visibility/size check is essential: recent Instagram builds keep this node in the tree at all times with zero size, which would otherwise cause false positives on every screen (see [Scrolless PR #69](https://github.com/duartebarbosadev/Scrolless/pull/69)).
2. **`REELS_TAB_SELECTED`** (fallback) — a clickable node with content description "Reels" that is selected and located in the bottom ~18% of the screen (the bottom navigation bar). This is kept as a safety net in case Instagram renames the clips viewer ID.

Anything else — Stories, Home feed, Explore grid, DMs, profiles — is allowed.

## History / lessons learned

**v1** used English-text heuristics on content descriptions ("Like"/"Comment"/"Share" action bar, "audio" track labels, "Suggested"/"For You"/"Reels" headers, story-viewer markers like "Close"/"Reply"/"story"). This proved fragile:

- Initially, Stories were falsely blocked (misread as DM reels).
- Fixing that broke For You reels: the story-viewer check ran first and treated any immersive screen with a "Back" button or a "Like" label (and no detectable audio track) as a story — which described For You reels exactly, so they slipped through.
- Text heuristics were also locale-dependent (English-only) and broke whenever Instagram tweaked its labels.

**v2** (current) replaced all text heuristics with the view-ID detection described above: one robust, locale-independent check that covers every Reels surface and structurally cannot match Stories.

## Known limitations

- If Instagram renames `clips_viewer_view_pager`, primary detection breaks; only the Reels-tab fallback (English-only) remains until the ID is updated.
- The Reels-tab fallback depends on the English "Reels" content description.
- Blocking reacts after the Reel appears — the user may see a brief flash of the Reel before Back is pressed.
- The 1.5 s cooldown means rapid re-entry into Reels within that window isn't re-blocked immediately.
