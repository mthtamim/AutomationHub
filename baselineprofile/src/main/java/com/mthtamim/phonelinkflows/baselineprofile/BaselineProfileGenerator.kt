package com.mthtamim.phonelinkflows.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile generator (v1.2.6).
 *
 * This is the class that CREATES the profile: BaselineProfileRule
 * launches the app and records which classes/methods the cold start
 * actually runs. StartupBenchmark only MEASURES with a profile
 * applied — it does not create one.
 *
 * Run it with (see BASELINE_PROFILE_CI.md for the device note):
 *   ./gradlew :app:generateBaselineProfile
 * The plugin copies the generated profile to
 * app/src/main/generated/baselineProfiles — copy that
 * baseline-prof.txt to app/src/main/baseline-prof.txt and commit it;
 * the release build (minify + R8) then consumes it automatically.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.mthtamim.phonelinkflows",
        includeInStartupProfile = true,
        profileBlock = {
            startActivityAndWait()
        }
    )
}
