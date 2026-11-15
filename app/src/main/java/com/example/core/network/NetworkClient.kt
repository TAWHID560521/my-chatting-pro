package com.example.core.network

import com.example.core.local.PreferencesManager
import com.example.core.model.ApiErrorResponse
import com.example.core.model.RefreshTokenRequest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class DynamicBaseUrlInterceptor(
    private val preferencesManager: PreferencesManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuredUrl = runBlocking { preferencesManager.currentApiBaseUrl() }.toHttpUrlOrNull()
        if (configuredUrl == null) {
            return chain.proceed(original)
        }
        val newUrl = original.url.newBuilder()
            .scheme(configuredUrl.scheme)
            .host(configuredUrl.host)
            .port(configuredUrl.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}

class AuthInterceptor(
    private val preferencesManager: PreferencesManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isPublicAuthEndpoint = path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/recovery-info")

        if (isPublicAuthEndpoint) {
            return chain.proceed(request)
        }

        val token = runBlocking { preferencesManager.currentSession().accessToken }
        if (token.isBlank()) {
            return chain.proceed(request)
        }

        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}

class TokenAuthenticator(
    private val preferencesManager: PreferencesManager,
    private val refreshApiProvider: () -> ApiService
) : Authenticator {
    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) return null

        return runBlocking {
            refreshMutex.withLock {
                val session = preferencesManager.currentSession()
                if (session.refreshToken.isBlank()) return@withLock null

                val requestAuth = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
                if (requestAuth != null && requestAuth != session.accessToken && session.accessToken.isNotBlank()) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer ${session.accessToken}")
                        .build()
                }

                val refreshCall = try {
                    refreshApiProvider().refreshToken(RefreshTokenRequest(session.refreshToken))
                } catch (_: Exception) {
                    null
                }

                if (refreshCall != null && refreshCall.isSuccessful && refreshCall.body() != null) {
                    val body = refreshCall.body()!!
                    preferencesManager.updateTokens(body.accessToken, body.refreshToken)
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer ${body.accessToken}")
                        .build()
                } else {
                    if (refreshCall != null && (refreshCall.code() == 401 || refreshCall.code() == 403)) {
                        preferencesManager.clearSession()
                    }
                    return@withLock null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

class NetworkClient(private val preferencesManager: PreferencesManager) {

    val moshi: Moshi = Moshi.Builder().build()

    private val baseOkHttpBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(DynamicBaseUrlInterceptor(preferencesManager))

    private val refreshOkHttpClient: OkHttpClient = baseOkHttpBuilder.build()

    private val refreshRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(refreshOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val refreshApiService: ApiService = refreshRetrofit.create(ApiService::class.java)

    val authenticatedOkHttpClient: OkHttpClient = baseOkHttpBuilder
        .addInterceptor(AuthInterceptor(preferencesManager))
        .authenticator(TokenAuthenticator(preferencesManager) { refreshApiService })
        .build()

    val mediaTransferOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(authenticatedOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ApiService::class.java)

    fun parseError(rawBody: String?): ApiErrorResponse? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            moshi.adapter(ApiErrorResponse::class.java).fromJson(rawBody)
        } catch (_: Exception) {
            null
        }
    }
}
