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
import com.example.wheatherapp.databinding.ActivityMainBinding
import com.google.gson.Gson
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 100

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation()  // Fetch current location
        SearchCity()  // Initialize search functionality
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
                val lat = it.latitude
                val lon = it.longitude
                fetchWeatherByCoordinates(lat, lon)  // Fetch weather based on current location
            } ?: run {
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .build()
            .create(ApiInterface::class.java)

        val call = retrofit.getWeatherByCoordinates(lat, lon, "d251152482d334b07e33525ea4adcd6e", "metric")

        call.enqueue(object : Callback<WeatherApp> {
            override fun onResponse(call: Call<WeatherApp>, response: Response<WeatherApp>) {
                if (response.isSuccessful && response.body() != null) {
                    updateWeatherUI(response.body()!!)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to fetch location weather", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<WeatherApp>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Failed to fetch location weather", Toast.LENGTH_SHORT).show()
            }
        })
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }



    private fun SearchCity() {
        val searchView = binding.searchView as SearchView

        searchView.post {
            try {
                val searchEditText = searchView.findViewById<AutoCompleteTextView>(
                    androidx.appcompat.R.id.search_src_text
                )
                searchEditText?.setTextColor(Color.BLACK)
                searchEditText?.setHintTextColor(Color.GRAY) // 👈 Ensures visible hint
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fix for setting SearchView text color
        searchView.post {
            try {
                val searchEditText = searchView.findViewById<AutoCompleteTextView>(
                    androidx.appcompat.R.id.search_src_text
                )
                searchEditText?.setTextColor(Color.BLACK)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null) {
                    fetchWeatherData(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })
    }

    private fun updateWeatherUI(weatherData: WeatherApp) {
        val temperature = weatherData.main.temp.roundToInt()
        val humidity = weatherData.main.humidity
        val windSpeed = weatherData.wind.speed
        val sunRise = weatherData.sys.sunrise.toLong()
        val sunSet = weatherData.sys.sunset.toLong()
        val seaLevel = weatherData.main.pressure
        val condition = weatherData.weather.firstOrNull()?.main ?: "Unknown"
        val maxTemp = weatherData.main.temp_max.roundToInt()
        val minTemp = weatherData.main.temp_min.roundToInt()
        val cityName = weatherData.name
        val countryCode = weatherData.sys.country
        val countryName = Locale("", countryCode).displayCountry
        val offsetSeconds = weatherData.timezone


        binding.time.text = "Time: ${timeWithOffset(offsetSeconds)}"
        binding.cityName.text = "$cityName, $countryName"
        binding.temp.text = "$temperature°C"
        binding.weather.text = condition
        binding.maxTemp.text = "Max Temp: $maxTemp°C"
        binding.minTemp.text = "Min Temp: $minTemp°C"
        binding.humidity.text = "$humidity%"
        binding.windSpeed.text = "${"%.1f".format(windSpeed)} m/s"
        binding.sunrise.text = time(sunRise)
        binding.sunset.text = time(sunSet)
        binding.sea.text = "$seaLevel hPa"
        binding.condition.text = condition
        binding.day.text = dayName(System.currentTimeMillis())
        binding.date.text = date()

        changeImagesAcoordingToWeatherCondtion(condition)
    }

    private fun timeWithOffset(offsetSeconds: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.SECOND, offsetSeconds)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = calendar.timeZone
        return sdf.format(calendar.time)
    }




    private fun fetchWeatherData(cityName: String) {
        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .build()
            .create(ApiInterface::class.java)

        val response = retrofit.getWeatherData(cityName, "d251152482d334b07e33525ea4adcd6e", "metric")

        response.enqueue(object : Callback<WeatherApp> {
            override fun onResponse(call: Call<WeatherApp>, response: Response<WeatherApp>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null) {
                    updateWeatherUI(responseBody)
                } else {
                    Toast.makeText(this@MainActivity, "Invalid city name", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<WeatherApp>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Invalid city name", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun changeImagesAcoordingToWeatherCondtion(conditions: String) {
        when (conditions) {
            "Clear Sky", "Sunny", "Clear","Sun" -> {
                binding.root.setBackgroundResource(R.drawable.sunny_background)
                binding.lottieAnimationView.setAnimation(R.raw.sun)
            }
            "Partly Cloudy", "Clouds", "Overcast", "Mist", "Foggy", "Cloud" -> {
                binding.root.setBackgroundResource(R.drawable.colud_background)
                binding.lottieAnimationView.setAnimation(R.raw.cloud)
            }
            "Light Rain", "Drizzle", "Moderate Rain", "Showers", "Heavy Rain", "Rain" -> {
                binding.root.setBackgroundResource(R.drawable.rain_background)
                binding.lottieAnimationView.setAnimation(R.raw.rain)
            }
            "Light Snow", "Moderate Snow", "Heavy Snow", "Blizzard", "Snow" -> {
                binding.root.setBackgroundResource(R.drawable.snow_background)
                binding.lottieAnimationView.setAnimation(R.raw.snow)
            }
            else -> {
                binding.root.setBackgroundResource(R.drawable.sunny_background)
                binding.lottieAnimationView.setAnimation(R.raw.sun)
            }
        }
        binding.lottieAnimationView.playAnimation()
    }


    private fun time(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp * 1000))
    }

    private fun date(): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun dayName(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        return sdf.format(Date())
    }
}
