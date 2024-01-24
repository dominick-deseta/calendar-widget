package com.example.calendarwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

public class MyWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return MyWidgetFactory(this.applicationContext, intent)
    }
}
public class MyWidgetFactory(private val context: Context, intent: Intent) :
    RemoteViewsService.RemoteViewsFactory {

    private var dummyData: List<String> = ArrayList<String>()
    private var events: List<EventItem> = ArrayList<EventItem>()
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val myIntent: Intent = intent
    private var pastDate = false

    override fun onCreate() {
        Log.d("MyWidgetFactory", "new factory created!")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDataSetChanged() {
        val sharedPreferences = context.getSharedPreferences("WidgetData", Context.MODE_PRIVATE)
        val eventsJson = sharedPreferences.getString("events", "[]")
        events = Gson().fromJson(eventsJson, object : TypeToken<List<EventItem>>() {}.type)
        pastDate = sharedPreferences.getBoolean("pastDate", false)
        Log.d("MyWidgetFactory", "Updated events: $events")
        Log.d("MyWidgetFactory", "Update pastDate: $pastDate")
    }

    override fun onDestroy() {
    }

    override fun getCount(): Int {
        return events.size
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun getViewAt(position: Int): RemoteViews {
        if (position >= events.size) {
            return RemoteViews(context.packageName, R.layout.list_item)
        }
        return RemoteViews(context.packageName, R.layout.list_item).apply {
            val eventSummary = events[position].summary
            val startTime = events[position].start.dateTime
            val endTime = events[position].end.dateTime
            setInt(R.id.eventText, "setTextColor", Color.WHITE)
            setInt(R.id.timeText, "setTextColor", Color.WHITE)
            setInt(R.id.listItem, "setBackgroundColor", Color.BLACK)
            setInt(R.id.listItem, "setBackgroundResource", 0)
            var timeStr = ""
            if (!(startTime.isNullOrEmpty() or endTime.isNullOrEmpty())) {
                val compStart = ZonedDateTime.parse(startTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                val compEnd = ZonedDateTime.parse(endTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                if (compEnd < ZonedDateTime.now()) {
                    setInt(R.id.eventText, "setTextColor", Color.GRAY)
                    setInt(R.id.timeText, "setTextColor", Color.GRAY)
                } else if (compStart < ZonedDateTime.now()) {
                    setInt(R.id.eventText, "setTextColor", Color.BLACK)
                    setInt(R.id.timeText, "setTextColor", Color.BLACK)
                    setInt(R.id.listItem, "setBackgroundColor", Color.WHITE)
                }
                val formatStart = OffsetDateTime.parse(startTime).format(DateTimeFormatter.ofPattern("h:mm"))
                val formatEnd = OffsetDateTime.parse(endTime).format(DateTimeFormatter.ofPattern("h:mm"))
                timeStr = formatStart + "-" + formatEnd
            } else if (position < events.size - 1 && !(
                events[position+1].start.dateTime.isNullOrEmpty() or events[position+1].end.dateTime.isNullOrEmpty()
                )
            ) {
                if (!pastDate) {
                    setInt(R.id.listItem, "setBackgroundResource", R.drawable.event_underline)
                } else {
                    setInt(R.id.listItem, "setBackgroundResource", R.drawable.event_underline_gray)
                }
            }
            if (pastDate) {
                setInt(R.id.eventText, "setTextColor", Color.GRAY)
                setInt(R.id.timeText, "setTextColor", Color.GRAY)
            }
            setTextViewText(R.id.eventText, eventSummary)
            setTextViewText(R.id.timeText, timeStr)
        }
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
