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
        val text: String
    )

    const val DEFAULT_TIMEOUT_MS = 25_000L
    private const val POLL_INTERVAL_MS = 1_000L

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /** JS：抽出所有含 thread_items 的 SJS 塊（只取前 40 塊，避免回傳過大） */
    private const val EXTRACT_JS =
        "(function(){try{" +
            "var out=[];" +
            "var ss=document.querySelectorAll('script[type=\"application/json\"][data-sjs]');" +
            "for(var i=0;i<ss.length&&out.length<60;i++){" +
            "var t=ss[i].textContent||'';" +
            "if(t.indexOf('thread_items')>=0)out.push(t);" +
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

    /** JS：DOM 直抽留言（免登入渲染後兜底） */
    private val EXTRACT_COMMENTS_JS: String =
        "(function(){try{" +
            "var out=[];var seen={};" +
            "var arts=document.querySelectorAll('article');" +
            "if(!arts||arts.length==0)arts=document.querySelectorAll('[data-pressable-container]');" +
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
            "if(s==='Like'||s==='Reply'||s==='Repost'||s==='Share')continue;" +
            "if(s.indexOf('Log in')>=0||s.indexOf('登入')>=0)continue;" +
            "cleaned.push(s);" +
            "}" +
            "var body=cleaned.join(String.fromCharCode(10));" +
            "if(author){" +
            "var at0=body.indexOf('@'+author);" +
            "if(at0>=0)body=(body.substring(0,at0)+body.substring(at0+author.length+1)).trim();" +
            "}" +
            "if(!body||body.length<2||body.length>2000)continue;" +
            "if(!author)continue;" +
            "var key=author+'||'+body;" +
            "if(seen[key])continue;seen[key]=1;" +
            "out.push({author:author,text:body});" +
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
                    settings.userAgentString = DESKTOP_UA
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
                val tx = o.optString("text").trim()
                if (a.isBlank() || tx.isBlank() || a.contains(" ") || a.length > 30) null
                else DomComment(author = a, text = tx.take(2000))
            }
        } catch (_: Exception) {
            emptyList()
        }
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
