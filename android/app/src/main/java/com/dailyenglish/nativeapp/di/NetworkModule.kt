package com.dailyenglish.nativeapp.di

import com.dailyenglish.nativeapp.BuildConfig
import com.dailyenglish.nativeapp.data.local.SessionStore
import com.dailyenglish.nativeapp.data.remote.ApiService
import com.dailyenglish.nativeapp.data.remote.TokenInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
  @Provides
  @Singleton
  fun provideOkHttp(session: SessionStore): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(TokenInterceptor(session))
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

  @Provides
  @Singleton
  fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

  @Provides
  @Singleton
  fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
