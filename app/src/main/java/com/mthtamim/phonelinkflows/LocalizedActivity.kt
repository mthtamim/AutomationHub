package com.mthtamim.phonelinkflows

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Base activity that applies the saved language and theme before the UI is
 * created. Language is read straight from the raw prefs because Config needs
 * a fully-built context; theme goes through AppCompatDelegate so the
 * DayNight theme follows it everywhere.
 */
open class LocalizedActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("phonelink_cfg", Context.MODE_PRIVATE)

        when (prefs.getString("theme_mode", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        val lang = prefs.getString("app_lang", "bn") ?: "bn"
        val wrapped = if (lang == "system") {
            newBase
        } else {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val cfg = Configuration(newBase.resources.configuration)
            cfg.setLocale(locale)
            newBase.createConfigurationContext(cfg)
        }
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        // v1.4.0 crash catcher: install before anything else so a
        // launch crash writes its stack trace to Downloads.
        CrashHandler.install(this)
        // v2.25.0: optional app theme (Settings > App theme). A non-default
        // theme carries its own accent, so it takes precedence over the
        // v2.11 accent picker; the accent picker still applies exactly
        // as before when the theme is Emerald Dark (the default).
        // Each theme style is defined in BOTH values/themes.xml and
        // values-night/themes.xml so it survives light/dark/system mode.
        try {
            val prefs = getSharedPreferences("phonelink_cfg",
                android.content.Context.MODE_PRIVATE)
            when (prefs.getString("app_theme", "emerald_dark")) {
                "aurora" -> setTheme(R.style.Theme_PhoneLinkAurora)
                "ocean_deep" -> setTheme(R.style.Theme_PhoneLinkOceanDeep)
                "midnight_sapphire" -> setTheme(R.style.Theme_PhoneLinkMidnightSapphire)
                "sunset_ember" -> setTheme(R.style.Theme_PhoneLinkSunsetEmber)
                "rose_quartz" -> setTheme(R.style.Theme_PhoneLinkRoseQuartz)
                "forest_mist" -> setTheme(R.style.Theme_PhoneLinkForestMist)
                "graphite_mono" -> setTheme(R.style.Theme_PhoneLinkGraphiteMono)
                else -> {
                    // v2.11: accent colour picked in General settings
                    when (prefs.getString("accent", "emerald")) {
                        "blue" -> setTheme(R.style.Theme_PhoneLinkBlue)
                        "purple" -> setTheme(R.style.Theme_PhoneLinkPurple)
                        "amber" -> setTheme(R.style.Theme_PhoneLinkAmber)
                        "rose" -> setTheme(R.style.Theme_PhoneLinkRose)
                    }
                }
            }
        } catch (_: Exception) { }
        super.onCreate(savedInstanceState)
        // v1.2.2: M3 motion — every activity gets the default
        // fade-through window transitions (300 ms, emphasized
        // easing) instead of the generic PL.ActivityAnim slide/fade
        // that windowAnimationStyle used to give Theme.PhoneLink.
        PLMotion.applyDefaultWindowTransitions(this)
    }

    /** v2.25.1: theme-attribute colours for subclasses, all via the
     *  single ThemeColors resolver (never a fixed R.color read, so
     *  glass themes are followed). Under Emerald Dark each resolves
     *  to its old value: accent #10B981, onSurface #EAF4EE (night),
     *  onSurfaceVariant #8FA79B (night), outline #2A3336 (night).
     *  Smoothness (2026-11-06): each resolver is a theme-attribute
     *  lookup, and the rule/flow lists called them once per pill
     *  and per card — hundreds of lookups per render. The theme
     *  cannot change without the activity being recreated, so the
     *  values are resolved once per activity instance. */
    private val cachedAccent by lazy { ThemeColors.primary(this) }
    private val cachedOnSurface by lazy { ThemeColors.onSurface(this) }
    private val cachedOnBackground by lazy { ThemeColors.onBackground(this) }
    private val cachedOnSurfaceVariant by lazy { ThemeColors.onSurfaceVariant(this) }
    private val cachedOnPrimaryContainer by lazy { ThemeColors.onPrimaryContainer(this) }
    private val cachedOutline by lazy { ThemeColors.outline(this) }
    private val cachedSurfaceVariant by lazy { ThemeColors.surfaceVariant(this) }

    protected fun themeAccentColor(): Int = cachedAccent

    protected fun themeOnSurface(): Int = cachedOnSurface

    protected fun themeOnBackground(): Int = cachedOnBackground

    protected fun themeOnSurfaceVariant(): Int = cachedOnSurfaceVariant

    /** v1.2.0: text/icon colour on the sage trigger box. */
    protected fun themeOnPrimaryContainer(): Int = cachedOnPrimaryContainer

    protected fun themeOutline(): Int = cachedOutline

    protected fun themeSurfaceVariant(): Int = cachedSurfaceVariant

    /** v2.25.1: bottom-nav tint for glass themes (checked = accent,
     *  unchecked = onSurfaceVariant). Emerald Dark callers must NOT
     *  call this — they keep the old XML selector, whose night
     *  values are checked #2FD68F / unchecked #7E938A. */
    protected fun themeNavTint(): android.content.res.ColorStateList =
        ThemeColors.navTint(this)

    /** v2.25.2: apply the glass nav tint to a bottom nav, if (and
     *  only if) a glass theme is active. Callers run this in onCreate
     *  AND onResume: the v2.25.1 screenshots still showed a green
     *  selected item, and re-applying on resume covers any later
     *  reset of the tint (tab switches, activity returns) as well as
     *  the case where the screenshots simply predated v2.25.1. The
     *  XML selector (bottom_nav_tint, now ?attr/plNav*) is the second
     *  layer; this programmatic tint wins at runtime. */
    protected fun applyGlassNavTint(
        nav: com.google.android.material.bottomnavigation.BottomNavigationView
    ) {
        if (Config.appTheme(this) != "emerald_dark") {
            nav.itemIconTintList = themeNavTint()
            nav.itemTextColor = themeNavTint()
        }
    }
}
