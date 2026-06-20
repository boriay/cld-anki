package com.catalanflashcard.data.network

import com.catalanflashcard.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val BASE_URL = BuildConfig.SYNC_BASE_URL

    private val httpClient = OkHttpClient.Builder()
        // OkHttp defaults to 10s read timeout, which is too tight for a sync
        // that hits a cold Cloud SQL f1-micro after a long idle (first request
        // pays for connection cold-start). Read is kept slightly above the
        // server's 30s chi timeout so the server returns its own timeout
        // response first instead of the client tearing the connection down.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        // Symmetric with read: a first full-sync batch on a slow mobile link
        // can take a while to upload. callTimeout(45s) is still the hard cap.
        .writeTimeout(35, TimeUnit.SECONDS)
        // Hard upper bound on the whole call (incl. connect/redirects/retries).
        .callTimeout(45, TimeUnit.SECONDS)
        .apply {
            // Network logging only in debug builds — avoids leaking endpoints
            // and request metadata into release logs.
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val syncApi: SyncApi = retrofit.create(SyncApi::class.java)

    val weatherApi: WeatherApi = retrofit.create(WeatherApi::class.java)
}
