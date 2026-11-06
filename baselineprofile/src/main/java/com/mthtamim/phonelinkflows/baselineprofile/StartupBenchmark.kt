package com.mthtamim.phonelinkflows.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start benchmark that the baselineprofile plugin runs to
 * GENERATE the baseline profile (v1.2.6).
 *
 * What it exercises: a cold launch of MainActivity to the first
 * frame (Home visible) — the exact path the v1.2.6 lazy inflation
 * was built to make cheaper. Nothing is tapped and no settings are
 * changed: the benchmark measures launch only, so the profile it
 * produces covers the classes/methods the cold start actually runs
 * (activity + Home tree + Application init).
 *
 * Run it with (see BASELINE_PROFILE_CI.md for the device note):
 *   ./gradlew :baselineprofile:generateBaselineProfile
 * The plugin writes the generated profile under
 * baselineprofile/build/… — copy the resulting baseline-prof.txt
 * to app/src/main/baseline-prof.txt and commit it; the release
 * build (minify + R8) then consumes it automatically.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.mthtamim.phonelinkflows",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require
        ),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        startActivityAndWait()
    }
}
