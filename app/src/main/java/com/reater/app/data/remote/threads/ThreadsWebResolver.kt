package com.reater.app.data.remote.threads

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Threads 內建瀏覽器解析器（免後端、免金鑰的「模擬開啟」）。
 *
 * 背景：/share/TOKEN 的跳轉是客戶端 JS 做的，OkHttp 永遠跟不到；
 * 一般 UA 拿到的又是無資料 JS 殼。解法就是真的用一個瀏覽器開啟它——
 * 手機本身就是最好的渲染後端（住宅網路，指紋正常）。
 *
 * 流程（全程隱藏 WebView，不打擾使用者）：
 *  1. 載入 /share/ 連結，JS 執行後自動跳轉到正文
 *     （https://www.threads.com/@user/post/CODE）。
 *  2. 偵測 URL 已離開 /share/ → 用 JS 抽出 data-sjs JSON 塊。
 *  3. 回傳 (finalUrl, sjsBlocks)，由 Facade 走既有 SJS 解析 + 媒體下載管線。
 *
 * 限制：公開貼文可解；需登入/私人帳號無解；逾時回 null（呼叫方走原兜底）。
 */
object ThreadsWebResolver {

    data class ResolvedPage(
        val finalUrl: String,
        val sjsBlocks: List<String>,
        /** 渲染後可見文字（SJS 缺失時的內文兜底，供 Facade 比對） */
        val renderedText: String = "",
        /** 渲染後完整 HTML（供 OkHttp 二次抓取失敗時的備用解析） */
        val renderedHtml: String = "",
        /** DOM 直抽留言（免登入 WebView 渲染後文章列表，SJS 為空時的關鍵兜底） */
        val domComments: List<DomComment> = emptyList()
    )

    data class DomComment(
        val author: String,
        val text: String,
        /** 該則留言渲染出的圖/影（排除頭像；kind 為 IMAGE/VIDEO） */
        val media: List<com.reater.app.data.remote.FetchedMedia> = emptyList(),
        /** 從 DOM 尾端數字列解析出的讚數（無則 0） */
        val likeCount: Int = 0
    )

    const val DEFAULT_TIMEOUT_MS = 25_000L
    private const val POLL_INTERVAL_MS = 1_000L

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * Googlebot UA：2026-09 實測一般 UA 只回 JS 殼，Googlebot UA 的初始 HTML
     * 就帶完整預渲染 payload（data.media 主貼 + direct_replies 留言）——
     * WebView 一載入即可抽出，不必等 SPA 水合。
     */
    private const val BOT_UA =
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

    /** JS：抽出含貼文 payload 的 SJS 塊（舊 thread_items / 新 direct_replies、data.media；只取前 60 塊） */
    private const val EXTRACT_JS =
        "(function(){try{" +
            "var out=[];" +
            "var ss=document.querySelectorAll('script[type=\"application/json\"][data-sjs]');" +
            "for(var i=0;i<ss.length&&out.length<60;i++){" +
            "var t=ss[i].textContent||'';" +
            "if(t.indexOf('thread_items')>=0||t.indexOf('direct_replies')>=0||t.indexOf('\"data\":{\"media\"')>=0)out.push(t);" +
            "}" +
            "return JSON.stringify(out);" +
            "}catch(e){return '[]';}})()"

    /** JS：取渲染後可見文字 + canonical + 全 HTML 長度（SJS 缺失時的兜底） */
    private const val EXTRACT_TEXT_JS =
        "(function(){try{" +
            "var canon='';" +
            "var lc=document.querySelector('link[rel=\"canonical\"]');" +
            "if(lc)canon=lc.getAttribute('href')||'';" +
            "var body=(document.body&&document.body.innerText)||'';" +
            "if(body.length>8000)body=body.substring(0,8000);" +
            "return JSON.stringify({url:location.href,canon:canon,text:body});" +
            "}catch(e){return '{}';}})()"

    /** JS：DOM 直抽留言（免登入渲染後兜底）
     *
     *  Threads article.innerText 典型結構（每行一項）：
     *    顯示名稱 / @handle / 時間（8小時） / 內文多行 / 數字列（8,099/115/…) / 動作詞
     *  舊版只濾英文 Like/Reply，導致時間＋數字＋顯示名全殘留進 body，
     *  詳情頁看起來像「每欄換一行」的跑版，且讚數永遠抓不到。
     *  此處在 JS 端先洗：去動作詞（中英）、去時間行、去純數字列（首個數字記為讚數）。
     */
    private val EXTRACT_COMMENTS_JS: String =
        "(function(){try{" +
            "var out=[];var seen={};" +
            "var arts=document.querySelectorAll('article');" +
            "if(!arts||arts.length==0)arts=document.querySelectorAll('[data-pressable-container]');" +
            "function isAction(s){" +
            "var t=s.trim().toLowerCase();" +
            "return t==='like'||t==='likes'||t==='reply'||t==='replies'||t==='repost'||t==='reposts'||" +
            "t==='share'||t==='shares'||t==='send'||" +
            "s==='讚'||s==='喜歡'||s==='愛心'||s==='回覆'||s==='回應'||s==='留言'||" +
            "s==='轉發'||s==='轉po'||s==='轉帖'||s==='分享'||s==='傳送';" +
            "}" +
            "function isTimestamp(s){" +
            "var t=s.trim();" +
            "if(/^[0-9]+\\s*[\\u79d2\\u5206\\u5c0f\\u6642\\u5929\\u9031\\u5468\\u6708\\u5e74]+$/.test(t))return true;" +
            "if(/^[0-9]+\\s*(s|sec|secs|m|min|mins|h|hr|hrs|d|day|days|w|week|weeks|mo|yr)\\.?$/i.test(t))return true;" +
            "if(t==='\\u6628\\u5929'||t==='\\u524d\\u5929'||t==='Yesterday')return true;" +
            "if(/^[0-9]{4}[./-][0-9]{1,2}[./-][0-9]{1,2}$/.test(t))return true;" +
            "if(/^[0-9]{1,2}[\\u6708\\/\\-][0-9]{1,2}[\\u65e5]?$/.test(t))return true;" +
            "return false;" +
            "}" +
            "function isCount(s){" +
            "var t=s.trim().replace(/,/g,'');" +
            "if(/^[0-9]+(\\.[0-9]+)?\\s*[KkMm\\u4e07\\u5343]?$/.test(t))return true;" +
            "return false;" +
            "}" +
            "function parseCount(s){" +
            "try{var t=s.trim().replace(/,/g,'');" +
            "var m=t.match(/^([0-9]+(\\.[0-9]+)?)\\s*([KkMm\\u4e07\\u5343])?$/);" +
            "if(!m)return 0;" +
            "var v=parseFloat(m[1]);var u=m[3]||'';" +
            "if(u==='K'||u==='k')v=v*1000;" +
            "else if(u==='M'||u==='m')v=v*1000000;" +
            "else if(u==='\\u4e07')v=v*10000;" +
            "else if(u==='\\u5343')v=v*1000;" +
            "return Math.floor(v);}catch(e){return 0;}}" +
            "for(var ai=0;ai<arts.length&&out.length<50;ai++){" +
            "var a=arts[ai];" +
            "var links=a.querySelectorAll('a');" +
            "var author='';" +
            "for(var li=0;li<links.length;li++){" +
            "var h=links[li].getAttribute('href')||'';" +
            "var mi=h.indexOf('/@');" +
            "if(mi>=0){author=h.substring(mi+2).split('/')[0].split('?')[0];if(author&&author.length>=2)break;}" +
            "}" +
            "if(!author){" +
            "var m2=(a.innerText||'').match(/@([A-Za-z0-9_.]{2,})/);" +
            "if(m2)author=m2[1];" +
            "}" +
            "var txt=(a.innerText||'').trim();" +
            "if(!txt||txt.length<2)continue;" +
            "if(txt.indexOf('Log in')>=0&&txt.length<120)continue;" +
            "var parts=txt.split(String.fromCharCode(10));" +
            "var cleaned=[];" +
            "for(var pi=0;pi<parts.length;pi++){" +
            "var s=parts[pi].trim();" +
            "if(!s)continue;" +
            "if(isAction(s))continue;" +
            "if(s.indexOf('Log in')>=0||s.indexOf('\\u767b\\u5165')>=0)continue;" +
            "cleaned.push(s);" +
            "}" +
            // 去頭：顯示名重複 / @handle 行 / 時間行
            "var hi=0;" +
            "while(hi<cleaned.length){" +
            "var hs=cleaned[hi];" +
            "var hsNoAt=hs.charAt(0)==='@'?hs.substring(1):hs;" +
            "if(author&&hsNoAt.toLowerCase()===author.toLowerCase()){hi++;continue;}" +
            "if(author&&hs.toLowerCase()===author.toLowerCase()){hi++;continue;}" +
            "if(isTimestamp(hs)){hi++;continue;}" +
            "break;}" +
            "cleaned=cleaned.slice(hi);" +
            // 去尾：純數字列＋動作詞殘留；Threads 尾端數字由上而下為 愛心/回覆/轉發，
            // 圖1修正：舊版取「最後一行」當讚數，3 數並列時會把轉發數誤當讚數；
            // 此處先收集全部尾端數字（由上而下），第一個才是讚數。
            "var likeN=0;" +
            "var tailNums=[];" +
            "while(cleaned.length>0){" +
            "var ts=cleaned[cleaned.length-1];" +
            "if(isAction(ts)){cleaned.pop();continue;}" +
            "if(isCount(ts)){tailNums.unshift(parseCount(ts));cleaned.pop();continue;}" +
            "break;}" +
            "if(tailNums.length>0)likeN=tailNums[0];" +
            // 尾端清理後若又露出時間行（少見排版），再去一次
            "while(cleaned.length>0&&isTimestamp(cleaned[cleaned.length-1])){cleaned.pop();}" +
            "var body=cleaned.join(String.fromCharCode(10));" +
            "if(author){" +
            "var at0=body.indexOf('@'+author);" +
            "if(at0>=0)body=(body.substring(0,at0)+body.substring(at0+author.length+1)).trim();" +
            "}" +
            "if(!body||body.length<2||body.length>2000)continue;" +
            "if(!author)continue;" +
            "var key=author+'||'+body;" +
            "if(seen[key])continue;seen[key]=1;" +
            "var med=[];var seenSrc={};" +
            "var mels=a.querySelectorAll('img,video,source');" +
            "for(var mx=0;mx<mels.length;mx++){" +
            "var el=mels[mx];" +
            "var src=el.getAttribute('src')||'';" +
            "if(!src||src.indexOf('http')!=0)continue;" +
            "if(seenSrc[src])continue;" +
            "if(src.indexOf('profile_pic')>=0||src.indexOf('s206x206')>=0||src.indexOf('s150x150')>=0||src.indexOf('s320x320')>=0||src.indexOf('s480x480')>=0||src.indexOf('s640x640')>=0||src.indexOf('emoji')>=0||src.indexOf('avatar')>=0)continue;" +
            // 頭像有時沒有 profile_pic/sXXXxXXX 標記；Threads 會以小尺寸 img 放在作者區，
            // 不能把它當成留言媒體。真正的貼文圖片通常以大於 96dp 的元素呈現。
            "var rect=el.getBoundingClientRect?el.getBoundingClientRect():null;" +
            "if(rect&&rect.width>0&&rect.height>0&&rect.width<=96&&rect.height<=96)continue;" +
            "var pa=el.closest?a.closest('a'):null;" +
            "if(pa){var ph=pa.getAttribute('href')||'';if(ph.indexOf('/@')>=0)continue;}" +
            "seenSrc[src]=1;" +
            "var kind=(el.tagName==='VIDEO'||el.tagName==='SOURCE'||src.indexOf('.mp4')>=0)?'VIDEO':'IMAGE';" +
            "med.push({kind:kind,url:src});" +
            "}" +
            "out.push({author:author,text:body,media:med,likeCount:likeN});" +
            "}" +
            "return JSON.stringify(out);" +
            "}catch(e){return '[]';}})()"

    private const val EXTRACT_HTML_JS =
        "(function(){try{" +
            "var h=document.documentElement?document.documentElement.outerHTML:'';" +
            "if(h.length>400000)h=h.substring(0,400000);" +
            "return JSON.stringify(h);" +
            "}catch(e){return '\"\"';}})()"

    /**
     * 必須在主執行緒呼叫（內部會切回主執行緒）。結果一律回調在主執行緒。
     * 成功回傳 ResolvedPage；逾時/失敗回傳 null。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun resolve(
        activity: Activity,
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onResult: (ResolvedPage?) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onResult(null)
            return
        }
        activity.runOnUiThread {
            var done = false
            val main = Handler(Looper.getMainLooper())
            var webView: WebView? = null

            fun finish(result: ResolvedPage?) {
                if (done) return
                done = true
                main.removeCallbacksAndMessages(null)
                runCatching {
                    webView?.stopLoading()
                    webView?.destroy()
                }
                webView = null
                onResult(result)
            }

            // 逾時保護：若已拿到 finalUrl（即使 SJS 為空）也回傳，讓呼叫方用真短碼走 OkHttp 二次抓取
            var timeoutExtract: ((Boolean, Boolean) -> Unit)? = null
            main.postDelayed({
                if (!done) {
                    val cur = runCatching { webView?.url }.getOrNull().orEmpty()
                    if (cur.isNotBlank() && !cur.contains("/share/")) {
                        val fn = timeoutExtract
                        if (fn != null) fn(true, true)
                        else finish(ResolvedPage(finalUrl = cur, sjsBlocks = emptyList()))
                    } else {
                        finish(null)
                    }
                }
            }, timeoutMs)

            try {
                webView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.userAgentString = BOT_UA
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false // 留在 WebView 內跳轉，才能觀察到

                        override fun onPageFinished(view: WebView?, urlNow: String?) {
                            super.onPageFinished(view, urlNow)
                            checkResolved()
                        }

                        /** 每隔一段時間也檢查一次（應對 JS 延遲跳轉） */
                        private fun schedulePoll() {
                            if (done) return
                            main.postDelayed({
                                if (!done) {
                                    val cur = runCatching { webView?.url }.getOrNull()
                                    if (cur != null && !cur.contains("/share/")) {
                                        extractCurrent()
                                    } else {
                                        schedulePoll()
                                    }
                                }
                            }, POLL_INTERVAL_MS)
                        }

                        init { timeoutExtract = { a, b -> extractCurrent(a, b) } }

                        private fun checkResolved() {
                            if (done) return
                            val cur = runCatching { webView?.url }.getOrNull().orEmpty()
                            if (cur.isBlank() || cur.contains("/share/")) {
                                schedulePoll()
                                return
                            }
                            extractCurrent()
                        }

                        private fun extractCurrent(allowEmptySjs: Boolean = false, isTimeout: Boolean = false) {
                            if (done) return
                            val cur = runCatching { webView?.url }.getOrNull().orEmpty()
                            if (cur.isBlank() || cur.contains("/share/")) return
                            // 嘗試從 DOM 找 canonical（JS 跳轉後 location 可能仍是 /share/ 但內容已是正文）
                            try {
                                webView?.evaluateJavascript(EXTRACT_JS) { raw ->
                                    if (done) return@evaluateJavascript
                                    val blocks = parseJsStringArray(raw)
                                    if (blocks.isNotEmpty()) {
                                        finishWithText(cur, blocks)
                                    } else if (allowEmptySjs || isTimeout) {
                                        // 逾時或允許空 SJS：仍回傳 finalUrl + 渲染文字，讓呼叫方用真短碼走 OkHttp 二次抓取
                                        finishWithText(cur, emptyList())
                                    } else {
                                        // SJS 還沒水合完成：繼續等（逾時會收尾，屆時 allowEmptySjs=true）
                                        schedulePollRetry()
                                        return@evaluateJavascript
                                    }
                                }
                            } catch (_: Exception) {
                                // evaluate 失敗：繼續等
                            }
                        }

                        private fun finishWithText(cur: String, blocks: List<String>) {
                            try {
                                webView?.evaluateJavascript(EXTRACT_TEXT_JS) { rawText ->
                                    val (canon, text) = parseTextPayload(rawText)
                                    val effectiveUrl = when {
                                        canon.isNotBlank() && canon.contains("/post/") -> canon
                                        else -> cur
                                    }
                                    try {
                                        webView?.evaluateJavascript(EXTRACT_COMMENTS_JS) { rawComments ->
                                            val dom = parseDomComments(rawComments)
                                            try {
                                                webView?.evaluateJavascript(EXTRACT_HTML_JS) { rawHtml ->
                                                    val html = parseJsSingleString(rawHtml)
                                                    finish(
                                                        ResolvedPage(
                                                            finalUrl = effectiveUrl,
                                                            sjsBlocks = blocks,
                                                            renderedText = text,
                                                            renderedHtml = html,
                                                            domComments = dom
                                                        )
                                                    )
                                                }
                                            } catch (_: Exception) {
                                                finish(
                                                    ResolvedPage(
                                                        finalUrl = effectiveUrl,
                                                        sjsBlocks = blocks,
                                                        renderedText = text,
                                                        domComments = dom
                                                    )
                                                )
                                            }
                                        }
                                    } catch (_: Exception) {
                                        finish(
                                            ResolvedPage(
                                                finalUrl = effectiveUrl,
                                                sjsBlocks = blocks,
                                                renderedText = text
                                            )
                                        )
                                    }
                                }
                            } catch (_: Exception) {
                                finish(ResolvedPage(finalUrl = cur, sjsBlocks = blocks))
                            }
                        }

                        private fun schedulePollRetry() {
                            if (done) return
                            main.postDelayed({
                                if (!done) {
                                    val cur = runCatching { webView?.url }.getOrNull().orEmpty()
                                    if (cur.isNotBlank() && !cur.contains("/share/")) {
                                        extractCurrent()
                                    }
                                }
                            }, POLL_INTERVAL_MS)
                        }
                    }
                }
                webView?.loadUrl(url)
            } catch (_: Exception) {
                finish(null)
            }
        }
    }

    /** 解析 evaluateJavascript 回傳的 JSON 字串陣列（帶引號跳脫）。 */
    fun parseJsStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            // evaluate 回傳的是 JSON 編碼的字串：先解一層
            val unescaped = org.json.JSONTokener(raw).nextValue() as? String ?: return emptyList()
            val arr = org.json.JSONArray(unescaped)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseJsSingleString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            org.json.JSONTokener(raw).nextValue() as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun parseDomComments(raw: String?): List<DomComment> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            val unescaped = org.json.JSONTokener(raw).nextValue() as? String ?: return emptyList()
            val arr = org.json.JSONArray(unescaped)
            (0 until minOf(arr.length(), 50)).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val a = o.optString("author").trim().trimStart('@')
                val txRaw = o.optString("text").trim()
                if (a.isBlank() || txRaw.isBlank() || a.contains(" ") || a.length > 30) null
                else {
                    // Kotlin 端二道清洗（防舊版 JS / 特殊排版殘留）：去頭顯示名+時間，去尾數字列
                    val cleaned = sanitizeDomCommentText(txRaw, a)
                    val tx = cleaned.text.take(2000)
                    if (tx.isBlank() || tx.length < 2) null
                    else {
                        val mediaArr = o.optJSONArray("media")
                        val media = if (mediaArr == null) emptyList() else (0 until mediaArr.length()).mapNotNull { mi ->
                            val mo = mediaArr.optJSONObject(mi) ?: return@mapNotNull null
                            val url = mo.optString("url").trim()
                            if (!url.startsWith("http")) null
                            else com.reater.app.data.remote.FetchedMedia(
                                kind = mo.optString("kind").ifBlank { "IMAGE" },
                                remoteUrl = url
                            )
                        }
                        val likeFromJs = o.optInt("likeCount", 0)
                        DomComment(
                            author = a,
                            text = tx,
                            media = media.take(6),
                            likeCount = if (likeFromJs > 0) likeFromJs else cleaned.likeCount
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class SanitizedComment(val text: String, val likeCount: Int = 0)

    private val domActionLines = setOf(
        "like", "likes", "reply", "replies", "repost", "reposts",
        "share", "shares", "send",
        "讚", "喜歡", "愛心", "回覆", "回應", "留言",
        "轉發", "轉po", "轉帖", "分享", "傳送"
    )

    private val domTimestampRegex = Regex(
        """^(\d+\s*[秒分鐘小时時天週周月年]+|\d+\s*(s|sec|secs|m|min|mins|h|hr|hrs|d|day|days|w|week|weeks|mo|yr)\.?|昨天|前天|Yesterday|\d{4}[./-]\d{1,2}[./-]\d{1,2}|\d{1,2}[月/\-]\d{1,2}日?)$""",
        RegexOption.IGNORE_CASE
    )

    private val domCountRegex = Regex("""^[\d,，\s]+(\.\d+)?\s*[KkMm萬千]?$""")

    private fun parseDomCount(s: String): Int {
        return try {
            val t = s.trim().replace(",", "").replace("，", "")
            val m = Regex("""^(\d+(\.\d+)?)\s*([KkMm萬千])?$""").find(t) ?: return 0
            var v = m.groupValues[1].toDoubleOrNull() ?: return 0
            when (m.groupValues[3]) {
                "K", "k" -> v *= 1000
                "M", "m" -> v *= 1_000_000
                "萬" -> v *= 10_000
                "千" -> v *= 1000
            }
            v.toInt()
        } catch (_: Exception) {
            0
        }
    }

    /** 與 JS 端同規則的 Kotlin 清洗：回傳（純內文，尾端數字由上而下首個當讚數） */
    fun sanitizeDomCommentText(raw: String, author: String): SanitizedComment {
        val lines = raw.split("\n").map { it.trim() }
            .filter { it.isNotEmpty() && it != "Log in" && !it.contains("Log in") && !it.contains("登入") }
            .filter { !domActionLines.contains(it.lowercase()) }
            // 圖1 文字渲染修正：內文若夾雜「8小時」「昨天」這類時間行，舊版只去頭尾，
            // 中段時間行會殘留成換行亂文；此處凡是命中時間格式的行一律過濾。
            .filter { !domTimestampRegex.matches(it) }
            .toMutableList()
        // 去頭：顯示名 / @handle / 時間
        var hi = 0
        while (hi < lines.size) {
            val hs = lines[hi]
            val noAt = if (hs.startsWith("@")) hs.drop(1) else hs
            if (author.isNotBlank() && noAt.equals(author, ignoreCase = true)) {
                hi++
                continue
            }
            if (domTimestampRegex.matches(hs)) {
                hi++
                continue
            }
            // 顯示名重複行（如「王小明」/「王小明」連續兩行）：與 @handle 不同但明顯非內文時跳過
            // 保守處理：僅當該行超短（<=12字）且下一行仍是同作者相關行時才視為頭部雜訊——此處只處理最常見的 handle 變體
            break
        }
        val body = if (hi > 0) lines.drop(hi).toMutableList() else lines
        // 去尾：數字列＋動作詞殘留；由上而下首個數字才是讚數（圖1 修正）
        val tailNums = mutableListOf<Int>()
        while (body.isNotEmpty()) {
            val ts = body.last()
            if (domActionLines.contains(ts.lowercase())) {
                body.removeAt(body.lastIndex)
                continue
            }
            if (domCountRegex.matches(ts)) {
                tailNums.add(0, parseDomCount(ts))
                body.removeAt(body.lastIndex)
                continue
            }
            break
        }
        val likeCount = tailNums.firstOrNull() ?: 0
        while (body.isNotEmpty() && domTimestampRegex.matches(body.last())) {
            body.removeAt(body.lastIndex)
        }
        var text = body.joinToString("\n").trim()
        if (author.isNotBlank()) {
            val at = "@$author"
            val idx = text.indexOf(at)
            if (idx >= 0) text = (text.substring(0, idx) + text.substring(idx + at.length)).trim()
        }
        // 內文若仍以純數字開頭（如殘留計數），去掉首行數字避免「3K」混入正文
        text = text.lines().dropWhile { domCountRegex.matches(it.trim()) }
            .joinToString("\n").trim()
        return SanitizedComment(text = text, likeCount = likeCount)
    }

    fun parseTextPayload(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank() || raw == "null") return "" to ""
        return try {
            val unescaped = org.json.JSONTokener(raw).nextValue() as? String ?: return "" to ""
            val obj = org.json.JSONObject(unescaped)
            obj.optString("canon").orEmpty() to obj.optString("text").orEmpty()
        } catch (_: Exception) {
            "" to ""
        }
    }
}
