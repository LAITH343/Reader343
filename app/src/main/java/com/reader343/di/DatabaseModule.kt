package com.reader343.di

import android.content.Context
import androidx.room.Room
import com.reader343.data.db.ReaderDatabase
import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.BookmarkDao
import com.reader343.data.db.dao.HighlightDao
import com.reader343.data.db.dao.NoteDao
import com.reader343.data.db.dao.OutlineDao
import com.reader343.data.db.dao.ProgressDao
import com.reader343.data.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReaderDatabase =
        Room.databaseBuilder(context, ReaderDatabase::class.java, ReaderDatabase.NAME).build()

    @Provides
    fun provideBookDao(db: ReaderDatabase): BookDao = db.bookDao()

    @Provides
    fun provideProgressDao(db: ReaderDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideHighlightDao(db: ReaderDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideNoteDao(db: ReaderDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideSessionDao(db: ReaderDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideBookmarkDao(db: ReaderDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideOutlineDao(db: ReaderDatabase): OutlineDao = db.outlineDao()
}
