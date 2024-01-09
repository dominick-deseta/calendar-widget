package com.example.calendarwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

const val PREF_CALENDAR_LIST_CACHE = "calendar_list_cache"
private var views: RemoteViews? = null

enum class codes(val message: String) {
    OK("OK"),
    BAD_RESPONSE("Bad Response"),
    FAILED_REQUEST("Failed Request"),
    NO_INTERNET("No Internet")
}
var failureCode = codes.OK

@RequiresApi(Build.VERSION_CODES.O)
private var viewingDate = ZonedDateTime.now()
val eventImages = intArrayOf(
    R.id.eventImage1, R.id.eventImage2, R.id.eventImage3, R.id.eventImage4, R.id.eventImage5,
    R.id.eventImage6, R.id.eventImage7, R.id.eventImage8, R.id.eventImage9, R.id.eventImage10
)
val timeImages = intArrayOf(
    R.id.timeImage1, R.id.timeImage2, R.id.timeImage3, R.id.timeImage4, R.id.timeImage5,
    R.id.timeImage6, R.id.timeImage7, R.id.timeImage8, R.id.timeImage9, R.id.timeImage10
)
val eventLayouts = intArrayOf(
    R.id.event1, R.id.event2, R.id.event3, R.id.event4, R.id.event5, R.id.event6, R.id.event7, R.id.event8, R.id.event9, R.id.event10
)

class CalendarWidget : AppWidgetProvider() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        views = RemoteViews(context.packageName, R.layout.calendar_widget)

        callCalendars(context, getAccessToken(context) + "")

//        CoroutineScope(Dispatchers.Main).launch {
//            refreshAccessToken(context, getRefreshToken(context) + "")
//        }

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RECEIVING!", "Receiving!")
        super.onReceive(context, intent)
        views = RemoteViews(context.packageName, R.layout.calendar_widget)

//        CoroutineScope(Dispatchers.Main).launch {
//            refreshAccessToken(context, getRefreshToken(context) + "")
//        }
        when (intent.action) {
            "ACTION_NEXT" -> { viewingDate = viewingDate.plusDays(1) }
            "ACTION_PREV" -> { viewingDate = viewingDate.minusDays(1) }
            "ACTION_TODAY" -> { viewingDate = ZonedDateTime.now() }
        }
        Log.d("Dom", "viewingDate = $viewingDate")
        // views!!.setImageViewBitmap(R.id.loading, convertStringToBitmap("Loading...", context, R.font.windows_command_prompt, 72f, Color.GRAY))
        // views!!.setViewVisibility(R.id.loading, View.VISIBLE)
        update(context)
        views?.let {
            callCalendars(context, getAccessToken(context) + "")
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onEnabled(context: Context) {
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    fun getSavedCalendarIds(context: Context): List<String> {
        val calendarIds = mutableListOf<String>()
        val sharedPref = context.getSharedPreferences("widget_prefs", MODE_PRIVATE)
        for (key in sharedPref.all.keys) {
            // Log.d("Shared Prefs", "${key.toString()} ${sharedPref.getString(key, "N/A")}")
            if (key.startsWith("calendarid_") && sharedPref.getBoolean(key, false)) {
                calendarIds.add(key.replace("calendarid_", ""))
            }
        }
        // Log.d("calendar ids: ", calendarIds.toString())
        return calendarIds
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callCalendars(context: Context, accessToken: String) {
        Log.d("Clearing...", "Clearing the events out")
        populateEventViews(context, listOf())
        val savedCalendarIds = getSavedCalendarIds(context)
        callEvents(context, accessToken, 0, savedCalendarIds, viewingDate, mutableListOf<EventItem>())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callEvents(context: Context, accessToken: String, calendarIdx: Int, calendarIds: List<String>, inDate: ZonedDateTime, inEvents: MutableList<EventItem>) {
        val timeMin = inDate.withHour(0).withMinute(0).withSecond(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val timeMax = inDate.withHour(23).withMinute(59).withSecond(59).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Log.d("Getting events with...", accessToken)
        if (isNetworkAvailable(context)) {
            NetworkService.googleCalendarApi.getEvents("Bearer " + accessToken, calendarIds[calendarIdx], timeMin, timeMax, true).enqueue(object :
//            NetworkService.googleCalendarApi.getEvents("Bearer " + accessToken, "iamtherealdinosaur@gmail.com", timeMin, timeMax, true).enqueue(object :
                    Callback<Events> {
                    override fun onResponse(call: Call<Events>, response: Response<Events>) {
                        if (response.isSuccessful) {
                            failureCode = codes.OK
                            Log.d("Good Response!", response.body().toString())
                            val events = response.body()
                            if (events != null) {
                                inEvents.addAll(events.eventItems)
                                if (calendarIdx != calendarIds.size - 1) {
                                    callEvents(context, accessToken, calendarIdx + 1, calendarIds, inDate, inEvents)
                                } else if (viewingDate == inDate) {
                                    populateEventViews(context, sortEvents(inEvents))
                                }
                            }
                        } else {
                            failureCode = codes.BAD_RESPONSE
                            Log.e(ContentValues.TAG, "Bad Response: ${response.errorBody()?.string()}")
                            views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
                            CoroutineScope(Dispatchers.Main).launch {
                                refreshAccessToken(context, getRefreshToken(context) + "")
                            }
                        }
                    }
                    override fun onFailure(call: Call<Events>, t: Throwable) {
                        failureCode = codes.FAILED_REQUEST
                        Log.e(ContentValues.TAG, "Failed request: ${t.message}")
                        views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
                        CoroutineScope(Dispatchers.Main).launch {
                            refreshAccessToken(context, getRefreshToken(context) + "")
                        }
                    }
                })
        } else {
            failureCode = codes.NO_INTERNET
            Log.e(ContentValues.TAG, "Tried to fetch events, but no internet.")
            views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sortEvents(eventItems: List<EventItem>): List<EventItem> {
        val sortedEvents = eventItems.sortedWith(
            Comparator { e1: EventItem, e2: EventItem ->
                when {
                    e1.start.dateTime != null && e2.start.dateTime != null -> {
                        val dt1 = ZonedDateTime.parse(e1.start.dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        val dt2 = ZonedDateTime.parse(e2.start.dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        dt1.compareTo(dt2)
                    }
                    e1.start.dateTime == null && e2.start.dateTime == null -> 0
                    e1.start.dateTime == null -> -1
                    else -> 1
                }
            }
        )
        return sortedEvents
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun populateEventViews(context: Context, eventItems: List<EventItem>) {
        // views!!.setViewVisibility(R.id.loading, View.INVISIBLE)
        // views!!.setInt(R.id.loading, "setMinimumHeight", 0)
        var previousIsCurrent = false
        for (i in 0..9) {
            val eventViewId = eventImages[i]
            val timeViewId = timeImages[i]
            val eventLayoutId = eventLayouts[i]
            if (i < eventItems.size) {
                val eventSummary = eventItems[i].summary
                val startTime = eventItems[i].start.dateTime
                val endTime = eventItems[i].end.dateTime
                var timeBitmap: Bitmap? = null
                var inColor = Color.WHITE
                if (!(startTime.isNullOrEmpty() or endTime.isNullOrEmpty())) {
                    val compStart = ZonedDateTime.parse(startTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    val compEnd = ZonedDateTime.parse(endTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    views!!.setInt(eventLayoutId, "setBackgroundResource", 0)
                    views!!.setViewPadding(eventLayoutId, 0, 0, 0, 10)
                    if (previousIsCurrent) {
                        views!!.setViewPadding(eventLayoutId, 0, 10, 0, 10)
                        previousIsCurrent = false
                    }
                    if (compEnd < ZonedDateTime.now()) {
                        inColor = Color.GRAY
                    } else if (compStart < ZonedDateTime.now()) {
                        views!!.setInt(
                            eventLayoutId,
                            "setBackgroundResource",
                            R.drawable.event_border
                        )
                        views!!.setViewPadding(eventLayoutId, 15, 15, 15, 15)
                        previousIsCurrent = true
                    }
                    val formatStart = OffsetDateTime.parse(startTime).format(DateTimeFormatter.ofPattern("h:mm"))
                    val formatEnd = OffsetDateTime.parse(endTime).format(DateTimeFormatter.ofPattern("h:mm"))
                    val time = formatStart + "-" + formatEnd
                    timeBitmap = convertStringToBitmap(time, context, R.font.windows_command_prompt, 72f, inColor)
                } else if (viewingDate.toLocalDate() < ZonedDateTime.now().toLocalDate()) {
                    inColor = Color.GRAY
                }
                views!!.setImageViewBitmap(timeViewId, timeBitmap)
                // Log.d("event", "summary = $eventSummary, startTime = $startTime")
                views!!.setImageViewBitmap(eventViewId, convertStringToBitmap(eventSummary, context, R.font.windows_command_prompt, 72f, inColor))
            } else {
                views!!.setImageViewBitmap(eventViewId, null)
                views!!.setImageViewBitmap(timeViewId, null)
            }
        }

        update(context)
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkInfo = connectivityManager.activeNetworkInfo
    return networkInfo != null && networkInfo.isConnected
}

@RequiresApi(Build.VERSION_CODES.O)
fun update(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
    for (appWidgetId in appWidgetIds) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    views!!.setOnClickPendingIntent(R.id.nextButton, getPendingSelfIntent(context, "ACTION_NEXT"))
    views!!.setOnClickPendingIntent(R.id.prevButton, getPendingSelfIntent(context, "ACTION_PREV"))
    views!!.setOnClickPendingIntent(R.id.dateImage, getPendingSelfIntent(context, "ACTION_TODAY"))
    val instant = viewingDate.toInstant()
    val date = Date.from(instant)
    val formattedDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
    if (failureCode == codes.OK) {
        views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(formattedDate, context, R.font.upheavtt, 96f, Color.WHITE))
    } else {
        views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
    }
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

fun convertStringToBitmap(eventSummary: String, context: Context, font: Int, size: Float, inColor: Int): Bitmap {
    val paint = Paint().apply {
        textSize = size
        typeface = ResourcesCompat.getFont(context, font)
        color = inColor
    }
    val bounds = Rect()
    paint.getTextBounds(eventSummary, 0, eventSummary.length, bounds)
    val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawText(eventSummary, -bounds.left.toFloat(), -bounds.top.toFloat(), paint)
    return bitmap
}

private fun getAccessToken(context: Context): String? {
    val sharedPref = context.getSharedPreferences("widget_prefs", MODE_PRIVATE)
    return sharedPref.getString("access_token", null)
}

private fun getRefreshToken(context: Context): String? {
    val sharedPref = context.getSharedPreferences("widget_prefs", MODE_PRIVATE)
    return sharedPref.getString("refresh_token", null)
}

suspend fun refreshAccessToken(context: Context, refreshToken: String) = withContext(Dispatchers.IO) {
    if (isNetworkAvailable(context)) {
        val requestBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v4/token")
            .post(requestBody)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val responseBody = response.body()?.string() + ""
        Log.d("New token after refresh", responseBody)
        val accessToken = JSONObject(responseBody + "").getString("access_token")
        saveToken(accessToken, "access_token", context)
    } else {
        Log.e("RefreshToken", "Tried to refresh access token, but no internet.")
    }
}

private fun saveToken(token: String, label: String, context: Context) {
    val sharedPref = context.getSharedPreferences("widget_prefs", MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString(label, token)
        apply()
        // android.util.Log.d(android.content.ContentValues.TAG, "saved $label token $token")
    }
}

// https://stackoverflow.com/questions/14798073/button-click-event-for-android-widget
fun getPendingSelfIntent(context: Context?, inAction: String?): PendingIntent? {
    val intent = Intent(context, CalendarWidget::class.java).apply { action = inAction }
    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

val clientId = "507042157003-jn7nr5i9hqkmkcp6paru4qf4th4lh521.apps.googleusercontent.com"
val clientSecret = "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"
