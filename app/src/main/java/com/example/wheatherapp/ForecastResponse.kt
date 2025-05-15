package com.example.wheatherapp

data class ForecastResponse(
    val list: List<ForecastItem>,
    val city: ForecastCity
)

data class ForecastCity(
    val name: String,
    val country: String
)

data class ForecastItem(
    val dt: Long,
    val main: MainForecast,
    val weather: List<Weather>,
    val dt_txt: String,
    val wind: Wind1
)

data class MainForecast(
    val temp: Double,
    val temp_min: Double,
    val temp_max: Double,
    val humidity: Int,
    val pressure: Int
)

data class Wind1(
    val speed: Double
)
