package com.example.finalproject.api

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.GET
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class AuthResponse(
    val message: String,
    val id: Long,
    val username: String,
    val email: String
)

data class ErrorResponse(
    val error: String
)

data class RoomResponse(
    val inviteCode: String,
    val message: String? = null
)

data class JoinRoomResponse(
    val message: String? = null,
    val roomId: Long? = null
)

data class RoomCreationRequest(
    val email: String
)

data class SuggestionRequest(
    val suggestion: String
)

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>
    
    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<User>
    
    @GET("api/auth/me")
    suspend fun getCurrentUserByEmail(@Query("email") email: String): Response<User>
    
    @POST("api/rooms/create")
    suspend fun createRoom(@Body creator: RoomCreationRequest): Response<RoomResponse>

    @POST("api/rooms/join")
    suspend fun joinRoom(@Query("inviteCode") inviteCode: String, @Body user: User): Response<JoinRoomResponse>
    
    @POST("api/rooms/{inviteCode}/suggest")
    suspend fun sendRestaurantSuggestion(
        @retrofit2.http.Path("inviteCode") inviteCode: String,
        @retrofit2.http.Body suggestion: SuggestionRequest
    ): Response<Void>
    
    companion object {
        private const val BASE_URL = "http://10.0.2.2:8080/"
        
        fun create(): ApiService {
            val logger = HttpLoggingInterceptor()
            logger.level = HttpLoggingInterceptor.Level.BODY
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ApiService::class.java)
        }
    }
} 