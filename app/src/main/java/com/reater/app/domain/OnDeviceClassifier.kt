package com.reater.app.domain

import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.ItemDao
import javax.inject.Inject
import javax.inject.Singleton

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

    companion object {
        /** 免費版自訂分類上限 */
        const val FREE_CUSTOM_CATEGORY_LIMIT = 3

        // 常用領域基礎關鍵字庫（當使用者尚未設定自訂關鍵字時，依照分類名稱自動匹配提供基本智慧分類能力）
        private val DOMAIN_KEYWORDS: Map<List<String>, List<String>> = mapOf(
            listOf("ai", "人工智慧", "提示詞", "prompt", "科技", "技術", "程式", "軟體", "工具", "code", "dev") to listOf(
                "提示詞", "prompt", "chatgpt", "gpt", "claude", "gemini", "openai", "llm", "大模型", "ai",
                "生成式", "midjourney", "stable diffusion", "cursor", "copilot", "github", "程式", "代碼",
                "軟體", "演算法", "開源", "api", "token", "agent", "機器學習", "深度學習", "transformer"
            ),
            listOf("投資", "理財", "股票", "財經", "金融", "商業", "經濟") to listOf(
                "股票", "etf", "投資", "理財", "美股", "台股", "基金", "大盤", "報酬率", "股息", "財報",
                "開戶", "證券", "虛擬貨幣", "比特幣", "crypto", "通膨", "匯率", "配息", "資產", "存股"
            ),
            listOf("美食", "餐廳", "料理", "食記", "吃", "咖啡", "甜點") to listOf(
                "美食", "餐廳", "好食", "好吃", "美味", "食記", "咖啡", "甜點", "料理", "早午餐", "拉麵",
                "菜單", "必吃", "火鍋", "居酒屋", "夜市", "小吃", "甜品", "下午茶", "推薦店家", "食譜"
            ),
            listOf("設計", "視覺", "ui", "ux", "排版", "美感", "藝術") to listOf(
                "設計", "ui", "ux", "figma", "排版", "視覺", "字型", "配色", "切版", "介面",
                "平面設計", "動效", "插畫", "美編", "使用者體驗", "icon"
            ),
            listOf("工作", "職場", "商業", "行銷", "職涯", "管理", "效率") to listOf(
                "職場", "工作", "行銷", "商業", "面試", "履歷", "管理", "團隊", "創業", "專案",
                "職涯", "升遷", "轉職", "主管", "薪水", "業績", "開會", "生產力", "效率"
            ),
            listOf("生活", "日常", "心情", "隨筆", "日記", "閱讀", "書單") to listOf(
                "生活", "日常", "心情", "日記", "紀錄", "感受", "感悟", "今天", "週末", "放鬆",
                "療癒", "碎念", "生活感", "閱讀", "讀書心得", "書摘"
            ),
            listOf("旅遊", "旅行", "景點", "住宿", "出國", "日本") to listOf(
                "旅遊", "旅行", "景點", "機票", "住宿", "飯店", "自由行", "日本", "行程", "觀光",
                "打卡", "出國", "簽證", "民宿", "行李"
            ),
            listOf("運動", "健身", "健康", "飲食", "體育") to listOf(
                "運動", "健身", "慢跑", "重訓", "健康", "飲食", "減肥", "減脂", "馬拉松", "瑜伽",
                "有氧", "核心", "增肌", "深蹲", "體態"
            ),
            listOf("娛樂", "電影", "動漫", "追劇", "影視", "音樂", "遊戲") to listOf(
                "電影", "影集", "追劇", "動漫", "漫畫", "心得", "推薦", "演員", "劇情", "劇透",
                "配音", "上映", "第一季", "第二季", "遊戲", "steam", "switch", "音樂"
            )
        )

        private val STOP_WORDS_EN = setOf("http", "https", "com", "www", "threads", "net", "org", "html", "the", "and", "for", "with", "this", "that")
        private val STOP_WORDS_ZH = setOf("這個", "那個", "一個", "沒有", "不是", "就是", "可以", "我們", "他們", "你們", "自己", "因為", "所以", "如果", "但是", "而且", "雖然", "已經", "還是", "覺得", "知道", "看到", "今天", "現在", "時候", "大家", "什麼", "怎麼", "這樣", "那樣", "很多", "一些", "非常")
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
        bodyScope: String
    ): ClassificationResult {
        val categories = categoryDao.getAllCategories()
        if (categories.isEmpty()) {
            return ClassificationResult(null, "未分類", 0)
        }

        val categoryScores = mutableMapOf<Long, Int>()
        val combinedText = (titleScope + " " + bodyScope).lowercase()
        val titleLower = titleScope.lowercase()
        val bodyLower = bodyScope.lowercase()

        // 1. 分類名稱直接比對（若內文或標題直接提到分類名稱，高權重）
        for (cat in categories) {
            val nameLower = cat.name.trim().lowercase()
            if (nameLower.length >= 2 && combinedText.contains(nameLower)) {
                val bonus = if (titleLower.contains(nameLower)) 20 else 10
                categoryScores[cat.id] = categoryScores.getOrDefault(cat.id, 0) + bonus
            }
        }

        // 2. 基礎領域關鍵字庫比對（PRO 內建基礎關鍵字分類，無須手動設定）
        for (cat in categories) {
            val catNameLower = cat.name.trim().lowercase()
            for ((domainNames, kwList) in DOMAIN_KEYWORDS) {
                val matchesDomain = domainNames.any { domain ->
                    catNameLower.contains(domain) || domain.contains(catNameLower)
                }
                if (matchesDomain) {
                    for (kw in kwList) {
                        if (combinedText.contains(kw)) {
                            val kwBonus = if (titleLower.contains(kw)) 4 else 2
                            categoryScores[cat.id] = categoryScores.getOrDefault(cat.id, 0) + kwBonus
                        }
                    }
                }
            }
        }

        // 3. 歷史分類關聯學習（使用者過去把包含特定詞的文章分類到 A，下次出現該特徵詞自動歸入 A）
        //    只採用「對特定分類具有區別度」的特徵詞，避免常見通用詞讓最大分類總是勝出。
        val preHistoryScores = categoryScores.toMap()
        runCatching {
            val historySummaries = itemDao.getRecentCategorizedSummaries()
            if (historySummaries.isNotEmpty()) {
                val candidateTerms = extractCandidateTerms(combinedText)
                if (candidateTerms.isNotEmpty()) {
                    val termToCategoryCount = mutableMapOf<String, MutableMap<Long, Int>>()
                    for (summary in historySummaries) {
                        val historyText = (summary.topicTag + " " + summary.bodyText + " " + summary.commentsText).lowercase()
                        for (term in candidateTerms) {
                            if (historyText.contains(term)) {
                                val catMap = termToCategoryCount.getOrPut(term) { mutableMapOf() }
                                catMap[summary.categoryId] = (catMap[summary.categoryId] ?: 0) + 1
                            }
                        }
                    }

                    for ((term, catCounts) in termToCategoryCount) {
                        val totalHits = catCounts.values.sum()
                        if (totalHits <= 0) continue
                        // 出現在超過 3 個不同分類的詞太通用，不具區別力，跳過
                        if (catCounts.size > 3) continue
                        val isTitleHit = titleLower.contains(term)
                        val termBaseWeight = if (term.length >= 3) 6 else 4
                        for ((catId, count) in catCounts) {
                            val exclusivity = count.toFloat() / totalHits.toFloat()
                            // 詞在該分類的佔比低於 40%，不具區別度，跳過
                            if (exclusivity < 0.4f) continue
                            // 限制單詞的歷史次數上限，避免大分類靠文章量取勝
                            val cappedCount = count.coerceAtMost(5)
                            val weight = (cappedCount * termBaseWeight * exclusivity).toInt().coerceAtLeast(1)
                            val finalBonus = if (isTitleHit) weight * 2 else weight
                            categoryScores[catId] = categoryScores.getOrDefault(catId, 0) + finalBonus
                        }
                    }
                }
            }
        }
        // 歷史學習的總貢獻上限：避免歷史信號壓過關鍵字比對
        for ((catId, totalScore) in categoryScores.toMap()) {
            val pre = preHistoryScores.getOrDefault(catId, 0)
            val historyBonus = totalScore - pre
            if (historyBonus > 40) {
                categoryScores[catId] = pre + 40
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
                "TITLE" -> if (titleLower.contains(term)) hit = true
                "BODY" -> if (bodyLower.contains(term)) hit = true
                else -> {
                    if (titleLower.contains(term)) {
                        hit = true
                        weightMultiplier += 1
                    } else if (bodyLower.contains(term)) {
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
    private fun extractCandidateTerms(text: String): Set<String> {
        val terms = mutableSetOf<String>()
        // 1. Hashtags: #xxx
        Regex("#([\\p{L}\\p{N}_]+)").findAll(text).forEach { m ->
            val tag = m.groupValues[1].trim()
            if (tag.length >= 2) terms.add(tag)
        }
        // 2. 引號括號內的詞: 「...」、【...】、"..."
        Regex("[「【『\"'“]([^」】』\"'”\\n]{2,15})[」】』\"'”]").findAll(text).forEach { m ->
            val phrase = m.groupValues[1].trim()
            if (phrase.length in 2..15) terms.add(phrase)
        }
        // 3. 英文/數字詞彙 (>= 3 碼，排除常見非資訊詞)
        Regex("[a-zA-Z0-9_]{3,20}").findAll(text).forEach { m ->
            val w = m.value.trim().lowercase()
            if (w !in STOP_WORDS_EN) terms.add(w)
        }
        // 4. CJK 分段短語滑窗（提取 3~4 字連續詞；2 字組合噪音太多，易讓最大分類靠通用詞取勝）
        val cleanCjk = text.replace(Regex("[^\\u4e00-\\u9fa5]"), " ")
        for (chunk in cleanCjk.split(" ").filter { it.length in 3..20 }) {
            for (len in 3..4.coerceAtMost(chunk.length)) {
                for (i in 0..(chunk.length - len)) {
                    val sub = chunk.substring(i, i + len)
                    if (sub !in STOP_WORDS_ZH) {
                        terms.add(sub)
                    }
                }
            }
        }
        return terms.take(50).toSet()
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
