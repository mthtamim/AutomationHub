# AutomationHub

**Automation, Rules & Flows for Android — everything from PhoneLink except phone forwarding.**

AutomationHub is the corrected-scope rebuild of the PhoneLink-Flows skeleton
(v1.0.0, rejected 2026-11-06 for opening empty with no Automation/Rules and
for being bigger than PhoneLink itself). Tamim's corrected scope, confirmed
2026-11-06: **everything except phone forwarding, same Settings, same UI.**

- **Package:** `com.mthtamim.phonelinkflows` (kept from the skeleton, so the
  `phonelink_*` SharedPreferences keys stay compatible with its data)
- **Version:** 1.0.0 (vc1) · `compileSdk`/`targetSdk` 34, `minSdk` 26 ·
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
   AppLock, MQTT/SMTP panel, backup/restore — all unproven until Tamim
   runs them on his phone.
