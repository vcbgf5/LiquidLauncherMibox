package com.liquidglass.tvlauncher.data

import android.content.Context
import com.liquidglass.tvlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class WeatherCity(val name: String, val latitude: Double, val longitude: Double)

/** Cities to pick from in the settings; any other place is looked up by name (see [WeatherRepository.geocode]). */
object Cities {
    val BUILTIN = listOf(
        WeatherCity("Warszawa", 52.2297, 21.0122),
        WeatherCity("Kraków", 50.0647, 19.9450),
        WeatherCity("Łódź", 51.7592, 19.4560),
        WeatherCity("Wrocław", 51.1079, 17.0385),
        WeatherCity("Poznań", 52.4064, 16.9252),
        WeatherCity("Gdańsk", 54.3520, 18.6466),
        WeatherCity("Szczecin", 53.4285, 14.5528),
        WeatherCity("Bydgoszcz", 53.1235, 18.0084),
        WeatherCity("Lublin", 51.2465, 22.5684),
        WeatherCity("Białystok", 53.1325, 23.1688),
        WeatherCity("Katowice", 50.2649, 19.0238),
        WeatherCity("Gdynia", 54.5189, 18.5305),
        WeatherCity("Częstochowa", 50.8118, 19.1203),
        WeatherCity("Radom", 51.4027, 21.1471),
        WeatherCity("Rzeszów", 50.0412, 21.9991),
        WeatherCity("Toruń", 53.0138, 18.5984),
        WeatherCity("Kielce", 50.8661, 20.6286),
        WeatherCity("Olsztyn", 53.7784, 20.4801),
        WeatherCity("Opole", 50.6751, 17.9213),
        WeatherCity("Zielona Góra", 51.9356, 15.5062),
        WeatherCity("Bielsko-Biała", 49.8224, 19.0584),
        WeatherCity("Sopot", 54.4416, 18.5601),
        WeatherCity("Zakopane", 49.2992, 19.9496),
    )
}

/** What the sky looks like, for the icon; day and night only matter for the clear and partly cloudy ones. */
enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

class WeatherDay(val date: String, val code: Int, val max: Double, val min: Double, val rainChance: Int?)

class Weather(
    val city: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
    val isDay: Boolean,
    val days: List<WeatherDay>,
    val fetchedAt: Long,
) {
    val kind: WeatherKind get() = kindOf(code)

    companion object {
        /** WMO weather interpretation codes, as Open-Meteo returns them. */
        fun kindOf(code: Int): WeatherKind = when (code) {
            0, 1 -> WeatherKind.CLEAR
            2 -> WeatherKind.PARTLY
            3 -> WeatherKind.CLOUDY
            45, 48 -> WeatherKind.FOG
            51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
            71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
            95, 96, 99 -> WeatherKind.STORM
            else -> WeatherKind.CLOUDY
        }

        fun textOf(code: Int): Int = when (code) {
            0 -> R.string.w_clear
            1 -> R.string.w_mostly_clear
            2 -> R.string.w_partly
            3 -> R.string.w_overcast
            45, 48 -> R.string.w_fog
            51, 53, 55 -> R.string.w_drizzle
            56, 57 -> R.string.w_freezing_drizzle
            61, 63 -> R.string.w_rain
            65 -> R.string.w_heavy_rain
            66, 67 -> R.string.w_freezing_rain
            71, 73 -> R.string.w_snow
            75 -> R.string.w_heavy_snow
            77 -> R.string.w_snow_grains
            80, 81, 82 -> R.string.w_showers
            85, 86 -> R.string.w_snow_showers
            95 -> R.string.w_storm
            96, 99 -> R.string.w_storm_hail
            else -> R.string.w_overcast
        }
    }
}

/**
 * Weather from Open-Meteo (free, no key): the current conditions and the next days for a city.
 * The last answer is kept, so the widget has something to show before the first download.
 */
object WeatherRepository {
    private const val PREFS = "weather"
    private const val KEY_JSON = "json"
    private const val KEY_CITY = "city"
    private const val KEY_AT = "at"

    suspend fun load(context: Context, city: WeatherCity): Weather? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${coord(city.latitude)}&longitude=${coord(city.longitude)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=5"
        val text = Net.download(url, 200_000)?.toString(Charsets.UTF_8) ?: return@withContext null
        val weather = try {
            parse(city.name, text, System.currentTimeMillis())
        } catch (_: Exception) {
            return@withContext null
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, text).putString(KEY_CITY, city.name).putLong(KEY_AT, weather.fetchedAt).apply()
        weather
    }

    /** The last downloaded weather, if any. */
    fun cached(context: Context): Weather? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JSON, null) ?: return null
        return try {
            parse(prefs.getString(KEY_CITY, "") ?: "", json, prefs.getLong(KEY_AT, 0L))
        } catch (_: Exception) {
            null
        }
    }

    /** Finds a place by name (for a city that isn't in the list); null when it isn't found or offline. */
    suspend fun geocode(name: String): WeatherCity? = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(name.trim(), "UTF-8")}&count=1&language=pl&format=json"
        val text = Net.download(url, 100_000)?.toString(Charsets.UTF_8) ?: return@withContext null
        try {
            val first = JSONObject(text).optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
            WeatherCity(first.getString("name"), first.getDouble("latitude"), first.getDouble("longitude"))
        } catch (_: Exception) {
            null
        }
    }

    private fun coord(value: Double) = String.format(Locale.US, "%.4f", value)

    private fun parse(city: String, text: String, fetchedAt: Long): Weather {
        val root = JSONObject(text)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxes = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val days = (0 until times.length()).map { i ->
            WeatherDay(
                date = times.getString(i),
                code = codes.optInt(i),
                max = maxes.optDouble(i),
                min = mins.optDouble(i),
                rainChance = if (rain != null && !rain.isNull(i)) rain.optInt(i) else null,
            )
        }
        val temperature = current.getDouble("temperature_2m")
        return Weather(
            city = city,
            temperature = temperature,
            feelsLike = current.optDouble("apparent_temperature", temperature),
            humidity = current.optInt("relative_humidity_2m"),
            windKmh = current.optDouble("wind_speed_10m"),
            code = current.optInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            days = days,
            fetchedAt = fetchedAt,
        )
    }
}
