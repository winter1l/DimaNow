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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LmsCacheMigrationTest {
    @Test
    fun versionFiveStoresNativeMetadataAndAttachmentRequestContextForOfflineUse() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LmsCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val detail = LmsDetailEntity(
            itemKey = "ASSIGNMENT:course:5553",
            sanitizedHtml = "<p>과제 안내</p>",
            fetchedAtMillis = 10_000,
            author = "담당교수",
            registeredAtMillis = 20_000,
            submissionStartsAtMillis = 30_000,
            submissionEndsAtMillis = 40_000,
            maxScore = "100점",
        )
        val attachment = LmsAttachmentEntity(
            key = "ASSIGNMENT:course:5553:1",
            itemKey = detail.itemKey,
            sourceId = "1",
            fileName = "과제 양식.pdf",
            downloadUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadAttachFile.dunet",
            sizeBytes = 264_542,
            requestMethod = LmsHttpMethod.GET.name,
            requestFieldsJson = "{\"report_attach_file_no\":\"1\",\"report_no\":\"5553\"}",
            refererUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=5553",
        )

        database.dao().replaceDetail(detail, listOf(attachment))

        assertEquals(detail, database.dao().getDetail(detail.itemKey))
        assertEquals(listOf(attachment), database.dao().getAttachments(detail.itemKey))
        database.close()
    }

    @Test
    fun versionFourCacheMigratesAdditivelyWithoutLosingOfflineDetailOrItemState() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "lms-v4-v5-migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE lms_courses (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, professor TEXT, classNo TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE lms_items (`key` TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, courseId TEXT NOT NULL, courseName TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, registeredAtMillis INTEGER, dueAtMillis INTEGER, detailUrl TEXT NOT NULL, isRead INTEGER NOT NULL, completionState TEXT NOT NULL, changeState TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE lms_details (itemKey TEXT NOT NULL PRIMARY KEY, sanitizedHtml TEXT NOT NULL, fetchedAtMillis INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE lms_attachments (`key` TEXT NOT NULL PRIMARY KEY, itemKey TEXT NOT NULL, sourceId TEXT NOT NULL, fileName TEXT NOT NULL, downloadUrl TEXT NOT NULL, sizeBytes INTEGER)")
                        db.execSQL("CREATE TABLE lms_sync (id INTEGER NOT NULL PRIMARY KEY, status TEXT NOT NULL, lastSuccessAtMillis INTEGER, errorMessage TEXT)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO lms_courses (id,name,professor,classNo) VALUES ('course','기존 과목','담당교수','D')")
            execSQL(
                "INSERT INTO lms_items (`key`,sourceId,courseId,courseName,kind,title,registeredAtMillis,dueAtMillis,detailUrl,isRead,completionState,changeState) " +
                    "VALUES ('NOTICE:course:91','91','course','기존 과목','NOTICE','기존 공지',1000,2000,'https://lms.dima.ac.kr/item/91',1,'COMPLETE','UPDATED')",
            )
            execSQL("INSERT INTO lms_details (itemKey,sanitizedHtml,fetchedAtMillis) VALUES ('NOTICE:course:91','<p>보존할 본문</p>',3000)")
            execSQL(
                "INSERT INTO lms_attachments (`key`,itemKey,sourceId,fileName,downloadUrl,sizeBytes) " +
                    "VALUES ('NOTICE:course:91:501','NOTICE:course:91','501','기존 첨부.pdf','https://lms.dima.ac.kr/file/501',264542)",
            )
            execSQL("INSERT INTO lms_sync (id,status,lastSuccessAtMillis,errorMessage) VALUES (1,'READY',4000,NULL)")
        }
        helper.close()

        val database = Room.databaseBuilder(context, LmsCacheDatabase::class.java, name)
            .addMigrations(LMS_CACHE_MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        assertEquals(LmsCourseEntity("course", "기존 과목", "담당교수", "D"), database.dao().getAllCourses().single())
        database.dao().getAllItems().single().also { item ->
            assertEquals("91", item.sourceId)
            assertEquals("course", item.courseId)
            assertEquals("기존 과목", item.courseName)
            assertEquals(LmsItemKind.NOTICE.name, item.kind)
            assertEquals("기존 공지", item.title)
            assertEquals(1_000L, item.registeredAtMillis)
            assertEquals(2_000L, item.dueAtMillis)
            assertEquals("https://lms.dima.ac.kr/item/91", item.detailUrl)
            assertEquals(true, item.isRead)
            assertEquals(LmsCompletionState.COMPLETE.name, item.completionState)
            assertEquals(LmsChangeState.UPDATED.name, item.changeState)
        }
        database.dao().getDetail("NOTICE:course:91")!!.also { detail ->
            assertEquals("<p>보존할 본문</p>", detail.sanitizedHtml)
            assertEquals(3_000L, detail.fetchedAtMillis)
            assertNull(detail.author)
            assertNull(detail.registeredAtMillis)
            assertNull(detail.submissionStartsAtMillis)
            assertNull(detail.submissionEndsAtMillis)
            assertNull(detail.maxScore)
        }
        database.dao().getAttachments("NOTICE:course:91").single().also { attachment ->
            assertEquals("501", attachment.sourceId)
            assertEquals("기존 첨부.pdf", attachment.fileName)
            assertEquals("https://lms.dima.ac.kr/file/501", attachment.downloadUrl)
            assertEquals(264_542L, attachment.sizeBytes)
            assertNull(attachment.requestMethod)
            assertNull(attachment.requestFieldsJson)
            assertNull(attachment.refererUrl)
        }
        assertEquals(LmsSyncState.READY.name, database.dao().getSync()!!.status)

        database.close()
        context.deleteDatabase(name)
    }

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
            .addMigrations(LMS_CACHE_MIGRATION_3_4, LMS_CACHE_MIGRATION_4_5)
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
