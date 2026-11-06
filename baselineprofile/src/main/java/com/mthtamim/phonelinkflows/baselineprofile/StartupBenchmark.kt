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
 * Cold-start benchmark that MEASURES launch with the baseline profile
 * applied (v1.2.6) — it does NOT generate the profile.
 * BaselineProfileGenerator is the class that creates it.
 *
 * What it exercises: a cold launch of MainActivity to the first
 * frame (Home visible) — the exact path the v1.2.6 lazy inflation
 * was built to make cheaper. Nothing is tapped and no settings are
 * changed: the benchmark measures launch only, so it reports the
 * time with the profile's classes pre-compiled.
 *
 * Run it after a profile exists (see BASELINE_PROFILE_CI.md for the
 * device note):
 *   ./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest
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
