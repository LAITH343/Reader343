package com.reader343.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReaderDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsAllData() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, sourceUri, filePath, pageCount, coverPath, addedAt) " +
                    "VALUES (1, 'Crafting Interpreters', 'content://x', '/books/a.pdf', 640, '/covers/a.png', 100)",
            )
            db.execSQL("INSERT INTO progress VALUES (1, 485, 0.5, 0.76, 200, NULL)")
            db.execSQL("INSERT INTO highlights VALUES (1, 1, 10, '[]', -1, 0, 5, 'quote', 300)")
            db.execSQL("INSERT INTO notes VALUES (1, 1, 10, '{}', 'a note', 400)")
            db.execSQL("INSERT INTO sessions VALUES (1, 1, 1000, 2000, 3)")
            db.execSQL("INSERT INTO bookmarks VALUES (1, 1, 12, 500)")
            db.execSQL("INSERT INTO outline VALUES (1, 1, 0, 'Intro', 0, 0)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT title, pageCount, coverPath, author, isbn, metadataStatus, userEdited FROM books").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Crafting Interpreters", c.getString(0))
                assertEquals(640, c.getInt(1))
                assertEquals("/covers/a.png", c.getString(2))
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
                assertTrue(c.isNull(5))
                assertEquals(0, c.getInt(6))
            }
            db.query("SELECT lastPage, percent FROM progress WHERE bookId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(485, c.getInt(0))
                assertEquals(0.76, c.getDouble(1), 0.0001)
            }
            listOf("highlights", "notes", "sessions", "bookmarks", "outline").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(table, 1, c.getInt(0))
                }
            }
            db.query("SELECT body FROM notes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("a note", c.getString(0))
            }
        }
    }

    @Test
    fun migrate2To3AddsUnknownTextLayer() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, sourceUri, filePath, pageCount, coverPath, addedAt, author) " +
                    "VALUES (1, 'Crafting Interpreters', 'content://x', '/books/a.pdf', 640, NULL, 100, 'Nystrom')",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT title, author, hasTextLayer FROM books").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Crafting Interpreters", c.getString(0))
                assertEquals("Nystrom", c.getString(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
