package com.binay.vscroll.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.binay.vscroll.data.local.dao.CollectionDao
import com.binay.vscroll.data.local.dao.DailyWatchRecordDao
import com.binay.vscroll.data.local.dao.VideoInteractionDao
import com.binay.vscroll.data.local.dao.VideoNoteDao
import com.binay.vscroll.data.local.dao.WatchHistoryDao
import com.binay.vscroll.data.local.entity.CollectionVideoCrossRefEntity
import com.binay.vscroll.data.local.entity.DailyWatchRecordEntity
import com.binay.vscroll.data.local.entity.VideoCollectionEntity
import com.binay.vscroll.data.local.entity.VideoInteractionEntity
import com.binay.vscroll.data.local.entity.VideoNoteEntity
import com.binay.vscroll.data.local.entity.WatchHistoryEntity

@Database(
    entities = [
        VideoInteractionEntity::class,
        VideoNoteEntity::class,
        WatchHistoryEntity::class,
        DailyWatchRecordEntity::class,
        VideoCollectionEntity::class,
        CollectionVideoCrossRefEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class VScrollDatabase : RoomDatabase() {

    abstract fun videoInteractionDao(): VideoInteractionDao
    abstract fun videoNoteDao(): VideoNoteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun dailyWatchRecordDao(): DailyWatchRecordDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val DATABASE_NAME = "vscroll_database.db"

        @Volatile
        private var INSTANCE: VScrollDatabase? = null

        fun getInstance(context: Context): VScrollDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VScrollDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(
                    DatabaseMigrations.MIGRATION_1_2,
                    DatabaseMigrations.MIGRATION_2_3,
                    DatabaseMigrations.MIGRATION_3_4
                )
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
