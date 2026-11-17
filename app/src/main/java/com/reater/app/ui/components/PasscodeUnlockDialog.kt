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
import androidx.compose.ui.res.stringResource
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
                    title = stringResource(R.string.passcode_title),
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
                        text = stringResource(R.string.pro_upsell_body),
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
                            text = if (isLinkMode) stringResource(R.string.passcode_link_mode_hint) else stringResource(R.string.passcode_manual_mode_hint),
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
                                contentDescription = stringResource(R.string.passcode_switch_mode_desc),
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
                                    stringResource(R.string.passcode_link_placeholder),
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
                                text = stringResource(R.string.passcode_parsed_email_code, parsed.first, parsed.second),
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
                                    stringResource(R.string.passcode_code_placeholder),
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
                                    stringResource(R.string.passcode_email_placeholder),
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
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.passcode_email_subject))
                                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.passcode_email_intent_text, emailInput))
                                }
                                runCatching { context.startActivity(intent) }
                                    .onFailure { Toast.makeText(context, context.getString(R.string.passcode_manual_email_hint), Toast.LENGTH_LONG).show() }
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
                                stringResource(R.string.passcode_contact_author),
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
                                        Toast.makeText(context, context.getString(R.string.passcode_link_parse_error), Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, context.getString(R.string.passcode_unlock_success), Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.passcode_verify_failed), Toast.LENGTH_SHORT).show()
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
                                if (isVerifying) stringResource(R.string.passcode_verifying) else stringResource(R.string.passcode_verify_and_unlock),
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
                        stringResource(R.string.passcode_play_billing_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp,
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.passcode_play_billing_price, billingState.price ?: stringResource(R.string.passcode_price_loading)),
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
                                stringResource(R.string.passcode_play_billing_restore),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (activity != null) viewModel.buyPro(activity)
                                else Toast.makeText(context, context.getString(R.string.passcode_play_billing_error), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.passcode_play_billing_button),
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
