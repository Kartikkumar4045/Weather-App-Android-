package com.example.wheatherapp

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
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.wheatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 100
    private val weatherCache = mutableMapOf<String, WeatherApp>()
    private val forecastCache = mutableMapOf<String, ForecastResponse>()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var searchDebounceJob: Job? = null

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val forecastDates = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupForecastCards()
        getCurrentLocation()
        setupSearchCity()
        setupCardClickEvents()
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
                    // Use cached current weather for today
                    weatherCache[city]?.let { updateWeatherUI(it) } ?: fetchWeatherData(city)
                } else {
                    forecastCache[city]?.let { updateForecastForDate(it, forecastDates[i]) }
                        ?: fetchForecastForDate(forecastDates[i], city)
                }
            }
        }
    }

    private fun updateForecastForDate(forecast: ForecastResponse, date: String) {
        val forecastList = forecast.list.filter { it.dt_txt.startsWith(date) }
        if (forecastList.isNotEmpty()) {
            val selectedItem = forecastList.find { it.dt_txt.contains("12:00:00") } ?: forecastList.first()
            updateForecastUI(selectedItem, date)
        } else {
            Toast.makeText(this, "No forecast available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchForecastForDate(date: String, city: String) {
        ioScope.launch {
            try {
                val response = RetrofitClient.instance.getFiveDayForecast(city, API_KEY, "metric").execute()
                if (response.isSuccessful && response.body() != null) {
                    val forecast = response.body()!!
                    mainScope.launch {
                        forecastCache[city] = forecast
                        updateForecastForDate(forecast, date)
                    }
                }
            } catch (e: Exception) {
                mainScope.launch { Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun updateForecastUI(item: ForecastItem, selectedDate: String) {
        binding.apply {
            val city = cityName.text.toString().split(",")[0].trim()
            cityName.text = "$city, IN"
            val calendar = Calendar.getInstance()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            selactedDay.text = if (todayStr == selectedDate) "Today" else SimpleDateFormat("dd, MMM", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)!!)
            temp.text = "${item.main.temp.roundToInt()}°C"
            weather.text = item.weather[0].main
            maxTemp.text = "Max Temp: ${item.main.temp_max.roundToInt()}°C"
            minTemp.text = "Min Temp: ${item.main.temp_min.roundToInt()}°C"
            humidity.text = "${item.main.humidity}%"
            sea.text = "${item.main.pressure} hPa"
            condition.text = item.weather[0].main
            windSpeed.text = "${"%.1f".format(item.wind.speed)} m/s"
            time.text = "Time: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.dt * 1000L))}"
            day.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(item.dt * 1000L))
            date.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(item.dt * 1000L))
            sunrise.text = "--:--"
            sunset.text = "--:--"
            changeImagesAcoordingToWeatherCondtion(item.weather[0].main)
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let { fetchWeatherByCoordinates(it.latitude, it.longitude) } ?: Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val current = async {
                    RetrofitClient.instance.getWeatherByCoordinates(lat, lon, API_KEY, "metric").execute()
                }
                val forecast = async {
                    RetrofitClient.instance.getFiveDayForecastByCoordinates(lat, lon, API_KEY, "metric").execute()
                }
                val currentResp = current.await()
                val forecastResp = forecast.await()

                if (currentResp.isSuccessful && currentResp.body() != null) {
                    val weather = currentResp.body()!!
                    weatherCache[weather.name] = weather
                    withContext(Dispatchers.Main) {
                        updateWeatherUI(weather)
                        forecastCache[weather.name]?.let { updateForecastForDate(it, forecastDates[0]) }
                    }
                }

                if (forecastResp.isSuccessful && forecastResp.body() != null) {
                    forecastCache[forecastResp.body()!!.city?.name ?: "Unknown"] = forecastResp.body()!!
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error fetching weather", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun setupSearchCity() {
        val searchView = binding.searchView as SearchView
        searchView.post {
            try {
                val searchEditText = searchView.findViewById<AutoCompleteTextView>(androidx.appcompat.R.id.search_src_text)
                searchEditText?.setTextColor(Color.BLACK)
                searchEditText?.setHintTextColor(Color.GRAY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { fetchWeatherData(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = true
        })
    }

    private fun fetchWeatherData(cityName: String) {
        ioScope.launch {
            try {
                val response = RetrofitClient.instance.getWeatherData(cityName, API_KEY, "metric").execute()
                if (response.isSuccessful && response.body() != null) {
                    val weather = response.body()!!
                    mainScope.launch {
                        weatherCache[cityName] = weather
                        updateWeatherUI(weather)
                        fetchForecastData(cityName)
                    }
                }
            } catch (e: Exception) {
                mainScope.launch {
                    Toast.makeText(this@MainActivity, "Failed to load city", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchForecastData(cityName: String) {
        if (forecastCache.containsKey(cityName)) return
        ioScope.launch {
            try {
                val forecast = RetrofitClient.instance.getFiveDayForecast(cityName, API_KEY, "metric").execute()
                if (forecast.isSuccessful && forecast.body() != null) {
                    forecastCache[cityName] = forecast.body()!!
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateWeatherUI(weatherData: WeatherApp) {
        binding.apply {
            cityName.text = "${weatherData.name}, ${weatherData.sys.country}"
            selactedDay.text = "Today"
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

            // Use the updated function for the local time
            time.text = "Time: ${timeWithOffset(weatherData.timezone)}" // Corrected local time
            day.text = dayName(System.currentTimeMillis())
            date.text = date()
        }

        weatherData.weather.firstOrNull()?.main?.let {
            changeImagesAcoordingToWeatherCondtion(it)
        }
    }


    private fun changeImagesAcoordingToWeatherCondtion(condition: String) {
        val (bg, anim) = when (condition) {
            "Clear", "Sunny" , "Clear Sky"-> Pair(R.drawable.sunny_background, R.raw.sun)
            "Clouds", "Overcast", "Mist", "Foggy","Partly Clouds" -> Pair(R.drawable.colud_background, R.raw.cloud)
            "Rain", "Drizzle", "Light Rain", "Drizzle", "Moderate Rain", "Showers", "Heavy Rain" -> Pair(R.drawable.rain_background, R.raw.rain)
            "Snow", "Light Snow", "Heavy Snow", "Moderate Snow", "Blizzard" -> Pair(R.drawable.snow_background, R.raw.snow)
            else -> Pair(R.drawable.sunny_background, R.raw.sun)
        }
        binding.root.setBackgroundResource(bg)
        binding.lottieAnimationView.setAnimation(anim)
        binding.lottieAnimationView.playAnimation()
    }

    // Function to calculate time with offset
    private fun timeWithOffset(offset: Int): String {
        // Calculate the time in milliseconds for the specific city
        val currentTimeMillis = System.currentTimeMillis() // Current time in UTC
        val localTimeMillis = currentTimeMillis + (offset * 1000L) // Adjust with offset (in seconds)

        // Format the local time
        val localTime = Date(localTimeMillis) // Convert to Date object
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()) // Time in HH:mm format
        timeFormat.timeZone = TimeZone.getTimeZone("GMT") // Ensure time zone is set to GMT for offset calculation
        return timeFormat.format(localTime) // Return formatted time
    }

    private fun time(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts * 1000))
    private fun date(): String = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    private fun dayName(ts: Long): String = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ts))

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainScope.cancel()
    }

    companion object {
        const val API_KEY = "d251152482d334b07e33525ea4adcd6e"
    }
}
