package com.example.clawpaw.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clawpaw.ssh.PortMapping
import com.example.clawpaw.ssh.ReversePortMapping
import com.example.clawpaw.ssh.SshPrefs
import com.example.clawpaw.ui.theme.ClawPawTheme
import com.example.clawpaw.ui.theme.clawpaw_primary

@OptIn(ExperimentalMaterial3Api::class)
class OnboardingActivity : ComponentActivity() {
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClawPawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onFinish = {
                            viewModel.completeOnboarding()
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("from_onboarding", true)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit
) {
    val step by viewModel.currentStep.collectAsStateWithLifecycle()
    val stepIndex = viewModel.stepIndex()
    val accessibilityChoice by viewModel.accessibilityChoice.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val useGateway by viewModel.useGateway.collectAsStateWithLifecycle()
    val useHttpService by viewModel.useHttpService.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("ClawPaw 引导", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (stepIndex > 0) {
                    IconButton(onClick = { viewModel.prevStep() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一步")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= stepIndex) clawpaw_primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val context = LocalContext.current
            val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val activityRecognitionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                if (results.values.any { !it }) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
            when (step) {
                OnboardingStep.Welcome -> StepWelcome()
                OnboardingStep.Accessibility -> StepAccessibility(viewModel)
                OnboardingStep.Connection -> StepConnection(viewModel)
                OnboardingStep.Authorization -> StepAuthorization(
                    viewModel = viewModel,
                    locationLauncher = locationLauncher,
                    cameraLauncher = cameraLauncher,
                    notificationLauncher = notificationLauncher,
                    contactsLauncher = contactsLauncher,
                    calendarLauncher = calendarLauncher,
                    storageLauncher = storageLauncher,
                    activityRecognitionLauncher = activityRecognitionLauncher,
                    bluetoothLauncher = bluetoothLauncher,
                    smsLauncher = smsLauncher,
                    phoneLauncher = phoneLauncher
                )
                OnboardingStep.Summary -> StepSummary(viewModel)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step != OnboardingStep.Welcome) {
                    TextButton(
                        onClick = { viewModel.prevStep() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("返回")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step == OnboardingStep.Welcome) {
                    TextButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                        Text("跳过")
                    }
                }
                Button(
                    onClick = {
                        if (step == OnboardingStep.Summary) onFinish() else viewModel.nextStep()
                    },
                    modifier = Modifier.weight(if (step == OnboardingStep.Welcome) 2f else 1f),
                    enabled = when (step) {
                        OnboardingStep.Accessibility -> when (accessibilityChoice) {
                            AccessibilityChoice.InfoOnly -> true
                            AccessibilityChoice.Full -> accessibilityEnabled
                        }
                        else -> viewModel.canGoNext()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (step == OnboardingStep.Summary) "开始使用" else "下一步")
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(
            Icons.Default.Smartphone,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = clawpaw_primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "ClawPaw",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "把手机变成可远程控制的节点",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WelcomeBlock(
                icon = Icons.Default.LocationOn,
                title = "获取信息",
                body = "定位 运动 电量",
                modifier = Modifier.weight(1f)
            )
            WelcomeBlock(
                icon = Icons.Default.TouchApp,
                title = "远程操作",
                body = "点击 滑动 输入 截图",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WelcomeBlock(
                icon = Icons.Default.Link,
                title = "连接方式",
                body = "Gateway直连 SSH隧道 HTTP服务",
                modifier = Modifier.weight(1f)
            )
            WelcomeBlock(
                icon = Icons.Default.Chat,
                title = "智能对话",
                body = "与OpenClaw等AI直接发消息",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val WELCOME_ICON_CONTAINER_SIZE = 52.dp
private val WELCOME_ICON_SIZE = 28.dp

@Composable
private fun WelcomeBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(
            elevation = 6.dp,
            shape = RoundedCornerShape(16.dp),
            ambientColor = clawpaw_primary.copy(alpha = 0.08f),
            spotColor = clawpaw_primary.copy(alpha = 0.04f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(WELCOME_ICON_CONTAINER_SIZE)
                    .clip(RoundedCornerShape(14.dp))
                    .background(clawpaw_primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(WELCOME_ICON_SIZE),
                    tint = clawpaw_primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepAccessibility(viewModel: OnboardingViewModel) {
    val choice by viewModel.accessibilityChoice.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshAccessibilityState()
            kotlinx.coroutines.delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "无障碍服务",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "作为 Node 连接时无需开启；若需远程操作手机，请开启。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        val infoOnlySelected = choice == AccessibilityChoice.InfoOnly
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.InfoOnly) }
                .then(
                    if (infoOnlySelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (infoOnlySelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (infoOnlySelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("获取基础信息", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "不开启无障碍。可获取定位、电量、WiFi 等信息，无法远程点击、滑动或输入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val fullSelected = choice == AccessibilityChoice.Full
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.Full) }
                .then(
                    if (fullSelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (fullSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (fullSelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("帮助操作手机", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "开启无障碍后可远程点击、滑动、输入、截图等。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                )
                if (fullSelected) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!accessibilityEnabled) {
                        Text(
                            "无障碍未开启",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("去开启无障碍")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "在「已下载的应用」或「已安装的服务」中找到「ClawPaw自动化」并开启。\n" +
                        "建议在设置中为该服务打开快捷方式，部分设备重启后需重新开启，快捷方式更方便。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StepConnection(viewModel: OnboardingViewModel) {
    val host by viewModel.connectionHost.collectAsStateWithLifecycle()
    val port by viewModel.connectionPort.collectAsStateWithLifecycle()
    val useGateway by viewModel.useGateway.collectAsStateWithLifecycle()
    val useHttpService by viewModel.useHttpService.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "连接方式",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "可多选：通过 Node（域名/IP）连接和/或启用 HTTP 服务，至少选择一项。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setUseGateway(!useGateway) }
                .then(if (useGateway) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp)) else Modifier),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useGateway, onCheckedChange = { viewModel.setUseGateway(it) })
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("通过 Node 连接（域名或 IP）", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("连接电脑上的 Gateway，支持局域网或公网。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (useGateway) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { viewModel.setConnectionHost(it) },
                        label = { Text("域名或 IP") },
                        placeholder = { Text("例如 192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "支持仅填主机（默认 ws），或完整地址如 wss://host:端口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port.toString(),
                        onValueChange = { s -> s.filter { c -> c.isDigit() }.take(5).toIntOrNull()?.let { viewModel.setConnectionPort(it) } },
                        label = { Text("端口") },
                        placeholder = { Text("18789") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setUseHttpService(!useHttpService) }
                .then(if (useHttpService) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp)) else Modifier),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useHttpService, onCheckedChange = { viewModel.setUseHttpService(it) })
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("启用 HTTP 服务", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("本机 HTTP 服务（如配合 Tailscale），端口 8765。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(20.dp))
        Text("扩展", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "使用 SSH 隧道时请先在此配置连接地址、用户名与端口映射，Gateway 地址填 127.0.0.1。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, SshTunnelActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("配置 SSH 隧道")
        }
    }
}

@Composable
private fun StepAuthorization(
    viewModel: OnboardingViewModel,
    locationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    contactsLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    calendarLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    storageLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    activityRecognitionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    bluetoothLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    smsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    phoneLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    var authRefreshTick by remember { mutableIntStateOf(0) }
    val notificationListenerEnabled = com.example.clawpaw.service.NotificationListener.isEnabled(context)

    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val allGranted = accessibilityEnabled && locationGranted && cameraGranted && notificationGranted

    LaunchedEffect(allGranted) {
        if (allGranted) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(500L)
            viewModel.refreshAccessibilityState()
            authRefreshTick++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "系统授权（可选）",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "以下权限可提升节点能力，非强制。可逐项申请或稍后在设置中申请。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                key(authRefreshTick) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("无障碍服务", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("远程操作手机（若需）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (accessibilityEnabled) Text("已开启", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                        else OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text("去授权") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OnboardingPermissionRow(context, Manifest.permission.ACCESS_FINE_LOCATION, "定位", "获取信息", locationLauncher)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OnboardingPermissionRow(context, Manifest.permission.CAMERA, "相机", "拍照/录像", cameraLauncher)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.POST_NOTIFICATIONS, "通知", "调试与提醒", notificationLauncher)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知监听", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("获取通知列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (notificationListenerEnabled) Text("已开启", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                        else OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text("去授权") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OnboardingPermissionRow(context, Manifest.permission.READ_CALENDAR, "日历", "读取日历", calendarLauncher)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OnboardingPermissionRow(context, Manifest.permission.READ_MEDIA_IMAGES, "照片", "读取照片", storageLauncher)
                    } else {
                        OnboardingPermissionRow(context, Manifest.permission.READ_EXTERNAL_STORAGE, "存储/照片", "读取文件与照片", storageLauncher)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.ACTIVITY_RECOGNITION, "运动/步数", "传感器步数", activityRecognitionLauncher)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.BLUETOOTH_CONNECT, "蓝牙", "附近设备", bluetoothLauncher)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OnboardingPermissionRow(context, Manifest.permission.READ_CONTACTS, "联系人", "读取联系人", contactsLauncher)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("短信", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("读取与发送短信", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val smsRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                        val smsSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                        if (smsRead && smsSend) Text("已授权", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                        else OutlinedButton(onClick = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text("去授权") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OnboardingPermissionRow(context, Manifest.permission.CALL_PHONE, "电话", "拨打电话", phoneLauncher)
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionRow(
    context: android.content.Context,
    permission: String,
    label: String,
    desc: String,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) Text("已授权", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
        else OutlinedButton(onClick = { launcher.launch(permission) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text("去授权") }
    }
}

private fun isLikelyIp(host: String): Boolean {
    val t = host.trim()
    if (t.isEmpty()) return false
    if (t == "localhost") return true
    return t.all { it.isDigit() || it == '.' } && t.count { it == '.' } == 3
}

@Composable
private fun StepSummary(viewModel: OnboardingViewModel) {
    val accChoice by viewModel.accessibilityChoice.collectAsStateWithLifecycle()
    val useGateway by viewModel.useGateway.collectAsStateWithLifecycle()
    val useHttpService by viewModel.useHttpService.collectAsStateWithLifecycle()
    val connHost by viewModel.connectionHost.collectAsStateWithLifecycle()
    val connPort by viewModel.connectionPort.collectAsStateWithLifecycle()
    val accEnabled = viewModel.isAccessibilityEnabled()
    val hostLabel = if (isLikelyIp(connHost)) "IP" else "域名"
    val context = LocalContext.current
    SshPrefs.init(context)
    val sshConfig = SshPrefs.getConfig()
    val sshMappings = SshPrefs.getPortMappings()
    val sshReverseMappings = SshPrefs.getReversePortMappings()
    val hasSshConfig = sshConfig.host.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = clawpaw_primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "准备就绪",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("你的选择", style = MaterialTheme.typography.titleSmall, color = clawpaw_primary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("模式", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (accChoice == AccessibilityChoice.InfoOnly) "获取基础信息" else "帮助操作手机",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (accChoice == AccessibilityChoice.Full) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("当前状态", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (accEnabled) "已开启" else "未开启（可稍后在设置中开启）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (accEnabled) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text("连接方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                if (useGateway) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Node (Gateway)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$connHost:$connPort ($hostLabel)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (useHttpService) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("HTTP 服务", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("开启（端口 8765）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (hasSshConfig) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("SSH 隧道", style = MaterialTheme.typography.titleSmall, color = clawpaw_primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("连接", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sshConfig.host}:${sshConfig.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("用户名", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (sshConfig.username.isNotBlank()) sshConfig.username else "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (sshMappings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("端口映射（正向）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sshMappings.forEach { m ->
                            Text(m.displayText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (sshReverseMappings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("端口映射（反向）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sshReverseMappings.forEach { m ->
                            Text(m.displayText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "点击「开始使用」进入主页。可在「Node 设置」中修改连接与节点名称，或配置 SSH。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
