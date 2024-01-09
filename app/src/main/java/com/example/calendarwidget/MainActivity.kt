package com.example.calendarwidget

import android.R
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.marginBottom
import com.example.calendarwidget.databinding.MainActivityBinding
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.ZonedDateTime


class MainActivity : ComponentActivity() {
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var binding: MainActivityBinding
    private lateinit var signInButton: SignInButton
    private lateinit var viewingDate: ZonedDateTime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .requestServerAuthCode("507042157003-jn7nr5i9hqkmkcp6paru4qf4th4lh521.apps.googleusercontent.com", true)
            .requestIdToken("507042157003-jn7nr5i9hqkmkcp6paru4qf4th4lh521.apps.googleusercontent.com")
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        var mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) { Log.d(TAG, "token = " + account.idToken) }
        signInButton = binding.signInButton
        signInButton.setOnClickListener {
            val signInIntent: Intent = mGoogleSignInClient.getSignInIntent()
            startActivityForResult(signInIntent, 123)
        }

        val calendarList = getSavedCalendarList()
        if (calendarList != null) {
            populateCheckboxes(calendarList)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult called")
        when (requestCode) {
            123 -> {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    Log.d(TAG, "task created")
                    val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                    Log.d(TAG, "account retrieved")
                    Log.d(TAG, account.requestedScopes.toString())
                    Log.d(TAG, account.grantedScopes.toString())
                    val authCode = account.serverAuthCode

                    CoroutineScope(Dispatchers.IO).launch {
                        val accessToken = getTokens(authCode + "")
                        callCalendars(accessToken)
                        // Make sure to switch back to the main thread to update the UI
                        launch(Dispatchers.Main) {
                        }
                    }
                } catch (e: ApiException) {
                    Log.e(TAG, e.toString())
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callCalendars(accessToken: String): String {
        var calendarId = ""
        NetworkService.googleCalendarApi.getCalendars("Bearer " + accessToken).enqueue(object :
                Callback<CalendarList> {
                override fun onResponse(call: Call<CalendarList>, response: Response<CalendarList>) {
                    if (response.isSuccessful) {
                        val calendarList = response.body()
                        Log.d(ContentValues.TAG, "Calendar List: $calendarList")
                        if (calendarList != null) {
                            saveCalendarList(calendarList)
                            calendarId = "iamtherealdinosaur@gmail.com"
                            populateCheckboxes(calendarList)
                            Log.d(ContentValues.TAG, "First calendar id: $calendarId")
                        }
                    } else { Log.e(ContentValues.TAG, "Failed to get calendars: ${response.errorBody()?.string()}") }
                }
                override fun onFailure(call: Call<CalendarList>, t: Throwable) { Log.e(ContentValues.TAG, "Failed to get calendars: ${t.message}") }
            })
        return calendarId
    }

    fun removeInvalidCalendarIds(calendarList: CalendarList) {
        val sharedPref = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val editor = sharedPref.edit()
        val storedCalendarIds = sharedPref.all.keys
            .filter { it.startsWith("calendarid_") }
            .map { it.replace("calendarid_", "") }
            .toSet()
        val validCalendarIds = calendarList.calendarItems.map { it.id }.toSet()
        val invalidCalendarIds = storedCalendarIds - validCalendarIds
        invalidCalendarIds.forEach { id ->
            editor.remove("calendarid_$id")
        }
        editor.apply()
    }

    fun populateCheckboxes(calendarList: CalendarList) {
        removeInvalidCalendarIds(calendarList)
        val checkboxContainer = binding.checkboxContainer
        checkboxContainer.removeAllViews()

        val sharedPref = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        for (calendarItem in calendarList.calendarItems) {
            val checkBox = CheckBox(this)
            checkBox.text = calendarItem.summary
            checkBox.isChecked = sharedPref.getBoolean("calendarid_${calendarItem.id}", false) // Set the initial checked state
            checkBox.textSize = 28f
            checkBox.setPadding(10, 10, 10, 10)
            // checkBox.typeface = ResourcesCompat.getFont(this, com.example.calendarwidget.R.font.windows_command_prompt)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                with(sharedPref.edit()) {
                    putBoolean("calendarid_${calendarItem.id}", isChecked)
                    apply()
                }
            }

            checkboxContainer.addView(checkBox)
        }
    }

    fun saveCalendarList(calendarList: CalendarList) {
        val sharedPref = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val editor = sharedPref.edit()
        val gson = Gson()
        val json = gson.toJson(calendarList)
        editor.putString(PREF_CALENDAR_LIST_CACHE, json)
        editor.apply()
    }

    fun getSavedCalendarList(): CalendarList? {
        val sharedPref = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString(PREF_CALENDAR_LIST_CACHE, null)
        return gson.fromJson(json, CalendarList::class.java)
    }

    private fun saveToken(token: String, label: String) {
        val sharedPref = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(label, token)
            apply()
            Log.d(TAG, "saved $label token $token")
        }
    }

    fun getTokens(authCode: String): String {
        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            // TODO: Move to user files (bad practice to have exposed!)
            .add("client_id", "507042157003-jn7nr5i9hqkmkcp6paru4qf4th4lh521.apps.googleusercontent.com")
            .add("client_secret", "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")
            .add("redirect_uri", "")
            .add("code", authCode)
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v4/token")
            .post(requestBody)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val responseBody = response.body()?.string() + ""
        Log.d(TAG, responseBody)
        val accessToken = JSONObject(responseBody + "").getString("access_token")
        val refreshToken = JSONObject(responseBody + "").getString("refresh_token")
        saveToken(accessToken, "access_token")
        saveToken(refreshToken, "refresh_token")
        return accessToken
    }
}
