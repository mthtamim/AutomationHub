package com.mthtamim.phonelinkflows

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONObject

/** v1.4.6: a home-screen button that fires the "widget_tap"
 *  trigger into both engines. Unlike RuleWidgetProvider (which
 *  runs one chosen rule), this one fires an event — any rule or
 *  flow with a widget_tap trigger runs. A rule/flow param holds
 *  the widget id ("42") to listen to one widget only; a blank
 *  param means any widget. */
class TriggerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, buildViews(context, id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TAP) {
            val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
            val data = JSONObject().put("widget_id", widgetId.toString())
            try { Automation.onEvent(context.applicationContext, "widget_tap", data) } catch (_: Exception) { }
            try { FlowEngine.onEvent(context.applicationContext, "widget_tap", data) } catch (_: Exception) { }
        }
    }

    companion object {
        const val ACTION_TAP = "pl_trigger_widget_tap"
        const val EXTRA_WIDGET_ID = "widget_id"

        fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_rule_layout)
            views.setTextViewText(R.id.wRuleName,
                ctx.getString(R.string.widget_trigger_name, widgetId))
            val intent = Intent(ctx, TriggerWidgetProvider::class.java)
                .setAction(ACTION_TAP).putExtra(EXTRA_WIDGET_ID, widgetId)
            val pi = PendingIntent.getBroadcast(ctx, 2000 + widgetId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.wRuleRoot, pi)
            return views
        }
    }
}
