# AutomationHub

[![Build APK](https://github.com/mthtamim/PhoneLink-Flows/actions/workflows/build-apk.yml/badge.svg)](https://github.com/mthtamim/PhoneLink-Flows/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/mthtamim/PhoneLink-Flows)](https://github.com/mthtamim/PhoneLink-Flows/releases/latest)

**Automation for Android — rules and visual flows, everything on your phone.**

AutomationHub turns your phone into an automation hub: simple if-this-then-that rules, visual flows with branches and conditions, a focus mode, and an app lock — with no account, no server, and no data leaving your device.

## Features

- **Rules** — 110+ triggers (time, battery, Wi-Fi, mobile data, Bluetooth, apps, notifications, sensors and more) and 130+ actions, with conditions and exit actions.
- **Flows** — a visual flow editor where triggers, conditions and actions chain together, with branching, waits, variables and JavaScript.
- **159 ready-made templates** — rules and flows you can add in one tap and edit afterwards.
- **Notification tools** — snooze a notification and have it come back, cool down noisy apps, and restore a dismissed notification.
- **Focus Guard** — block distracting apps on a schedule you set.
- **AppLock** — lock apps behind a PIN or biometrics.
- **Quick triggers** — a Quick Settings tile and a home-screen widget that can start any rule or flow.
- **Geo-fence** — run automations when you arrive at or leave a place.
- **8 themes** — including dark and glass styles.
- **Local only** — automations run on the phone. There is no account, no cloud sync and no analytics.

## Download

Get the latest APK from the [Releases page](https://github.com/mthtamim/PhoneLink-Flows/releases/latest).

- Android 8.0 or newer, 64-bit (arm64) phones.
- If your browser asks, allow installing apps from that source.
- Updating from an older debug build: uninstall the old app first — export your rules and flows from Settings → Data & backup beforehand. Release builds update over each other afterwards.

## Permissions

AutomationHub asks only for what its features need, and everything is optional — a feature simply stays off until you grant its permission.

| Permission | Used for |
| --- | --- |
| Notification access | Notification triggers, replies, snooze and restore |
| Usage access | App-launched triggers and the Flow Editor's app detection |
| Accessibility | Answering/ending calls and screen-based actions |
| Device admin | AppLock and screen lock actions |
| Location | Geo-fence and showing the current Wi-Fi network name |
| Draw over other apps | Floating bubble and overlays |
| Do Not Disturb access | Silence and DND actions |
| Shizuku (optional) | System-level actions without root |

## Usage

1. Open AutomationHub and turn on the **Flows service** switch on the Home screen — automations only run while it is on.
2. Create a **Rule** (one trigger → actions) or a **Flow** (visual chains with conditions), or add a ready-made **Template**.
3. Test a rule from its card — the Log screen shows what ran and when.

## Privacy

Everything is stored and processed on your phone. AutomationHub has no account system, sends nothing to any server, and collects no analytics.

## Building from source

Open the project in Android Studio and build the `app` module. CI builds the debug and release APKs on every push to `main`.

## Version history

| Version | What's new |
| --- | --- |
| 1.4.10 | Snoozed notifications now come back instead of being re-snoozed in a loop |
| 1.4.9 | Wi-Fi on/off triggers work with Location off (transport-based detection) |
| 1.4.8 | Snooze matches app names; mobile-data detection; Flow Editor canvas pan; notification triggers reach rules |
| 1.4.7 | Notification snooze / cooldown / undo, and JavaScript actions |
| 1.4.6 | Quick Settings tile and home-screen widget triggers |
| 1.4.5 | Music triggers react instantly via media sessions |
| 1.4.4 | Durations in seconds/minutes everywhere; pinch-zoom to a point; trigger-detection fixes |
| 1.4.3 | Faster template adding (engine cache and trigger index); 100 new templates |
| 1.4.2 | Essentials-style pill navigation; FocusLock settings moved to Settings |
| 1.4.1 | Launch crash fixes |
| 1.4.0 | Material 3 redesign with 8 themes |

Full release notes are on the [Releases page](https://github.com/mthtamim/PhoneLink-Flows/releases).

## Support

Found a bug or have an idea? Open an [issue](https://github.com/mthtamim/PhoneLink-Flows/issues).
