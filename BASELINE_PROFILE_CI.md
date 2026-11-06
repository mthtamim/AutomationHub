# Baseline profile — generation setup (v1.2.6)

## What exists now (scaffolding only — no profile is generated yet)

- Root `build.gradle`: `com.android.baselineprofile` plugin 8.5.2
  (applied to no module yet — `apply false`, same as the other plugins).
- `settings.gradle`: `include ':baselineprofile'`.
- `baselineprofile/` module (`com.android.test` + `com.android.baselineprofile`,
  target `:app`): `StartupBenchmark` measures a cold launch of
  MainActivity to the first frame, 5 iterations, and the plugin's
  `generateBaselineProfile` task collects the profile from that run.
- **No `app/src/main/baseline-prof.txt` exists yet, so the release
  build consumes NO profile today.** Nothing about what ships changes
  until a real profile is generated, verified, and committed there
  (R8 then applies it automatically in the release build — no extra
  wiring is needed; minify + shrink are already on).

## The one thing that decides HOW it can be generated: the ABI

The app is arm64-only (`ndk abiFilters 'arm64-v8a'` + ML Kit native
libs), so it **cannot install on an x86_64 emulator**
(`INSTALL_FAILED_NO_MATCHING_ABIS`). That rules out the usual
Gradle Managed Device (Pixel 6 / x86_64 on a GitHub runner) — which
is why the module declares **no managed device**: the generate task
runs on a CONNECTED device instead. In CI that means an **arm64
emulator**, which runs under QEMU emulation on the x86 host — it
works, but it is SLOW (boot alone can take 10–20 minutes, and the
5-iteration benchmark crawls). Budget a 2-hour job timeout and
expect the occasional flake; a re-run is the remedy, not a code fix.

## Workflow to create on GitHub (via the browser — the snapshot push
## cannot carry workflow files; do NOT commit this file's YAML as
## .github/workflows/baseline-profile.yml through push-files)

Create `.github/workflows/baseline-profile.yml` with exactly this
content (workflow_dispatch only — it never runs on push):

```yaml
name: Generate baseline profile

on:
  workflow_dispatch:

jobs:
  generate:
    name: Generate baseline-prof.txt (arm64 emulator)
    runs-on: ubuntu-latest
    timeout-minutes: 120
    steps:
      - name: Check out
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      # arm64 is REQUIRED: the app cannot install on an x86_64
      # emulator (arm64-only APK). The arm64 image runs emulated
      # on the x86 host, so boot + benchmark are slow — the long
      # timeouts below are deliberate.
      - name: Generate baseline profile on arm64 emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: arm64-v8a
          target: google_apis
          emulator-options: -no-window -no-audio -no-boot-anim -camera-back none
          disable-animations: true
          emulator-boot-timeout-seconds: 1800
          script: ./gradlew :baselineprofile:generateBaselineProfile --stacktrace

      - name: Find the generated profile
        run: |
          find baselineprofile/build -name 'baseline-prof.txt' -print
          test -n "$(find baselineprofile/build -name 'baseline-prof.txt' -print -quit)"

      - name: Upload baseline-prof.txt
        uses: actions/upload-artifact@v4
        with:
          name: baseline-prof
          path: baselineprofile/build/**/baseline-prof.txt
          if-no-files-found: error
          retention-days: 90
```

## After the run succeeds — the one manual step

1. Download the `baseline-prof` artifact from the run.
2. Verify it before trusting it: it must be non-empty, plain text,
   and contain lines for the app's own classes (e.g.
   `Lcom/mthtamim/phonelinkflows/MainActivity;` and the Home
   classes). An empty file or a file with only framework classes
   means the benchmark did not actually exercise the launch.
3. Copy it to `app/src/main/baseline-prof.txt` in the repo and push
   it like any other file. From the next release build on, R8
   applies it — no `build.gradle` change is needed for that.
4. If the arm64 emulator run keeps failing or timing out, the
   fallback is a real arm64 device (Tamim's phone) with a local
   Android SDK: connect it over USB with USB debugging on and run
   `./gradlew :baselineprofile:generateBaselineProfile` — same
   command, same output location, same verify-then-copy step.

## What was deliberately NOT done

- No managed device in the module (see the ABI note — it would
  fail to install, every time).
- No `app/src/main/baseline-prof.txt` yet (there is nothing real
  to put there; a hand-written or empty profile would be worse
  than none).
- No change to the app module's `build.gradle` beyond the version
  bump — the profile is consumed by file presence alone.
- No claim of a measured speedup: the profile is ungenerated, and
  no device here can measure cold start.
