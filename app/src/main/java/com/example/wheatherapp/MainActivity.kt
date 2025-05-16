package com.example.wheatherapp

import androidx.appcompat.widget.SearchView
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wheatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 100
    private val weatherCache = mutableMapOf<String, WeatherApp>()
    private val forecastCache = mutableMapOf<String, WeatherApp>()
    private val forecastDates = mutableListOf<String>()

    companion object {
        const val API_KEY = "21641257cf984940b2661325251605"
        const val BASE_URL = "https://api.weatherapi.com/v1/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            setupForecastCards()
            setupSearchCity()
            setupCardClickEvents()

            // Check location permission
            if (hasLocationPermission()) {
                getCurrentLocation()
            } else {
                requestLocationPermission()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "App failed to start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                // Fallback to a default city if permission denied
                fetchWeatherData("Delhi")
            }
        }
    }

    private fun setupForecastCards() {
        val dayIds = listOf(binding.firstDay, binding.secondDay, binding.thirdDay, binding.fourthDay, binding.fifthDay)
        val dateIds = listOf(binding.firstDate, binding.secondDate, binding.thirdDate, binding.fourthDate, binding.fifthDate)

        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd, MMM", Locale.getDefault())
        val dateStorageFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        forecastDates.clear()
        for (i in 0 until 5) {
            dayIds[i].text = dayFormat.format(calendar.time).uppercase(Locale.getDefault())
            dateIds[i].text = dateFormat.format(calendar.time).uppercase(Locale.getDefault())
            forecastDates.add(dateStorageFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun setupCardClickEvents() {
        val cardViews = listOf(binding.cardDay1, binding.cardDay2, binding.cardDay3, binding.cardDay4, binding.cardDay5)

        for (i in cardViews.indices) {
            cardViews[i].setOnClickListener {
                val city = binding.cityName.text.toString().split(",")[0].trim()
                if (i == 0) {
                    weatherCache[city]?.let { updateWeatherUI(it) } ?: fetchWeatherData(city)
                } else {
                    forecastCache[city]?.let { updateForecastForDate(it, forecastDates[i]) }
                        ?: fetchForecastData(city)
                }
            }
        }
    }

    private fun updateForecastForDate(weatherApp: WeatherApp, selectedDate: String) {
        val forecastDay = weatherApp.forecast.forecastday.find { it.date == selectedDate }
        if (forecastDay != null) {
            updateForecastUI(forecastDay, selectedDate)
        } else {
            Toast.makeText(this, "No forecast available for selected date", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateForecastUI(forecastDay: Forecastday, selectedDate: String) {
        binding.apply {
            val city = cityName.text.toString().split(",")[0].trim()
            cityName.text = "$city, ${forecastDay.day.condition.text}"

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            selactedDay.text = if (todayStr == selectedDate) "Today" else
                SimpleDateFormat("EEEE", Locale.getDefault()).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)!!
                )

            temp.text = "${forecastDay.day.avgtemp_c.roundToInt()}°C"
            weather.text = forecastDay.day.condition.text
            maxTemp.text = "Max: ${forecastDay.day.maxtemp_c.roundToInt()}°C"
            minTemp.text = "Min: ${forecastDay.day.mintemp_c.roundToInt()}°C"
            humidity.text = "${forecastDay.day.avghumidity}%"
            windSpeed.text = "${(forecastDay.day.maxwind_kph / 3.6).format(1)} m/s"
            sunrise.text = forecastDay.astro.sunrise
            sunset.text = forecastDay.astro.sunset
            sea.text = "-- hPa"
            condition.text = forecastDay.day.condition.text

            day.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecastDay.date)!!
            )
            date.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecastDay.date)!!
            )
        }
        changeImagesAccordingToWeatherCondition(forecastDay.day.condition.text)
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                fetchWeatherByCoordinates(it.latitude, it.longitude)
            } ?: run {
                // Fallback if location is null
                fetchWeatherData("Delhi")
            }
        }.addOnFailureListener { e ->
            Log.e("LocationError", "Failed to get location", e)
            fetchWeatherData("Delhi")
        }
    }

    private fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getWeatherByCoordinates("$lat,$lon", API_KEY)
                val weatherResponse = response.execute()

                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!

                    // Fetch forecast data
                    val forecastResponse = RetrofitClient.instance.getFiveDayForecastByCoordinates(
                        "$lat,$lon",
                        API_KEY
                    ).execute()

                    if (forecastResponse.isSuccessful && forecastResponse.body() != null) {
                        val forecast = forecastResponse.body()!!
                        weatherCache[weather.location.name] = weather
                        forecastCache[weather.location.name] = forecast

                        withContext(Dispatchers.Main) {
                            updateWeatherUI(weather)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to get forecast data",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to get weather data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("WeatherError", "Exception while fetching weather", e)
                }
            }
        }
    }

    private fun setupSearchCity() {
        val searchView = binding.searchView // This is already androidx.appcompat.widget.SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    if (it.isNotBlank()) {
                        fetchWeatherData(it)
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean = true
        })
    }

    private fun fetchWeatherData(cityName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getWeatherData(cityName, API_KEY)
                val weather = response.execute().body()

                if (weather != null) {
                    weatherCache[cityName] = weather

                    withContext(Dispatchers.Main) {
                        updateWeatherUI(weather)
                        fetchForecastData(cityName)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "City not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "HTTP error: ${e.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchForecastData(cityName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getFiveDayForecast(cityName, API_KEY, 5)
                val forecast = response.execute().body()

                if (forecast != null) {
                    forecastCache[cityName] = forecast

                    withContext(Dispatchers.Main) {
                        updateForecastForDate(forecast, forecastDates[0])
                    }
                }
            } catch (e: Exception) {
                Log.e("ForecastError", "Failed to fetch forecast", e)
            }
        }
    }

    private fun updateWeatherUI(weatherData: WeatherApp) {
        try {
            binding.apply {
                cityName.text = "${weatherData.location.name}, ${weatherData.location.country}"
                selactedDay.text = "Today"
                temp.text = "${weatherData.current.temp_c.roundToInt()}°C"
                weather.text = weatherData.current.condition.text

                // Safely access forecast data
                weatherData.forecast?.forecastday?.firstOrNull()?.let { forecastDay ->
                    maxTemp.text = "Max: ${forecastDay.day.maxtemp_c.roundToInt()}°C"
                    minTemp.text = "Min: ${forecastDay.day.mintemp_c.roundToInt()}°C"
                    sunrise.text = forecastDay.astro.sunrise
                    sunset.text = forecastDay.astro.sunset
                } ?: run {
                    maxTemp.text = "--"
                    minTemp.text = "--"
                    sunrise.text = "--"
                    sunset.text = "--"
                }

                humidity.text = "${weatherData.current.humidity}%"
                windSpeed.text = "%.1f m/s".format(weatherData.current.wind_kph / 3.6)
                sea.text = "${weatherData.current.pressure_mb} hPa"
                condition.text = weatherData.current.condition.text
                time.text = "Time: ${timeWithOffset(weatherData.location.tz_id)}"
                day.text = dayName(System.currentTimeMillis())
                date.text = date()
            }
            changeImagesAccordingToWeatherCondition(weatherData.current.condition.text)
        } catch (e: Exception) {
            Log.e("WeatherUI", "Error updating UI", e)
            Toast.makeText(this, "Error displaying weather data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeImagesAccordingToWeatherCondition(condition: String) {
        val (bg, anim) = when {
            condition.contains("Sunny", ignoreCase = true) ||
                    condition.contains("Clear", ignoreCase = true) ->
                Pair(R.drawable.sunny_background, R.raw.sun)

            condition.contains("Cloud", ignoreCase = true) ||
                    condition.contains("Overcast", ignoreCase = true) ||
                    condition.contains("Mist", ignoreCase = true) ||
                    condition.contains("Fog", ignoreCase = true) ->
                Pair(R.drawable.colud_background, R.raw.cloud)

            condition.contains("Rain", ignoreCase = true) ||
                    condition.contains("Drizzle", ignoreCase = true) ||
                    condition.contains("Shower", ignoreCase = true) ->
                Pair(R.drawable.rain_background, R.raw.rain)

            condition.contains("Snow", ignoreCase = true) ||
                    condition.contains("Blizzard", ignoreCase = true) ->
                Pair(R.drawable.snow_background, R.raw.snow)

            else -> Pair(R.drawable.sunny_background, R.raw.sun)
        }

        runOnUiThread {
            binding.root.setBackgroundResource(bg)
            binding.lottieAnimationView.setAnimation(anim)
            binding.lottieAnimationView.playAnimation()
        }
    }

    private fun timeWithOffset(timeZoneId: String): String {
        return try {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))
            SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = calendar.timeZone
            }.format(calendar.time)
        } catch (e: Exception) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    private fun date(): String = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    private fun dayName(ts: Long): String = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ts))

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}