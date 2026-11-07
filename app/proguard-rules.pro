# --- AutomationHub release (2026-11-06): minify ON. These are the
# things R8 cannot see statically. Manifest components (activities,
# services, receivers, providers) are kept by AGP's default rules;
# the rules below cover the rest. NOTE: CI builds the DEBUG apk, so
# minify is NOT exercised by CI — the first release build must be
# phone-tested before it ships.
# Custom view inflated from XML by class name (activity_flow_editor).
-keep public class com.mthtamim.phonelinkflows.FlowCanvasView {
    public <init>(...);
}
# AppWidget provider + configure activity (manifest-declared).
-keep public class com.mthtamim.phonelinkflows.RuleWidgetProvider
-keep public class com.mthtamim.phonelinkflows.RuleWidgetConfigure
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
