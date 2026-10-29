package com.zunelo.pdfreader.telemetry.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import com.zunelo.pdfreader.BuildConfig
import com.zunelo.pdfreader.common.AppLog
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object OkHttpManager {
    private const val MAX_RETRIES = 5
    private const val RETRY_INTERVAL_MILLIS = 5_000L
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val service: JsonService by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .build()
            .create(JsonService::class.java)
    }

    suspend fun postJson(
        url: String,
        returnBodyInRelease: Boolean = false,
        bodyProvider: () -> String
    ): String? {
        repeat(MAX_RETRIES + 1) { attempt ->
            val response = try {
                service.post(url, bodyProvider().toRequestBody(jsonMediaType))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (response?.isSuccessful == true) {
                val body = try {
                    response.body()?.string().orEmpty()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (body != null) {
                    val returnResponseBody = BuildConfig.DEBUG || returnBodyInRelease
                    if (returnResponseBody) AppLog.debug { "postJson response: $body" }
                    return if (returnResponseBody) body else ""
                }
            }
            response?.errorBody()?.close()
            if (attempt < MAX_RETRIES) delay(RETRY_INTERVAL_MILLIS)
        }
        return null
    }

    private interface JsonService {
        @POST
        suspend fun post(
            @Url url: String,
            @Body body: okhttp3.RequestBody
        ): Response<okhttp3.ResponseBody>
    }
}
