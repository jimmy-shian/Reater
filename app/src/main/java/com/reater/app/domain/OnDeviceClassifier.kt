package com.reater.app.domain

import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.ItemDao
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Singleton
class OnDeviceClassifier @Inject constructor(
    private val categoryDao: CategoryDao,
    private val itemDao: ItemDao
) {

    data class ClassificationResult(
        val categoryId: Long?,
        val categoryName: String,
        val score: Int
    )

    private data class ExtractedTerms(
        val allTerms: Set<String>,
        val strongTerms: Set<String>
    )

    companion object {
        /** 免費版自訂分類上限 */
        const val FREE_CUSTOM_CATEGORY_LIMIT = 5

        private val ASCII_KW_REGEX_CACHE = ConcurrentHashMap<String, Regex>()
        private val URL_REGEX = Regex("https?://\\S+|www\\.\\S+")
        private val MENTION_REGEX = Regex("(?<![a-z0-9_])@[a-z0-9_.]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val CATEGORY_SPLIT_REGEX = Regex("[\\s/\\\\|·・\\-_&+、，,]+")
        private val ASCII_CJK_TOKEN_REGEX = Regex("([a-z0-9]+)|([\\u4e00-\\u9fa5]+)")
        private val LEADING_CATEGORY_VERBS = setOf('寫', '學', '做', '看', '拍', '聊', '談', '逛', '煮', '畫', '彈', '唱')
        private val CJK_FUNCTION_CHARS = setOf(
            '的', '了', '嗎', '呢', '吧', '啊', '喔', '哦', '啦', '嘛', '呀', '耶', '欸',
            '不', '沒', '很', '都', '也', '就', '才', '又', '還', '再', '只', '把', '被',
            '讓', '給', '在', '從', '到', '跟', '和', '與', '於', '之', '是', '這', '那',
            '哪', '誰', '什', '怎', '幾', '個', '篇', '則', '些', '我', '你', '他', '她',
            '它', '們'
        )
        private val CONVERSATIONAL_AFFIXES_ZH = setOf(
            "這個", "那個", "一個", "沒有", "不是", "就是", "可以", "我們", "他們", "你們",
            "因為", "所以", "如果", "但是", "而且", "雖然", "已經", "還是", "覺得", "知道",
            "看到", "大家", "什麼", "怎麼", "這樣", "那樣", "很多", "一些", "非常", "真的",
            "超級", "哈哈"
        )
        private val CJK_SPLIT_PARTICLE_REGEX = Regex("[的了嗎呢吧啊喔哦啦嘛呀耶欸]")
        private val STOP_WORDS_ZH_REGEX by lazy {
            Regex(STOP_WORDS_ZH.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) })
        }

        private fun Char.isAsciiAlnum(): Boolean = this in 'a'..'z' || this in '0'..'9'

        /** 清除 URL 與 @mention 噪音，並轉小寫壓縮空白 */
        internal fun stripNoise(text: String): String {
            if (text.isBlank()) return ""
            return text.lowercase()
                .replace(URL_REGEX, " ")
                .replace(MENTION_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
        }

        /** 比對關鍵字（ASCII 邊界避免 ui 命中 guitar、ai 命中 taiwan） */
        internal fun containsKeyword(textLower: String, kwLower: String): Boolean {
            if (kwLower.isEmpty() || textLower.isEmpty()) return false
            val needLeftBoundary = kwLower.first().isAsciiAlnum()
            val needRightBoundary = kwLower.last().isAsciiAlnum()
            if (!needLeftBoundary && !needRightBoundary) {
                return textLower.contains(kwLower)
            }
            val cacheKey = "${if (needLeftBoundary) '1' else '0'}${if (needRightBoundary) '1' else '0'}:$kwLower"
            val regex = ASCII_KW_REGEX_CACHE.getOrPut(cacheKey) {
                val prefix = if (needLeftBoundary) "(?<![a-z0-9])" else ""
                val suffix = if (needRightBoundary) "(?![a-z0-9])" else ""
                Regex(prefix + Regex.escape(kwLower) + suffix)
            }
            return regex.containsMatchIn(textLower)
        }

        /** 從複合分類名稱提取核心詞彙（如 美麗的文案→文案、GPT指令→gpt+指令、寫小說→小說、蘋果Mac→蘋果+mac） */
        internal fun extractCategoryCoreTerms(categoryName: String): List<String> {
            val fullLower = categoryName.trim().lowercase()
            if (fullLower.isEmpty()) return emptyList()
            val core = linkedSetOf<String>()

            fun addCandidate(raw: String) {
                val t = raw.trim().lowercase()
                if (t.length < 2 || t == fullLower) return
                if (t in STOP_WORDS_EN || t in STOP_WORDS_ZH) return
                core.add(t)
            }

            for (part in fullLower.split(CATEGORY_SPLIT_REGEX).filter { it.isNotBlank() }) {
                if (part != fullLower) {
                    addCandidate(part)
                }
                if (part.contains('的')) {
                    addCandidate(part.substringAfterLast('的'))
                    continue
                }
                if (part.contains('之')) {
                    addCandidate(part.substringAfterLast('之'))
                    continue
                }
                val segments = ASCII_CJK_TOKEN_REGEX.findAll(part).map { it.value }.toList()
                if (segments.size > 1) {
                    for (seg in segments) {
                        addCandidate(seg)
                    }
                }
                if (part.all { it in '\u4e00'..'\u9fa5' } && part.length >= 3 && part.first() in LEADING_CATEGORY_VERBS) {
                    addCandidate(part.drop(1))
                }
            }
            return core.toList()
        }

        /** 依分類名稱取得對應的領域關鍵字清單 */
        internal fun getDomainKeywordsForCategory(categoryName: String): List<String> {
            val catNameLower = categoryName.trim().lowercase()
            if (catNameLower.isEmpty()) return emptyList()
            val coreTerms = extractCategoryCoreTerms(categoryName)
            val result = linkedSetOf<String>()
            for ((domainNames, kwList) in DOMAIN_KEYWORDS) {
                if (matchesDomainNames(catNameLower, coreTerms, domainNames)) {
                    result.addAll(kwList)
                }
            }
            return result.toList()
        }

        private fun matchesDomainNames(
            catNameLower: String,
            coreTerms: List<String>,
            domainNames: List<String>
        ): Boolean {
            return domainNames.any { domain ->
                containsKeyword(catNameLower, domain) ||
                    (catNameLower.length >= 2 && containsKeyword(domain, catNameLower)) ||
                    coreTerms.any { core -> containsKeyword(core, domain) || domain == core }
            }
        }

        // 常用領域基礎關鍵字庫（當使用者尚未設定自訂關鍵字時，依照分類名稱自動匹配提供基本智慧分類能力）
        private val DOMAIN_KEYWORDS: Map<List<String>, List<String>> = mapOf(
            listOf("ai", "人工智慧", "gpt", "提示詞", "指令", "prompt", "llm", "科技") to listOf(
                "提示詞", "prompt", "chatgpt", "gpt", "gpt-4", "gpt4", "claude", "gemini", "openai", "anthropic",
                "llm", "大模型", "ai", "生成式", "midjourney", "stable diffusion", "flux", "sora", "perplexity",
                "deepseek", "cursor", "copilot", "rag", "fine-tune", "微調", "token", "agent", "mcp",
                "機器學習", "深度學習", "transformer", "指令集", "系統提示詞", "角色扮演指令", "ai工具"
            ),
            listOf("程式", "開發", "軟體", "code", "dev", "工程", "後端", "前端", "技術", "演算法") to listOf(
                "程式", "程式碼", "代碼", "軟體", "開發", "前端", "後端", "全端", "演算法", "資料結構",
                "開源", "github", "git", "api", "sdk", "docker", "kubernetes", "linux", "bash", "sql",
                "python", "kotlin", "java", "typescript", "javascript", "react", "compose", "flutter", "rust",
                "重構", "debug", "除錯", "部署", "ci/cd", "架構", "資料庫"
            ),
            listOf("設計", "視覺", "ui", "ux", "uiux", "排版", "美感", "介面", "平面") to listOf(
                "設計", "ui", "ux", "uiux", "figma", "sketch", "排版", "視覺", "字型", "字體",
                "配色", "色票", "切版", "介面", "使用者體驗", "使用體驗", "互動設計", "平面設計",
                "動效", "插畫", "海報", "留白", "網格系統", "設計系統", "design system", "wireframe", "prototype", "icon"
            ),
            listOf("蘋果", "mac", "apple", "macos", "macbook", "iphone", "ipad", "ios") to listOf(
                "蘋果", "apple", "mac", "macos", "macbook", "macbook pro", "macbook air", "imac",
                "mac mini", "mac studio", "iphone", "ipad", "ios", "ipados", "watchos", "apple watch",
                "airpods", "icloud", "airdrop", "finder", "homebrew", "xcode", "safari", "spotlight",
                "捷徑", "shortcuts", "m1", "m2", "m3", "m4", "apple silicon"
            ),
            listOf("音樂", "歌曲", "樂團", "演唱", "歌單", "聽歌") to listOf(
                "音樂", "歌曲", "新歌", "單曲", "專輯", "ep", "歌單", "歌詞", "副歌", "旋律",
                "編曲", "作曲", "作詞", "吉他", "鋼琴", "貝斯", "鼓手", "樂團", "主唱",
                "演唱會", "音樂祭", "live house", "spotify", "apple music", "kkbox", "翻唱",
                "爵士", "搖滾", "民謠", "嘻哈", "饒舌", "古典樂", "耳機", "音響", "黑膠"
            ),
            listOf("寫小說", "小說", "創作", "故事", "輕小說", "連載") to listOf(
                "小說", "寫小說", "寫作", "創作", "故事", "連載", "章節", "大綱", "世界觀", "設定集",
                "主角", "反派", "配角", "人設", "人物設定", "角色設定", "伏筆", "情節", "文筆",
                "起承轉合", "第一人稱", "第三人稱", "奇幻", "懸疑", "推理", "言情", "科幻", "輕小說", "同人"
            ),
            listOf("文案", "語錄", "金句", "閱讀", "書單", "散文", "詩") to listOf(
                "文案", "金句", "語錄", "佳句", "摘錄", "書摘", "書單", "閱讀", "讀書筆記", "讀書心得",
                "散文", "現代詩", "詩集", "句子", "段落", "社群文案", "行銷文案", "標題", "共鳴", "文字控"
            ),
            listOf("日本", "日旅", "東京", "大阪", "京都", "北海道", "沖繩") to listOf(
                "日本", "日旅", "東京", "大阪", "京都", "北海道", "札幌", "沖繩", "福岡", "名古屋",
                "橫濱", "奈良", "神戶", "輕井澤", "河口湖", "富士山", "新幹線", "日幣", "日圓",
                "壽司", "居酒屋", "溫泉", "溫泉旅館", "神社", "鳥居", "御朱印", "唐吉訶德",
                "jr", "suica", "icoca", "藥妝", "櫻花季", "楓葉季"
            ),
            listOf("自然", "風景", "戶外", "登山", "山林", "露營", "植物", "生態") to listOf(
                "自然", "大自然", "自然風景", "風景", "風景照", "森林", "山林", "登山", "百岳", "步道",
                "健行", "爬山", "露營", "野營", "星空", "銀河", "日出", "日落", "夕陽",
                "海邊", "海岸", "海景", "瀑布", "溪流", "雲海", "植物", "觀葉植物", "多肉",
                "野生動物", "生態", "國家公園", "賞花", "花海"
            ),
            listOf("旅遊", "旅行", "景點", "住宿", "出國", "自由行") to listOf(
                "旅遊", "旅行", "景點", "機票", "廉航", "住宿", "飯店", "青旅", "自由行", "獨旅",
                "行程", "觀光", "護照", "簽證", "民宿", "行李", "機場", "轉機", "自駕", "租車", "伴手禮"
            ),
            listOf("投資", "理財", "股票", "財經", "金融", "經濟", "etf") to listOf(
                "股票", "etf", "投資", "理財", "美股", "台股", "基金", "大盤", "報酬率", "殖利率",
                "股息", "財報", "開戶", "證券", "複委託", "虛擬貨幣", "比特幣", "crypto",
                "通膨", "匯率", "利率", "配息", "資產配置", "存股", "定期定額", "現金流"
            ),
            listOf("美食", "餐廳", "料理", "食記", "咖啡", "甜點", "小吃") to listOf(
                "美食", "餐廳", "好吃", "美味", "食記", "咖啡", "咖啡廳", "手沖", "甜點", "蛋糕",
                "料理", "早午餐", "拉麵", "菜單", "必吃", "火鍋", "居酒屋", "夜市", "小吃",
                "甜品", "下午茶", "食譜", "烘焙", "義大利麵", "燒肉", "滷肉飯", "宵夜"
            ),
            listOf("工作", "職場", "商業", "行銷", "職涯", "管理", "效率") to listOf(
                "職場", "工作", "行銷", "商業模式", "面試", "履歷", "管理", "團隊", "創業", "專案",
                "職涯", "升遷", "轉職", "離職", "主管", "同事", "薪水", "年終", "業績", "開會", "生產力", "效率", "簡報"
            ),
            listOf("生活", "日常", "心情", "隨筆", "日記") to listOf(
                "生活", "日常", "心情", "日記", "紀錄", "感悟", "生活感", "碎碎念", "療癒",
                "放鬆", "小確幸", "紓壓", "獨處", "居家", "整理", "收納", "儀式感"
            ),
            listOf("運動", "健身", "健康", "飲食", "體育", "減脂") to listOf(
                "運動", "健身", "健身房", "慢跑", "重訓", "健康", "減肥", "減脂", "增肌",
                "馬拉松", "瑜伽", "皮拉提斯", "有氧", "核心", "深蹲", "體態", "體脂", "蛋白質", "熱量"
            ),
            listOf("娛樂", "電影", "動漫", "追劇", "影視", "遊戲", "影集") to listOf(
                "電影", "影集", "追劇", "動漫", "動畫", "漫畫", "演員", "導演", "劇情", "劇透",
                "無雷", "影評", "觀後感", "配音", "上映", "第一季", "第二季", "片單",
                "遊戲", "電玩", "steam", "switch", "ps5", "實況"
            )
        )

        private val STOP_WORDS_EN = setOf(
            "http", "https", "com", "www", "threads", "net", "org", "html", "htm", "php", "asp",
            "utm", "ref", "share", "link",
            "the", "and", "for", "with", "this", "that", "from", "have", "has", "had",
            "was", "were", "are", "not", "but", "you", "your", "they", "their",
            "what", "when", "where", "which", "who", "how", "why",
            "just", "like", "more", "very", "really", "about", "into", "out",
            "can", "will", "would", "could", "should", "been", "being", "also", "than", "then",
            "some", "all", "one", "two", "get", "got", "see", "say", "said", "make", "made",
            "know", "think", "good", "best", "time", "day", "today", "lol", "omg", "btw", "idk", "imo", "tbh"
        )
        private val STOP_WORDS_ZH = setOf(
            "這個", "那個", "一個", "兩個", "沒有", "不是", "就是", "可以", "不能", "不要", "不用",
            "我們", "他們", "她們", "你們", "自己", "別人", "因為", "所以", "如果", "但是", "不過",
            "而且", "雖然", "已經", "還是", "或者", "覺得", "感覺", "以為", "知道", "了解", "發現",
            "看到", "看見", "今天", "昨天", "明天", "現在", "以前", "以後", "時候", "時間", "最近",
            "剛剛", "馬上", "大家", "有人", "什麼", "怎麼", "為什麼", "這樣", "那樣", "這麼", "那麼",
            "很多", "一些", "一點", "非常", "真的", "超級", "實在", "其實", "真的嗎", "對吧", "是不是",
            "有沒有", "好嗎", "謝謝", "感謝", "麻煩", "請問", "分享", "推薦", "心得", "問題", "辦法",
            "方式", "東西", "事情", "原因", "結果", "地方", "部分", "台灣", "台北", "網友", "原po",
            "留言", "回覆", "文章", "貼文", "照片", "影片", "截圖", "連結", "轉發", "收藏", "追蹤", "按讚",
            "哈哈", "哈哈哈", "哈哈哈哈", "笑死", "傻眼", "超好笑", "無言", "天啊", "天哪", "到底",
            "居然", "竟然", "果然", "難怪", "原本", "本來", "應該", "可能", "大概", "也許", "一定",
            "完全", "根本", "簡直", "幾乎", "差不多", "稍微", "比較", "特別", "尤其", "甚至",
            "總之", "順便", "補充", "更新"
        )
    }

    /**
     * 多維度智慧分類：
     * 1. 分類名稱命中：內文直接命中某分類名稱
     * 2. 基礎領域關鍵字比對：依分類名稱關聯領域關鍵字（即使未設定自訂關鍵字也有基本分類能力）
     * 3. 歷史分類關聯學習：分析過往使用者分類到各分類的文章特徵詞（如「提示詞」曾被歸至分類 A，則新文章命中「提示詞」即強烈推薦 A）
     * 4. 資料庫關鍵字比對：若有手動或自訂的 KeywordEntity 則加權計分
     */
    suspend fun classify(
        titleScope: String,
        bodyScope: String,
        excludeItemId: Long? = null
    ): ClassificationResult {
        val categories = categoryDao.getAllCategories()
        if (categories.isEmpty()) {
            return ClassificationResult(null, "未分類", 0)
        }

        val categoryScores = mutableMapOf<Long, Int>()
        val titleLower = stripNoise(titleScope)
        val bodyLower = stripNoise(bodyScope)
        val combinedText = "$titleLower $bodyLower".trim()

        // 1. 分類名稱直接比對與核心詞彙比對（支援複合名稱如 美麗的文案、GPT指令、寫小說、蘋果Mac）
        for (cat in categories) {
            val nameLower = cat.name.trim().lowercase()
            if (nameLower.length >= 2 && containsKeyword(combinedText, nameLower)) {
                val bonus = if (containsKeyword(titleLower, nameLower)) 20 else 10
                categoryScores[cat.id] = categoryScores.getOrDefault(cat.id, 0) + bonus
            }
            var coreBonusSum = 0
            for (coreTerm in extractCategoryCoreTerms(cat.name)) {
                if (containsKeyword(combinedText, coreTerm)) {
                    coreBonusSum += if (containsKeyword(titleLower, coreTerm)) 6 else 3
                }
            }
            if (coreBonusSum > 0) {
                categoryScores[cat.id] = categoryScores.getOrDefault(cat.id, 0) + coreBonusSum.coerceAtMost(8)
            }
        }

        // 2. 基礎領域關鍵字庫比對（PRO 內建基礎關鍵字分類，使用邊界比對避免子字串誤判）
        for (cat in categories) {
            for (kw in getDomainKeywordsForCategory(cat.name)) {
                if (containsKeyword(combinedText, kw)) {
                    val kwBonus = if (containsKeyword(titleLower, kw)) 4 else 2
                    categoryScores[cat.id] = categoryScores.getOrDefault(cat.id, 0) + kwBonus
                }
            }
        }

        // 3. 歷史分類關聯學習（以分類文章數平方根正規化，避免大量文章分類壟斷）
        runCatching {
            val rawSummaries = itemDao.getRecentCategorizedSummaries()
            val historySummaries = if (excludeItemId != null) {
                rawSummaries.filter { it.id != excludeItemId }
            } else {
                rawSummaries
            }
            if (historySummaries.isNotEmpty()) {
                val docsPerCategory = historySummaries.groupingBy { it.categoryId }.eachCount()
                val extracted = extractCandidateTerms(combinedText)
                if (extracted.allTerms.isNotEmpty()) {
                    val termToCategoryCount = mutableMapOf<String, MutableMap<Long, Int>>()
                    for (summary in historySummaries) {
                        val historyText = stripNoise("${summary.topicTag} ${summary.bodyText} ${summary.commentsText}")
                        if (historyText.isEmpty()) continue
                        for (term in extracted.allTerms) {
                            if (containsKeyword(historyText, term)) {
                                val catMap = termToCategoryCount.getOrPut(term) { mutableMapOf() }
                                catMap[summary.categoryId] = (catMap[summary.categoryId] ?: 0) + 1
                            }
                        }
                    }

                    val catHistoryBonuses = mutableMapOf<Long, MutableList<Int>>()
                    for ((term, catCounts) in termToCategoryCount) {
                        val totalHits = catCounts.values.sum()
                        if (totalHits <= 0 || catCounts.size > 3) continue
                        val isStrong = term in extracted.strongTerms
                        val isTitleHit = containsKeyword(titleLower, term)
                        val termBaseWeight = if (term.length >= 3) 4 else 3
                        for ((catId, count) in catCounts) {
                            val docsInCat = docsPerCategory[catId] ?: 1
                            // 已有一定數量文章的分類，一般滑窗詞至少命中 2 篇才納入歷史特徵
                            if (docsInCat >= 3 && !isStrong && count < 2) continue
                            val exclusivity = count.toFloat() / totalHits.toFloat()
                            if (exclusivity < 0.5f) continue
                            val normalizedCount = (count.toDouble() / sqrt(docsInCat.toDouble())).coerceAtMost(2.0)
                            val baseScore = (normalizedCount * termBaseWeight * exclusivity).roundToInt()
                            val weight = if (docsInCat < 3) baseScore.coerceAtLeast(1) else baseScore
                            if (weight <= 0) continue
                            val finalBonus = if (isTitleHit) (weight * 3) / 2 else weight
                            catHistoryBonuses.getOrPut(catId) { mutableListOf() }.add(finalBonus)
                        }
                    }

                    for ((catId, bonuses) in catHistoryBonuses) {
                        val totalHistoryBonus = bonuses.sortedDescending().take(3).sum().coerceAtMost(8)
                        categoryScores[catId] = categoryScores.getOrDefault(catId, 0) + totalHistoryBonus
                    }
                }
            }
        }

        // 4. 資料庫關鍵字庫比對（若有手動新增或匯入的 KeywordEntity）
        val keywords = runCatching { categoryDao.getAllKeywords() }.getOrDefault(emptyList())
        for (kw in keywords) {
            val term = kw.term.lowercase().trim()
            if (term.isEmpty()) continue
            var hit = false
            var weightMultiplier = kw.weight
            when (kw.matchScope) {
                "TITLE" -> if (containsKeyword(titleLower, term)) hit = true
                "BODY" -> if (containsKeyword(bodyLower, term)) hit = true
                else -> {
                    if (containsKeyword(titleLower, term)) {
                        hit = true
                        weightMultiplier += 1
                    } else if (containsKeyword(bodyLower, term)) {
                        hit = true
                    }
                }
            }
            if (hit) {
                val currentScore = categoryScores.getOrDefault(kw.categoryId, 0)
                categoryScores[kw.categoryId] = currentScore + (weightMultiplier * 3)
            }
        }

        val best = categoryScores.maxByOrNull { it.value }
        if (best != null && best.value >= 2) {
            val matchedCategory = categories.firstOrNull { it.id == best.key }
            if (matchedCategory != null) {
                return ClassificationResult(
                    categoryId = matchedCategory.id,
                    categoryName = matchedCategory.name,
                    score = best.value
                )
            }
        }

        val defaultCat = categories.firstOrNull { it.isDefault }
        return ClassificationResult(
            categoryId = defaultCat?.id,
            categoryName = defaultCat?.name ?: "未分類",
            score = 0
        )
    }

    /**
     * 從文章中提取高價值特徵詞（標籤、引號詞、實質 CJK 詞彙與英文單詞）
     */
    private fun extractCandidateTerms(textLower: String): ExtractedTerms {
        val strongTerms = linkedSetOf<String>()
        val weakTerms = linkedSetOf<String>()

        fun isFillerTerm(term: String): Boolean {
            if (term in STOP_WORDS_ZH || term in STOP_WORDS_EN) return true
            if (term.all { it in CJK_FUNCTION_CHARS }) return true
            return false
        }

        // 1. Hashtags: #xxx
        Regex("#([\\p{L}\\p{N}_]+)").findAll(textLower).forEach { m ->
            val tag = m.groupValues[1].trim()
            if (tag.length >= 2 && !isFillerTerm(tag)) {
                strongTerms.add(tag)
            }
        }
        // 2. 引號括號內的詞: 「...」、【...】、"..."
        Regex("[「【『\"'“]([^」】』\"'”\\n]{2,15})[」】』\"'”]").findAll(textLower).forEach { m ->
            val phrase = m.groupValues[1].trim()
            if (phrase.length in 2..15 && !isFillerTerm(phrase)) {
                strongTerms.add(phrase)
            }
        }
        // 3. 英文/數字詞彙 (>= 3 碼，排除純數字與常見非資訊詞)
        Regex("[a-z0-9_]{3,20}").findAll(textLower).forEach { m ->
            val w = m.value.trim('_').lowercase()
            if (w.length >= 3 && !w.all { it.isDigit() } && w !in STOP_WORDS_EN) {
                weakTerms.add(w)
            }
        }
        // 4. CJK 分段短語滑窗（先遮罩廢話與語氣助詞，避免跨詞切出偽詞）
        val maskedCjk = textLower
            .replace(STOP_WORDS_ZH_REGEX, " ")
            .replace(CJK_SPLIT_PARTICLE_REGEX, " ")
        val cleanCjk = maskedCjk.replace(Regex("[^\\u4e00-\\u9fa5]"), " ")
        for (rawChunk in cleanCjk.split(" ")) {
            val chunk = rawChunk.trim { it in CJK_FUNCTION_CHARS }
            if (chunk.length !in 2..20) continue
            if (chunk.length == 2) {
                if (!isFillerTerm(chunk) && chunk !in CONVERSATIONAL_AFFIXES_ZH) {
                    weakTerms.add(chunk)
                }
                continue
            }
            for (len in 3..4.coerceAtMost(chunk.length)) {
                for (i in 0..(chunk.length - len)) {
                    val sub = chunk.substring(i, i + len)
                    val trimmed = sub.trim { it in CJK_FUNCTION_CHARS }
                    if (trimmed.length < 2 || isFillerTerm(trimmed)) continue
                    val prefix2 = trimmed.take(2)
                    val suffix2 = trimmed.takeLast(2)
                    if (prefix2 in CONVERSATIONAL_AFFIXES_ZH || suffix2 in CONVERSATIONAL_AFFIXES_ZH) continue
                    weakTerms.add(trimmed)
                }
            }
        }

        val combined = (strongTerms + weakTerms).take(50).toSet()
        return ExtractedTerms(allTerms = combined, strongTerms = strongTerms.intersect(combined))
    }

    /**
     * Seeds initial 8 categories with 120-160 normalized keywords.
     */
    suspend fun seedInitialCategoriesIfEmpty() {
        runCatching {
            categoryDao.deleteDefaultCategories()
            categoryDao.deleteOrphanKeywords()
            categoryDao.nullOutDanglingCategoryRefs()
        }
    }
}
