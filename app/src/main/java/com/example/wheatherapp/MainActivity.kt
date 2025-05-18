package com.example.wheatherapp

import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.appcompat.widget.SearchView
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wheatherapp.databinding.ActivityMainBinding
import com.example.wheatherapp.databinding.DialogHourlyDetailsBinding
import com.example.wheatherapp.databinding.ItemHourlyDetailBinding
import com.github.mikephil.charting.charts.LineChart
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var currentCity: String = ""

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
            setupHourlyForecast()

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

    private fun setupHourlyForecast() {
        binding.hourlyHeader.setOnClickListener {
            toggleHourlyForecast()
        }

        binding.hourlyMore.setOnClickListener {
            showDetailedHourlyForecast(0) // Show today's data by default
        }

        binding.viewHourlyDetails.setOnClickListener {
            showDetailedHourlyForecast(0) // Show today's data by default
        }
    }

    private fun toggleHourlyForecast() {
        if (binding.hourlyForecastSection.visibility == View.VISIBLE) {
            binding.hourlyForecastSection.visibility = View.GONE
            binding.hourlyTitle.text = "Hourly forecast ▼"
        } else {
            binding.hourlyForecastSection.visibility = View.VISIBLE
            binding.hourlyTitle.text = "Hourly forecast ▲"
            loadHourlyData()
        }
    }

    private fun loadHourlyData() {
        forecastCache[currentCity]?.let { weatherApp ->
            val hourlyData = weatherApp.forecast.forecastday[0].hour
            setupTemperatureChart(hourlyData)
        }
    }

    private fun setupTemperatureChart(hourlyData: List<Hour>) {
        val entries = mutableListOf<Entry>()
        val xLabels = mutableListOf<String>()

        hourlyData.forEachIndexed { index, hour ->
            entries.add(Entry(index.toFloat(), hour.temp_c.toFloat()))
            xLabels.add(formatHour(hour.time))
        }

        val dataSet = LineDataSet(entries, "Temperature (°C)").apply {
            color = Color.BLACK
            lineWidth = 2f
            setCircleColor(Color.BLACK)
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = Color.BLACK
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillColor = Color.parseColor("#80D8FF")
            setDrawFilled(true)
            fillAlpha = 80
        }

        with(binding.hourlyChart) {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.BLACK
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return xLabels.getOrNull(value.toInt()) ?: ""
                    }
                }
                granularity = 1f
                labelCount = 6
                axisLineColor = Color.BLACK
            }

            axisLeft.apply {
                textColor = Color.BLACK
                setDrawGridLines(false)
                axisMinimum = entries.minOf { it.y } - 2
                axisMaximum = entries.maxOf { it.y } + 2
                axisLineColor = Color.BLACK
            }
            axisRight.isEnabled = false
            animateX(1000)
            invalidate()
        }
    }

    private var hourlyDialogBinding: DialogHourlyDetailsBinding? = null

    private fun showDetailedHourlyForecast(dayIndex: Int = 0) {
        val hourlyData = forecastCache[currentCity]?.forecast?.forecastday?.get(dayIndex)?.hour ?: return

        hourlyDialogBinding = DialogHourlyDetailsBinding.inflate(layoutInflater)
        hourlyForecastDialog = BottomSheetDialog(this).apply {
            setContentView(hourlyDialogBinding!!.root)

            // Setup close button
            hourlyDialogBinding!!.closeButton.setOnClickListener { dismiss() }
            hourlyDialogBinding!!.backMainBtn.setOnClickListener { dismiss() }

            // Setup RecyclerView
            hourlyDialogBinding!!.hourlyRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = HourlyAdapter(hourlyData)
                setHasFixedSize(true)
            }

            // Setup chart
            setupDetailedChart(hourlyDialogBinding!!.detailedChart, hourlyData)

            setOnDismissListener {
                hourlyForecastDialog = null
                hourlyDialogBinding = null
            }
        }
        hourlyForecastDialog?.show()
    }

    private fun setupDetailedChart(chart: LineChart, hourlyData: List<Hour>) {
        val entries = mutableListOf<Entry>()
        val xLabels = mutableListOf<String>()

        hourlyData.forEachIndexed { index, hour ->
            entries.add(Entry(index.toFloat(), hour.temp_c.toFloat()))
            xLabels.add(formatHour(hour.time))
        }

        val dataSet = LineDataSet(entries, "Temperature (°C)").apply {
            color = Color.BLACK
            lineWidth = 3f
            setCircleColor(Color.BLACK)
            circleRadius = 5f
            setDrawCircleHole(false)
            valueTextSize = 12f
            valueTextColor = Color.BLACK
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillColor = Color.parseColor("#80D8FF")
            setDrawFilled(true)
            fillAlpha = 100
        }

        with(chart) {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)

            // X-axis customization
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.BLACK
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return xLabels.getOrNull(value.toInt()) ?: ""
                    }
                }
                granularity = 1f
                labelCount = 24
                axisLineColor = Color.BLACK
            }

            // Y-axis customization
            axisLeft.apply {
                textColor = Color.BLACK
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                axisMinimum = entries.minOf { it.y } - 2
                axisMaximum = entries.maxOf { it.y } + 2
                axisLineColor = Color.BLACK
            }
            axisRight.isEnabled = false

            // Extra customization
            setDrawBorders(true)
            animateX(1500)
            invalidate()
        }
    }

    private inner class HourlyAdapter(private val hourlyData: List<Hour>) :
        RecyclerView.Adapter<HourlyAdapter.HourlyViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourlyViewHolder {
            val binding = ItemHourlyDetailBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return HourlyViewHolder(binding)
        }

        override fun onBindViewHolder(holder: HourlyViewHolder, position: Int) {
            holder.bind(hourlyData[position])
        }

        override fun getItemCount() = hourlyData.size

        inner class HourlyViewHolder(private val binding: ItemHourlyDetailBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(hour: Hour) {
                binding.timeText.text = formatHour(hour.time)
                binding.tempText.text = "${hour.temp_c.toInt()}°C"
                binding.conditionText.text = hour.condition.text
                binding.humidityText.text = "${hour.humidity}%"
                binding.windText.text = "${hour.wind_kph.toInt()} km/h"

                // Set weather icon based on condition
                val iconRes = when {
                    hour.condition.text.contains("rain", true) -> R.raw.rain
                    hour.condition.text.contains("shower", true) -> R.raw.rain
                    hour.condition.text.contains("drizzle", true) -> R.raw.rain
                    hour.condition.text.contains("overcast", true) -> R.raw.cloud
                    hour.condition.text.contains("mist", true) -> R.raw.cloud
                    hour.condition.text.contains("fog", true) -> R.raw.cloud
                    hour.condition.text.contains("cloud", true) -> R.raw.cloud
                    else -> R.raw.sun
                }
                binding.weatherIcon.setImageResource(iconRes)
            }
        }
    }

    private fun formatHour(timeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h a", Locale.getDefault())
            val date = inputFormat.parse(timeString)
            outputFormat.format(date)
        } catch (e: Exception) {
            timeString.takeLast(5)
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
                if (currentCity.isNotEmpty()) {
                    forecastCache[currentCity]?.let { forecast ->
                        if (i == 0) {
                            // For cardDay1, show current time weather
                            showCurrentTimeWeather(forecast)
                            loadHourlyDataForDay(0)
                        } else {
                            // For other cards, show daily forecast
                            updateForecastForDate(forecast, forecastDates[i])
                            loadHourlyDataForDay(i)
                        }
                    } ?: run {
                        fetchForecastData(currentCity.split(",")[0].trim())
                    }
                }
            }
        }
    }

    private fun loadHourlyDataForDay(dayIndex: Int) {
        forecastCache[currentCity]?.let { weatherApp ->
            if (weatherApp.forecast.forecastday.size > dayIndex) {
                val hourlyData = weatherApp.forecast.forecastday[dayIndex].hour
                setupTemperatureChart(hourlyData)
                if (hourlyForecastDialog?.isShowing == true) {
                    updateDetailedForecastDialog(hourlyData)
                }
            }
        }
    }

    private var hourlyForecastDialog: BottomSheetDialog? = null

    private fun updateDetailedForecastDialog(hourlyData: List<Hour>) {
        hourlyDialogBinding?.let { binding ->
            binding.hourlyRecyclerView.adapter = HourlyAdapter(hourlyData)
            setupDetailedChart(binding.detailedChart, hourlyData)
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
            // Update main weather display
            temp.text = "${forecastDay.day.avgtemp_c.roundToInt()}°C"
            weather.text = forecastDay.day.condition.text
            maxTemp.text = "Max: ${forecastDay.day.maxtemp_c.roundToInt()}°C"
            minTemp.text = "Min: ${forecastDay.day.mintemp_c.roundToInt()}°C"
            humidity.text = "${forecastDay.day.avghumidity}%"
            windSpeed.text = "${(forecastDay.day.maxwind_kph / 3.6).format(1)} m/s"
            sunrise.text = forecastDay.astro.sunrise
            sunset.text = forecastDay.astro.sunset
            condition.text = forecastDay.day.condition.text

            // Update date information
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            selactedDay.text = if (todayStr == selectedDate) "Today" else
                SimpleDateFormat("EEEE", Locale.getDefault()).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)!!
                )

            day.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecastDay.date)!!
            )
            date.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecastDay.date)!!
            )

            // Update weather images
            changeImagesAccordingToWeatherCondition(forecastDay.day.condition.text)
        }
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
                    currentCity = "${weather.location.name}, ${weather.location.country}"
                    weatherCache[currentCity] = weather

                    // Fetch forecast data
                    val forecastResponse = RetrofitClient.instance.getFiveDayForecastByCoordinates(
                        "$lat,$lon",
                        API_KEY
                    ).execute()

                    if (forecastResponse.isSuccessful && forecastResponse.body() != null) {
                        val forecast = forecastResponse.body()!!
                        forecastCache[currentCity] = forecast

                        withContext(Dispatchers.Main) {
                            binding.cityName.text = currentCity
                            showCurrentTimeWeather(forecast)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupSearchCity() {
        val searchView = binding.searchView
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
                val weatherResponse = response.execute()

                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentCity = "${weather.location.name}, ${weather.location.country}"
                    weatherCache[currentCity] = weather

                    // Fetch forecast data
                    val forecastResponse = RetrofitClient.instance.getFiveDayForecast(
                        cityName,
                        API_KEY,
                        5
                    ).execute()

                    if (forecastResponse.isSuccessful && forecastResponse.body() != null) {
                        val forecast = forecastResponse.body()!!
                        forecastCache[currentCity] = forecast

                        withContext(Dispatchers.Main) {
                            //updateCityName(weather.location.name, weather.location.country)
                            //updateForecastForDate(forecast, forecastDates[0])
                            binding.cityName.text = currentCity
                            showCurrentTimeWeather(forecast)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error fetching data for $cityName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showCurrentTimeWeather(forecast: WeatherApp) {
        try {
            // Get current hour in 24-hour format (e.g., "14" for 2 PM)
            val currentHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date())

            // Find the hour data that matches current time
            val currentDay = forecast.forecast.forecastday[0]
            val currentHourData = currentDay.hour.find {
                it.time.startsWith("${currentDay.date} $currentHour")
            } ?: currentDay.hour.first()

            // Update UI with current hour data
            binding.apply {
                temp.text = "${currentHourData.temp_c.roundToInt()}°C"
                weather.text = currentHourData.condition.text
                maxTemp.text = "Max: ${currentDay.day.maxtemp_c.roundToInt()}°C"
                minTemp.text = "Min: ${currentDay.day.mintemp_c.roundToInt()}°C"
                humidity.text = "${currentHourData.humidity}%"
                windSpeed.text = "%.1f m/s".format(currentHourData.wind_kph / 3.6)
                sunrise.text = currentDay.astro.sunrise
                sunset.text = currentDay.astro.sunset
                sea.text = "${currentHourData.pressure_mb} hPa"
                condition.text = currentHourData.condition.text
                time.text = "Time: ${timeWithOffset(forecast.location.tz_id)}"
                day.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                date.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
            }

            changeImagesAccordingToWeatherCondition(currentHourData.condition.text)
        } catch (e: Exception) {
            Log.e("CurrentTimeWeather", "Error showing current time weather", e)
            // Fallback to showing day average if hourly data fails
            updateForecastForDate(forecast, forecastDates[0])
        }
    }


        private fun fetchForecastData(cityName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getFiveDayForecast(cityName, API_KEY, 5)
                val forecastResponse = response.execute()

                if (forecastResponse.isSuccessful && forecastResponse.body() != null) {
                    val forecast = forecastResponse.body()!!
                    forecastCache[cityName] = forecast

                    withContext(Dispatchers.Main) {
                        updateForecastForDate(forecast, forecastDates[0])
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error fetching forecast",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}