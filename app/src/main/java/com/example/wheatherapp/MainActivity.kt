package com.example.wheatherapp

import kotlin.math.roundToInt
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.example.wheatherapp.databinding.ActivityMainBinding
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 100
    private val weatherCache = mutableMapOf<String, WeatherApp>()
    private val forecastCache = mutableMapOf<String, ForecastResponse>()
    private var searchDebounceJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val forecastDates = mutableListOf<String>() // Store in "yyyy-MM-dd" format

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupForecastCards()
        getCurrentLocation()  // Fetch current location
        setupSearchCity()  // Initialize search functionality
        setupCardClickEvents()
    }

    private fun setupForecastCards() {
        val dayIds = listOf(
            binding.firstDay,
            binding.secondDay,
            binding.thirdDay,
            binding.fourthDay,
            binding.fifthDay
        )

        val dateIds = listOf(
            binding.firstDate,
            binding.secondDate,
            binding.thirdDate,
            binding.fourthDate,
            binding.fifthDate
        )

        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd, MMM", Locale.getDefault())
        val dateStorageFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        forecastDates.clear()

        for (i in 0 until 5) {
            val day = dayFormat.format(calendar.time).uppercase(Locale.getDefault())
            val date = dateFormat.format(calendar.time).uppercase(Locale.getDefault())
            val fullDate = dateStorageFormat.format(calendar.time)

            dayIds[i].text = day
            dateIds[i].text = date
            forecastDates.add(fullDate)

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun setupCardClickEvents() {
        val cardViews = listOf(
            binding.cardDay1,
            binding.cardDay2,
            binding.cardDay3,
            binding.cardDay4,
            binding.cardDay5
        )

        for (i in cardViews.indices) {
            cardViews[i].setOnClickListener {
                val city = binding.cityName.text.toString().split(",")[0].trim()

                if (i == 0) {
                    // For the first card, fetch current weather data
                    weatherCache[city]?.let { cachedWeather ->
                        updateWeatherUI(cachedWeather) // Update UI with cached weather data
                    } ?: fetchWeatherData(city) // Fetch new data if not cached
                } else {
                    // For other cards, fetch forecast data
                    forecastCache[city]?.let { cachedForecast ->
                        val selectedDate = forecastDates[i]
                        updateForecastForDate(cachedForecast, selectedDate) // Update UI with cached forecast data
                    } ?: fetchForecastForDate(forecastDates[i], city) // Fetch new forecast data if not cached
                }
            }
        }
    }


    private fun updateForecastForDate(forecast: ForecastResponse, date: String) {
        val forecastList = forecast.list
        val selectedDayForecasts = forecastList.filter { it.dt_txt.startsWith(date) }

        if (selectedDayForecasts.isNotEmpty()) {
            val selectedItem = selectedDayForecasts.find { it.dt_txt.contains("12:00:00") }
                ?: selectedDayForecasts.first()
            updateForecastUI(selectedItem, date)
        } else {
            Toast.makeText(this, "No forecast available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchForecastForDate(date: String, city: String) {
        ioScope.launch {
            try {
                // Execute the call and get the response
                val response = RetrofitClient.instance.getFiveDayForecast(
                    city,
                    "d251152482d334b07e33525ea4adcd6e",
                    "metric"
                ).execute() // Changed from enqueue to execute

                // Now check response.isSuccessful
                if (response.isSuccessful && response.body() != null) {
                    val forecast = response.body()!!
                    mainScope.launch {
                        forecastCache[city] = forecast
                        updateForecastForDate(forecast, date)
                    }
                } else {
                    mainScope.launch {
                        Toast.makeText(this@MainActivity, "Forecast load failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainScope.launch {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateForecastUI(item: ForecastItem, selectedDate: String) {
        binding.apply {
            val city = cityName.text.toString().split(",")[0].trim()
            cityName.text = "$city, ${Locale("", "IN").displayCountry}"

            val calendar = Calendar.getInstance()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val selectedDayText = if (todayStr == selectedDate) {
                "Today"
            } else {
                val displayFormat = SimpleDateFormat("dd, MMM", Locale.getDefault())
                val selectedDateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
                displayFormat.format(selectedDateParsed!!)
            }
            selactedDay.text = selectedDayText

            temp.text = "${item.main.temp.roundToInt()}°C"
            weather.text = item.weather[0].main
            maxTemp.text = "Max Temp: ${item.main.temp_max.roundToInt()}°C"
            minTemp.text = "Min Temp: ${item.main.temp_min.roundToInt()}°C"
            humidity.text = "${item.main.humidity}%"
            sea.text = "${item.main.pressure} hPa"
            condition.text = item.weather[0].main
            windSpeed.text = "${item.wind.speed} m/s"

            val dtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dt = dtFormat.parse(item.dt_txt)
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            val forecastTime = Date(item.dt * 1000L)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            time.text = "Time: ${timeFormat.format(forecastTime)}"
            day.text = dayFormat.format(dt)
            date.text = date()

            sunrise.text = "--:--"
            sunset.text = "--:--"

            changeImagesAcoordingToWeatherCondtion(item.weather[0].main)
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                fetchWeatherByCoordinates(it.latitude, it.longitude)
            } ?: run {
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Run both requests in parallel
                val currentDeferred = async {
                    RetrofitClient.instance.getWeatherByCoordinates(
                        lat, lon,
                        "d251152482d334b07e33525ea4adcd6e",
                        "metric"
                    ).execute()
                }

                val forecastDeferred = async {
                    RetrofitClient.instance.getFiveDayForecastByCoordinates(
                        lat, lon,
                        "d251152482d334b07e33525ea4adcd6e",
                        "metric"
                    ).execute()
                }

                val currentResponse = currentDeferred.await()
                val forecastResponse = forecastDeferred.await()

                if (currentResponse.isSuccessful && currentResponse.body() != null) {
                    val weatherData = currentResponse.body()!!
                    val cityKey = weatherData.name

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        weatherCache[cityKey] = weatherData
                        binding.cityName.text = "${weatherData.name}, ${weatherData.sys.country}"
                        updateWeatherUI(weatherData)

                        forecastCache[cityKey]?.let { forecast ->
                            updateForecastForDate(forecast, forecastDates[0])
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to get current weather data", Toast.LENGTH_SHORT).show()
                    }
                }

                if (forecastResponse.isSuccessful && forecastResponse.body() != null) {
                    val forecastData = forecastResponse.body()!!
                    val cityName = forecastData.city?.name
                        ?: binding.cityName.text.toString().split(",")[0].trim()

                    forecastCache[cityName] = forecastData

                    val displayedCity = binding.cityName.text.toString().split(",").firstOrNull()?.trim() ?: ""
                    if (cityName.equals(displayedCity, ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            updateForecastForDate(forecastData, forecastDates[0])
                        }
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show()
                    Log.e("WeatherApp", "Network error", e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to fetch weather data", Toast.LENGTH_SHORT).show()
                    Log.e("WeatherApp", "Unexpected error", e)
                }
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchCity() {
        val searchView = binding.searchView as SearchView

        searchView.post {
            try {
                val searchEditText = searchView.findViewById<AutoCompleteTextView>(
                    androidx.appcompat.R.id.search_src_text
                )
                searchEditText?.setTextColor(Color.BLACK)
                searchEditText?.setHintTextColor(Color.GRAY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchDebounceJob?.cancel()
                query?.let { fetchWeatherData(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchDebounceJob?.cancel()
                newText?.takeIf { it.length >= 3 }?.let { query ->
                    searchDebounceJob = mainScope.launch {
                        delay(500)
                        fetchWeatherData(query)
                    }
                }
                return true
            }
        })
    }

    private fun fetchWeatherData(cityName: String) {
        weatherCache[cityName]?.let {
            updateWeatherUI(it)
            return
        }

        ioScope.launch {
            try {
                val response = RetrofitClient.instance.getWeatherData(
                    cityName,
                    "d251152482d334b07e33525ea4adcd6e",
                    "metric"
                ).execute() // Changed from enqueue to execute for coroutines

                if (response.isSuccessful) { // Now checking response.isSuccessful
                    response.body()?.let { weatherData ->
                        mainScope.launch {
                            weatherCache[cityName] = weatherData
                            updateWeatherUI(weatherData)
                            // Prefetch forecast for this city
                            fetchForecastData(cityName)
                        }
                    }
                } else {
                    mainScope.launch {
                        Toast.makeText(this@MainActivity, "City not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainScope.launch {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchForecastData(cityName: String) {
        if (forecastCache.containsKey(cityName)) return

        ioScope.launch {
            try {
                // Execute the request and get the response
                val response = RetrofitClient.instance.getFiveDayForecast(
                    cityName,
                    "d251152482d334b07e33525ea4adcd6e",
                    "metric"
                ).execute() // Add .execute() to actually make the request

                // Now we can safely access .body() on the Response
                if (response.isSuccessful) {
                    response.body()?.let { forecast ->
                        forecastCache[cityName] = forecast
                    }
                }
            } catch (e: Exception) {
                // Silent fail - forecast will load when needed
                Log.e("WeatherApp", "Error fetching forecast", e)
            }
        }
    }

    private fun updateWeatherUI(weatherData: WeatherApp) {
        binding.apply {
            time.text = "Time: ${getCurrentTimeWithOffset(weatherData.timezone)}"
            //cityName.text = "${weatherData.name}, ${Locale("", weatherData.sys.country).displayCountry}"
            //temp.text = "${weatherData.main.temp.roundToInt()}°C"
            cityName.text = "${weatherData.name}, ${weatherData.sys.country}"
            temp.text = "${weatherData.main.temp.roundToInt()}°C"
            weather.text = weatherData.weather.firstOrNull()?.main ?: "Unknown"
            maxTemp.text = "Max Temp: ${weatherData.main.temp_max.roundToInt()}°C"
            minTemp.text = "Min Temp: ${weatherData.main.temp_min.roundToInt()}°C"
            humidity.text = "${weatherData.main.humidity}%"
            windSpeed.text = "%.1f m/s".format(weatherData.wind.speed)
            sunrise.text = time(weatherData.sys.sunrise.toLong())
            sunset.text = time(weatherData.sys.sunset.toLong())
            sea.text = "${weatherData.main.pressure} hPa"
            condition.text = weatherData.weather.firstOrNull()?.main ?: "Unknown"
            day.text = dayName(System.currentTimeMillis())
            date.text = date()
        }

        weatherData.weather.firstOrNull()?.main?.let {
            changeImagesAcoordingToWeatherCondtion(it)
        }
    }

    private fun changeImagesAcoordingToWeatherCondtion(conditions: String) {
        val (bgRes, animRes) = when (conditions) {
            "Clear Sky", "Sunny", "Clear", "Sun" -> Pair(R.drawable.sunny_background, R.raw.sun)
            "Partly Cloudy", "Clouds", "Overcast", "Mist", "Foggy", "Cloud" ->
                Pair(R.drawable.colud_background, R.raw.cloud)
            "Light Rain", "Drizzle", "Moderate Rain", "Showers", "Heavy Rain", "Rain" ->
                Pair(R.drawable.rain_background, R.raw.rain)
            "Light Snow", "Moderate Snow", "Heavy Snow", "Blizzard", "Snow" ->
                Pair(R.drawable.snow_background, R.raw.snow)
            else -> Pair(R.drawable.sunny_background, R.raw.sun)
        }

        binding.root.setBackgroundResource(bgRes)
        binding.lottieAnimationView.setAnimation(animRes)
        binding.lottieAnimationView.playAnimation()
    }

    private fun timeWithOffset(offsetSeconds: Int): String {
        val now = System.currentTimeMillis()
        val timezoneOffsetMs = offsetSeconds * 1000L
        val localTime = now + timezoneOffsetMs
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(localTime))
    }

    private fun getCurrentTimeWithOffset(offsetSeconds: Int): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis() + (offsetSeconds * 1000L)
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
    }

    private fun time(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp * 1000))
    }

    private fun date(): String {
        return SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    }

    private fun dayName(timestamp: Long): String {
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainScope.cancel()
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    val instance: ApiInterface by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiInterface::class.java)
    }
}
