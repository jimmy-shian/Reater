package com.reater.app.data.local

import androidx.room3.Database
import androidx.room3.migration.Migration
import androidx.room3.RoomDatabase
import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.AiRunDao
import com.reater.app.data.local.dao.CommentDao
import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.dao.MediaDao
import com.reater.app.data.local.dao.ProDao
import com.reater.app.data.local.dao.TagDao
import com.reater.app.data.local.entity.AiRunEntity
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.FetchAttemptEntity
import com.reater.app.data.local.entity.ItemEntity
import com.reater.app.data.local.entity.ItemFtsEntity
import com.reater.app.data.local.entity.ItemTagCrossRef
import com.reater.app.data.local.entity.KeywordEntity
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.data.local.entity.SavedQueryEntity
import com.reater.app.data.local.entity.TagEntity
import com.reater.app.data.local.entity.UserEditEntity

@Database(
    entities = [
        ItemEntity::class,
        UserEditEntity::class,
        CommentEntity::class,
        MediaEntity::class,
        CategoryEntity::class,
        KeywordEntity::class,
        TagEntity::class,
        ItemTagCrossRef::class,
        AiRunEntity::class,
        FetchAttemptEntity::class,
        ItemFtsEntity::class,
        SavedCollectionEntity::class,
        SavedQueryEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aiRunDao(): AiRunDao
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun commentDao(): CommentDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun proDao(): ProDao

    companion object {
        private fun androidx.sqlite.SQLiteConnection.execSql(sql: String) {
            prepare(sql).use { it.step() }
        }

        val MIGRATION_1_2: Migration = Migration(1, 2) { connection ->
            connection.execSql("""CREATE TABLE IF NOT EXISTS `saved_collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconName` TEXT NOT NULL, `rulesJson` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)""")
            connection.execSql("""CREATE TABLE IF NOT EXISTS `saved_queries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `filterJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)""")
        }

        val MIGRATION_2_3: Migration = Migration(2, 3) { connection ->
            listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT").forEach { operation ->
                connection.execSql("DROP TRIGGER IF EXISTS `room_fts_content_sync_items_fts_$operation`")
            }
            connection.execSql("DROP TABLE IF EXISTS `items_fts`")
            connection.execSql("CREATE VIRTUAL TABLE `items_fts` USING FTS5(`searchText`, tokenize='trigram')")
            connection.execSql(
                """INSERT INTO `items_fts` (`rowid`, `searchText`)
                    SELECT i.`id`,
                        COALESCE(i.`bodyText`, '') || ' ' || COALESCE(i.`commentsText`, '') || ' ' ||
                        COALESCE(i.`authorHandle`, '') || ' ' || COALESCE(i.`authorDisplayName`, '') || ' ' ||
                        COALESCE(u.`userBodyOverride`, '') || ' ' || COALESCE(u.`manualNote`, '') || ' ' ||
                        COALESCE(u.`manualSummary`, '') || ' ' ||
                        COALESCE((SELECT group_concat(c.`text`, ' ') FROM `comments` c WHERE c.`itemId` = i.`id`), '') || ' ' ||
                        COALESCE((SELECT group_concat(t.`name`, ' ') FROM `item_tags` it JOIN `tags` t ON t.`id` = it.`tagId` WHERE it.`itemId` = i.`id`), '')
                    FROM `items` i LEFT JOIN `user_edits` u ON u.`itemId` = i.`id`"""
            )
        }

        val MIGRATION_3_4: Migration = Migration(3, 4) { connection ->
            connection.execSql("ALTER TABLE `items` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            connection.execSql("ALTER TABLE `items` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            connection.execSql("ALTER TABLE `categories` ADD COLUMN `avatarIcon` TEXT NOT NULL DEFAULT 'camel'")
        }

        val MIGRATION_4_5: Migration = Migration(4, 5) { connection ->
            connection.execSql("ALTER TABLE `user_edits` ADD COLUMN `openCount` INTEGER NOT NULL DEFAULT 0")
            connection.execSql("ALTER TABLE `user_edits` ADD COLUMN `lastOpenedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
