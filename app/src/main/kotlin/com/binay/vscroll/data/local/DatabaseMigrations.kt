package com.binay.vscroll.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations ensuring user interaction data is never destructively erased.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Safe, non-destructive migration: Add tags column to video_interactions
            db.execSQL("ALTER TABLE video_interactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Safe, non-destructive migration: Create watch_history and daily_watch_records
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watch_history` (
                    `media_store_id` INTEGER NOT NULL,
                    `cached_title` TEXT NOT NULL,
                    `cached_folder_name` TEXT NOT NULL,
                    `content_uri` TEXT NOT NULL,
                    `cached_duration_ms` INTEGER NOT NULL,
                    `first_watched_at` INTEGER NOT NULL,
                    `last_watched_at` INTEGER NOT NULL,
                    `total_watch_time_ms` INTEGER NOT NULL,
                    `session_count` INTEGER NOT NULL,
                    `last_position_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`media_store_id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_last_watched_at` ON `watch_history` (`last_watched_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_total_watch_time_ms` ON `watch_history` (`total_watch_time_ms`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_watch_records` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date_string` TEXT NOT NULL,
                    `media_store_id` INTEGER NOT NULL,
                    `watch_time_ms` INTEGER NOT NULL,
                    `session_count` INTEGER NOT NULL,
                    `last_watched_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_watch_records_date_string_media_store_id` ON `daily_watch_records` (`date_string`, `media_store_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_watch_records_date_string` ON `daily_watch_records` (`date_string`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Safe, non-destructive migration: Create video_collections and collection_videos
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_collections` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_video_collections_name` ON `video_collections` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_collections_updated_at` ON `video_collections` (`updated_at`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection_videos` (
                    `collection_id` INTEGER NOT NULL,
                    `media_store_id` INTEGER NOT NULL,
                    `added_at` INTEGER NOT NULL,
                    PRIMARY KEY(`collection_id`, `media_store_id`),
                    FOREIGN KEY(`collection_id`) REFERENCES `video_collections`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_videos_collection_id` ON `collection_videos` (`collection_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_videos_media_store_id` ON `collection_videos` (`media_store_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_videos_added_at` ON `collection_videos` (`added_at`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Safe, non-destructive migration: Add reaction_type column to video_interactions
            db.execSQL("ALTER TABLE video_interactions ADD COLUMN reaction_type TEXT DEFAULT NULL")
            // Populate existing liked videos with default reaction "LOVE"
            db.execSQL("UPDATE video_interactions SET reaction_type = 'LOVE' WHERE is_liked = 1 AND reaction_type IS NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_interactions_reaction_type` ON `video_interactions` (`reaction_type`)")
        }
    }
}
