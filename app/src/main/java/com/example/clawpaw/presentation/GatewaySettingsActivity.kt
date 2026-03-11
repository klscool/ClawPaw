package com.example.clawpaw.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clawpaw.gateway.GatewayConnection
import com.example.clawpaw.ui.theme.ClawPawTheme

class GatewaySettingsActivity : ComponentActivity() {
    private val viewModel: GatewaySettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClawPawTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GatewaySettingsScreen(viewModel = viewModel, onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewaySettingsScreen(
    viewModel: GatewaySettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val host by viewModel.gatewayHost.collectAsStateWithLifecycle()
    val token by viewModel.gatewayToken.collectAsStateWithLifecycle()
    val password by viewModel.gatewayPassword.collectAsStateWithLifecycle()
    val maskedToken by viewModel.maskedGatewayToken.collectAsStateWithLifecycle()
    val maskedPassword by viewModel.maskedGatewayPassword.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val nodeDisplayName by viewModel.nodeDisplayName.collectAsStateWithLifecycle(initialValue = "")
    val gatewayPort by viewModel.gatewayPort.collectAsStateWithLifecycle(initialValue = 18789)
    var editHost by remember(host) { mutableStateOf(host) }
    var editNodeName by remember(nodeDisplayName) { mutableStateOf(nodeDisplayName) }
    var editPort by remember(gatewayPort) { mutableStateOf(gatewayPort.toString()) }
    var editToken by remember(token) { mutableStateOf(token) }
    var editPassword by remember(password) { mutableStateOf(password) }
    var showToken by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showClearKeysConfirm by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("OpenClaw Node 设置", style = MaterialTheme.typography.titleLarge) },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "作用：把本机注册为 OpenClaw 的「节点」，让电脑端可通过 OpenClaw 远程控制本机（点击、滑动、输入等）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "前提条件：① 已开启本应用的无障碍服务；② 电脑端已运行 OpenClaw 并开启 Gateway；③ 本机与电脑网络互通（同 WiFi、Tailscale 等）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = editNodeName,
                        onValueChange = { editNodeName = it },
                        label = { Text("节点名称") },
                        placeholder = { Text("留空则使用设备型号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editHost,
                        onValueChange = { editHost = it },
                        label = { Text("Gateway 地址（支持域名或 IP）") },
                        placeholder = { Text("例如 192.168.1.100 或 gateway.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "支持仅填主机（默认 ws），或完整地址如 wss://host:端口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { s -> editPort = s.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("端口") },
                        placeholder = { Text("18789") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "认证：Token 与密码二选一，连接时优先使用 Token。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gateway Token（可选）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (token.isEmpty()) "未设置" else "已设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (token.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (showToken) editToken else maskedToken.ifEmpty { editToken },
                        onValueChange = { editToken = it },
                        label = { Text("Token") },
                        placeholder = { Text("留空则仅设备签名或使用密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "隐藏 Token" else "显示 Token", color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gateway 密码（可选，与 Token 二选一）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (password.isEmpty()) "未设置" else "已设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (showPassword) editPassword else maskedPassword.ifEmpty { editPassword },
                        onValueChange = { editPassword = it },
                        label = { Text("密码") },
                        placeholder = { Text("部分网关支持密码认证") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "隐藏密码" else "显示密码", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.updateNodeDisplayName(editNodeName)
                        viewModel.updateHost(editHost)
                        editPort.toIntOrNull()?.let { viewModel.updateGatewayPort(it) }
                        viewModel.updateToken(editToken)
                        viewModel.updatePassword(editPassword)
                        viewModel.connect()
                        Toast.makeText(context, "正在连接…", Toast.LENGTH_SHORT).show()
                    },
                    enabled = connectionState !is GatewayConnection.ConnectionState.Connected && editHost.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("连接")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.disconnect()
                        Toast.makeText(context, "已断开", Toast.LENGTH_SHORT).show()
                    },
                    enabled = connectionState is GatewayConnection.ConnectionState.Connected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("断开")
                }
            }

            Text(
                text = when (connectionState) {
                    is GatewayConnection.ConnectionState.Connected -> "Gateway：已连接"
                    is GatewayConnection.ConnectionState.Connecting -> "Gateway：连接中…"
                    is GatewayConnection.ConnectionState.Disconnected -> "Gateway：未连接"
                    is GatewayConnection.ConnectionState.Error -> "Gateway：连接失败"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showClearKeysConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("清除密钥对（重新配对）")
            }
            if (showClearKeysConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearKeysConfirm = false },
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = { Text("确认清除密钥对", style = MaterialTheme.typography.titleLarge) },
                    text = { Text("清除后需重新连接，并在电脑端执行 openclaw nodes approve 重新授权。确定继续？", style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showClearKeysConfirm = false
                                viewModel.clearDeviceIdentity()
                                Toast.makeText(context, "已清除设备身份，请重新连接并在主机执行 openclaw nodes approve", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearKeysConfirm = false }) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

        }
    }
}
