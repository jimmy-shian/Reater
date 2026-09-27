package com.reater.app.di

import android.content.Context
import androidx.room.Room
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.CommentDao
import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.dao.MediaDao
import com.reater.app.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "reater_database.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCommentDao(db: AppDatabase): CommentDao = db.commentDao()

    @Provides
    fun provideMediaDao(db: AppDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
}
