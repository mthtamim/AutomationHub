# --- AutomationHub release (2026-11-06): minify ON. These are the
# things R8 cannot see statically. Manifest components (activities,
# services, receivers, providers) are kept by AGP's default rules;
# the rules below cover the rest. CI builds BOTH debug and release,
# so minify IS exercised by CI (run #46 caught the Rhino rules
# below). The first release build must still be phone-tested
# before it ships.
# Custom view inflated from XML by class name (activity_flow_editor).
-keep public class com.mthtamim.phonelinkflows.FlowCanvasView {
    public <init>(...);
}
# AppWidget provider + configure activity (manifest-declared).
-keep public class com.mthtamim.phonelinkflows.RuleWidgetProvider
-keep public class com.mthtamim.phonelinkflows.RuleWidgetConfigure
# Rhino JS engine (v1.4.7): Rhino references java.beans.* (not
# in Android) and java.lang.Module (Java 9+) from
# JavaToJSONConverters and friends — R8 failed run #46 on the
# missing classes. They are never called on Android, so silence
# the warnings instead of keeping the classes.
-dontwarn java.beans.**
-dontwarn java.lang.Module
-dontwarn org.mozilla.javascript.**
# Rhino JS engine (v1.4.7): the hub API is reached reflectively
# by Rhino (LiveConnect) — R8 must not rename/strip it.
-keep class com.mthtamim.phonelinkflows.JsApi {
    *;
}
# Shizuku: newProcess is reached reflectively by name
# (ShizukuHelper.kt, ShizukuTap.kt) — R8 must not rename/strip it.
-keep class rikka.shizuku.Shizuku {
    *** newProcess(...);
}
-keep class rikka.shizuku.ShizukuProvider

# v1.4.12: JSch (SFTP) loads algorithm classes by name.
-keep class com.jcraft.jsch.** {
    *;
}
-dontwarn com.jcraft.jsch.**
