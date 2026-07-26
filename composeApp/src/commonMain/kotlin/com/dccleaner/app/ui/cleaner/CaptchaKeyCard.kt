package com.dccleaner.app.ui.cleaner

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import com.dccleaner.app.ui.theme.dccleanerSwitchColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CaptchaKeyCard(
    uiColors: UiColors,
    twocaptchaKey: String,
    onTwocaptchaKeyChange: (String) -> Unit,
    isTwocaptchaValid: Boolean?,
    onTwocaptchaValidChange: (Boolean?) -> Unit,
    isCheckingTwocaptcha: Boolean,
    onIsCheckingTwocaptchaChange: (Boolean) -> Unit,
    coroutine: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onValidateTwocaptchaKey: suspend (String) -> Boolean
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card
    var isCaptchaSettingEnabled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(twocaptchaKey) {
        if (twocaptchaKey.isNotBlank()) isCaptchaSettingEnabled = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.AccountBox,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "2captcha 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )

                    isTwocaptchaValid?.let { isValid ->
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isValid) uiColors.success else uiColors.danger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Switch(
                    checked = isCaptchaSettingEnabled,
                    onCheckedChange = { isCaptchaSettingEnabled = it },
                    colors = dccleanerSwitchColors(uiColors)
                )
            }

            if (!isCaptchaSettingEnabled) {
                return@Column
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = twocaptchaKey,
                onValueChange = {
                    onTwocaptchaKeyChange(it)
                    onTwocaptchaValidChange(null)
                },
                label = { Text("2captcha API 키") },
                placeholder = { Text("2captcha API 키를 입력하세요") },
                leadingIcon = {
                    Icon(Icons.Default.AccountBox, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors),
                singleLine = true,
                enabled = !isCheckingTwocaptcha
            )

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (twocaptchaKey.isNotBlank()) {
                        coroutine.launch {
                            onIsCheckingTwocaptchaChange(true)
                            val success = onValidateTwocaptchaKey(twocaptchaKey)
                            onTwocaptchaValidChange(success)
                            onIsCheckingTwocaptchaChange(false)

                            if (success) {
                                snackbarHostState.showSnackbar("2captcha 키가 성공적으로 설정되었습니다")
                            } else {
                                snackbarHostState.showSnackbar("유효하지 않은 2captcha 키입니다")
                            }
                        }
                    }
                },
                enabled = twocaptchaKey.isNotBlank() && !isCheckingTwocaptcha,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (isTwocaptchaValid) {
                        true -> uiColors.success
                        false -> uiColors.danger
                        null -> primaryColor
                    }
                )
            ) {
                if (isCheckingTwocaptcha) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("확인 중...")
                } else {
                    when (isTwocaptchaValid) {
                        true -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("설정 완료")
                        }

                        false -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("다시 시도")
                        }

                        null -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("키 확인")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "2captcha.com에서 API 키를 발급받으세요\n캡챠 해결이 필요할 때 자동으로 사용됩니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
