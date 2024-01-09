package com.example.calendarwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.calendarwidget.databinding.CalendarWidgetConfigureBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * The configuration screen for the [CalendarWidget] AppWidget.
 */
class CalendarWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var appWidgetText: EditText
    private var onClickListener = View.OnClickListener {
        val context = this@CalendarWidgetConfigureActivity

        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
    private lateinit var binding: CalendarWidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = CalendarWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addButton.setOnClickListener(onClickListener)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callCalendars(context: Context, accessToken: String) {
        NetworkService.googleCalendarApi.getCalendars("Bearer " + accessToken).enqueue(object :
            Callback<CalendarList> {
            override fun onResponse(call: Call<CalendarList>, response: Response<CalendarList>) {
                if (response.isSuccessful) {
                    val calendarList = response.body()
                    Log.d(ContentValues.TAG, "Calendar List: $calendarList")
                    if (calendarList != null) {

                    }
                } else { Log.e(ContentValues.TAG, "Failed to get calendars: ${response.errorBody()?.string()}") }
            }
            override fun onFailure(call: Call<CalendarList>, t: Throwable) { Log.e(ContentValues.TAG, "Failed to get calendars: ${t.message}") }
        })
    }

}

