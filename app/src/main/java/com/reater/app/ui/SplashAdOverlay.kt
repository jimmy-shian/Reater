package com.reater.app.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.delay

@Composable
fun SplashAdOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yesolve_prefs", Context.MODE_PRIVATE) }
    val isBypassed = remember {
        val token = context.getString(com.reater.app.R.string.ad_bypass_reset_token)
        if (token.isNotEmpty() && prefs.getString("yesolve_ad_bypass_reset_token", "") != token) {
            prefs.edit().putBoolean("yesolve_ad_bypass", false).putString("yesolve_ad_bypass_reset_token", token).apply()
        }
        prefs.getBoolean("yesolve_ad_bypass", false)
    }

    if (isBypassed) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var countdown by remember { mutableStateOf(5) }
    val splashActive = remember { mutableStateOf(true) }
    var clickTimestamps by remember { mutableStateOf(listOf<Long>()) }

    LaunchedEffect(Unit) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val resourceId = context.resources.getIdentifier("admob_unit_splash", "string", context.packageName)
        val adUnitId = if (resourceId != 0) context.getString(resourceId) else ""
        if (!adUnitId.startsWith("ca-app-pub-")) return@LaunchedEffect

        MobileAds.initialize(context) {
            if (!splashActive.value || activity.isFinishing) return@initialize
            AppOpenAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        if (!splashActive.value || activity.isFinishing) return
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                splashActive.value = false
                            }

                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                splashActive.value = false
                            }
                        }
                        ad.show(activity)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) = Unit
                }
            )
        }
    }

    val triggerBackdoor: () -> Unit = {
        val now = System.currentTimeMillis()
        val recent = clickTimestamps.filter { now - it <= 3000L } + now
        clickTimestamps = recent
        if (recent.size >= 5) {
            splashActive.value = false
            prefs.edit().putBoolean("yesolve_ad_bypass", true).apply()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        splashActive.value = false
        onDismiss()
    }

    // Root overlay: consumes ALL clicks to prevent pass-through to MainActivity and TopAppBar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .zIndex(9999f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = triggerBackdoor
                    )
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, Color(0xFF1E293B)), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "R",
                        color = Color(0xFFE2E8F0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Reater",
                        color = Color(0xFFF8FAFC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Threads 閱讀整理器",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Text(
                    text = if (countdown > 0) "0${countdown}s" else "進入 ›",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Center: Native Threads Thread-Spine Post Card Layout
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = triggerBackdoor
                ),
            color = Color(0xFF0B101D),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Card Header metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF38BDF8), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "精選串文導讀",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "SPONSORED",
                        color = Color(0xFF475569),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Thread Spine layout: left line + right content
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left thread spine column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .border(1.5.dp, Color(0xFF38BDF8), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(56.dp)
                                .background(Color(0xFF1E293B))
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF475569), CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right Content column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "高效消化長文與跨串論點",
                            color = Color(0xFFF1F5F9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "自動解構 Threads 嵌套引言與回覆階層，離線快取全文並自動建立結構化導讀。",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(3.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B))
                            ) {
                                Text(
                                    text = "#串文脈絡",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(3.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B))
                            ) {
                                Text(
                                    text = "#離線整理",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF161F33), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本機離線運作 · 無追蹤碼",
                        color = Color(0xFF475569),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "AD · 5677764204",
                        color = Color(0xFF334155),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Bottom Brand
        Text(
            text = "yesolve studio",
            color = Color(0xFF334155),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}
