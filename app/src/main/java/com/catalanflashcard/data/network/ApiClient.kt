package com.catalanflashcard.data.network

import com.catalanflashcard.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private val BASE_URL = BuildConfig.SYNC_BASE_URL

    private val httpClient = OkHttpClient.Builder()
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

    val syncApi: SyncApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SyncApi::class.java)
}
