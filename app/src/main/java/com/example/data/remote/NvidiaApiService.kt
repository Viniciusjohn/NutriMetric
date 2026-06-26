package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface NvidiaApiService {
    @POST
    suspend fun analisarPrato(
        @Url url: String,
        @Body request: OpenAiChatRequest
    ): Response<ResponseBody>
}
