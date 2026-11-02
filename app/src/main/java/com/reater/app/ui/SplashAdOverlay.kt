package com.reater.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay

/** 統一倒數長度（秒） */
private const val AD_DURATION = 5

/** 後門略過：5 秒內累計 5 次點擊 */
private const val BACKDOOR_WINDOW = 5000L
private const val BACKDOOR_CLICKS = 5

/**
 * 開場廣告：直接長成 Reater 的 Threads 貼文卡。
 * 讀取狀態膠囊（7dp 圓點 + 標籤）、作者列、內文、內嵌內容塊、標籤 chips、
 * 互動列與 LinearProgressIndicator —— 全部取自 PostCard / CategoryFilterBar 的既有樣式，
 * 並使用 MaterialTheme.colorScheme 跟隨淺色／深色。
 *
 * 「廣告畫面」與「真正的 Google 廣告」合為同一層：內嵌內容塊的位置
 * 用 AdMob「原生進階 (Native Advanced)」廣告素材渲染 —— 不再二次撥放
 * 全螢幕 App Open 蓋版。（AdMob 後台的開場單元必須是原生進階類型，否則 no-fill。）
 */
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

    val scheme = MaterialTheme.colorScheme
    var countdown by remember { mutableStateOf(AD_DURATION) }
    val splashActive = remember { mutableStateOf(true) }
    var clickTimestamps by remember { mutableStateOf(listOf<Long>()) }
    val nativeAd = remember { mutableStateOf<NativeAd?>(null) }

    // 載入 AdMob 原生進階廣告：素材將渲染在貼文卡的內嵌內容塊（= 廣告槽）
    LaunchedEffect(Unit) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val resourceId = context.resources.getIdentifier("admob_unit_splash", "string", context.packageName)
        val adUnitId = if (resourceId != 0) context.getString(resourceId) else ""
        if (!adUnitId.startsWith("ca-app-pub-")) return@LaunchedEffect

        MobileAds.initialize(context) {
            if (!splashActive.value || activity.isFinishing) return@initialize
            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    if (!splashActive.value || activity.isFinishing) {
                        ad.destroy()
                        return@forNativeAd
                    }
                    nativeAd.value = ad
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) = Unit
                })
                .build()
            loader.loadAd(AdRequest.Builder().build())
        }
    }

    // 後門略過：5 秒內累計 5 次點擊（貼文卡標頭 / 頂列）
    val triggerBackdoor: () -> Unit = {
        val now = System.currentTimeMillis()
        val recent = clickTimestamps.filter { now - it <= BACKDOOR_WINDOW } + now
        clickTimestamps = recent
        if (recent.size >= BACKDOOR_CLICKS) {
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
            .background(scheme.background)
            .zIndex(9999f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 頂列：App 識別 + 倒數 chip（後門觸發點 ①） ──
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(scheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            color = scheme.onPrimaryContainer,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Reater",
                            color = scheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "串文導讀引擎 · 離線快取",
                            color = scheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    color = scheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant)
                ) {
                    Text(
                        text = if (countdown > 0) "0${countdown}s" else "進入",
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Threads 貼文卡（後門觸發點 ②） ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = triggerBackdoor
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.5.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 貼文卡標頭：讀取狀態膠囊 + 作者 + SPONSORED（= PostCard 標頭）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // 讀取狀態膠囊（= PostCard 的未讀/已讀膠囊）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.primaryContainer.copy(alpha = 0.8f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(scheme.primary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "贊助",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "yesolve studio",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface
                            )
                        }
                        Text(
                            text = "SPONSORED",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 內文
                    Text(
                        text = "高效消化長文與跨串論點",
                        color = scheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "自動解構 Threads 嵌套引言與回覆階層，離線快取全文並建立結構化導讀。",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 內嵌內容塊：廣告槽（= PostCard 的內嵌媒體塊）
                    // 原生進階廣告載入成功時，素材直接渲染在這個位置（同一層，無第二層蓋版）
                    val loadedAd = nativeAd.value
                    if (loadedAd != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx ->
                                buildNativeAdSlotView(ctx, loadedAd)
                            }
                        )
                    } else {
                        Surface(
                            color = scheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(3.dp)
                                        .background(scheme.primary.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                                )
                                Text(
                                    text = "贊助內容 · 廣告載位",
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 標籤 chips（= PostCard 的分類 chips）
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = scheme.primaryContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "#串文脈絡",
                                color = scheme.onPrimaryContainer,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = scheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "#離線整理",
                                color = scheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 互動列（= PostCard 底部動作列）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "本機離線運作 · 無追蹤碼",
                            color = scheme.outline,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "由 Google 贊助託播",
                            color = scheme.outline,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ── 倒數進度（= Material 3 LinearProgressIndicator） ──
            LinearProgressIndicator(
                progress = { countdown.toFloat() / AD_DURATION.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = scheme.primary,
                trackColor = scheme.surfaceVariant,
            )
        }

        // ── 署名 ──
        Text(
            text = "yesolve studio",
            color = scheme.outline.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

/**
 * 程式化建立 NativeAdView 槽（放在貼文卡的內嵌內容塊位置）。
 * 必須先註冊 asset views 再 setNativeAd，Google 才會記錄曝光與點擊。
 * 卡片底色跟隨系統淺色／深色主題。
 */
private fun buildNativeAdSlotView(context: Context, ad: NativeAd): View {
    val density = context.resources.displayMetrics.density
    fun dp(v: Int) = Math.round(v * density)
    val pad = dp(12)

    val dark = isDarkThemeApprox(context)
    val bodyColor = android.graphics.Color.parseColor(if (dark) "#D1D5DB" else "#374151")
    val titleColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#111827")
    val surfaceColor = if (dark) android.graphics.Color.parseColor("#111827") else android.graphics.Color.parseColor("#F4F4F5")

    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(surfaceColor)
            cornerRadius = dp(10).toFloat()
        }
        setPadding(pad, pad, pad, pad)
    }

    // 「廣告」徽章（Google 原生廣告政策：必須讓使用者明確辨識）
    val badgeRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    val badge = TextView(context).apply {
        text = "廣告"
        setTextColor(android.graphics.Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#374151"))
            cornerRadius = dp(4).toFloat()
        }
        setPadding(dp(6), dp(2), dp(6), dp(2))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    badgeRow.addView(badge)
    if (!ad.advertiser.isNullOrBlank()) {
        val advert = TextView(context).apply {
            text = ad.advertiser
            setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            }
        }
        badgeRow.addView(advert)
    }
    card.addView(badgeRow)

    var mediaView: MediaView? = null
    var headlineView: TextView? = null
    var bodyView: TextView? = null

    ad.mediaContent?.let { mediaContent ->
        val aspectRatio = if (mediaContent.aspectRatio > 0f) mediaContent.aspectRatio else 1.2f
        val mediaHeight = (280f / aspectRatio).coerceIn(110f, 220f).dp(density)
        mediaView = MediaView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, mediaHeight
            ).apply { topMargin = dp(10) }
        }
        card.addView(mediaView)
    }

    if (!ad.headline.isNullOrBlank()) {
        headlineView = TextView(context).apply {
            text = ad.headline
            setTextColor(titleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        card.addView(headlineView)
    }

    if (!ad.body.isNullOrBlank()) {
        bodyView = TextView(context).apply {
            text = ad.body
            setTextColor(bodyColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        card.addView(bodyView)
    }

    val cta = Button(context).apply {
        text = ad.callToAction?.takeIf { it.isNotBlank() } ?: "瞭解更多"
        setTextColor(android.graphics.Color.WHITE)
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(40)
        stateListAnimator = null
        background = GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#2563EB"))
            cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        ).apply { topMargin = dp(12) }
    }
    card.addView(cta)

    return NativeAdView(context).apply {
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addView(card)
        mediaView?.let { setMediaView(it) }
        headlineView?.let { setHeadlineView(it) }
        bodyView?.let { setBodyView(it) }
        setCallToActionView(cta)
        setNativeAd(ad)
    }
}

private fun Float.dp(density: Float): Int = Math.round(this * density)

/** 粗略主題偵測：跟 Material 深色模式提供一致的廣告卡片配色 */
private fun isDarkThemeApprox(context: Context): Boolean =
    (context.resources.configuration.uiMode
        .and(android.content.res.Configuration.UI_MODE_NIGHT_MASK)) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
