import com.example.wheatherapp.WeatherApp
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {

    @GET("forecast.json")
    fun getForecast(
        @Query("key") apiKey: String,
        @Query("q") cityName: String,
        @Query("days") days: Int = 3,
        @Query("aqi") aqi: String = "no",
        @Query("alerts") alerts: String = "no"
    ): Call<WeatherApp>

    @GET("current.json")
    fun getWeatherData(
        @Query("q") cityName: String,
        @Query("key") apiKey: String,
        @Query("aqi") aqi: String = "no"
    ): Call<WeatherApp>

    @GET("forecast.json")
    fun getFiveDayForecast(
        @Query("q") cityName: String,
        @Query("key") apiKey: String,
        @Query("days") days: Int = 5,
        @Query("aqi") aqi: String = "no"
    ): Call<WeatherApp>  // Note: changed from ForecastResponse to WeatherApp

    @GET("current.json")
    fun getWeatherByCoordinates(
        @Query("q") latLon: String,   // format: "lat,lon"
        @Query("key") apiKey: String,
        @Query("aqi") aqi: String = "no"
    ): Call<WeatherApp>

    @GET("forecast.json")
    fun getFiveDayForecastByCoordinates(
        @Query("q") latLon: String,
        @Query("key") apiKey: String,
        @Query("days") days: Int = 5,
        @Query("aqi") aqi: String = "no"
    ): Call<WeatherApp>  // Note: changed from ForecastResponse to WeatherApp
}
