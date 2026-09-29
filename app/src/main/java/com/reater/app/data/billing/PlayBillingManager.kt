package com.reater.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.reater.app.BuildConfig
import com.reater.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class BillingUiState(
    val price: String? = null,
    val isPending: Boolean = false,
    val message: String? = null
)

@Singleton
class PlayBillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository
) : PurchasesUpdatedListener {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionPending = false

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun connectAndRefresh() {
        if (billingClient.isReady) {
            refreshPurchases()
            queryProductPrice()
            return
        }
        if (connectionPending) return
        connectionPending = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connectionPending = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshPurchases()
                    queryProductPrice()
                } else {
                    _uiState.value = _uiState.value.copy(message = result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                connectionPending = false
            }
        })
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) {
            connectAndRefresh()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.value = _uiState.value.copy(message = result.debugMessage)
                return@queryPurchasesAsync
            }
            val owned = purchases.filter { PRODUCT_ID in it.products }
            _uiState.value = _uiState.value.copy(
                isPending = owned.any { it.purchaseState == Purchase.PurchaseState.PENDING }
            )
            val purchased = owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (purchased.isEmpty()) {
                persistEntitlement(false)
            } else {
                purchased.forEach(::processPurchased)
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            connectAndRefresh()
            _uiState.value = _uiState.value.copy(message = "正在連線至 Google Play，請稍後再試。")
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient.queryProductDetailsAsync(params) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.value = _uiState.value.copy(message = result.debugMessage)
                return@queryProductDetailsAsync
            }
            val details = response.productDetailsList.firstOrNull()
            if (details == null) {
                _uiState.value = _uiState.value.copy(message = "Google Play 目前無法提供 Pro 商品。")
                return@queryProductDetailsAsync
            }
            launchProductFlow(activity, details)
        }
    }

    private fun queryProductPrice() {
        if (!billingClient.isReady) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient.queryProductDetailsAsync(params) { result, response ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val offer = response.productDetailsList.firstOrNull()
                    ?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                _uiState.value = _uiState.value.copy(price = offer?.formattedPrice)
            }
        }
    }

    private fun launchProductFlow(activity: Activity, details: ProductDetails) {
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        val offerToken = offer?.offerToken
        if (!offerToken.isNullOrEmpty()) paramsBuilder.setOfferToken(offerToken)
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.value = _uiState.value.copy(message = result.debugMessage)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty()
                .filter { PRODUCT_ID in it.products }
                .forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> processPurchased(purchase)
                        Purchase.PurchaseState.PENDING -> _uiState.value =
                            _uiState.value.copy(isPending = true, message = "付款處理中，完成後 Pro 會自動開通。")
                    }
                }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _uiState.value = _uiState.value.copy(message = result.debugMessage)
        }
    }

    private fun processPurchased(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || PRODUCT_ID !in purchase.products) return
        if (purchase.isAcknowledged) {
            persistEntitlement(true)
            _uiState.value = _uiState.value.copy(isPending = false, message = "Reater Pro 已開通。")
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                persistEntitlement(true)
                _uiState.value = _uiState.value.copy(isPending = false, message = "Reater Pro 已開通。")
            } else {
                _uiState.value = _uiState.value.copy(message = "購買已完成，確認授權時發生問題；下次開啟會重試。")
            }
        }
    }

    private fun persistEntitlement(owned: Boolean) {
        appScope.launch { settingsRepository.setProEntitlementFromPlay(owned) }
    }

    companion object {
        // 商品 ID 統一由 app/build.gradle.kts 的 buildConfigField(PRODUCT_ID) 管理
        val PRODUCT_ID: String = BuildConfig.PRO_PRODUCT_ID
    }
}
