package com.reater.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reater.app.data.local.dao.CategoryDao
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun commentDao(): CommentDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun proDao(): ProDao
}
