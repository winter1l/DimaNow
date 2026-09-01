package com.example.dimanow.lms

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LmsCacheMigrationTest {
    @Test
    fun versionThreeItemsMigrateWithoutBeingMarkedNewOrComplete() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "lms-migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE lms_courses (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, professor TEXT, classNo TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE lms_items (`key` TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, courseId TEXT NOT NULL, courseName TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, registeredAtMillis INTEGER, dueAtMillis INTEGER, detailUrl TEXT NOT NULL, isRead INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE lms_details (itemKey TEXT NOT NULL PRIMARY KEY, sanitizedHtml TEXT NOT NULL, fetchedAtMillis INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE lms_attachments (`key` TEXT NOT NULL PRIMARY KEY, itemKey TEXT NOT NULL, sourceId TEXT NOT NULL, fileName TEXT NOT NULL, downloadUrl TEXT NOT NULL, sizeBytes INTEGER)")
                        db.execSQL("CREATE TABLE lms_sync (id INTEGER NOT NULL PRIMARY KEY, status TEXT NOT NULL, lastSuccessAtMillis INTEGER, errorMessage TEXT)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO lms_items (`key`,sourceId,courseId,courseName,kind,title,registeredAtMillis,dueAtMillis,detailUrl,isRead) " +
                "VALUES ('NOTICE:course:91','91','course','과목','NOTICE','기존 공지',NULL,NULL,'https://lms.dima.ac.kr/item/91',1)",
        )
        helper.close()

        val database = Room.databaseBuilder(context, LmsCacheDatabase::class.java, name)
            .addMigrations(LMS_CACHE_MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val item = database.dao().getAllItems().single()

        assertEquals(LmsCompletionState.UNKNOWN.name, item.completionState)
        assertEquals(LmsChangeState.NONE.name, item.changeState)
        assertEquals(true, item.isRead)

        database.close()
        context.deleteDatabase(name)
    }
}
