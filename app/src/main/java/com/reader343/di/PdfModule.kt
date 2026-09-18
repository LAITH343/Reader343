package com.reader343.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PdfDispatcher

@Module
@InstallIn(SingletonComponent::class)
object PdfModule {

    @Provides
    @Singleton
    fun providePdfiumCore(@ApplicationContext context: Context): PdfiumCore = PdfiumCore(context)

    @Provides
    @Singleton
    @PdfDispatcher
    fun providePdfDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
}
