package com.catalanflashcard.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SyncApi {
    @POST("api/v1/sync")
    suspend fun sync(
        @Body request: SyncRequest,
        @Header("Authorization") auth: String
    ): SyncResponse
}
