package com.reader343.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "author TEXT",
            "description TEXT",
            "publishedYear INTEGER",
            "publisher TEXT",
            "isbn TEXT",
            "remoteCoverUrl TEXT",
            "metadataCoverPath TEXT",
            "metadataSource TEXT",
            "metadataFetchedAt INTEGER",
            "metadataStatus TEXT",
            "userEdited INTEGER NOT NULL DEFAULT 0",
        ).forEach { column -> db.execSQL("ALTER TABLE books ADD COLUMN $column") }
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
