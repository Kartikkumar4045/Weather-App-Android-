package com.example.wheatherapp

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {
    @GET("weather")
    fun getWeatherData(
        @Query("q") city: String,
        @Query("appid") appid: String,
        @Query("units") units: String,
        @Query("lang") lang: String = "en"  // Added language parameter
    ): Call<WeatherApp>

    @GET("weather")
    fun getWeatherByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appid: String,
        @Query("units") units: String,
        @Query("lang") lang: String = "en"  // Added language parameter
    ): Call<WeatherApp>

    @GET("forecast")
    fun getFiveDayForecast(
        @Query("q") city: String,
        @Query("appid") appid: String,
        @Query("units") units: String,
        @Query("cnt") cnt: Int = 40,       // Added count parameter (5 days * 8 forecasts/day)
        @Query("lang") lang: String = "en"  // Added language parameter
    ): Call<ForecastResponse>

    @GET("forecast")
    fun getFiveDayForecastByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appid: String,
        @Query("units") units: String,
        @Query("cnt") cnt: Int = 40,       // Added count parameter
        @Query("lang") lang: String = "en"  // Added language parameter
    ): Call<ForecastResponse>

}