package com.example.finalproject.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Json

data class ChatGptRequest(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "type") val type: String = "restaurant_suggestion"
)

data class ChatGptResponse(
    @Json(name = "suggestions") val suggestions: List<String>? = null,
    @Json(name = "error") val error: String? = null
)

interface ChatGptService {
    @POST("api/chatgpt/suggest")
    suspend fun getRestaurantSuggestions(@Body request: ChatGptRequest): Response<ChatGptResponse>
    
    companion object {
        private const val BASE_URL = "http://10.0.2.2:8080/"
        private const val USE_MOCK = false

        fun create(): ChatGptService {
            if (USE_MOCK) {
                return object : ChatGptService {
                    override suspend fun getRestaurantSuggestions(request: ChatGptRequest): Response<ChatGptResponse> {
                        kotlinx.coroutines.delay(1500)
                        
                        val suggestions = listOf(
                            "McDonald's",
                            "Burger King",
                            "Chipotle",
                            "Olive Garden",
                            "The Cheesecake Factory", 
                            "Outback Steakhouse"
                        ).shuffled().take(4)
                        
                        return Response.success(ChatGptResponse(suggestions = suggestions, error = null))
                    }
                }
            }
            
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
                .create(ChatGptService::class.java)
        }
    }
}

class ChatGptHelper(private val service: ChatGptService = ChatGptService.create()) {
    suspend fun getRestaurantSuggestions(prompt: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = ChatGptRequest(
                prompt = prompt,
                type = "restaurant_suggestion"
            )
            
            val response = service.getRestaurantSuggestions(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                
                if (body?.error != null) {
                    return@withContext Result.failure(Exception(body.error))
                }
                
                val suggestions = body?.suggestions ?: emptyList()
                Result.success(suggestions)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Failed to get suggestions: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 