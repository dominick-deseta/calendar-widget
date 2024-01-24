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
import android.graphics.Paint
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

const val PREF_CALENDAR_LIST_CACHE = "calendar_list_cache"
private var views: RemoteViews? = null
private var calendarEvents: MutableList<EventItem> = mutableListOf()
private var totalNumCalendars = 0
private var fetchedNumCalendars = 0

enum class codes(val message: String) {
    OK("OK"),
    BAD_RESPONSE("Bad Response"),
    FAILED_REQUEST("Failed Request"),
    NO_INTERNET("No Internet")
}
var failureCode = codes.OK

@RequiresApi(Build.VERSION_CODES.O)
private var viewingDate = ZonedDateTime.now()

class CalendarWidget : AppWidgetProvider() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        views = RemoteViews(context.packageName, R.layout.calendar_widget)

//        CoroutineScope(Dispatchers.Main).launch {
//            refreshAccessToken(context, getRefreshToken(context) + "")
//        }

        callCalendars(context, getAccessToken(context) + "")

        appWidgetIds.forEach { appWidgetId ->
            val intent = Intent(context, MyWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            views?.apply {
                setRemoteAdapter(R.id.lorem_ipsum, intent)
                setEmptyView(R.id.lorem_ipsum, R.id.empty_view)
            }
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateListView(context: Context, events: List<EventItem>) {
        val sharedPreferences = context.getSharedPreferences("WidgetData", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("events", Gson().toJson(events)).apply()
        sharedPreferences.edit().putBoolean("pastDate", viewingDate.toLocalDate() < ZonedDateTime.now().toLocalDate()).apply()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.lorem_ipsum)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RECEIVING!", "Receiving!")
        super.onReceive(context, intent)

        if (views == null) {
            views = RemoteViews(context.packageName, R.layout.calendar_widget).apply {
                setRemoteAdapter(R.id.lorem_ipsum, intent)
                setEmptyView(R.id.lorem_ipsum, R.id.empty_view)
                Log.d("views", "set remote adapter")
            }
        }
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
        val savedCalendarIds = getSavedCalendarIds(context)
        totalNumCalendars = savedCalendarIds.size
        fetchedNumCalendars = 0
        calendarEvents = mutableListOf()
        updateListView(context, mutableListOf())
        savedCalendarIds.forEach { id ->
            callEvents(context, accessToken, id, viewingDate)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callEvents(context: Context, accessToken: String, calendarId: String, inDate: ZonedDateTime) {
        val timeMin = inDate.withHour(0).withMinute(0).withSecond(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val timeMax = inDate.withHour(23).withMinute(59).withSecond(59).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Log.d("Getting events with...", accessToken)
        if (isNetworkAvailable(context)) {
            NetworkService.googleCalendarApi.getEvents("Bearer " + accessToken, calendarId, timeMin, timeMax, true).enqueue(object :
//            NetworkService.googleCalendarApi.getEvents("Bearer " + accessToken, "iamtherealdinosaur@gmail.com", timeMin, timeMax, true).enqueue(object :
                        Callback<Events> {
                    override fun onResponse(call: Call<Events>, response: Response<Events>) {
                        if (response.isSuccessful) {
                            failureCode = codes.OK
                            Log.d("Good Response!", response.body().toString())
                            val events = response.body()
                            if (events != null) {
                                calendarEvents.addAll(events.eventItems)
                                if (fetchedNumCalendars < totalNumCalendars - 1) {
                                    fetchedNumCalendars += 1
                                } else if (viewingDate == inDate) {
                                    updateListView(context, sortEvents(calendarEvents))
                                    // populateEventViews(context, sortEvents(inEvents))
                                }
                            }
                        } else {
                            failureCode = codes.BAD_RESPONSE
                            Log.e(ContentValues.TAG, "Bad Response: ${response.errorBody()?.string()}")
                            views!!.setTextViewText(R.id.displayDate, failureCode.message) // views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
                            CoroutineScope(Dispatchers.Main).launch {
                                refreshAccessToken(context, getRefreshToken(context) + "")
                            }
                        }
                    }
                    override fun onFailure(call: Call<Events>, t: Throwable) {
                        failureCode = codes.FAILED_REQUEST
                        Log.e(ContentValues.TAG, "Failed request: ${t.message}")
                        views!!.setTextViewText(R.id.displayDate, failureCode.message)
                        // views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
                        CoroutineScope(Dispatchers.Main).launch {
                            refreshAccessToken(context, getRefreshToken(context) + "")
                        }
                    }
                })
        } else {
            failureCode = codes.NO_INTERNET
            Log.e(ContentValues.TAG, "Tried to fetch events, but no internet.")
            views!!.setTextViewText(R.id.displayDate, failureCode.message)
            // views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sortEvents(eventItems: MutableList<EventItem>): List<EventItem> {
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
    views!!.setOnClickPendingIntent(R.id.displayDate, getPendingSelfIntent(context, "ACTION_TODAY"))
    val instant = viewingDate.toInstant()
    val date = Date.from(instant)
    val formattedDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
    if (failureCode == codes.OK) {
        views!!.setTextViewText(R.id.displayDate, formattedDate)
        // views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(formattedDate, context, R.font.upheavtt, 96f, Color.WHITE))
    } else {
        views!!.setTextViewText(R.id.displayDate, failureCode.message)
        // views!!.setImageViewBitmap(R.id.dateImage, convertStringToBitmap(failureCode.message, context, R.font.upheavtt, 96f, Color.WHITE))
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
