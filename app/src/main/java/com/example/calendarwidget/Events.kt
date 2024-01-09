package com.example.calendarwidget

import com.google.gson.annotations.SerializedName

data class Events (
    val kind: String,
    val etag: String,
    val summary: String,
    val description: String,
    val updated: String,
    val timeZone: String,
    val accessRole: String,
    @SerializedName("defaultReminders") val defaultReminders: List<EventDefaultReminder>,
    val nextSyncToken: String,
    @SerializedName("items") val eventItems: List<EventItem>
)

data class EventDefaultReminder (
    val method: String,
    val minutes: Long
)

data class EventItem (
    val kind: String,
    val etag: String,
    val id: String,
    val status: String,
    val htmlLink: String,
    val created: String,
    val updated: String,
    val summary: String,
    val colorID: String? = null,
    val creator: Creator,
    val organizer: Creator,
    val start: End,
    val end: End,
    val recurrence: List<String>? = null,
    val iCalUID: String,
    val sequence: Long,
    val reminders: Reminders,
    val eventType: String
)

data class Creator (
    val email: String,
    val displayName: String? = null,
    val self: Boolean
)

data class End (
    val date: String? = null,
    val dateTime: String? = null,
    val timeZone: String? = null
)

data class Reminders (
    val useDefault: Boolean
)
