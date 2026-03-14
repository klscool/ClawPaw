package com.example.clawpaw.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.clawpaw.R

/**
 * 扫码/配对码解析成功后的确认弹窗：展示 URL、扫到的 Token，可选本地连接，选择注册角色后确认。
 */
@Composable
fun PairRoleConfirmDialog(
    host: String,
    port: Int,
    token: String,
    onDismiss: () -> Unit,
    onChooseNode: (host: String, port: Int, token: String) -> Unit,
    onChooseOperator: (host: String, port: Int, token: String) -> Unit
) {
    var effectiveHost by remember(host) { mutableStateOf(host) }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    val urlDisplay = "$effectiveHost:$port"
    val tokenDisplay = if (token.length > 24) "${token.take(12)}…${token.takeLast(8)}" else token

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.pair_role_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.pair_confirm_url),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { effectiveHost = "127.0.0.1" },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.pair_confirm_use_local),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = urlDisplay,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.pair_confirm_token),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tokenDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.pair_confirm_choose),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.pair_confirm_role_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedRole == "node") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (selectedRole == "node") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRole = "node" }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.pair_as_node),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedRole == "node") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.pair_role_node_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedRole == "node") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedRole == "operator") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (selectedRole == "operator") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRole = "operator" }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.pair_as_operator),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedRole == "operator") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.pair_role_operator_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedRole == "operator") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        when (selectedRole) {
                            "node" -> onChooseNode(effectiveHost, port, token)
                            "operator" -> onChooseOperator(effectiveHost, port, token)
                            else -> { }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedRole != null
                ) {
                    Text(stringResource(R.string.pair_confirm_confirm))
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        stringResource(R.string.pair_confirm_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
