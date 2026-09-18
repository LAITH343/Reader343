package com.reader343.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.BookmarkDao
import com.reader343.data.db.dao.HighlightDao
import com.reader343.data.db.dao.NoteDao
import com.reader343.data.db.dao.OutlineDao
import com.reader343.data.db.dao.ProgressDao
import com.reader343.data.db.dao.SessionDao
import com.reader343.data.db.entity.BookEntity
import com.reader343.data.db.entity.BookmarkEntity
import com.reader343.data.db.entity.HighlightEntity
import com.reader343.data.db.entity.NoteEntity
import com.reader343.data.db.entity.OutlineEntryEntity
import com.reader343.data.db.entity.ProgressEntity
import com.reader343.data.db.entity.SessionEntity

@Database(
    entities = [
        BookEntity::class,
        ProgressEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        SessionEntity::class,
        BookmarkEntity::class,
        OutlineEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun sessionDao(): SessionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun outlineDao(): OutlineDao

    companion object {
        const val NAME = "reader343.db"
    }
}
