package com.catalanflashcard.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Drives MIGRATION_4_5 directly against a hand-built v4 schema instead of
// MigrationTestHelper: room-testing 2.8.4 can't deserialize the exported schema
// bundles (FieldBundle serializer throws), which is unrelated to the migration
// itself. The v4 decks DDL below mirrors the exported 4.json. The post-migration
// schema is asserted column-by-column against the v5 entity.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-4-5-test"

    @After
    fun tearDown() {
        ctx.deleteDatabase(dbName)
    }

    @Test
    fun migrate4To5_dropsDescription_andPreservesRows() {
        ctx.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // decks as exported in schema 4.json (with description).
                        db.execSQL(
                            "CREATE TABLE decks (" +
                                "id TEXT NOT NULL, name TEXT NOT NULL, " +
                                "description TEXT NOT NULL DEFAULT '', " +
                                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                                "deletedAt INTEGER, PRIMARY KEY(id))"
                        )
                        db.execSQL("CREATE INDEX index_decks_updatedAt ON decks (updatedAt)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        try {
            db.execSQL(
                "INSERT INTO decks (id, name, description, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('d1', 'Deck One', 'old description', 1000, 2000, NULL)"
            )

            FlashcardDatabase.MIGRATION_4_5.migrate(db)

            // The row survives with every non-dropped column intact.
            db.query("SELECT id, name, createdAt, updatedAt, deletedAt FROM decks").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("d1", c.getString(0))
                assertEquals("Deck One", c.getString(1))
                assertEquals(1000L, c.getLong(2))
                assertEquals(2000L, c.getLong(3))
                assertTrue(c.isNull(4))
            }

            // The description column is gone; only the v5 columns remain.
            db.query("PRAGMA table_info(decks)").use { c ->
                val cols = buildList {
                    while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                assertFalse(cols.contains("description"))
                assertEquals(listOf("id", "name", "createdAt", "updatedAt", "deletedAt"), cols)
            }

            // The updatedAt index is recreated by the migration.
            db.query("PRAGMA index_list(decks)").use { c ->
                val indexes = buildList {
                    while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                assertTrue(indexes.contains("index_decks_updatedAt"))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun migrate5To6_addsLanguageAndPinned_andPreservesRows() {
        val name = "migration-5-6-test"
        ctx.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // decks as exported in schema 5.json (description dropped).
                        db.execSQL(
                            "CREATE TABLE decks (" +
                                "id TEXT NOT NULL, name TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                                "deletedAt INTEGER, PRIMARY KEY(id))"
                        )
                        db.execSQL("CREATE INDEX index_decks_updatedAt ON decks (updatedAt)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        try {
            db.execSQL(
                "INSERT INTO decks (id, name, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('d1', 'Deck One', 1000, 2000, NULL)"
            )

            FlashcardDatabase.MIGRATION_5_6.migrate(db)

            // Existing row survives; new columns default to NULL / 0.
            db.query("SELECT id, name, language, pinned FROM decks").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("d1", c.getString(0))
                assertEquals("Deck One", c.getString(1))
                assertTrue(c.isNull(2))           // language NULL -> treated as user deck
                assertEquals(0L, c.getLong(3))     // pinned default 0
            }

            db.query("PRAGMA table_info(decks)").use { c ->
                val cols = buildList {
                    while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                assertTrue(cols.contains("language"))
                assertTrue(cols.contains("pinned"))
            }
        } finally {
            helper.close()
            ctx.deleteDatabase(name)
        }
    }
}
