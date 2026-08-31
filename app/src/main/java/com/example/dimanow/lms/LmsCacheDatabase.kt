package com.example.dimanow.lms

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lms_courses")
data class LmsCourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val professor: String?,
)

@Entity(tableName = "lms_items")
data class LmsItemEntity(
    @PrimaryKey val key: String,
    val sourceId: String,
    val courseId: String,
    val courseName: String,
    val kind: String,
    val title: String,
    val registeredAtMillis: Long?,
    val dueAtMillis: Long?,
    val detailUrl: String,
)

@Entity(tableName = "lms_details")
data class LmsDetailEntity(
    @PrimaryKey val itemKey: String,
    val sanitizedHtml: String,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "lms_attachments")
data class LmsAttachmentEntity(
    @PrimaryKey val key: String,
    val itemKey: String,
    val sourceId: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
)

@Entity(tableName = "lms_sync")
data class LmsSyncEntity(
    @PrimaryKey val id: Int = 1,
    val status: String,
    val lastSuccessAtMillis: Long?,
    val errorMessage: String?,
)

@Dao
interface LmsCacheDao {
    @Query("SELECT * FROM lms_courses ORDER BY name")
    fun observeCourses(): Flow<List<LmsCourseEntity>>

    @Query("SELECT * FROM lms_items ORDER BY COALESCE(dueAtMillis, registeredAtMillis, 0) DESC")
    fun observeItems(): Flow<List<LmsItemEntity>>

    @Query("SELECT * FROM lms_items")
    suspend fun getAllItems(): List<LmsItemEntity>

    @Query("SELECT * FROM lms_sync WHERE id = 1")
    fun observeSync(): Flow<LmsSyncEntity?>

    @Query("SELECT * FROM lms_sync WHERE id = 1")
    suspend fun getSync(): LmsSyncEntity?

    @Query("SELECT * FROM lms_items WHERE `key` = :key")
    suspend fun getItem(key: String): LmsItemEntity?

    @Query("SELECT * FROM lms_details WHERE itemKey = :key")
    suspend fun getDetail(key: String): LmsDetailEntity?

    @Query("SELECT * FROM lms_attachments WHERE itemKey = :key ORDER BY fileName")
    suspend fun getAttachments(key: String): List<LmsAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(rows: List<LmsCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(rows: List<LmsItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetail(row: LmsDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(rows: List<LmsAttachmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSync(row: LmsSyncEntity)

    @Query("DELETE FROM lms_courses")
    suspend fun clearCourses()

    @Query("DELETE FROM lms_items")
    suspend fun clearItems()

    @Query("DELETE FROM lms_attachments WHERE itemKey = :itemKey")
    suspend fun clearAttachments(itemKey: String)

    @Query("DELETE FROM lms_attachments")
    suspend fun clearAllAttachments()

    @Query("DELETE FROM lms_details")
    suspend fun clearDetails()

    @Query("DELETE FROM lms_sync")
    suspend fun clearSync()

    @Transaction
    suspend fun replaceDashboard(courses: List<LmsCourseEntity>, items: List<LmsItemEntity>, successAtMillis: Long) {
        clearCourses()
        clearItems()
        insertCourses(courses)
        insertItems(items)
        setSync(LmsSyncEntity(status = LmsSyncState.READY.name, lastSuccessAtMillis = successAtMillis, errorMessage = null))
    }

    @Transaction
    suspend fun replaceDetail(detail: LmsDetailEntity, attachments: List<LmsAttachmentEntity>) {
        insertDetail(detail)
        clearAttachments(detail.itemKey)
        insertAttachments(attachments)
    }

    @Transaction
    suspend fun clearPrivateData() {
        clearAllAttachments()
        clearDetails()
        clearItems()
        clearCourses()
        clearSync()
    }
}

@Database(
    entities = [
        LmsCourseEntity::class,
        LmsItemEntity::class,
        LmsDetailEntity::class,
        LmsAttachmentEntity::class,
        LmsSyncEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LmsCacheDatabase : RoomDatabase() {
    abstract fun dao(): LmsCacheDao
}
