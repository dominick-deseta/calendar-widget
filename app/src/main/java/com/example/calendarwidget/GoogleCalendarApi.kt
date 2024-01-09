import com.example.calendarwidget.CalendarList
import com.example.calendarwidget.Events
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleCalendarApi {
    @GET("users/me/calendarList")
    fun getCalendars(@Header("Authorization") authHeader: String): Call<CalendarList>

    @GET("calendars/{calendarId}/events")
    fun getEvents(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean,
//        @Query("orderBy") orderBy: String
    ): Call<Events> }
