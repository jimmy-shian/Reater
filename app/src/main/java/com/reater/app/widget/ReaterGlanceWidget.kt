package com.reater.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.room.Room
import com.reater.app.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ReaterGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "reater_database.db"
        ).fallbackToDestructiveMigration().build()

        val unreadList = try {
            db.itemDao().observeUnreadItemDetails().first()
        } catch (e: Exception) {
            emptyList()
        }

        val displayItem = unreadList.firstOrNull()

        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF1E1E1E), Color(0xFFFFFFFF)))
                        .padding(14.dp)
                ) {
                    if (displayItem == null) {
                        Column(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Reater 稍後閱讀",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color(0xFF888888), Color(0xFF666666))
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "目前無待讀貼文，快去 Threads 分享吧！",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = ColorProvider(Color(0xFFAAAAAA), Color(0xFF888888))
                                )
                            )
                        }
                    } else {
                        Column(
                            modifier = GlanceModifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@${displayItem.item.authorHandle}",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color(0xFF4DA3FF), Color(0xFF0066CC))
                                    )
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = "待讀",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorProvider(Color(0xFFAAAAAA), Color(0xFF777777))
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            Text(
                                text = displayItem.displayBody.take(120),
                                maxLines = 3,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorProvider(Color(0xFFDDDDDD), Color(0xFF222222))
                                )
                            )

                            if (displayItem.manualNote.isNotBlank()) {
                                Spacer(modifier = GlanceModifier.height(4.dp))
                                Text(
                                    text = "筆記: ${displayItem.manualNote.take(40)}",
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorProvider(Color(0xFFFFB300), Color(0xFFD97706))
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class ReaterGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReaterGlanceWidget()
}
