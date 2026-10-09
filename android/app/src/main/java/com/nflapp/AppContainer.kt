package com.nflapp

import android.content.Context
import com.nflapp.data.NflRepository
import com.nflapp.data.SettingsRepository
import com.nflapp.data.local.NflDatabase
import com.nflapp.data.remote.NflApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Manual dependency injection: one instance per process, owned by [NflApp]. */
class AppContainer(context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // No OkHttp Cache on purpose: the ETag is stored in Room next to the data it
    // belongs to, so a 304 always means "what is in the database is current".
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: NflApi = Retrofit.Builder()
        .baseUrl(BuildConfig.DATA_BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NflApi::class.java)

    val database = NflDatabase.create(context)

    val repository = NflRepository(api, database, json, BuildConfig.PREDICTIONS_URL, BuildConfig.STATS_URL)

    val settings = SettingsRepository(context)
}
