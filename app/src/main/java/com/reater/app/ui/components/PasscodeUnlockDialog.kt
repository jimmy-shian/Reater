package com.reater.app.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reater.app.R
import com.reater.app.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun PasscodeUnlockDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    initialEmail: String = "",
    initialCode: String = ""
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val billingState by viewModel.billingUiState.collectAsState()
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    var isLinkMode by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    var passcode by remember(initialCode) { mutableStateOf(initialCode) }
    var emailInput by remember(initialEmail) { mutableStateOf(initialEmail) }
    var isVerifying by remember { mutableStateOf(false) }

    val unlockScrollState = rememberScrollState()
    DismissFocusOnScroll(unlockScrollState, focusManager)

    fun parseUnlockLink(input: String): Pair<String, String>? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (uri != null && uri.isHierarchical) {
            val email = uri.getQueryParameter("email")?.trim().orEmpty()
            val code = (uri.getQueryParameter("code") ?: uri.getQueryParameter("key"))?.trim().orEmpty()
            if (email.isNotBlank() && code.isNotBlank()) return email to code
        }
        val emailMatch = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").find(trimmed)?.value
        val codeMatch = Regex("""PRO-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}""", RegexOption.IGNORE_CASE).find(trimmed)?.value
            ?: Regex("""(?:code|key)[=:\s]+([A-Za-z0-9_-]+)""").find(trimmed)?.groups?.get(1)?.value
        if (!emailMatch.isNullOrBlank() && !codeMatch.isNullOrBlank()) {
            return emailMatch to codeMatch
        }
        return null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DialogHeader(
                    title = "開通 Reater Pro",
                    onClose = onDismiss,
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(unlockScrollState)
                        .dismissFocusOnTap(focusManager)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ProCopy.UNLOCK_DESC,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLinkMode) "貼上一鍵開通連結 (單欄自動解析)" else "序號通行碼 / 授權信箱開通",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = {
                                isLinkMode = !isLinkMode
                                if (!isLinkMode && linkInput.isNotBlank()) {
                                    parseUnlockLink(linkInput)?.let { (e, c) ->
                                        emailInput = e
                                        passcode = c
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mode_switch),
                                contentDescription = "切換輸入模式",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (isLinkMode) {
                        OutlinedTextField(
                            value = linkInput,
                            onValueChange = {
                                linkInput = it
                                parseUnlockLink(it)?.let { (e, c) ->
                                    emailInput = e
                                    passcode = c
                                }
                            },
                            placeholder = {
                                Text(
                                    "貼上 reater:... 或 http://... 一行文字",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        val parsed = parseUnlockLink(linkInput)
                        if (parsed != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "已識別信箱：${parsed.first} ｜ 序號：${parsed.second}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = passcode,
                            onValueChange = { passcode = it },
                            placeholder = {
                                Text(
                                    "輸入專屬開通序號 (如 PRO-XXXX-XXXX-XXXX)",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            placeholder = {
                                Text(
                                    "輸入註冊的 Email (必填，與序號綁定)",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:jimmy910824@gmail.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "Reater Pro 授權申請")
                                    putExtra(Intent.EXTRA_TEXT, "您好，我想申請 Reater Pro 授權憑證 / 密碼。\nEmail: $emailInput")
                                }
                                runCatching { context.startActivity(intent) }
                                    .onFailure { Toast.makeText(context, "請手動寄信至 jimmy910824@gmail.com", Toast.LENGTH_LONG).show() }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(
                                horizontal = 4.dp,
                                vertical = 8.dp
                            )
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "聯繫作者信箱",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                val targetCode: String
                                val targetEmail: String
                                if (isLinkMode) {
                                    val parsed = parseUnlockLink(linkInput)
                                    if (parsed == null) {
                                        Toast.makeText(context, "無法解析連結，請確認格式（需含 email 與 code 參數）或切換雙欄手動輸入", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    targetEmail = parsed.first
                                    targetCode = parsed.second
                                } else {
                                    targetEmail = emailInput
                                    targetCode = passcode
                                }

                                isVerifying = true
                                coroutineScope.launch {
                                    val success = viewModel.unlockWithPasscode(targetCode, targetEmail)
                                    isVerifying = false
                                    if (success) {
                                        Toast.makeText(context, "Reater Pro 已成功解鎖！", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "授權碼或密碼不正確，請確認後重試或聯繫作者。", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = (if (isLinkMode) linkInput.isNotBlank() else passcode.isNotBlank()) && !isVerifying,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(
                                horizontal = 4.dp,
                                vertical = 8.dp
                            )
                        ) {
                            Text(
                                if (isVerifying) "驗證中..." else "驗證並開通",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Google Play Purchase Section
                    Text(
                        "透過 Google Play 一次性購買",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp,
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "價格：${billingState.price ?: "讀取中..."}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.restorePro() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "恢復已購項目",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (activity != null) viewModel.buyPro(activity)
                                else Toast.makeText(context, "無法啟動 Google Play 購買流程", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Google Play 購買",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
