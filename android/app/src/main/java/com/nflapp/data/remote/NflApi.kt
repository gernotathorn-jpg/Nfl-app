package com.nflapp.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Only talks to our own GitHub Pages files. A null [etag] omits the header;
 * a matching one yields HTTP 304 with an empty body.
 */
interface NflApi {
    @GET
    suspend fun predictions(
        @Url url: String,
        @Header("If-None-Match") etag: String?,
    ): Response<PredictionsDto>

    @GET
    suspend fun stats(
        @Url url: String,
        @Header("If-None-Match") etag: String?,
    ): Response<StatsDto>
}
