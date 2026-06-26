package com.example.data.remote

import com.example.BuildConfig
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()
        
        val builder = originalRequest.newBuilder()
        
        when {
            url.contains("generativelanguage.googleapis.com") -> {
                builder.header("Authorization", "Bearer ${BuildConfig.GEMINI_API_KEY}")
            }
            url.contains("integrate.api.nvidia.com") -> {
                val apiKey = if (BuildConfig.NVIDIA_API_KEY.isNotEmpty() && BuildConfig.NVIDIA_API_KEY != "MY_NVIDIA_API_KEY") {
                    BuildConfig.NVIDIA_API_KEY
                } else {
                    BuildConfig.NV_API_KEY
                }
                builder.header("Authorization", "Bearer $apiKey")
            }
        }
        
        chain.proceed(builder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: NvidiaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://placeholder.com/") // Base URL fake pois usaremos @Url dinâmica
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NvidiaApiService::class.java)
    }

    val openFoodFactsApi: OpenFoodFactsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
}
