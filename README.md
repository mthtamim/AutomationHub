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

## v1.2.2 — Material 3 motion + smoothness (SHIPPED 22:24, CI #17 Success)

**CI fix (run #16 failed, 9 compile errors):** window transitions
require framework `android.transition.Transition` — the Material
(AndroidX) classes cannot be assigned to a window, so the
container transform is now a framework ChangeBounds + Fade
(bounds morph + content fade, 450 ms, emphasized easing) and the
default window transition is a framework Fade (300 ms, emphasized
easing); the Material colour morph does not apply to windows.
View-level shared-axis still uses MaterialSharedAxis. Also fixed:
`TimeInterpolator` import (android.animation, not
android.view.animation) and a `getDrawable(this)` inside a
LinearLayout apply block that passed the layout instead of the
Activity (AutomationActivity.kt:1077). `ThemeColors.surface()` /
`background()` removed (now unused).

Motion reference: the real Essentials app (Jetpack Compose +
Material 3 Expressive) as Tamim showed it in a screen recording
on 2026-11-06. Correction the same evening: the target is the
app's PERFORMANCE and SMOOTHNESS — stable frame rate, no dropped
frames on scroll or screen opens, instant touch response — NOT
the nav bar. The morphing bottom bar from the earlier reading
of the video is demoted to nice-to-have and is NOT in this pass:
it would need a new sub-bar layout, new strings and a "?" dialog,
which is real risk, not free.

What changed (code-inspection findings; on-phone confirmation
still needed — no frame-rate numbers were measured, none are
claimed):
- Shared-element morph (framework ChangeBounds + Fade, 450 ms,
  emphasized easing): tapping a rule
  or flow card morphs into its editor (RuleEditorActivity /
  FlowEditorActivity) — the card is the shared element, unique
  transition name per card (rule_card_<id> / flow_card_<id>),
  the editor container grows out of it, back reverses the morph.
  The + FAB morphs into the editor the same way (fab_new_rule /
  fab_new_flow). The editor installs the transform in onCreate
  (PLMotion.setupEditorTransform, right after setContentView) and
  postpones the enter transition until the first frame is laid
  out, so a heavy editor open never drops frames mid-morph.
  (Framework, not MaterialContainerTransform — see the CI fix
  note above; no colour morph on windows.)
- Shared axis (MaterialSharedAxis X, 300 ms, emphasized easing):
  Rules <-> Flows tab switch (the inner column — tab row,
  status/FocusLock cards, list — moves as one), and Home <->
  Settings in MainActivity (showPage, skipped on the very first
  call before the first layout).
- Fade through (framework Fade, 300 ms, emphasized easing):
  the default window transition for EVERY activity, installed in
  code from LocalizedActivity.onCreate (PLMotion.
  applyDefaultWindowTransitions). This replaces the generic
  PL.ActivityAnim 200 ms slide / 150 ms fade: the
  android:windowAnimationStyle line is removed from
  Theme.PhoneLink in both values/themes.xml and
  values-night/themes.xml. The PL.ActivityAnim style and the
  anim files stay in res, unreferenced. All other activity opens
  (templates, pickers, AppLock, Permissions, sub-screens opened
  as activities) get the fade-through — no spatial relationship,
  no shared axis, nothing hand-rolled.
- Easing + durations: everything uses the M3 emphasized easing
  (PathInterpolator 0.2, 0, 0, 1) and the M3 scale — medium
  300 ms, large 450 ms. One helper owns it all: PLMotion.kt
  (new file) — sharedAxisX(), openWithTransform(),
  setupEditorTransform(), beginSharedAxisX(),
  applyDefaultWindowTransitions() (frameworkFade() /
  frameworkMorph() are private helpers inside it).
- Switches: the two code-built platform Switch widgets
  (rule/flow card toggle, Flows service row) are SwitchMaterial
  now — the M3 switch morphs thumb/track instead of flipping
  instantly. All XML layouts already used SwitchMaterial.
- List entrance: on the first open, the visible rows fade/slide
  in with a 30 ms stagger (300 ms, emphasized easing, 16dp rise).
  It plays once per activity instance, for the first list shown
  (the initially visible tab); the other tab's first appearance
  is a tab switch, which already has its shared-axis transition,
  and the two must not animate the same rows at once. The item
  animator is off for that first submit (its own add-fade would
  fight the stagger) and restored before the entrance runs. The
  entrance waits for the next pre-draw (doOnPreDraw), i.e. after
  the new rows are laid out. Bind/recycle reset guards
  (alpha/translationY reset in both adapters' onBindViewHolder
  and onViewRecycled) make sure a row rebound or recycled
  mid-entrance never appears invisible or shifted.
- Ripples / state layers (verified, not changed): bg_action_pill
  and bg_action_ghost are RippleDrawables with
  ?attr/colorControlHighlight (no hardcoded colours, theme
  follows); bg_nav_tab_ripple is a mask-only pill ripple; the
  tabs, + button, ⋮ menu and status row use
  selectableItemBackground(Borderless). New: the card body is
  tappable now, so the card root gets a theme ripple foreground
  (selectableItemBackground, clipped to the card).
- Dialogs + bottom sheets (verified, not changed): the 44
  AlertDialog.Builder sites and the two plain BottomSheetDialog
  sites (theme picker, palette sheet) rely on the M3 theme's
  default motion — no custom durations, nothing to align.
- List animator (kept): the RecyclerView default item animator
  stays — its durations are inside the M3 scale. It is only
  switched off for the one first-load submit (see entrance
  above) and restored immediately after.
- PERFORMANCE — first-open main-thread audit: the one real
  blocker found was renderHealth() in AutomationActivity — five
  Binder/IPC calls (AccessibilityManager, NotificationManager,
  AppOps, AlarmManager, and the Shizuku IPC, the slowest) ran
  synchronously in onResume, i.e. on the main thread before the
  first frame. They now run on a background thread (kotlin
  thread helper); only the small paint (health title + broken
  rows) runs on the main thread, with a generation guard so a
  stale paint is dropped and nothing paints after the activity
  is gone. Audited and left alone: MainActivity.refreshStatus
  (rules/flows/log parsed once, small Binder calls —
  BatteryManager, WifiManager, DevicePolicyManager,
  AccessibilityManager — a few ms total), renderFocusLock (one
  getPackageInfo call), TemplatesActivity (recycling adapter,
  only visible rows bound), the pickers (already load on a
  background thread), the editors (rule JSON / flow file are a
  few KB; the container transform postpones the morph until the
  rows are laid out). The RecyclerView + DiffUtil + caches from
  v1.2.1 already cover the repeat paths.
- Version 1.2.2 (vc5). No new dependencies, no new permissions,
  no minify/R8, no MQTT UI, no forwarding/relay touched, no
  behaviour change — every button/handler/id still wired (pill
  bar, DIY cards, exit section, all six pills per card, toggles,
  ⋮ menus, templates entry, FAB). The card body is now tappable
  (opens its editor); that is the only new tap target.
- Not done: the morphing bottom bar (demoted — see above);
  baseline profiles (needs a release build + a profile
  generation run, not a debug CI build); a release build with
  minify/R8 (needs a release-keystore decision); lazy inflation
  of the MainActivity Settings page (activity_main inflates the
  whole home + settings + sub-screen tree on cold start — the
  biggest remaining first-frame cost — but making the Settings
  page a ViewStub would move ~60 findViewById calls behind a
  lazy inflate, which is a rewrite with real NPE risk, not a
  safe one-pass fix); on-phone measurement of the feel (needs
  Tamim's test). Phone test pending: card -> editor morph and
  back, FAB morph, tab switch, Home <-> Settings, staggered
  first-open entrance, switch motion, no dropped frames on
  scroll/open, version 1.2.2.

## v1.2.3 — visible motion (not pushed yet)

Why: Tamim confirmed 1.2.2 installed (version shows 1.2.2) but
reported NO visible changes vs 1.2.1 and performance still like
before. Two causes were proven from the code; the morph cause
was not.

Proven cause 1 — the tab switch could not animate as built:
switchTab() ran a MaterialSharedAxis scene transition around a
DiffUtil list rebind. The same ViewHolders are rebound in place
— no views appear or disappear — so the transition has nothing
to move. The tab fills just changed colour instantly. Fix:
PLMotion.slideFadeIn() — a direct view animation on the list
itself (48dp slide in from the tab direction + fade, 300 ms,
emphasized easing) that runs regardless of what the diff does.
Same for MainActivity Home <-> Settings (showPage). The
beginSharedAxisX call is kept (it may still animate the
status/FocusLock card visibility change), but the visible motion
no longer depends on it. The animation only runs on a real
switch (switchTab gates on a changed tab; showPage keeps its
isLaidOut + same-tab guards), so re-tapping a selected tab and
onResume never replay it.

Proven cause 2 — activity opens got LESS visible, not more:
v1.2.2 replaced the 200 ms slide (PL.ActivityAnim) with a
300 ms framework Fade as the default window transition. On a
dark UI a fade is barely noticeable. Fix: the default window
transition is now a framework Slide from the bottom (M3 medium
duration, emphasized easing) for enter / reenter / return;
Fade is kept only for the outgoing (exit) transition, where a
slide-down would read as "back" while navigating forward.

Morph (unproven cause, made robust by construction): the
card -> editor morph wiring is placed correctly on both sides
(unique transition names, editors call setupEditorTransform
right after setContentView, all activities extend
LocalizedActivity so FEATURE_ACTIVITY_TRANSITIONS is requested)
yet nothing shows on Tamim's phone — no cause could be proven
from code alone, so none is claimed. Two guarantees instead:
(1) if the shared-element match fails, the Slide enter
transition from above plays for the non-shared content, so the
editor open always shows visible motion; (2) a postponed enter
transition can no longer hang the open — setupEditorTransform
now posts a 600 ms delayed startPostponedEnterTransition() as
a safety net (calling start twice is a no-op).

Not done — Settings page lazy inflation (ViewStub): investigated
and NOT done in this pass. MainActivity.onCreate eagerly binds
~15 lateinit settings fields (tvSetPill, tvHubPermsSub,
tvHubSecSub, tvHubDataSub, tvHubDiag, tvPinInfo, tvAdminInfo,
tvGeoInfo, tvFocusGuardInfo, tvFocusAppsInfo, btnFocusStart,
btnFocusEnd, tvStats, tvA11yState, tvGeneralDiag) and wires
~30 more settings listeners, and refreshStatus() /
refreshSettingsHub() / refreshFocusGuard() touch those fields
from onResume and the 60 s tick — moving all ~50 sites behind
a lazy inflate is a rewrite with real crash risk if any site is
missed, and the sub-screen tree inside activity_main (~764
lines, 52 ids) would still inflate at cold start anyway, so
the win would be partial. No smaller safe first-frame win was
found that moves the needle: the remaining onResume work
already parses once and costs a few ms. Baseline profiles and
a release build with minify/R8 still need a release-keystore
decision. No frame-rate numbers were measured, none are claimed.

Version 1.2.3 (vc6). No new dependencies, no new permissions,
no minify/R8, no MQTT UI, no forwarding/relay touched, no
behaviour change — every button/handler/id still wired. The
only new visible behaviour is motion: tab/page switches slide
+ fade, activity opens slide up. Phone test pending: tab switch
animation (Rules <-> Flows), Home <-> Settings animation,
editor open — morph or slide fallback — and back, every other
activity open slides up, cold-start feel, version 1.2.3.

## v1.2.4 — tab mix-up + redial timing (not pushed yet)

Why: two phone-reported bugs on 1.2.2, confirmed again on
1.2.3 (screenshots 22:49, video 22:56).

Bug 1 — tab mix-up: the bottom-bar pill and the shown page
disagreed in BOTH directions (Settings pill + Home/Dashboard
page, and Home pill + Settings page), and only sometimes — an
intermittent race, not a permanently wrong mapping.

Bug 1 mechanism, proven from the code: handleTabExtra painted
the pill ONLY (selectNavTab) and never switched the page, so
the DIY -> Main round-trip (AutomationActivity.goMain + finish)
desynced in both directions depending on which page was showing;
onCreate did the same (selectNavTab(startTab) without showPage);
onResume repainted the pill from the stale navSelectedId instead
of healing it; openSettings/closeSettings and the back-press path
also painted the pill only; setupNavBar re-painted the pill
after every click.

Bug 1 fix: showPage is now THE tab switcher and the single
source of truth — it paints the pill FIRST, then switches the
page. handleTabExtra, onCreate, openSettings/closeSettings,
the back-press path and the nav-bar clicks all go through
showPage; onResume heals the pill from the SHOWN page instead
of repainting from navSelectedId. An idempotency guard at the
top of showPage (already on that page -> re-assert the pill,
return, never replay the transition) makes a second tap
mid-transition safe. The 1.2.3 slideFadeIn motion is untouched.

Bug 2 — redial: the 1st call dials, later ones never dial
(video on 1.2.3: number 01761348083, SIM 1, gap 5 s, max 4,
ring 5 s — the first call dialled and ended ~15 s in, then NO
2nd call in the remaining ~15 s of the 30 s video, status stuck
at attempt 1). Mechanism: the loop was driven by FlowService's
60 s Handler tick (not Doze-proof, dies with the process), and
three state transitions (calling -> ended -> wait -> calling)
each cost a tick, plus a fixed 25 s log settle — so the real
wait was minutes, not the 5 s gap. placeCall also swallowed all
failures silently while the state machine advanced.

Bug 2 fix — CallShield's working pattern, ported: the next
redial is armed as an EXACT AlarmManager alarm
(setExactAndAllowWhileIdle, RedialReceiver) instead of waiting
for the tick; the call end is caught promptly — FlowCallReceiver's
IDLE branch now polls Redial (the Redial object's own poll, not
a Lab hook); and one poll can advance several steps (calling ->
ended -> wait), so the chain no longer costs a tick per
transition. The 25 s settle is no longer fixed: the log row is
checked 5 s after the call end (CallShield's log-check delay —
the row is usually written at call end, but not guaranteed),
and the full 25 s settle applies only when the row is not there
yet. A 35 s watchdog (settle + 10 s) still bounds a stalled
calling/ended phase when the call never starts (no IDLE
transition then). A call that never started does not burn an
attempt: the retry re-uses the same attempt number, retries
after 30 s, and after 5 consecutive failures the session stops
with a clear message. The answered check now reads only THIS
call's log row (date >= lastCallAt) — an older row from a
previous attempt no longer counts as "answered".

Exact-alarm grant (CallShield's rule): SCHEDULE_EXACT_ALARM was
already declared in the manifest (for the time rules), so this
pass adds NO new permission. The grant is checked before
scheduling (canScheduleExactAlarms, API 31+); when it is missing
there is NO silent inexact fallback — the session still starts
on the 60 s tick, but the log says so once per session, the
Dashboard Start toast says so, and the status line carries
"exact alarm off" while the session runs. The 60 s tick stays
as the slow fallback driver.

Max-attempts check (video said max 4 but status "1 / 8"):
verified, NOT a bug. The Dashboard Start handler reads
etDashRedialMax and passes it straight into Redial.start,
which stores it and the status line reads it back — bindings
are correct and no code path turns 4 into 8 (an empty field
falls back to 20, never 8). The "8" is a misread of the Bengali
digit ৪. No change made here.

Honest minimum wait: with the grant on, the 2nd call comes
~gap after the call ends when the log row is already there
(gap 5 s -> ~5 s), and ~gap + 5 s when the row is late. The
25 s settle survives only as the fallback bound for a missing
row, and the place-failure retry is 30 s. No shorter wait is
safe: reading the log before ~5 s can mistake "row not written
yet" for "not answered" and dial once more after an answered
call.

Not ported from CallShield: its dedicated RedialService (a
foreground service owning the call loop). AutomationHub's Redial
is a shared object driven by FlowService's tick + the exact
alarm + the phone-state receiver, and a new foreground service
would be a bigger change with its own notification and
lifecycle; the alarm + IDLE-hook design delivers the same
timing (gap + 5 s) without it. If the exact-alarm grant is off,
the IDLE hook still fires promptly but the "wait" -> place step
waits for the next tick (up to 60 s) — that is the only
slow path left, and it is surfaced, not silent.

Version 1.2.4 (vc7). No new dependencies, no new permissions
(SCHEDULE_EXACT_ALARM already declared), no minify/R8, no MQTT
UI, no forwarding/relay touched, no behaviour change beyond the
two fixes. Phone test pending: tab pill always matches the page
after Home/DIY/Settings taps + back press + reopen (both
directions, rapid double taps); redial 2nd/3rd attempts dial on
time (gap 5 s -> ~5 s after the call ends) in the background
with the app closed, version 1.2.4; with the exact-alarm grant
off, the status line shows "exact alarm off" and redial runs
slow on the tick.

## v1.2.5 — Home scroll jank + Rules editor slowness + AutoTap run leak (not pushed yet)

Why: phone verdict 23:24 — 1.2.3 felt FAST (first confirmation
the 1.2.3 motion work landed), but 1.2.4 feels heavy again.
Tamim narrowed it 23:29–23:30: the heaviness is WHEN SCROLLING
ON HOME, and the Rules editor ALSO sometimes feels slow.

Home scroll mechanism, proven from the code: page_home.xml is a
ScrollView holding one big LinearLayout (441 lines) with
FOURTEEN layout_weight uses. Nested weights force double
measure passes, and any text change in the tree forces a full
re-measure of it. MainActivity.refreshStatus() runs on every
onResume and unconditionally re-set every status text (plus
refreshDashboard() with BatteryHelper + ownWifi() Binder IPC),
so each resume re-measured the whole Home tree even when
nothing had changed.

Home fix: (1) refreshStatus() / refreshDashboard() /
refreshFocusGuard() / refreshSettingsHub() / refreshAutoTapCard()
/ refreshRedialCard() now assign a TextView / ProgressBar /
text colour ONLY when the new value differs
(setTextIfChanged / setProgressIfChanged /
setTextColorIfChanged in MainActivity) — a resume with no
changes touches no view, so no re-measure happens at all.
(2) The hero device row no longer uses a weight: the
horizontal LinearLayout + weight=1 text column became a
RelativeLayout (phone art pinned to the end, text column
filling the rest) — same look, one measure pass instead of
two. The other 13 weights are the equal-split button/field
rows (Start/Stop, SIM chips, gap/max, ring/speaker, on-answer,
Start/Stop): weight is the only framework way to keep the
exact 50/50 (33/33/33) split without adding a ConstraintLayout
dependency — GridLayout/TableLayout weights cost the same
second pass, and fixed dp widths would break on other screen
widths. With the changed-value guards, a re-measure now only
happens when a value actually changes, so the remaining weight
cost is paid rarely instead of on every resume.

Rules editor mechanism, proven from the code: every
trigger/condition/action/exit row is built programmatically
(Row init: ~6–10 views, backgrounds, typeface) and addView-ed
into LinearLayout containers during onCreate, all synchronously
on the main thread — a rule with many rows blocks the open.
Two extra costs on the same path: buildCategoryChips() called
Automation.rules(this) — a full JSON parse of ALL rules — on
the main thread during open; and Row.refreshChip() called
AutoCatalog.label()+hint(), each doing a
resources.getIdentifier() scan of the resource table, per row
(plus refreshCondChip() for action rows).

Rules editor fix: (1) the existing-rule row load moved to
loadExistingRule(), run after the first frame via a post on
the content view — the editor chrome draws at once, rows
build right after; row order, pickers, save/load and the
exit-section logic are verbatim, only the timing moves, and
user input cannot arrive before it runs. (2)
buildCategoryChips() parses the rules JSON on a background
thread and posts only the Chip building back to the main
thread. (3) AutoCatalog.label()/hint() results are cached by
kind+type+locale (locale in the key, so an in-app language
switch never serves stale strings) — the getIdentifier scan
now runs once per type instead of once per row.

Shared intermittent suspect, fixed: Redial.poll() is
@Synchronized and was called on the MAIN thread by
FlowCallReceiver's IDLE branch and by RedialReceiver (alarm),
while the 60 s tick thread also polls — a main-thread poll
could block behind a background poll doing call-log queries.
Both receivers now call Redial.pollAsync(), which runs the
same poll on one shared single-thread worker; the
@Synchronized poll() still serialises polls with the tick.
Exact-alarm timing is identical (the alarm still fires on
time; only poll execution moves off the main thread), and
the no-silent-fallback rule is untouched.

NOT changed (exonerated, verified in code): the 60 s
FlowService tick work already runs on a background thread
(FlowService.kt tickRunnable starts a Thread for
Automation.tick), so the tick was not touched.

AutoTap run leak, reported 23:41: after a script finished, the
app kept running in the background and a notification stayed
visible. Two causes, proven from RunnerService.kt: (1) runLoop()
had no try/finally — an exception in the loop killed the worker
thread without finishRun(), leaving running=true, the foreground
"চলছে" notification (id 41) and the wake lock behind; (2) even
on a normal finish, finishRun() posted an "AutoTap রান শেষ"
summary notification (id 44) that sat in the shade until tapped.
Fix: runLoop() is now a wrapper (runLoopInner holds the old body
verbatim) that guarantees finishRun("ত্রুটির কারণে বন্ধ হয়েছে")
on any exception, plus a finally that force-clears the running
flag, wake lock, floating panel and foreground notification if
finishRun never ran or failed midway; the final summary
notification is gone entirely (postFinalNotification and
FINAL_NOTIF_ID removed) — the run report is already saved
in-app via Store.setLastReport. FlowService and its "is running"
notification (id 101) are untouched: that one stays while the
Flows switch is ON, by Android's foreground-service rule.

Version 1.2.5 (vc8). No new dependencies, no new permissions,
no minify/R8, no MQTT UI, no forwarding/relay touched, no
behaviour or look change beyond the fixes. Phone test pending:
Home scrolls smooth after resume/reopen, Rules editor opens
fast on a rule with many rows, redial timing unchanged
(gap 5 s -> ~5 s after the call ends), no RunnerService
notification remains after an AutoTap run ends, version 1.2.5.

## v1.4.1 — crash catcher (diagnostic for the launch crash)

Why: v1.4.0 (run #37) still crashes on launch on Tamim's phone — the
nav-bar cast fix was not the (only) cause, and the crash leaves no
visible stack trace. This version adds `CrashHandler.kt`: a default
uncaught-exception handler installed first in `LocalizedActivity.onCreate`
that writes the full stack trace to `Downloads/AutomationHub-crash.txt`
before the process dies. No UI or behaviour change otherwise.

## v1.4.0 — preview deltas: palettes, nav bar + FAB, SearchBar, Templates + Log screens, Snackbars (not pushed yet)

Why: Tamim approved the remaining preview-scope items
("baki gulo koro", 2026-11-07) for ALL places — Autotap,
Autodial (redial) and AppLock included. This pass also folds
in the fix for the v1.3.0 CI failure (run #34): v1.3.0 used
Compose Material3 role names that do not exist as attributes
in the Views material library, so `:app:processDebugResources`
failed with "style attribute 'attr/colorInverseSurface' not
found" (same for the other three). The Views names are
colorSurfaceInverse, colorOnSurfaceInverse,
colorPrimaryInverse and scrimBackground — renamed everywhere
in this pass. v1.3.0 never produced an APK and has no release;
this build is the proof.

What changed:
- Palettes: the preview's hex values applied verbatim for all
  8 themes, dark + light (Emerald primary #4edeB4, replacing
  the app's #10B981). Derived rules, documented in the theme
  files: surfaceContainerHighest = high lightened (dark) /
  darkened (light) by 6% HSL lightness; outlineVariant = 50%
  RGB mix of outline + onSurface; scrimBackground = #52000000
  (M3 standard); onError dark #450A0A / light #FFFFFF;
  errorContainer dark #33151A / light #FDECEC; secondary =
  primary (app convention — the preview has no secondary
  token). Glass rgba values become #AARRGGBB; aurora/graphite
  gradients are 3-stop linear (angle 270), no glow layers;
  NEW light drawables bg_theme_aurora_light.xml and
  bg_theme_graphite_mono_light.xml. Solid themes' flat
  android:windowBackground removed (the preview is flat for
  them). themePreviews() hardcoded hex updated to the preview's
  dark values.
- Navigation: the signature pill bottom bar is replaced by the
  preview's 4-item NavigationBar + FAB (same 4 destinations
  the pill served: Home, DIY, Templates, Settings). FAB
  (primaryContainer, 60dp) opens the "নতুন তৈরি করো" sheet
  (new rule / new flow / from template); it hides on
  sub-screens because the bar hides as a unit. New files:
  res/menu/nav_main.xml, res/layout/nav_m3_bar.xml,
  res/layout/bottomsheet_new.xml, res/values/strings_m3nav.xml,
  NavBarHelper.kt (setupBar / setupFab / showNewSheet).
- Home SearchBar: inline search over rules, flows and
  templates (HomeSearch.kt, SearchBar in page_home.xml);
  a rule hit opens the Rule editor, a flow hit the Flow
  editor, a template hit the Templates screen with the query.
  Back closes the search first; leaving Home closes it.
- Templates screen: own activity (TemplatesActivity.kt,
  activity_templates.xml) — MaterialCardView rows, category
  chips (সব / চার্জিং / সময় / লোকেশন / অ্যাপ / FocusLock),
  search with a "query" extra. Categories come from the real
  trigger types. Note: the FocusLock chip currently shows an
  empty list — no template in Templates.kt has a focuslock
  trigger yet (mapping kept for future templates).
- Log screen: own activity (LogActivity.kt,
  activity_log.xml) — chips আজ (default) / গতকাল / সব +
  "মুছে ফেলো" (confirm dialog, clears both logs);
  newest-first rows with `dd MMM HH:mm`. Automation log
  (timestamped) in all filters; FlowStore log (undated) only
  under সব. Entry points: the Data panel's "কার্যকলাপের লগ
  দেখো" button and the Automation menu log items (the
  per-rule filtered log dialog is unchanged).
- Snackbars: 33 Toasts become Snackbar (same text, same
  duration, anchor = content view). 31 Toasts stay Toast, for
  two reasons only: (a) 17 background paths with no Activity
  / view to anchor to — FlowEngine (1), Automation (1), and
  the AutoTap overlay services RecordOverlay (5),
  EditOverlay (4), Dock (2), Runner (1), PreflightOverlay (1),
  PickerOverlay (1), Calibrate (1); (b) 14 places where the
  Activity calls finish() right after the message — a
  Snackbar dies with its Activity, so the confirmation would
  never be seen; Toast is the only visible confirmation there
  — FlowRestoreActivity (4: bad file, empty, restore done,
  error), FlowFlowImportActivity (3: bad, success, error),
  FlowFloImportActivity (2: bad file, error), ScanActivity (4:
  permission denied, camera failed, scripts imported, QR
  no-match), RuleEditorActivity (1: history restored).
- Motion: PLMotion 300 ms → 340 ms (tabs) / 320 ms
  (shared-axis) per the preview; window transitions stay
  300 ms, editor morph stays 450 ms.
- Segmented: the Automation Rules | Flows selected state is
  now SecondaryContainer + onSecondaryContainer text (was the
  v1.3.0 mint fill); unselected state unchanged.
- Leftover non-M3 audit, everywhere: AutoTap's programmatic
  UI (EditText → TextInputEditText, Button → MaterialButton —
  backgroundTintList = null where a custom GradientDrawable
  background is assigned afterwards, so it is not tinted
  primary); redial rows in the rule editor (etNumber / etGap /
  etMax / etRing, cbSpeaker / cbAlert → MaterialCheckBox);
  FlowPicker etFilter cast (matches dialog_picker.xml);
  AppLock was already fully M3 (no change). Latent crash
  fixed: v1.3.0 converted the Permissions gauge XML to
  CircularProgressIndicator but left the Kotlin field typed
  ProgressBar — findViewById<ProgressBar> would have thrown
  ClassCastException the moment PermissionsActivity opened
  (never shipped: v1.3.0 has no APK). Field type fixed.

Deliberately NOT changed: the pill bar's files remain as
dead files (nav_pill_bar.xml, bg_nav_*.xml, ess_bar_* colours,
menu/bottom_nav.xml — the snapshot push cannot delete files);
the old solid-theme gradient drawables (bg_theme_ocean_deep,
bg_theme_midnight_sapphire, bg_theme_sunset_ember,
bg_theme_rose_quartz, bg_theme_forest_mist) are now unused;
the battery ring; switches; PLMotion window transitions
(300 ms) / morph (450 ms); the FocusLock chip (empty until a
focuslock template exists); PromptActivity's plain Button (it
runs under a framework dialog theme — a MaterialButton would
crash); Ui.kt's custom toggleSwitch / valueButton / chip
controls and its SeekBar (converting it would change the
on-release reporting API); ImageButtons (custom backgrounds,
no Material equivalent needed); FlowService notification id
101; no MQTT UI restored; no speed claim — nothing measured.

Version 1.4.0 (vc11). No new dependencies, no new
permissions, no behaviour change except the messages listed
above (Snackbar where the screen stays open, Toast where it
closes or there is no screen). Phone test pending: all 8
themes on every screen (light + dark); nav bar tab switches
(Home / DIY / Templates / Settings) and FAB sheet (new rule,
new flow, from template); Home SearchBar (rule, flow,
template hits; Back closes it); Templates screen (chips +
search, add from template); Log screen (আজ / গতকাল / সব,
মুছে ফেলো, Data-panel entry); segmented selected state in
both themes; Snackbars visible where the screen stays open
(save flow, copy/paste node, PIN wrong, permission gauge);
Toasts still seen on import/restore/scan close; redial rows
in the rule editor (number, gap, max, ring, speaker, alert);
AutoTap screens (buttons, fields, PIN dialog); AppLock PIN
screen; version 1.4.0.

## v1.3.0 — full Material Design 3 (pushed 2026-11-07)

Why: the app's M3 adoption was partial — AppCompat dialogs,
framework date/time pickers, a round MaterialButton posing as a
FAB, plain inset text fields, a hand-rolled tab paint, and a theme
with only half the M3 colour roles (no surfaceContainerLowest /
Low / Highest, no tertiary, no inverse, no outlineVariant /
scrim). This pass finishes the M3 system, visual-only: no
behaviour, no ids, no wiring changed — one exception: the
Rules | Flows tabs are now a real MaterialButtonToggleGroup
(same ids, same paint, same motion).

What changed, screen by screen:
- Themes (all 8, light + night): full M3 roles — surfaceContainer
  Lowest / Low / Highest, surfaceDim / Bright, tertiary family,
  inverse family, outlineVariant, scrim. Glass themes derive
  theirs from their own palettes (tertiary = primary family,
  inverse = onBackground / background). Cards now use
  surfaceContainerLow.
- Dialogs: all 68 AlertDialog.Builder sites (Main, Automation,
  both editors, AppLocker, Flow* activities, StudyGuard, AutoTap)
  are MaterialAlertDialogBuilder — same buttons, same callbacks.
- Pickers: Focus Guard time, flow date and flow time are
  MaterialTimePicker / MaterialDatePicker (24-hour clock kept;
  the flow date is read back in UTC so it never shifts a day).
- Automation tabs: Rules | Flows is a MaterialButtonToggleGroup
  (segmented control, singleSelection + selectionRequired, pill
  ends per the preview); the mint / dark fill paint and the
  shared-axis motion are unchanged.
- Flow editor: the "+" is a real FloatingActionButton (48dp,
  ic_plus icon, primaryContainer fill).
- Fields: rule name / category and the app-picker manual package
  field are M3 FilledBox (PL.Field — the preview's Filled text
  field; hint stays inline because the label above already names
  the field). PL.Field's 14dp bottom corners are the app's house
  style; the preview draws 14 top / 0 bottom.
- Permissions gauge: M3 CircularProgressIndicator (96dp ring, 8dp
  track) — same id, same setProgress.
- Bottom sheets (theme picker, flow palette): M3 drag handle.
- Panels: the 10 Settings-panel cards drop their inline attrs and
  take the PL.CardView theme style (identical values); the
  stroke-override cards (rule editor WHEN / THEN DO / EXIT, Home
  hero) keep their strokes.
- AutoTap screens: Theme.AutoTap is now a Material3 parent (same
  colours), so its dialogs are M3 too.

Deliberately NOT changed: the pill bottom bar (the app's
signature, fixed sage tokens, same in all 8 themes); the battery
ring and the gauge's centred text; the flow-editor title fields
(etFlowName / etFlowFolder — title text, not form fields); the
dashboard FilledBox fields (already M3); switches (already
SwitchMaterial); PLMotion (already M3 emphasized easing); the
44 Toasts (no Snackbar — toasts also fire from background paths
with no anchor view); no speed claim — a redesign alone makes
nothing faster, and nothing was measured. No MQTT UI restored;
FlowService notification id 101 untouched.

Version 1.3.0 (vc10). No new dependencies, no new permissions,
no behaviour change. Phone test pending: all 8 themes on every
screen; Settings first-open + each of the 4 panels; Rules / Flows
tab switch both ways; flow-editor FAB opens the palette; rule
name / category save; app-picker manual package add; Focus Guard
time picker; flow date / time dialogs; permission gauge value;
theme picker sheet; every dialog (PIN set, accent, update,
geo radius, import) still opens and still acts; version 1.3.0.

Preview deltas (from the inspection of
automationhub-material-3-preview, 2026-11-07 — NOT yet built,
need Tamim's word): the preview lists 9 screens, not 6 —
Templates and a dedicated Log screen do not exist in the app;
its 8 theme palettes use different hex values from the app's
current themes (no seed hex is stored — the palettes would have
to be copied verbatim, including the theme-picker preview cards
and the glass gradient drawables); it shows a 4-item
NavigationBar + FAB where the app keeps its signature pill bar;
a Home SearchBar; Snackbars instead of Toasts; motion at
340 ms (tabs) / 320 ms (shared-axis) vs PLMotion's 300 ms;
segmented selected = SecondaryContainer where the app keeps its
approved mint fill. None of these were changed in this pass —
they were built in v1.4.0 (section above), on Tamim's approval
2026-11-07.

## v1.2.6 — cold-start speed: lazy Settings + baseline profile (shipped 2026-11-07)

Why: cold start inflated the whole tree — `activity_main.xml`
(824 lines) included the Settings hub (`page_settings.xml`,
304 lines) AND all four sub-screen panels (~700 lines, 52 ids)
inside the subRoot overlay, every one of them `visibility=gone`
but inflated anyway, before the first frame. The v1.2.3 pass
deliberately did NOT do this: ~50 call sites in MainActivity
touch those views (15 lateinit fields + ~30 listeners), and
refreshStatus() / refreshSettingsHub() / refreshFocusGuard()
touch them from onResume — moving them behind a lazy inflate
is a rewrite with real crash risk if any site is missed, and
the sub-screen tree would still inflate anyway, so the win
would be partial. This pass does the whole thing, panels
included, so the win is no longer partial.

What changed (MainActivity.kt, activity_main.xml):
- The Settings hub is a `ViewStub` (`stubSettings`,
  `inflatedId=pageSettings`) — `ensureSettings()` inflates it
  on the first visit to the Settings tab (from `showPage()`),
  binds the 5 hub fields, wires the 5 hub tiles (verbatim from
  wireV217 — which is deleted), and ends with
  `refreshSettingsHub()`.
- Each panel moved verbatim into its own file
  (`panel_general.xml`, `panel_smart.xml`, `panel_security.xml`,
  `panel_data.xml` — only the xmlns declarations were added to
  each root) and is a `ViewStub` in the subContent tree —
  `ensurePanel(groupId)` inflates it on its first open (from
  `openSubScreen()`), runs its `bindPanel*()` (field bindings +
  listeners verbatim from onCreate / wireV24), and ends with
  `refreshStatus()`. Panels that never open are never inflated.
- `pageSettings` is nullable now; `showPage()` null-safes its
  visibility set, and `onResume()` heals the pill from
  `settingsOpen` (the same truth showPage maintains) instead of
  reading `pageSettings.visibility`.
- `refreshStatus()` touches settings views only behind
  `hubReady` / `isPanelReady(...)` guards; `refreshFocusGuard()`
  returns early when the Smart panel is not inflated.
- `refreshSettingsHub()` no longer reads values back from other
  settings views (`tvPinInfo.text`, `tvStats.text`) — those live
  in panels that may not exist yet. It computes the same strings
  from Config/data instead (`pinState`/`adminState` from
  Config/AdminReceiver, stats from `buildStatsText(rules, flows,
  logs)` — the signature gained the `logs` parameter, which
  refreshStatus already had parsed). `tvGeneralDiag` is guarded
  by the General panel being ready.
- Nothing visible changed: same ids, same listeners, same
  values, same themes/PIN/FocusLock/Shizuku behaviour — only
  WHEN the views inflate. At cold start, `activity_main.xml`
  is 165 lines (was 824); only Home + toolbar + bottom bar +
  the small sub-screen shell inflate.

Baseline profile (scaffolding only — no profile generated yet):
- Root `build.gradle` gains the `androidx.baselineprofile`
  plugin (1.2.4, `apply false`); `settings.gradle` includes
  `:baselineprofile`; the new module holds `BaselineProfileGenerator`
  (creates the profile) and `StartupBenchmark`
  (cold launch of MainActivity to first frame, 5 iterations, measures
  with the profile applied); `:app` applies the plugin too and has a
  `baselineProfile project(':baselineprofile')` dependency, so
  `:app:generateBaselineProfile` exists. (Fix 01:24 — the first
  version used the never-published `com.android.baselineprofile`
  8.5.2 id and CI run #31 failed resolving it in 32s.)
- No `app/src/main/baseline-prof.txt` exists yet, so the
  release build consumes NO profile today. Generation needs a
  device the app can install on — the app is arm64-only, so
  the usual x86_64 managed device cannot install it; the module
  deliberately declares no managed device and the generation
  runs on a connected arm64 emulator (slow, under emulation).
  Full steps + workflow YAML: see BASELINE_PROFILE_CI.md.

Version 1.2.6 (vc9). No new permissions, no MQTT UI, no
forwarding/relay touched, no behaviour or look change — only
when views inflate. New dependencies are test-only
(benchmark-macro in the `:baselineprofile` module; the app APK
gains nothing). Phone test pending: cold start feels faster
on first launch; Settings tab opens normally on first tap;
each of the four panels opens normally on first tap; all
Settings toggles/buttons still work and still show the saved
values; Back from a panel still returns to the hub; the app
still restores to the Settings tab after a recreate; version
1.2.6. No frame-rate or cold-start numbers were measured —
none are claimed.

### Release signing (wired 2026-11-07 — keystore encrypted in repo, passphrase via GitHub Secrets)

- `app/build.gradle` now has `signingConfigs.release`: PKCS12
  keystore, alias `automationhub`, cert SHA-256 fingerprint
  `A6:D7:8B:FF:A5:93:3D:62:93:DF:CA:6B:2D:3D:27:AB:BC:F7:45:CD:06:C7:33:82:92:7D:82:74:FA:C2:C0:5D`
  (fingerprint only — no password, no keystore content, lives
  anywhere in the repo).
- The release keystore itself is NEVER committed in the clear to this public
  repo. It is stored ENCRYPTED as `app/release.keystore.enc.b64`
  (AES-256-CBC, PBKDF2; ciphertext only — useless without the passphrase).
  CI decrypts it with the short passphrase from the
  `RELEASE_KEYSTORE_PASSPHRASE` GitHub Secret. (History: the first attempt
  passed the whole base64 keystore as a `RELEASE_KEYSTORE_B64` secret, but
  the 3584-char value kept getting mangled on paste — runs #23/#25 both
  failed with `base64: invalid input` — so the long paste was replaced by
  this encrypted blob + short passphrase. The obsolete secret may stay;
  nothing reads it anymore.) The other three secrets
  (`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`) still hold the keystore passwords/alias. The
  workflow decodes the blob and runs `assembleRelease` with
  `RELEASE_KEYSTORE_PATH=app/release.keystore` (+ the other env vars). The build resolves that path against the repo root
  first (a bare `file()` in the app module would look for
  `app/app/release.keystore`); with no env, it falls back to a
  `release.keystore` next to `build.gradle` for local builds.
- The release buildType signs ONLY when the keystore file exists
  AND `RELEASE_KEYSTORE_PASSWORD` is set; otherwise the release
  stays unsigned exactly as before — debug CI and local builds
  without the env keep working. `minifyEnabled true` +
  `shrinkResources true` + proguardFiles are unchanged.
- First release install needs ONE uninstall of the debug-key
  build: the release signature differs from the fixed debug key,
  so Android will refuse to install over it — export rules/flows
  first, uninstall, install the release APK, import back. After
  that, release builds update over each other.
- The workflow still builds debug only — it needs the
  `assembleRelease` step (browser edit, GitHub App cannot touch
  workflows). The first release build must be phone-tested before
  it ships.
