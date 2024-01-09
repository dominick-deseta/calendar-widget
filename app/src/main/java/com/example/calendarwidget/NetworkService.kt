package com.example.calendarwidget
import GoogleCalendarApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create

object NetworkService {
    val googleCalendarApi: GoogleCalendarApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/calendar/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleCalendarApi::class.java)
}
