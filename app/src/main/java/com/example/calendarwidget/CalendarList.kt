package com.example.calendarwidget

import com.google.gson.annotations.SerializedName

data class CalendarList (
    val kind: String,
    val etag: String,
    val nextPageToken: String,
    @SerializedName("items") val calendarItems: List<CalendarItem>
)

data class CalendarItem (
    val kind: String,
    val etag: String,
    val id: String,
    val summary: String,
    val timeZone: String,
    val colorID: String,
    val backgroundColor: String,
    val foregroundColor: String,
    val selected: Boolean,
    val accessRole: String,
    @SerializedName("defaultReminders") val calendarDefaultReminders: List<CalendarDefaultReminder>,
    val notificationSettings: NotificationSettings? = null,
    val primary: Boolean? = null,
    val conferenceProperties: ConferenceProperties,
    val description: String? = null
)

data class ConferenceProperties (
    val allowedConferenceSolutionTypes: List<String>
)

data class CalendarDefaultReminder (
    val method: String,
    val minutes: Long
)

data class NotificationSettings (
    val notifications: List<Notification>
)

data class Notification (
    val type: String,
    val method: String
)
