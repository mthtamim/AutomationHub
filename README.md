# Flows — standalone automation app (SKELETON)

**Status: skeleton pushed to PhoneLink-Flows, NOT built by CI, NOT phone-proven.**
CI is the final judge for compile errors — local validation cannot catch
Kotlin type/API errors. The PhoneLink source tree was
only read, never modified.

## Origin

Extracted from the PhoneLink v2.25.2 source tree
(`~/workspace/github-push/phonelink/`, package `com.mthtamim.phonelink`),
because PhoneLink had grown heavy and the automation side — Rules and
Flows — was loosely coupled enough to split out:

- The Rules engine (`AppRules.kt`, `Automation.kt`) never called
  `FlowEngine`; only `AutomationActivity.kt`, `ThemeColors.kt` and
  `ShizukuHelper.kt` referenced Flow code.
- `FlowService` was already its own foreground service.

## Identity

- Package / applicationId: `com.mthtamim.phonelinkflows`
  (differs from `com.mthtamim.phonelink` and `com.mthtamim.automationlab`)
- App label: **Flows**
- versionCode 1, versionName 1.0.0
- compileSdk 34, minSdk 26, targetSdk 34, arm64-v8a only (same trade-off
  as PhoneLink: no emulator / 32-bit-only installs)

## Home screen

`FlowsMainActivity` mirrors PhoneLink's Automation **Flows tab** exactly
in behaviour: a service row (switch + live/stale/off text), flow cards
with a toggle, a trigger summary, and pill buttons **Run now / Edit /
Duplicate / Log / Delete**, plus a **+ FAB** that opens
`FlowEditorActivity` for a new flow. The header has a Settings button
and a ⋮ menu: Import, .flo import, Export, Backup, Restore, Log.

## Kept

- All 32 `Flow*` Kotlin files: engine, store, IO (backup/restore),
  `.flo` import, catalog, triggers, conditions/actions, editor, picker,
  dialogs, service + all receivers + boot receiver.
- Shizuku keyevent answer/end (keyevent 5/6) exactly as PhoneLink does
  it — Shizuku first, no CallShield bridge in this app.
- The PhoneLink dark UI language (near-black green `#101415`, mint
  `#10B981`, dark cards `#1C2326`) with the full 8-theme system:
  Emerald Dark, Liquid Glass Aurora, Ocean Deep, Midnight Sapphire,
  Sunset Ember, Rose Quartz, Forest Mist, Graphite Glass Mono.
- Working actions kept: `lock_screen` (device admin), redial, `qr_show`,
  MQTT publish, FocusLock actions (via `Config.focusLockToken`).
- Support files: `Config`, `ThemeColors`, `LocalizedActivity`,
  `ShizukuHelper`, `PermChecks`, `Stats`, `QrShow`, `MqttManager`,
  `Redial`, and a trimmed `AdminReceiver` (only `isActive()`, for the
  `lock_screen` action — PhoneLink's forwarding-side admin hooks were
  not copied).

## Removed

- SMS / notification / call **forwarding and relay** (no ntfy, no peer
  topics, no pairing). `Config`'s relay topic helpers and pair-code
  generator were deleted, not just unused.
- The **Rules engine and Rules UI** (`AppRules.kt`, `Automation.kt`,
  `AutomationActivity.kt`, rule widgets).
- **Dashboard, Command Hub, AppLock, Inbox, pairing/relay settings.**
- The launcher-hide `.MainAlias` alias (PhoneLink's icon feature).
- Unused widget/autotap XML files (they referenced removed providers).

## Stubbed, never faked (catalog entries kept so old flows still load)

- `rule_enabled` condition → always false (no Rules engine here).
- `rule_run` / `rule_enable` / `rule_disable` → log "unavailable in the
  standalone Flows app (Rules engine removed)".
- `notify_peer` / `speak_peer` / `command_peer` → log unavailable
  (relay removed).
- `lock_app` / `unlock_app` → log unavailable (AppLock removed).

## Known skeleton gaps (to fix before any release)

1. **MQTT subscribe:** `MqttManager.wantedFilters()` returns empty —
   there are no rules left to subscribe for, and publish still works.
   A flow-level `mqtt_message` trigger topic setting is the missing
   piece; until then `subscribeAll` is a no-op.
2. **Redial ring-limit:** the merged AutoTap engine was NOT carried
   over, so the ring-limit end-call tap always reports "no tap" and the
   session waits for the natural end (carrier timeout, decline,
   voicemail). Shizuku keyevent 6 is deliberately NOT used there —
   without the AutoTap ringing guard a blind keyevent could cut a live
   conversation. Redial polling is now driven by the Flows service tick
   (every ~60 s, not PhoneLink's ~20 s Automation tick), so redial gaps
   are approximate up to ~60 s.
3. **Launcher icon is a placeholder:** `ic_launcher` was carried over
   from PhoneLink. Replace it with real Flows art before any release;
   do not ship PhoneLink's icon as final.
4. **Permissions were carried over wholesale** from PhoneLink because
   Flow triggers/actions still need them (SMS, call, location, camera,
   etc.). Trim in a later pass once each trigger's permission is
   audited on device — do not silently drop permissions triggers need.
5. **Unused resources were carried over** (whole `res/` copied for
   resource-resolution safety). Prune layouts/drawables/strings for
   removed screens later; only `activity_flow_editor`,
   `activity_flow_scan`, `bottomsheet_palette` and `dialog_picker`
   are inflated by the kept code.
6. **No CI workflow yet.** The repository now exists; the workflow
   file must be added via the GitHub web UI (the GitHub App cannot push
   `.github/workflows/*`), as with the other apps.

## Data model note

Flows live in this app's own prefs (`flows_store`, config in
`flows_cfg`) — separate from PhoneLink's. Nothing migrates
automatically. Bring flows over via Export/Import or `.flo` import.

## Settings

`FlowsSettingsActivity` is minimal by design: the 8-theme picker
(`Config.appTheme` + `recreate()`) and the Flows service switch with
the same start/stop logic as PhoneLink's Flows tab. Nothing else.

## Validation done locally

- `~/workspace/tools/kt_check.py` over all 44 Kotlin files: exit 0,
  every file balanced (braces/parens/brackets 0, stray quotes 0).
- Zero code references to removed PhoneLink code, verified by grep
  (`com.mthtamim.phonelink` outside the new package, `Automation.`,
  `Relay.`, `AppLocker.`, `RuleEditorActivity`, `AutomationActivity`,
  `LinkService`, `SnapService`, `ScreenShareService`,
  `com.autotap.app`, MainAlias) in Kotlin, layouts, values and the
  manifest; every manifest class resolves to a class in the tree;
  every `R.*` reference resolves to a carried-over resource or to
  `android.R`; no `BuildConfig` use, no `?attr/colorBackground`, no
  `itemTextColorList`, dotted styles all carry explicit parents.
- Historical prose comments mentioning PhoneLink remain in a few file
  headers — those are documentation, not code references.
