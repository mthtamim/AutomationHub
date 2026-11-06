# AutomationHub

**Automation, Rules & Flows for Android — everything from PhoneLink except phone forwarding.**

AutomationHub is the corrected-scope rebuild of the PhoneLink-Flows skeleton
(v1.0.0, rejected 2026-11-06 for opening empty with no Automation/Rules and
for being bigger than PhoneLink itself). Tamim's corrected scope, confirmed
2026-11-06: **everything except phone forwarding, same Settings, same UI.**

- **Package:** `com.mthtamim.phonelinkflows` (kept from the skeleton, so the
  `phonelink_*` SharedPreferences keys stay compatible with its data)
- **Version:** 1.2.0 (vc3) · `compileSdk`/`targetSdk` 34, `minSdk` 26 ·
  arm64-v8a only · minify off
- **Repo (for the push):** `mthtamim/PhoneLink-Flows`
- **Status: STAGING — not pushed, no release.** This tree is the
  ready-for-push candidate; it has NOT been swapped into
  `~/workspace/github-push/phonelink-flows/` yet, and it has never been
  compiled (see Validation).

## What it is

- **Automation — full:** Rules engine (`Automation.kt`, triggers, conditions,
  actions, redial, scheduling, MQTT publish/subscribe, FTP/SMTP, webhook
  server, local camera actions) + Flows engine (visual flow editor, `.flo`
  import/export, FlowService, 28-file merged AutoTap engine in
  `com.autotap.app`).
- **Settings — full:** all 8 themes (Emerald Dark default, Liquid Glass
  Aurora, Ocean Deep, Midnight Sapphire, Sunset Ember, Rose Quartz, Forest
  Mist, Graphite Glass Mono), permissions, security, data & backup.
- **Dashboard** (Home) and **AppLock** (PIN/biometric, per-app lock,
  intruder capture).
- **No forwarding:** no SMS/notification/call/battery/app-event forwarding,
  no ntfy relay, no pair code, no Inbox, no Remote/Command Hub.

## Keep / remove (with file counts)

| Area | Count |
|---|---|
| Kotlin files | 100 total — 72 in `com.mthtamim.phonelinkflows` + 28 in `com.autotap.app` (SnapService is the 72nd, newly ported) |
| Layouts | 17 (was 37 in the skeleton) |
| Drawables | 89 (was 134) |
| Strings per locale | 1390 (was 1911 in PhoneLink; 390 dead strings deleted in this rebuild) |
| Manifest components | 61 (activities, services, receivers, providers — all resolve) |

**Removed layouts (20):** `activity_apps`, `activity_bt`, `activity_charts`,
`activity_dark`, `activity_expenses`, `activity_gallery`, `activity_panic`,
`activity_qr`, `activity_scan`, `activity_screen`, `activity_simple`,
`activity_voice`, `item_app_rule`, `item_inbox`, `page_config`,
`page_remote`, `widget_inbox_layout`, `widget_layout`,
`widget_lock_layout`, plus `res/xml/widget_*_info.xml` (3 files).
Each was grep-verified unreferenced by kept Kotlin before deletion.

**Removed from the main screen:** `panelForward` (forwarding toggles),
`panelFamily` (lost-mode / family check-in), and the relay-server, Telegram,
offline-queue, SMS-fallback, diagnostics, peers, LAN-web-inbox and browser-
sessions cards of `panelRelay`. `panelRelay` survives as the **MQTT & Email**
panel (MQTT broker card, SMTP card, Shizuku status line); the Settings hub
tile was retitled to `mqtt_smtp_title` and now opens it. `panelData` was
restored from PhoneLink (backup/restore + full-settings backup only; stats
reset, retention, autosave, CSV/PDF export, expenses, charts and auto-backup
controls removed as dead).

**Removed code:** `Relay`, `LinkService`, `ScreenShareService`,
`MainAlias`, `SmsReceiver`, `CallReceiver`, `SystemReceiver`,
`PackageReceiver`, `SecretReceiver`, `QsTileService`, Inbox/Status/Lock
widgets, `BtActivity`, `VoiceActivity`. Zero references remain in code
(comments excluded; 12 comment-only mentions of LinkService/Relay remain
as documentation).

## Dependency decisions (with grep evidence)

`app/build.gradle` was rewritten from the audit, not copied:

- **Removed:** ML Kit `translate` (0 references), ML Kit `text-recognition`
  (0 references). Play Services `location` was removed in the first
  draft on a wrong audit ("0 references") and **re-added 2026-11-06**
  — see CI fix below: `FlowTriggers.activityRec` and
  `FlowSystemReceiver` use `com.google.android.gms.location.*`.
- **Kept, against the first draft of this task:** `lifecycle-service`
  (SnapService extends `LifecycleService`) and CameraX `camera-video`
  (SnapService `Recorder`/`VideoCapture`). The task text said to remove
  both; the audit proved both are needed by a kept action.
- **Kept:** CameraX core/camera2/lifecycle/view (FlowScanActivity
  `ImageAnalysis`/`PreviewView`/`ProcessCameraProvider`, FlowCameraActivity
  `ImageCapture`), ML Kit `barcode-scanning` (FlowScanActivity `qr_scan`),
  ZXing core (QrShow `MultiFormatWriter`), biometric (AppLocker/LockActivity),
  Shizuku api + provider, Paho MQTT (MqttManager), appcompat, activity-ktx,
  material. **Added:** `androidx.core:core-ktx:1.13.1` (androidx.core used
  in ~20 files; it was missing from the skeleton gradle).
- **Ftp.kt / Smtp.kt / WebhookServer.kt — KEPT, not forwarding:** `Ftp.transfer`
  is called by Automation.kt:2566/2570 (`ftp_upload`/`ftp_download`);
  `Smtp.send` by Automation.kt:2144 (`send_email`); `WebhookServer.start/stop`
  by FlowService.kt:94–130 (LAN webhook trigger). All three are automation
  actions.

## Port notes (real breakages found and fixed)

- **SnapService ported** from PhoneLink and trimmed: `take_photo` /
  `record_video` save locally (`Pictures/AutomationHub`,
  `Movies/AutomationHub`) and notify locally instead of relay upload
  (`Relay.postFile`, `LinkService.CHANNEL_SERVICE` removed). Its foreground
  notification uses `FlowEngine.NOTIF_CHANNEL_ID` (`phonelink_flows`),
  channel created in-service.
- **A11yService.kt:** a half-deleted `Relay.send(...)` left dangling string
  arguments and an unbalanced `)` — fixed with
  `Automation.localNotify(...)` focus-guard message.
- **AppLockerActivity.kt:** deleted Peer + Intruder binds left a dangling
  `AppLocker.setIntruderOn(this, it)}` — Intruder bind restored (local
  feature); Peer switch removed from `activity_applocker.xml` and its
  strings deleted in both locales.
- **PermissionsActivity:** Bt/Voice "Open" buttons launched removed
  activities — they now request the permissions directly (Bluetooth 44,
  mic 45).
- **Update.kt:** User-Agent and APK filename rebranded; **fixed a
  pre-existing bug** — FileProvider authority `.fileprovider` →
  `.files` (matches the manifest; PhoneLink had the same bug). The
  release check still points at `mthtamim/PhoneLink-Flows`.
- **Strings:** `PhoneLink` → `AutomationHub` in referenced strings (both
  locales); `footer_about` and `admin_explanation` rewritten (the relay /
  remote-lock text was false for this app). `fl_token_dialog_msg` kept —
  the FocusLock card label is out of scope.
- **Config.kt:** SMTP setter added (`setSmtp`, in `setMqtt` style) so the
  SMTP card's Save button works again; one unused import
  (`kotlin.random.Random`, pre-existing in PhoneLink) removed.
- **Prefs keys deliberately unchanged** (`phonelink_flows`,
  `phonelink_auto`) for data compatibility; theme style names stay
  `Theme.PhoneLink*` for the same reason. `app_name` is AutomationHub.

## Name & logo

- `app_name` = **AutomationHub** in `values` and `values-bn`.
- Launcher icon: dark `#101415` background, mint `#10B981` gear +
  connected flow nodes (`res/drawable/ic_launcher.xml`, copied from the
  old dir where Tamim chose it 2026-11-06). Manifest references it once.

## Known gaps

- **No auto-migration from PhoneLink.** Moving Rules/Flows across is
  Export/Import and `.flo` files only; prefs keys are `phonelink_*` but
  PhoneLink's data layout is not imported automatically.
- **MQTT subscribe / redial:** carried over from PhoneLink's Automation
  code as-is; not re-verified on a phone in this rebuild.
- **CI run #4 (2026-11-06, commit `86e549fa`) FAILED** in 1m 50s at
  `:app:compileDebugKotlin` — the errors are fixed locally (see CI fix
  below), but the fix has NOT been pushed yet, so no passing CI run
  exists for this tree.
- **No phone test yet.** Everything below is static verification only.
- **Placeholder-free:** the launcher icon is the real chosen icon (no
  placeholder), but the app has never run, so the first launch is
  unproven.

## Validation (2026-11-06, local only — no kotlinc/java available, CI is the compile judge)

| Check | Result |
|---|---|
| `kt_check.py` over all 100 Kotlin files | **92/100 pass.** The 8 failures are naive-counter artifacts, proven by diff against PhoneLink v2.25.2 originals: 4 byte-identical (FloatingPanel, HeatmapActivity, Model, PreflightOverlayService) and 4 with identical counts (AutoTap MainActivity/RunnerService/WidgetProvider, Automation.kt braces −2). Template-aware check on Model.kt: 0/0/0 balanced. Stray quotes: 0 in all files. Expression-body bare-return candidates: 0. |
| Reference validation (`/tmp/validate_refs2.py`) | Manifest 61 components, **0 unresolved**; manifest @drawable/@string/@style/@xml all resolve; R.layout/R.drawable/R.string/R.color/R.menu/R.xml/R.id all resolve (after normalising XML style dots → R underscores, excluding `android.R.*`); `?attr/plBtnFill`, `plNavIndicator` exist in `attrs.xml`; other `?attr/color*` are Material3 attrs from pinned material 1.12.0. The R.style "missing" entries are the dot/underscore normalisation false positive, verified in the earlier run. |
| Removed-code grep (comments stripped) | `Relay.` 0, `LinkService` 0, `ScreenShareService` 0, `MainAlias` 0, `com.mthtamim.phonelink` 0 in code. `SnapService` 3 hits = definition + 2 Automation.kt callers. |
| XML well-formedness | All 127 XML files under `res/` + `AndroidManifest.xml` parse clean. |
| Unused-import scan (edited files) | Clean after removing `kotlin.random.Random` from Config.kt. |
| Dead strings | 390 with zero references removed from both locales (dynamic `at_`/`ac_`/`aa_` families excluded — they are resolved via `getIdentifier` in AutomationActivity.kt/FlowCatalog.kt). |

## CI fix (2026-11-06 — run #4 failed, fixed locally, NOT pushed)

Run #4 (run ID 42184108471, commit `86e549fa`) failed at
`:app:compileDebugKotlin`. The first validation missed these because
it only grepped for `Relay.`/`LinkService`/etc. — it never checked
whether kept code still **calls** removed classes, or whether kept
classes' **APIs** still matched. The corrected method, re-run after
the fix: (1) diff the class lists of PhoneLink v2.25.2 (144 files)
vs this tree (100 files) = 53 removed classes, then grep the whole
tree for every one of them with comments stripped; (2) audit every
`Type.member` call site against the member actually defined in the
kept file; (3) grep every `com.google.android.gms.*`, ML Kit and
CameraX reference and confirm its library is in `app/build.gradle`;
(4) re-validate all `R.*` refs and manifest classes; (5) run
`kt_check.py` **per file** (it short-circuits on the first failure
when given many files, so a single bulk run proves nothing).

Fixes, each verified by the re-run audits:

- **NotifListener (5 sites, Automation.kt:871/1181/2027/2028/2532):**
  the kept `FlowNotifListener` gained the missing API, mirroring
  PhoneLink's `NotifListener` exactly — live `instance` set in
  `onCreate`/cleared in `onDestroy`, plus `hasNotification(pkg)`,
  `cancelPackage(pkg)`, `dismissAll()` and `replyByPackage(ctx, pkg,
  text)` built on `activeNotifications` + `cancelNotification` +
  RemoteInput. The `notif_present` condition, `clear_notif` and
  `notif_reply` actions now work for real; nothing was stubbed to
  false. Call sites rewired to `FlowNotifListener.*`.
- **BtShare (5 sites, Automation.kt:1001/1876/1882/1883/2939):**
  local `btAdapter()` helper (`BluetoothAdapter.getDefaultAdapter()`,
  try/catch, `@Suppress("DEPRECATION")`) replaces
  `BtShare.adapter()` for the `bt_connected` condition, `bt_toggle`
  action and `bt_near`/`bt_far` discovery. BtShare's RFCOMM
  send/receive server stays removed (forwarding).
- **SessionState (Automation.kt:1698/1699, SnapService.kt):** the trim
  had deleted `snapFront`/`videoSecs` while the kept SnapService
  still reads them — both fields restored with PhoneLink's defaults
  (`false` / `0`). The trim comment claiming they were "gone with
  SnapService" was wrong; SnapService is kept.
- **ScanActivity (AutomationActivity.kt:561):** the ⋮ menu's QR item
  now launches the kept `FlowScanActivity`, and `finishScan()` sets
  the `RESULT_OK` + `"code"` result extra PhoneLink's ScanActivity
  returned, so `scanLauncher` still fires the `qr_scanned` event.
  Flow-thread launches ignore the result (they wait on the
  `FlowScan` latch), so this is harmless there.
- **play-services-location (FlowSystemReceiver.kt:75/93–95,
  FlowTriggers.kt:1527/1552):** re-added `21.0.1` to
  `app/build.gradle` and corrected the audit comment. The
  `flow_activity` trigger is live kept code
  (`ActivityRecognition.getClient` + `GoogleApiAvailability` +
  `ActivityRecognitionResult`/`DetectedActivity`); the first audit
  only grepped `LocationServices`/`FusedLocation` and missed it.
- **SystemReceiver (Helpers.kt:253):** `batteryStatusText()` is now
  a local `BatteryHelper` function (PhoneLink's exact logic:
  `BatteryManager` % + sticky `ACTION_BATTERY_CHANGED` intent),
  and the diagnostics text calls it.
- **MainActivity.kt (749/1165/1192/1397):** `showSetPinDialog()`
  took no arguments but only the `onDone` overload survived the
  trim — the Security button now calls `showSetPinDialog { }`
  (the pattern already used at line 370). The no-arg overload in
  PhoneLink was the *command* PIN dialog; the command PIN's backing
  store was removed with the relay, so it was not restored.
  `FlowStore.all(this)` (×3) does not exist — replaced with the
  kept `FlowStore.flows(this)`. The button/state strings
  (`btn_set_pin`, `pin_state_set/unset`) still said "command PIN"
  while the state line already showed the app-lock PIN, so all four
  were reworded to "app PIN" in both locales.

Re-run validation after the fix (local only, CI is still the
compile judge):

| Check | Result |
|---|---|
| `kt_check.py` per file, all 100 Kotlin files | **92/100 pass** — same 8 pre-existing naive-counter artifacts as before (Automation.kt braces −2, identical to the PhoneLink original; 7 AutoTap files). All 7 edited files pass individually. |
| Removed-class audit (53 removed classes, comments stripped) | **0 code hits** — the only remaining match is the string literal `"Relay প্যানেলের Shizuku বোতাম থেকে ঠিক করো।"` in Automation.kt:2778. |
| Cross-object API audit (`Type.member` vs defined members) | **0 mismatches.** |
| Library audit (gms / ML Kit / CameraX refs vs `app/build.gradle`) | All resolve; `play-services-location:21.0.1` re-added. |
| R.* / XML refs + manifest classes | **0 missing, 0 unresolved, 0 malformed XML.** |
| Leftover error sites | `showSetPinDialog()`, `FlowStore.all(`: 0 remaining. |

## Push note (2026-11-06 — Tamim tapped "Push")

The swap into `~/workspace/github-push/phonelink-flows/` is done, and this
tree is what was pushed. The CI workflow (`.github/workflows/build-apk.yml`)
lives only on GitHub (placed via the web UI — the GitHub App cannot touch
workflows) and was not part of the push, so it stays untouched.

**Stale files the connector cannot delete in bulk:** the snapshot push
cannot delete files, so 67 files from the v1 skeleton remain in the repo:
- **18 removed-screen layouts** were overwritten with inert placeholders
  (empty `LinearLayout`, references nothing) because they referenced
  strings/drawables that no longer exist — left as-is they would have
  broken resource linking and failed the CI build.
- **49 harmless dead files** remain as-is (2 Kotlin activities
  `FlowsMainActivity`/`FlowsSettingsActivity`, 45 drawables, 2 layouts);
  every reference in them still resolves, they are not in the manifest,
  and they cannot break the build. They only add a little dead weight.
- A bulk-delete cleanup pass (e.g. via github.dev multi-select, or a git
  clone) should remove all 67 later. Until then, do not mistake the
  placeholders for real screens.

## What still blocks a release

1. **CI re-run:** the CI fix above is local-only; it needs Tamim's
   push tap, then the next CI run must pass — local checks cannot
   prove compilation.
2. **Phone test:** first launch, Automation Rules + Flows, Settings/themes,
   AppLock, backup/restore — all unproven until Tamim
   runs them on his phone.

## Peer-UI removal pass (2026-11-06 — Tamim's phone screenshots)

Tamim installed the AutomationHub APK and circled what was still
forwarding/peer UI. Everything he marked — plus the same feature's
duplicates elsewhere — was removed from `MainActivity.kt`,
`page_home.xml`, `page_settings.xml` and `activity_main.xml`:

- **Dashboard:** the four peer tiles (Location / Flashlight / Ringer /
  Walkie-talkie) and the whole Quick Send card (clipboard send, file
  send, voice message, walkie-talkie voice, broadcast, voice command,
  siren, message schedule, templates, contact card, wallpaper set,
  My status + Save). The SMS-only SOS button lived inside that card
  and went with it; the Panic / Emergency card in Settings remains
  the emergency path (note: in the current code the Panic PIN only
  sets a flag — no code path sends the panic SMS; pre-existing gap,
  unchanged by this pass). Kept on the Dashboard: device card
  (battery/Wi-Fi), Recent activity, AutoTap, Redial.
- **Settings:** the "Connected device & account" card (device +
  "Manage keys" pair-code button) and the "MQTT & Email" tile with
  its `panelRelay` sub-screen (MQTT broker/user/pass + SMTP fields).
- **Also removed (dead, unambiguously peer-only):** the "My status"
  card in Appearance & General (its Dashboard field was its
  shortcut), and the Clipboard auto-sync + Walkie-talkie autoplay
  toggles in the Smart panel — none of the three had any code wiring.

**MQTT consequence:** `Config`'s MQTT/SMTP accessors, `MqttManager`
and the Flow `mqtt_publish` / `send_email` actions are untouched and
keep working with the **last-saved** broker/SMTP values — but there
is now **no UI to change those values**. If the broker or SMTP
settings ever need changing, the panel has to come back (or the
values edited via settings import).

**Left in place deliberately:** the Smart panel's other toggles
(SMS trigger/reply, auto-reply, TTS, routine, geo-fence, daily
summary) are local automation, not peer forwarding; the Data panel's
Backup/Restore buttons back up the *full local settings*
(`SettingsTransfer`), not the removed Inbox. Dead strings/drawables
from the removed UI were left in `strings.xml` / `drawable/` —
harmless, listed in the fix report.

Local validation for this pass: all 3 edited layouts parse as XML;
`MainActivity.kt` braces 214/214, parens 807/807, brackets 7/7;
every `R.*` reference in `MainActivity.kt` resolves; every manifest
component resolves to a class (except library providers). CI is
still the compile judge — not pushed yet.

## Unnecessary-items removal pass 2 (2026-11-06 — Tamim's phone screenshots, approved in chat)

Tamim identified leftover unnecessary items in the Security & App
Lock and Smart & Automation panels, approved their removal, then
added two exceptions (App Locker stays, wrong-unlock selfie stays)
and one addition (Dashboard Recent activity also goes).

**Removed — Security panel (`panelSecurity`, hub Panic card):**
Panic PIN + SOS numbers + save (`btnPanicPin`, `etSosNumbers`,
`btnSaveSos`), the hub Panic card (`cardHubPanic`, which only opened
the Security screen), encryption (`swEncryption`), guard
(`swGuard`), security log (`btnSecLog` + `showSecLogDialog()`).
The orphaned one-line prompt helper (`showTextPrompt()`, only used
by the Panic PIN) went with it.

**Removed — Smart panel (`panelSmart`):** the whole first card
(apps picker `btnApps`, Quiet hours `swQuiet`/`btnQuietTimes`,
digest `btnDigest`, priority numbers `etPriority`, alert words
`etAlertWords`, save `btnSaveSmart`, SMS trigger `swSmsTrigger`),
TTS + calls (`swTts`, `swTtsCalls`), SMS reply/auto-reply
(`swSmsReply`, `swAutoReply`, `etAutoReply`), routine
(`swRoutine`, `btnRoutineTime`), low-location (`swLowLoc`), daily
summary (`swDailySummary`), auto-priority (`swAutoPriority`,
whole extra card), usage state line (`tvSmartUsageState`),
FocusGuard (`btnFocusGuard` + `showFocusDialog()`), mode
(`btnMode`, dead/unwired), Smart Studio (`btnSmartStudio` +
`tvSmartStudioInfo`). Most of these were dead UI with no code
wiring at all; Quiet hours and digest had no consumers outside
`MainActivity`/`Config`, and the #ring/#loc/#battery SMS fallback
has no handler in code.

**Removed — Dashboard:** Recent activity card (`cardDashRecent` +
`tvDashRecent` + its `refreshDashboard()` block). Dashboard now
holds only the device card, AutoTap and Redial.

**Kept — exceptions (Tamim's words):** App Locker IN FULL (app
PIN set/change, `swAppLock`, `btnAppLocker`, `AppLockerActivity`)
and wrong-unlock selfie (`swIntruder`, relocated into the App Lock
card after the PIN buttons — it needs device admin, which stays).

**Kept — infrastructure:** device admin + accessibility state
lines and the permissions link (Flow lock-screen action and
AutoTap need them), Automation master toggle (`swAutoMaster`),
geofence (`swGeo` + set/radius UI — `geofence_in`/`geofence_out`
is a real Flow trigger), the Smart permissions link
(`btnSmartPermsLink`, kept for the geo location permission), and
the Smart/Security hub tiles (the trimmed panels still open from
them).

**FocusLock token decision:** the token is NOT configured in the
removed FocusGuard dialog — that dialog only set the Focus *Guard*
schedule (focusOn/focusPkgs/focusWindow, enforced by
`A11yService`). The FocusLock token (`Config.focusLockToken`) is
saved once from `AutomationActivity`, and the `focuslock_control`
Flow action reads it from there, so removing FocusGuard does not
break it. Caveat: `A11yService` still enforces a Focus Guard
schedule if one was saved earlier — with the dialog gone there is
no UI to turn it off (it defaults off).

**Left in place deliberately:** the Data panel's Inbox
backup/restore buttons (not in the approved list); dead strings
for the removed UI remain in `strings.xml` (harmless); `Config`
accessors for removed toggles remain (dead prefs, harmless).

Local validation for this pass: all 3 edited layouts parse as XML;
all removed view ids grep to 0 across `java/` + `res/layout/` +
`res/xml/` (one false positive: `setSosNumbers` in `Config.kt`
contains the substring of the removed `etSosNumbers` id);
`MainActivity.kt` (1273 → 1113 lines) is brace/paren balanced per
a proper Kotlin scanner (strings/`${}` templates/raw strings/char
literals/comments aware); every `R.*` ref in `MainActivity.kt`
resolves (the one apparent miss is `android.R.string.ok`); all 35
manifest components resolve to classes (`QrShowActivity` lives in
`QrShow.kt`); the two Add-Flow crash-fix files are untouched. CI
is still the compile judge — not pushed yet.

## Improvement pass (2026-11-06 — Tamim: MQTT stays removed, do the rest, professional theme picker)

This pass supersedes three leftovers from pass 2 above: the Focus
Guard caveat (now has an off-switch), the Inbox backup/restore
buttons (now removed), and the dead strings/Config accessors (now
removed). MQTT stays removed — no MQTT UI was restored anywhere.

### A. Repo cleanup
- Deleted the 18 inert placeholder layouts from the push tree
  (each verified as the 6-line empty-LinearLayout placeholder with
  zero references before deletion).
- Deleted 27 dead drawables from both trees (54 files) — zero
  references in code/layouts/styles/manifest.
- Total: 72 files deleted. Push tree now has 17 layouts, 62
  drawables. `FlowsMainActivity.kt` / `FlowsSettingsActivity.kt`
  do NOT exist locally (remote-repo only). No other dead
  non-placeholder layouts exist locally.

### B. Dead resources
- Removed 101 dead strings from `values/strings.xml` +
  `values-bn/strings.xml` in both trees (404 removals). Dynamic
  strings `at_/ac_/aa_/ath_/ach_/aah_*` were excluded — they are
  loaded via `getIdentifier` in FlowCatalog/AutomationActivity.
- Removed 14 dead Config accessors from both trees
  (`quietOn/quietStart/quietEnd/setQuietWindow/digestMinutes/
  digitsOf/setSmtp/setMqtt/pinDigestOf/guardOn/sosNumbers/
  setSosNumbers/setPanicPinHash/panicActive`) — zero callers after
  the fix. Caught in verification: `Config.applyMode()` "study"
  branch called `digestMinutes` internally — fixed to a direct
  prefs read. Kept deliberately: `setFocusOn/setFocusPkgs/
  setFocusWindow` (needed for D), the `accent()` getter (accent
  picker still exists), mqtt/smtp getters (the engine uses them),
  `setPanicActive` + the `panicPinHash` getter (LockActivity
  uses them).

### C. Data panel
- Removed `btnBackupInbox` / `btnRestoreInbox` and their wiring
  (`backupLauncher/restoreLauncher/writeBackup/finishRestore/
  pendingBackupUri`) from MainActivity (1113 → 937 lines) and the
  layout row. Those buttons were mislabeled duplicates of the full
  backup (same `SettingsTransfer.export/import`). Export/Import
  config buttons remain.
- Consequence: password-encrypted backup creation/restore goes
  away (`cfgExportLauncher` writes a plain export) — see
  "What still blocks a release".

### D. Focus Guard off-switch
- Enforcement lives only in `A11yService.kt`: `focusOn` + a
  package in `focusPkgs` + inside `focusWindow` → the app is
  bounced to home + a local notification. Defaults off.
- Added a switch to the More card in `panelSmart` (`swFocusGuard`
  + `tvFocusGuardInfo`, 4 new strings EN+BN). Turning it ON with
  no saved packages toasts and reverts to off; turning it OFF
  clears the saved schedule. Wired in MainActivity with a
  `focusGuardUpdating` guard flag; `refreshFocusGuard()` is called
  from `refreshStatus()`.
- The FocusLock token is safe: the token is saved from
  `AutomationActivity.kt` and read by `focuslock_control` — the
  removed FocusGuard dialog only set the schedule, so removing it
  did not break the action.

### E. PhoneLink import ("PhoneLink থেকে আনো")
- Where: Data panel, in the Full settings backup card
  (`btnImportPhonelink`), next to Export/Import config.
- How it works: the button shows a dialog with the exact steps,
  then opens a multi-file picker. Each picked file is detected
  by content and merged — never wiped:
  - PhoneLink full backup (`kind:"full-backup"`, from PhoneLink
    Settings → Full backup — everything (file)) → its Rules are
    merged into Hub's rules (deduped, collisions re-id'd).
  - Flows backup (`formatVersion:1` + `flows`, from PhoneLink
    Automation → Flows tab ⋮ → Backup flows) → each Flow is saved
    (same id overwrites the old one).
  - Single flow JSON (`nodes` + non-empty `name`) → saved
    (a missing or colliding id gets a fresh one).
  - Bare rules array → merged like the full-backup rules.
  - Anything else → skipped and counted in the result toast
    ("%d rule(s) and %d flow(s) imported (%d file(s) skipped)").
- Limits (honest): there is NO automatic migration — Android
  sandboxing blocks reading PhoneLink's private files directly,
  so the user must export the two files from PhoneLink first and
  pick them here. Hub settings (theme, PIN) are deliberately NOT
  overwritten with PhoneLink's `cfg`. `.flo` files are Automate's
  proprietary binary format, not PhoneLink flows, and are not
  imported here.

### F. Professional theme picker
- Replaces the old plain single-choice AlertDialog. Now a bottom
  sheet (`bottomsheet_theme_picker.xml`) with all 8 themes as
  preview cards in a 2-column grid. Each card is a mini preview
  built from that theme's REAL palette (background, blended
  surface, primary accent dot, on-surface name, a small
  "AutomationHub" card with the automation pill) and a ✓ check
  badge in the primary colour on the selected card.
- Tapping a card saves the choice (`Config.setAppTheme`) and
  recreates the activity, so the whole app follows instantly.
  Only the existing 8 palettes are used — no new colours.
- The light/dark/system toggle in General settings is untouched
  and independent: it controls night mode. The 7 glass themes
  render identically in light/dark/system (static palettes);
  Emerald Dark follows the light/dark palettes, so its card
  shows the dark look.

### G. Release groundwork (code only)
- Version: `versionName "1.0.0"`, `versionCode 1` at the time of
  this pass — bumped to 1.1.0/vc2 in the 2026-11-06 sensor/speed
  pass (see below).
- `release` build type now has `minifyEnabled true` +
  `shrinkResources true` (debug build untouched — CI builds
  debug). `app/proguard-rules.pro` (was empty) now keeps:
  `FlowCanvasView` (custom view inflated from XML by class
  name), `RuleWidgetProvider` + `RuleWidgetConfigure`,
  `rikka.shizuku.Shizuku.newProcess` (called reflectively by
  name in ShizukuHelper/ShizukuTap — R8 must not rename it)
  and `ShizukuProvider`. No Gson/Moshi/serialization in the app;
  the only other reflection is the same Shizuku call.
- NOTE: minify is NOT exercised by CI (debug only) — the first
  release build must be phone-tested before it ships.

### H. This README section (both trees).

Local validation for this pass: every removed file/id/string
greps to 0 across `java/` + `res/` + manifest; every edited
layout parses as XML; `MainActivity.kt` is brace/paren balanced
per a proper Kotlin scanner (strings/`${}` templates/raw
strings/char literals/comments aware); every `R.*` ref in the
edited Kotlin files resolves; all manifest components resolve to
classes; the two Add-Flow crash-fix files are untouched
(`activity_flow_editor.xml` still contains
`com.mthtamim.phonelinkflows.FlowCanvasView`,
`widget_rule_info.xml` still contains
`com.mthtamim.phonelinkflows.RuleWidgetConfigure`); no MQTT UI
was restored (`panelRelay` / `tileHubRelay` / `mqtt_smtp_title`
stay absent); the kept items are still present (App Locker +
PIN, intruder selfie, device admin/accessibility states,
automation master toggle, geofence). CI is still the compile
judge — not pushed yet.

## Focus Guard setup card + geofence fix (2026-11-06)

### 1. Focus Guard setup — NEW card, not the old dialog
The old FocusGuard setup dialog was removed in the unnecessary-items
pass, leaving only an on/off switch that could never turn on (no
saved packages -> toast + revert). This pass brings setup back as a
dedicated card in the Smart panel, with a fresh layout:

- `activity_main.xml`: new Focus Guard card in `panelSmart` (header
  row with shield icon + title + subtitle + the moved `swFocusGuard`
  switch, live summary `tvFocusGuardInfo`, app section, time window,
  preset chips). The switch was MOVED out of the More card (same ids,
  same wiring — exactly one `swFocusGuard` / `tvFocusGuardInfo` in
  the layout). OFF still only disables (packages are kept, so ON
  restores the same setup — the switch never cleared `focus_pkgs`).
- `FocusGuardPickerActivity.kt` (new, 184 lines) +
  `activity_focus_guard_picker.xml` (new): searchable multi-select
  list (icon + name + checkbox, system apps behind a toggle) reusing
  `AppLocker.allInstalled` / `appIcon` / `pickerExcluded` — the
  AppLocker picker itself was NOT reused (it writes the lock set
  directly). Checkboxes edit a local set only; Save writes
  `Config.setFocusPkgs`, turns the guard on when the list is
  non-empty, off when cleared. Manifest: `.FocusGuardPickerActivity`
  added (exported=false).
- Time window: start/end buttons open a 24-hour TimePickerDialog;
  presets — পড়ার সময় 18:00–22:00, রাত 22:00–06:00 (overnight
  windows work, as the enforcer already handled fs > fe), দিনের
  কাজ 09:00–17:00 — all via `Config.setFocusWindow`. Saving applies
  immediately: the card summary and the switch refresh on return
  (`refreshStatus()` -> `refreshFocusGuard()` in `onResume`).
- A11yService enforcement is UNTOUCHED (same bounce-to-home +
  throttled local notify). FocusLock token path untouched. No new
  permissions. New strings (EN+BN): `focus_guard_sub`,
  `focus_apps_label/none/chosen/choose`, `focus_time_label/start/end`,
  `focus_preset_study/night/work`, `focus_picker_title`,
  `focus_saved_count`, `focus_cleared`.

### 2. Geofence "location পাওয়া যায় না" — root cause + fix
Root cause (verified in `Helpers.kt`): `LocationHelper.fetchCoords`
ONLY read `getLastKnownLocation` from GPS/NETWORK/PASSIVE and called
`onFail()` when all were null — it never requested a fresh fix,
despite its own doc comment promising one. A last-known fix only
exists if some app asked for location recently; after a reboot, or
with location rarely used, every provider returns null — so
btnSetGeo failed even with location permission granted. The
proximity-alert registration itself (`FlowTriggers.geofenceSync`,
`addProximityAlert`) was checked and has no blocker (only a
SecurityException log on missing permission).
Fix (`Helpers.kt`, signature unchanged — all 5 callers intact):
keep the fast last-known path; when nothing is cached, request ONE
fresh fix via `requestSingleUpdate` on GPS and/or Network (main
looper, first fix wins, ~20 s timeout -> onFail, single-callback
guard via AtomicBoolean, listener removed on first fix/timeout).
No new permissions. The Automation latch callers (`location_in`,
weather, sunrise/sunset) still wait at most 6 s — a late fix just
counts down an already-released latch, so they cannot hang.
`btnSetGeo` failure now distinguishes the two cases
(`LocationHelper.anyProviderEnabled`): location switched off ->
dialog offering to open Location settings; providers on but no fix
in 20 s -> the old "Location not available" toast. New strings
(EN+BN): `geo_no_provider_title/msg`, `geo_open_settings`.
How to test: GPS on, open Settings -> Smart -> "Set current place
as centre" (or after a fresh install / reboot, when no cached fix
exists) — it should now take up to ~20 s and then set the centre
instead of failing at once; with location switched off it should
offer the settings dialog.

### Validation (both trees)
Every layout + values file parses as XML; `MainActivity.kt`,
`Helpers.kt`, `FocusGuardPickerActivity.kt`, `Config.kt`,
`A11yService.kt` are brace/paren balanced per a proper Kotlin
scanner; all new ids/strings resolve in both locales; the two
`R.id.navHome/navSettings` refs resolve in `res/menu/bottom_nav.xml`;
`FocusGuardPickerActivity` is in the manifest; crash-fix files
untouched; no MQTT UI restored; kept items present (App Locker +
PIN, intruder selfie, device admin/accessibility, automation master
toggle, geofence). CI is still the compile judge — not pushed yet.

## Templates gallery + single-flow share + Quick Settings tile (2026-11-06)

Tamim's picks from the feature list: Automation log already exists
(so it was NOT built), NFC was dropped; these three were built.

### 1. Templates gallery — 49 templates (25 Rules + 24 Flows)
- Entry: the ⋮ menu on BOTH Automation tabs (Rules tab and Flows
  tab) gained a "Templates" item (`MENU_TEMPLATES`), opening a NEW
  window — `TemplatesActivity` (+ `activity_templates.xml`,
  manifest-declared). One searchable list, two sections (Rules, then
  Flows); each row shows the template name, a one-line Bangla
  description, and a trigger → actions summary built from
  AutoCatalog/FlowCatalog labels (so it follows the current locale).
  Tapping "যোগ করো" adds it at once — no editing needed — and the
  row becomes "যোগ হয়েছে ✓" so the same tap cannot double-add.
  Returning to Automation shows the new item immediately
  (AutomationActivity re-renders in onResume).
- Adding: Rules go through `Automation.importJson` (collision-safe
  re-id; `saveAll` refreshes watchers and time alarms). Flows go
  through `FlowStore.saveFlow` (the Activity re-ids on collision).
  Rule templates are grouped under cat "টেমপ্লেট" in the list.
  Flow templates are linear trigger → action chains built with
  `FlowFlow.newNode/newEdge`; two use a condition node
  (`battery_below`, `time_range`) with a "yes" edge — a "no" result
  ends the walk cleanly (`followEdges` returns null).
- Data: `Templates.kt` — templates are data, not code paths, so
  adding one means adding one `Template(...)` entry. Names and
  descriptions are Bangla in code (the app's default language);
  trigger/action labels come from the catalogs.
- Geofence flow templates ("বাড়ি ফিরলে", "বাড়ি ছাড়লে") read the
  saved geo centre at add time (`Config.geoIsSet/Lat/Lng/Radius`)
  and refuse with a toast when no location is set yet, instead of
  saving a broken flow. They are flows only: proximity alerts are
  registered from FLOW nodes (`FlowTriggers.geofenceSync` reads
  `flowTriggerNodes`), so a rule with a geofence trigger would
  never fire.

### 2. Why each template is real — verified against the engines
Every trigger/action type in `Templates.kt` was checked against the
actual dispatch and param parsing in `Automation.kt` (rules) and
`FlowEngine.kt` (flows) before authoring — nothing was invented:
- Rules and flows do NOT share one event bus. `call_incoming`,
  `call_missed`, `sms_received`, `headphone_in/out`, `screen_on/off`
  are dispatched to `FlowEngine.onEvent` only, and geofence alerts
  are registered from flow nodes only — so none of those appear in
  rule templates. `sunrise`/`sunset` are in FlowEngine's
  TICK_IGNORED set with no event source — rules-only (sunset is a
  rule template). `wifi_connected/disconnected` reach rules via the
  tick poll.
- Param formats differ between engines and the templates follow
  each: rules use `volume_set` "music:80" (percent) and
  `brightness_set` 0–100, `dnd_set` "none"/"off", `screen_timeout_set`
  seconds, `set_alarm` "HH:MM|message"; flows use `volume_set`
  "music=10" (raw), `brightness_set` 0–255 (76 ≈ 30%), `dnd_set`
  "on"/"off", `screen_timeout_set` milliseconds, `set_alarm` "HH:MM"
  only, `set_timer` seconds only. `wait` does not exist in
  FlowEngine and `ringer`/`mode_set` are not flow actions — unused.
  `time_at_days` is "time=HH:MM|days=sun,mon,tue,wed,thu" in both.
- Excluded on purpose: NFC (Tamim), MQTT, relay/peer actions,
  FocusLock/AutoTap/redial picker steps, `send_sms`/`call_number`/
  WhatsApp/Telegram (they need a real number the template cannot
  know), `rule_enable/disable` (target-by-name is fragile).

### 3. Single-flow share
- Each flow card in the Flows tab gained a Share pill (next to
  Dup/Log/Delete). It shares the stored flow JSON with the Android
  share sheet — that JSON is already the exact single-flow format
  the "PhoneLink থেকে আনো" import detects (`nodes` + non-empty
  `name`), so the receiver can import it directly. The import side
  was not changed.

### 4. Quick Settings tile — Automation on/off
- New `AutomationTileService` (manifest: `BIND_QUICK_SETTINGS_TILE`
  + `QS_TILE` filter, icon `ic_nav_auto`, label "AutomationHub").
  Tapping the tile flips the Automation master switch — it writes
  exactly the same preference the Settings toggle writes
  (`Config.setToggle(..., "automation_on", ...)`, which the engines
  read on every event), and the tile state mirrors it
  (ACTIVE = on, subtitle "চালু আছে"/"বন্ধ আছে"). No new
  permissions; the tile never adds itself.
- Settings → Automation card gained an "Add Quick Settings tile"
  button (`btnAddQsTile`). On Android 13+ it calls
  `StatusBarManager.requestAddTileService` (user confirms in the system panel);
  on older Android, or if the request is declined, the toast says
  to add it manually from the QS panel's Edit screen.

## Live sensor paths + rule-card pills + speed (2026-11-06)

### 1. WiFi / charging now fire live, not only on the 60 s poll
- `FlowService` registers a dynamic `ACTION_BATTERY_CHANGED`
  receiver in `onCreate` (unregistered in `onDestroy`) that calls
  `Automation.onBatteryChanged` on every battery change. That
  function had zero callers since LinkService went away, so
  charging_on/off only came from the 60 s poll.
- `FlowService` also registers a `ConnectivityManager.NetworkCallback`
  (WiFi transport): `onAvailable` fires `wifi_connected` with the
  SSID to BOTH engines (`FlowEngine.onEvent` + `Automation.onEvent`),
  `onLost` fires `wifi_disconnected` to both. The 60 s poll and
  `FlowWifiReceiver` stay as fallback. Everything is wrapped in
  try/catch — the service never crashes on this.
- `Automation.currentSsid` (and the identical `FlowEngine.currentSsid`)
  now read the SSID from the live network's `WifiInfo` first and
  fall back to the deprecated `connectionInfo.ssid`; null, empty and
  `"<unknown ssid>"` all normalize to `""`, so a bad read can no
  longer poison `last_ssid` or rule matching. Note: with location
  off, Android 10+ still reports no SSID — the triggers then wait
  for the next live event instead of matching a wrong name.

### 2. Rule cards match the flow cards
- Rule cards in `AutomationActivity.render()` now have the same six
  pills as flow cards, in the same order: Test, Edit, Duplicate,
  Share, Log, Delete (same `pillButton` style). Duplicate deep-copies
  the rule JSON with a new `r<millis>` id and the same ` (copy)`
  suffix the flow cards use (`flow_dup_suffix`); Share sends the
  rule as a one-element JSON array, because `Automation.importJson`
  only accepts arrays — a bare rule object would fail to import
  back; Log shows the per-rule lines from `Automation.recentLog`
  (rule runs log as `▶ <name> (<event>)`), with the same empty
  state as the flow log dialog. All pill labels reuse the existing
  flow strings (`flow_card_dup` / `flow_card_share` /
  `flow_card_log`, `flow_log_empty`) — no new strings.

### 3. Speed: tick off the main thread, one parse per refresh
- `FlowService`'s 60 s tick (`tickForeground`, `checkForegroundApp`,
  `maybeArm`, `Automation.tick`) now runs on a background thread;
  only the `serviceLastTick` heartbeat stamp stays on the main
  looper, on time. The debug build is unchanged (minify still off
  — reflection risk) and the log caps (50 rule / 300 flow lines)
  were already fine.
- `MainActivity.refreshStatus` now parses rules/flows/log ONCE per
  refresh and passes them to `buildStatsText` and
  `refreshSettingsHub`, which used to re-parse the same JSON a
  second and third time. No behavior change.

### 4. Version bump
- `versionName "1.1.0"`, `versionCode 2` — the version Tamim saw
  ("1.00") had never been bumped since 1.0.0/vc1.

### Validation (both trees)
49 templates parsed (25 rules + 24 flows); every template trigger,
action, constraint and condition verified against
`AutoCatalog`/`FlowCatalog` (rule T111/C41/A169, flow T181/C41/A182)
with zero forbidden types; every layout + values file parses as
XML; `Templates.kt`, `TemplatesActivity.kt`,
`AutomationTileService.kt`, `AutomationActivity.kt`,
`MainActivity.kt` are brace/paren balanced per a proper Kotlin
scanner; all new/edited `R.*` refs resolve (strings in both
locales, ids incl. `res/menu/bottom_nav.xml`, drawables, layouts);
manifest components resolve (new activity + tile service);
crash-fix files untouched; no MQTT UI restored; kept items present
(App Locker + PIN, intruder selfie, device admin/accessibility,
automation master toggle, geofence, Focus Guard card + switch);
first-pass files intact (FocusGuardPickerActivity,
`requestSingleUpdate` in `Helpers.kt`). CI is still the compile
judge — not pushed yet. Phone test pending: open Templates from
both ⋮ menus, add a Rule and a Flow template, share one flow, add
the QS tile and toggle it.

## Essentials restyle + Exit actions for Rules (2026-11-06) — v1.2.0

### 1. Essentials-style UI, whole app (Views, not Compose)
Tamim sent the sameerasw/essentials DIY routines screen and chose
"পুরো অ্যাপ" from the preview. The look is layout + card language,
not a 9th theme — all 8 themes keep working; accents come from the
existing `ThemeColors` resolver (`themeAccentColor()` etc.), never
hardcoded emerald.
- **Cards:** `PL.CardView` default radius 22dp → **28dp** (theme
  default, so every `MaterialCardView` without an explicit radius
  follows), the 11 explicit `cardCornerRadius="22dp"` in layouts →
  28dp, and `card_bg.xml` (code-built rule/flow/service cards) →
  28dp. Near-black background, very rounded cards, sage trigger
  boxes, darker action boxes — same language on Dashboard, Rules,
  Flows, Focus Guard, AppLock and Settings. No new main-thread work:
  the restyle only changes drawables/radii and rebuilds the same
  card views `render()`/`renderFlows()` already built.
- **DIY routine cards** (`AutomationActivity`): sage trigger box
  (`bg_trigger_box`, `?attr/colorPrimaryContainer` + on-container
  text) with a family icon + label + param, a small green arrow
  chip column (`bg_arrow_chip`, → entry, ← exit), and darker action
  boxes (`bg_action_box`, `?attr/colorSurfaceContainer`) with an
  ellipsized label ("Toggle Ce..." style, `+N` when more follow).
  The old IF/THEN inset boxes and the "N actions" line are gone —
  the same information lives in the boxes. Per-card pills are
  unchanged (Rules: Test/Edit/Duplicate/Share/Log/Delete; Flows:
  Run/Edit/Duplicate/Share/Log/Delete), as are the Rules/Flows tab
  switch, both ⋮ menus, templates entry and the service switch row.
  Flow cards use the same DIY row (first trigger node + first
  action node; Flows have no exit actions).
- **Pill bottom bar** (`res/layout/nav_pill_bar.xml`, included by
  `activity_main.xml` as `bottomNav` and by
  `activity_automation.xml` as `autoBottomNav`): light sage-green
  rounded bar (`ess_bar_bg` #A9D6B3, fixed token like
  `btn_fill_night` — same in every theme) holding Home / DIY /
  Settings; selected tab = dark pill (`ess_bar_sel_bg` #12241B)
  with light text+icon; a separate dark-green circular + button
  (`?attr/colorPrimary`) at the right end. Same three destinations
  and behaviour as the old `BottomNavigationView`: DIY opens
  `AutomationActivity` (selection stays), Home/Settings switch the
  dashboard page in `MainActivity` / hand over with the `"tab"`
  extra from `AutomationActivity`. The bar's + button replaces the
  two old FABs (removed from the layout and from `switchTab`): it
  opens the rule editor on the Rules tab and the flow editor on the
  Flows tab. `selectNavTab()`/`setupNavBar()` in both activities
  paint the bar; `applyGlassNavTint` is no longer called by either
  (the function itself stays in `LocalizedActivity`).
- **New drawables:** `bg_trigger_box`, `bg_action_box`,
  `bg_arrow_chip`, `bg_nav_bar`, `bg_nav_sel`, `bg_nav_add`;
  new fixed tokens `ess_bar_bg` / `ess_bar_text` /
  `ess_bar_sel_bg` / `ess_bar_sel_text`; new strings `nav_home`,
  `nav_diy`, `ed_exit`, `ed_exit_note`, `ed_add_exit_action`.
  `ThemeColors.onPrimaryContainer()` + `LocalizedActivity.
  themeOnPrimaryContainer()` added for the trigger-box text/icon
  (fallback `md_onPrimaryContainer`, exists in both color files).

### 2. Exit actions for RULES only (Flows out of scope)
A rule may now carry an optional `"exit_actions"` array (same shape
as `"actions"`). Entry actions = the existing `"actions"`. When a
rule fires on an event and it has exit actions AND its trigger type
has a known opposite, the rule is recorded as active in prefs
(`exit_active`: `{ruleId: {end, param}}`); when the opposite event
arrives, the exit actions run through the normal interpreter (a copy
of the rule with `"actions"` replaced by `"exit_actions"`, so loops,
waits and If/Else work exactly as for entries) and the entry is
cleared — one-shot, the entry must fire again to re-arm. Firing
twice while armed just re-arms (one entry). Exits respect the same
rest/debounce window as entries (own `fired_exit_<id>` key) and log
as `⏴ <name> (exit: <event>)`, so the Log explains them.
- **Opposite-event map** (`Automation.OPPOSITE_EVENTS`, 18 pairs —
  only trigger types that really exist as events fired into
  `Automation.onEvent`): charging_on↔charging_off,
  wifi_connected↔wifi_disconnected, screen_locked↔screen_unlocked,
  app_launched↔app_closed, bt_connected↔bt_disconnected,
  dnd_on↔dnd_off, airplane_on↔airplane_off, vpn_on↔vpn_off,
  roaming_on↔roaming_off, dark_on↔dark_off, hotspot_on↔hotspot_off,
  battery_saver_on↔battery_saver_off, mobile_data_on↔mobile_data_off,
  gps_on↔gps_off, music_started↔music_stopped,
  proximity_near↔proximity_far, flip_down↔flip_up, bt_near↔bt_far.
  **Excluded:** screen_on/off, geofence_in/out, headphone_in/out
  (FlowEngine-only events, never reach the Rules engine),
  wireless_charging (no opposite event), and all tick-evaluated
  triggers (time_*, battery_below/above/full/temp, steps_goal,
  weather, etc. — they have no end event).
- **Param matching:** the exit only fires when the opposite event
  matches the entry's trigger param (`triggerParamMatches`, same
  matcher as entries) — e.g. `app_closed` must be for THAT package,
  so one app closing cannot fire another app's exit. A param
  mismatch leaves the entry armed. `wifi_disconnected` carries no
  SSID, so its param is blank and always matches.
- **Stale entries never fire:** armed exits are cleared on boot
  (`FlowBootReceiver` → `Automation.clearAllExits`), when a rule is
  disabled (`setEnabled`), when it is deleted (`delete`), and when
  it is re-saved without exit actions (`upsert`); `fireExits` also
  drops the entry when the rule is missing, disabled or exit-less.
- **Editor** (`RuleEditorActivity`): a "3. WHEN IT ENDS (Exit
  actions)" card after THEN DO, mirroring the actions editor
  (same action picker, plain rows only — If/Else, FocusLock,
  AutoTap and Redial stay entry-only). The section shows only when
  the rule's FIRST trigger (same order as `buildRule`: plain rows
  first) has a known opposite; otherwise a note says the trigger
  has no end so exit actions cannot run. Visibility refreshes when
  a trigger row is added, removed, or its type is re-picked.
  `buildRule()` saves `exit_actions` only when the first trigger
  has an opposite — dead exit actions are dropped, not saved.
- **Cards:** the rule card shows the exit row (← chip + second
  action box) only when `exit_actions` is non-empty.
- **Templates:** 51 total now (27 rules + 24 flows). Two new rule
  templates demonstrate exit actions, using only real catalog
  actions: `r_brave_data` (Brave opens → `mobile_data_toggle`,
  Brave closes → `mobile_data_toggle`) and `r_charge_dnd`
  (charging on → `dnd_set` none, charger removed → `dnd_set` off).
  `Template` gains an optional `exitActions` field;
  `Templates.buildRule` serializes it. `TemplatesActivity.
  summaryFor` now shows the real app label for app triggers
  (was hardcoded "YouTube" for every `app_launched` template) and
  appends ` ↩ <exit labels>` when a template has exit actions.
- **Rules without `exit_actions` behave exactly as before:** the
  `onEvent` entry loop is unchanged except for the arm step after
  `runActions`, which is guarded by `hasExitActions(rule)` AND a
  non-null opposite — and `fireExits` returns immediately when no
  exit is armed.

### 3. Version bump
- `versionName "1.2.0"`, `versionCode 3`.

### Validation (both trees)
- Kotlin-aware brace/paren balance (comments/strings excluded) on
  every edited `.kt` in both trees; every layout + values file
  parses as XML; all new/edited `R.*` refs resolve (strings in both
  locales, new ids, drawables, layouts, `nav_pill_bar` include).
- Grep-verified: every id/handler found before the restyle is still
  present and wired after (bottom-nav destinations, `showPage`,
  `handleTabExtra`, `onSaveInstanceState` tab, sub-screen hide/show,
  `tabRules`/`tabFlows`, `btnAutoMenu`, `statusCard`/`flCard`,
  `btnFlToken`/`btnFlOpen`, `btnRestTime`, `swRulesSync`, all rule
  and flow pills, all editor rows/buttons); `requestAddTileService`,
  `NetworkCallback`, `onBatteryChanged` untouched; no MQTT UI
  reintroduced; no forwarding/relay code touched; workflow
  untouched; crash-fix files (`activity_flow_editor.xml`,
  `res/xml/widget_rule_info.xml`) untouched.
- `diff -rq` the two trees: the ONLY difference is README.md
  (push-history section, push tree only). Not pushed — waits for
  Tamim's tap. Phone test pending: pill bar on all 3 tabs + the +
  button on both Automation tabs, DIY cards on rules and flows,
  exit actions firing (Brave / charging templates), theme picker
  across all 8 themes, version showing 1.2.0.

## v1.2.1 — install fix + DIY card fixes (not pushed yet)
- WHY updates failed ("Something went wrong. App not installed."):
  every CI build signed the debug APK with a fresh keystore generated
  on that runner, so each build had a different signature and Android
  refused to install it over the previous one. This was true for every
  CI-built app, not just AutomationHub. Fix: one fixed debug keystore
  (PKCS12, alias `androiddebugkey`, standard Android-debug DN/passwords
  — a DEBUG-only key, never for release), stored base64-encoded at
  `app/debug.keystore.b64` because the snapshot push cannot carry binary
  files, decoded at build time by `app/build.gradle`, and wired via
  `signingConfigs.debug`. Every build from now on carries the same
  signature.
- ONE-TIME COST: this build's signature still differs from the
  currently installed (random-key) build, so it installs only after
  one uninstall. Export rules/flows first (⋮ menu → export), uninstall,
  install 1.2.1, import back. After that, future updates install over
  the old app normally.
- Rules pills: the six pills (Test/Edit/Dup/Share/Log/Delete) overflowed
  the card and the last ones were cut off — the rules row was missing
  the HorizontalScrollView the flow cards already had. Now wrapped
  identically; all pills reachable by scrolling.
- Arrow chips: the →/← text glyphs rendered wrong on the phone (looked
  like stray letters). Replaced with vector drawables
  `ic_arrow_forward` / `ic_arrow_back` (new), tinted theme accent.
- Version 1.2.1 (vc4). Verified: brace/paren balance 0/0 in
  `AutomationActivity.kt` (both trees); `ic_arrow_forward.xml` parses;
  `HorizontalScrollView` now in both render paths; no old text-glyph
  arrow calls left; exit engine + sensor APIs (`armExit`/`fireExits`,
  `requestAddTileService`, `NetworkCallback`, `onBatteryChanged`)
  untouched; no MQTT UI; crash-fix files untouched; `diff -rq` the two
  trees — the ONLY difference is README.md. Not pushed — waits for
  Tamim's tap. Phone test pending: install 1.2.1 over the old app after
  one uninstall + export/import, pills scroll on rule cards, arrow
  chips show real arrows, version 1.2.1.

## v1.2.1 (smoothness pass — same unpushed release, not pushed yet)
- Audit (code inspection; no benchmark numbers — on-phone
  confirmation still needed): the dominant cost was
  AutomationActivity.render()/renderFlows() rebuilding EVERY card
  (~15 views per card) on the main thread on every onResume and
  tab switch; FlowStore.flows() re-read + re-parsed every flow
  file from disk on every MainActivity refresh and Flows render;
  FlowCanvasView.onDraw issued ~2000 drawCircle calls per frame
  for the grid plus a Path + HashMap allocation per edge per
  frame; app labels/icons went through PackageManager (Binder +
  icon decode) per row per render; theme colours were resolved
  per pill/card; Wi-Fi SSID/band were two Binder reads per
  refresh; the app version was a getPackageInfo call per refresh.
- Fixes: (1) render()/renderFlows() now fingerprint the data
  (rules raw prefs string; FlowStore version counter) and skip
  the card rebuild when nothing changed — only the status/tick
  lines repaint; any write changes the fingerprint, so a skip
  can never show stale cards. (2) FlowStore.flows() is cached
  in memory, invalidated only in saveFlow/deleteFlow — the only
  writers of the flows dir (exports go to exports/, engine
  scratch to flowfiles/). (3) Canvas: grid is one drawPoints
  call from a cached array; edge bezier Paths cached until the
  nodes/edges JSON changes; all Color.parseColor strings are
  constants now; node fill + kind labels resolved once per
  frame; node-rect map reused. (4) App labels cached in
  Automation + AppLocker (label + icon), cleared per-package by
  FlowPackageReceiver on install/update/remove. (5) Theme colours
  resolved once per activity instance (theme change recreates
  the activity, so no staleness). (6) Wi-Fi SSID+band from one
  connectionInfo read; version name resolved once. (7)
  refreshStatusLine() reuses render()'s parsed list instead of
  re-parsing rules.
- NOT fixed, with reasons: RecyclerView conversion (too invasive
  for this pass — the fingerprint skip covers all repeat visits;
  only the first open still builds all cards); moving
  saveAll()'s refreshWatchers/scheduleTimeRules off the toggle
  path (async re-registration could race with rapid toggles;
  needs phone measurement to justify); a time-based debounce of
  refreshStatus() (would risk stale permission state after
  returning from the Permissions screen); minify/R8 (reflection
  risk, excluded); the 60 s tick poll queries (already on the
  background thread since 1.1.0).
- Verified: brace/paren/bracket balance 0/0/0 (block-comment-aware
  checker) on every edited .kt in both trees — the two flagged
  files show the same deltas as the pristine v1.2.0 files, i.e.
  pre-existing checker artifacts in untouched code, not edits;
  1.1.0 + 1.2.1 fixes intact (tick Thread, parse-once refresh,
  keystore, signingConfigs, arrow drawables, pills scroll,
  version 1.2.1/vc4); requestAddTileService, NetworkCallback,
  onBatteryChanged, armExit/fireExits untouched; no MQTT UI;
  workflow untouched; crash-fix files untouched; diff -rq the
  two trees — the ONLY difference is README.md. Not pushed —
  waits for Tamim's tap. Phone test pending: resume/tab-switch
  smoothness, locker list scroll, flow editor pan/zoom/drag,
  version still 1.2.1.

## v1.2.1 — premium feel (step 2, not pushed yet)
- RecyclerView lists: Rules and Flows cards are RecyclerView items
  now (RuleAdapter / FlowAdapter with DiffUtil + ListAdapter in
  AutomationActivity.kt) — only visible rows are bound, scrolling
  recycles the card shells (head row, DIY slot, pill row built once
  per holder and rebound with data). Category headers (rules), the
  service switch row (flows) and empty states are adapter items.
  Headers (status card, FocusLock card) stay outside the list — the
  FocusLock card is now fixed above the list instead of scrolling
  with it. The step-1 fingerprint skip still applies on top (no
  submit when nothing changed); the service tick line is repainted
  directly without a rebind, so it never flickers.
- Light animations: press ripple on every pill (bg_action_pill /
  bg_action_ghost are ripples now — the editor chips and template
  buttons get it too), pill-shaped ripple on the bottom-bar tabs,
  borderless ripple on the + button, ripple on the Rules/Flows
  tabs, the ⋮ menu button, the status line row and the sub-screen
  back button. One short slide/fade pair for every activity
  transition (PL.ActivityAnim, 200ms slide / 150ms fade, via
  android:windowAnimationStyle on Theme.PhoneLink in both
  values/themes.xml and values-night/themes.xml). List add/remove/
  change uses the RecyclerView default item animator — nothing
  custom, nothing heavy.
- No new dependencies (RecyclerView comes from the Material
  library's transitive dependency — if CI ever reports it
  unresolved, add androidx.recyclerview:recyclerview:1.3.2 to
  app/build.gradle), no new permissions, no minify/R8, version
  still 1.2.1 (vc4).
- Verified: brace/paren balance — both checkers give the same
  result on the edited file as on the CI-proven v1.2.0 file
  (tracker 3/2 is a pre-existing checker artifact; opcode-level
  diff of the changed regions nets 0/0), so the edit is
  balance-neutral; all edited/new XML parses (and all res XML in
  the tree parses); all R.* refs used resolve (strings in both
  locales, drawables, anims, styles); step-1 + v1.2.1 fixes intact
  (caches, fingerprint skip, drawPoints, lazy helpers, keystore,
  signing config, arrow drawables, pill scroll via the shared
  shell); APIs intact (requestAddTileService, NetworkCallback,
  onBatteryChanged, armExit/fireExits); every pill/toggle/menu/
  template/exit wiring still present; crash-fix files and workflow
  untouched; `diff -rq` the two trees — the ONLY difference is
  README.md. One real bug was caught in review and fixed before
  delivery (ServiceHolder smart-cast in FlowAdapter). Not pushed —
  waits for Tamim's tap. Phone test pending: smooth scrolling on
  both tabs, add/delete animations, ripples, slide/fade between
  screens, FocusLock card pinned, version 1.2.1.
- Not done: release build (needs a release-keystore decision);
  on-phone measurement of the feel (needs Tamim's test); tab
  switches reset the list scroll position (adapter swap).
