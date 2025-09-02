package com.ssafy.jangan_mobile.service


import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val BASE_URL = "http://152.42.220.24/"

    val api: MapService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MapService::class.java)
    }
    val fcmApi: FcmService by lazy{
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .build()
            .create(FcmService::class.java)
    }
}
