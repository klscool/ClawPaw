package com.example.clawpaw.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.util.GatewayPairingHelper
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clawpaw.R
import com.example.clawpaw.build.FlavorCommandGate
import com.example.clawpaw.ssh.PortMapping
import com.example.clawpaw.ssh.ReversePortMapping
import com.example.clawpaw.ssh.SshPrefs
import com.example.clawpaw.ui.theme.ClawPawTheme
import com.example.clawpaw.ui.theme.clawpaw_primary

@OptIn(ExperimentalMaterial3Api::class)
class OnboardingActivity : LocaleAwareActivity() {
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
    val progressIndex = viewModel.progressIndex()
    val stepCount = viewModel.onboardingStepCount()
    val accessibilityChoice by viewModel.accessibilityChoice.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val useGateway by viewModel.useGateway.collectAsStateWithLifecycle()
    val useHttpService by viewModel.useHttpService.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (progressIndex > 0) {
                    IconButton(onClick = { viewModel.prevStep() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_prev))
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
            repeat(stepCount) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= progressIndex) clawpaw_primary
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
                OnboardingStep.Accessibility -> {
                    when (FlavorCommandGate.currentTier()) {
                        FlavorCommandGate.Tier.SENSITIVE -> StepDataScope(viewModel)
                        else -> StepAccessibility(viewModel)
                    }
                }
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
                        Text(stringResource(R.string.common_back))
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
                        Text(stringResource(R.string.common_skip))
                    }
                }
                Button(
                    onClick = {
                        if (step == OnboardingStep.Summary) onFinish() else viewModel.nextStep()
                    },
                    modifier = Modifier.weight(if (step == OnboardingStep.Welcome) 2f else 1f),
                    enabled = viewModel.canGoNext(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (step == OnboardingStep.Summary) stringResource(R.string.onboarding_start) else stringResource(R.string.common_next))
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
            Icons.Default.Phone,
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
            stringResource(R.string.onboarding_welcome_subtitle),
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
                title = stringResource(R.string.onboarding_welcome_info),
                body = stringResource(R.string.onboarding_welcome_info_body),
                modifier = Modifier.weight(1f)
            )
            WelcomeBlock(
                icon = Icons.Default.Person,
                title = stringResource(R.string.onboarding_welcome_remote),
                body = stringResource(R.string.onboarding_welcome_remote_body),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WelcomeBlock(
                icon = Icons.Default.Share,
                title = stringResource(R.string.onboarding_welcome_connection),
                body = stringResource(R.string.onboarding_welcome_connection_body),
                modifier = Modifier.weight(1f)
            )
            WelcomeBlock(
                icon = Icons.Default.Mail,
                title = stringResource(R.string.onboarding_welcome_chat),
                body = stringResource(R.string.onboarding_welcome_chat_body),
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

    if (FlavorCommandGate.hasAccessibilityFlavor()) {
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.refreshAccessibilityState()
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            stringResource(R.string.main_accessibility_service),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_node_no_accessibility),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        val infoBasicSelected = choice == AccessibilityChoice.InfoBasic
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.InfoBasic) }
                .then(
                    if (infoBasicSelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (infoBasicSelected) Icons.Default.Check else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (infoBasicSelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.onboarding_get_info), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.onboarding_no_accessibility_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val infoAllSelected = choice == AccessibilityChoice.InfoAll
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.InfoAll) }
                .then(
                    if (infoAllSelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (infoAllSelected) Icons.Default.Check else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (infoAllSelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.onboarding_get_info_all), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.onboarding_get_info_all_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val operateSelected = choice == AccessibilityChoice.Operate
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.Operate) }
                .then(
                    if (operateSelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (operateSelected) Icons.Default.Check else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (operateSelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.onboarding_help_operate), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    stringResource(R.string.onboarding_with_accessibility_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                )
                if (operateSelected) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!accessibilityEnabled) {
                        Text(
                            stringResource(R.string.onboarding_accessibility_not_enabled),
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
                        Text(stringResource(R.string.onboarding_open_accessibility))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.onboarding_accessibility_how_to),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 敏感信息包：仅「基础信息 / 含敏感信息」两档，无远程操作选项 */
@Composable
private fun StepDataScope(viewModel: OnboardingViewModel) {
    val choice by viewModel.accessibilityChoice.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            stringResource(R.string.onboarding_data_scope_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_data_scope_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        val infoBasicSelected = choice == AccessibilityChoice.InfoBasic
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.InfoBasic) }
                .then(
                    if (infoBasicSelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (infoBasicSelected) Icons.Default.Check else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (infoBasicSelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.onboarding_get_info), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.onboarding_no_accessibility_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val infoAllSelected = choice == AccessibilityChoice.InfoAll
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAccessibilityChoice(AccessibilityChoice.InfoAll) }
                .then(
                    if (infoAllSelected) Modifier.border(2.dp, clawpaw_primary, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (infoAllSelected) Icons.Default.Check else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (infoAllSelected) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.onboarding_get_info_all), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.onboarding_get_info_all_desc),
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
    val token by viewModel.connectionToken.collectAsStateWithLifecycle()
    val connectionPassword by viewModel.connectionPassword.collectAsStateWithLifecycle()
    val useGateway by viewModel.useGateway.collectAsStateWithLifecycle()
    val useHttpService by viewModel.useHttpService.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RetrofitClient.init(context)
    var scanOrCodeExpanded by remember { mutableStateOf(true) }
    var manualConfigExpanded by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }
    val activity = context as? Activity
    var pendingParsed by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    fun tryApplyContent(content: String) {
        val (parsed, reason) = GatewayPairingHelper.parseWithReason(content.trim())
        if (parsed == null) {
            Toast.makeText(context, context.getString(R.string.main_link_qr_parse_error_detail, reason ?: ""), Toast.LENGTH_LONG).show()
            return
        }
        pendingParsed = parsed
    }
    val qrScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val content = result.data?.getStringExtra(QrScanActivity.EXTRA_QR_CONTENT)?.trim()
            if (!content.isNullOrEmpty()) tryApplyContent(content)
        }
    }
    if (pendingParsed != null) {
        val (host, port, token) = pendingParsed!!
        PairRoleConfirmDialog(
            host = host,
            port = port,
            token = token,
            onDismiss = { pendingParsed = null },
            onChooseNode = { h, p, t ->
                GatewayPairingHelper.saveParsedAsNode(context, h, p, t)
                viewModel.setConnectionHost(h)
                viewModel.setConnectionPort(p)
                viewModel.setConnectionToken(t)
                viewModel.setUseGateway(true)
                pendingParsed = null
                pairingCode = ""
                Toast.makeText(context, context.getString(R.string.onboarding_pair_success), Toast.LENGTH_SHORT).show()
            },
            onChooseOperator = { h, p, t ->
                GatewayPairingHelper.saveParsedAsOperator(context, h, p, t)
                viewModel.setConnectionHost(h)
                viewModel.setConnectionPort(p)
                viewModel.setConnectionToken(t)
                viewModel.setUseGateway(true)
                pendingParsed = null
                pairingCode = ""
                Toast.makeText(context, context.getString(R.string.onboarding_pair_success), Toast.LENGTH_SHORT).show()
            }
        )
    }
    fun applyPairingCode() {
        tryApplyContent(pairingCode.trim())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            stringResource(R.string.onboarding_connection_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_connection_multi_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        // 1. 扫码或配对码连接（展开后显示说明 + 配对码输入 + 扫码）；仅顶部标题行可点击展开/收起
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { scanOrCodeExpanded = !scanOrCodeExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = clawpaw_primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.onboarding_guide_scan_or_code), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(if (scanOrCodeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                if (scanOrCodeExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.onboarding_guide_scan_or_code_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it },
                        placeholder = { Text(stringResource(R.string.main_link_setup_code_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { applyPairingCode() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), enabled = pairingCode.isNotBlank()) {
                            Text(stringResource(R.string.main_link_connect_with_code))
                        }
                        OutlinedButton(
                            onClick = {
                                activity?.let { act ->
                                    qrScanLauncher.launch(android.content.Intent(act, QrScanActivity::class.java))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(stringResource(R.string.main_link_scan_qr_btn)) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 手动配置：默认收起，仅顶部标题行可点击展开/收起
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { manualConfigExpanded = !manualConfigExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = clawpaw_primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.onboarding_manual_config), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(if (manualConfigExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                if (manualConfigExpanded) {
                    var showToken by remember { mutableStateOf(false) }
                    var showPassword by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = host,
                        onValueChange = { viewModel.setConnectionHost(it); viewModel.setUseGateway(it.trim().isNotBlank()) },
                        label = { Text(stringResource(R.string.onboarding_domain_or_ip)) },
                        placeholder = { Text(stringResource(R.string.onboarding_domain_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.gateway_address_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port.toString(),
                        onValueChange = { s -> s.filter { c -> c.isDigit() }.take(5).toIntOrNull()?.let { viewModel.setConnectionPort(it) } },
                        label = { Text(stringResource(R.string.common_port)) },
                        placeholder = { Text(stringResource(R.string.onboarding_port_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { viewModel.setConnectionToken(it) },
                        label = { Text(stringResource(R.string.gateway_persistent_token)) },
                        placeholder = { Text(stringResource(R.string.gateway_persistent_token_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) stringResource(R.string.gateway_hide_token) else stringResource(R.string.gateway_show_token), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = connectionPassword,
                        onValueChange = { viewModel.setConnectionPassword(it) },
                        label = { Text(stringResource(R.string.gateway_password)) },
                        placeholder = { Text(stringResource(R.string.gateway_password_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) stringResource(R.string.gateway_hide_password) else stringResource(R.string.gateway_show_password), style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
                    Text(stringResource(R.string.onboarding_http_card_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.onboarding_http_card_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (FlavorCommandGate.hasSshFlavor()) {
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.onboarding_extend), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.onboarding_ssh_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, SshTunnelActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_configure_ssh))
            }
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
    val choice by viewModel.accessibilityChoice.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    var authRefreshTick by remember { mutableIntStateOf(0) }
    val notificationListenerEnabled = com.example.clawpaw.service.NotificationListener.isEnabled(context)
    val tier = FlavorCommandGate.currentTier()
    val showOperateAccessibilityRow = FlavorCommandGate.hasAccessibilityFlavor() && choice == AccessibilityChoice.Operate
    val showSensitivePermissionBlock = tier != FlavorCommandGate.Tier.BASIC && choice == AccessibilityChoice.InfoAll
    val showBluetoothRow = tier != FlavorCommandGate.Tier.BASIC

    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else true
    val allGranted = locationGranted && cameraGranted && notificationGranted &&
        (!showOperateAccessibilityRow || accessibilityEnabled) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || activityRecognitionGranted)

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
            stringResource(R.string.onboarding_auth_optional_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_permission_hint),
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
                key(authRefreshTick, choice, tier) {
                    if (showOperateAccessibilityRow) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.main_accessibility_service), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.onboarding_remote_operate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (accessibilityEnabled) Text(stringResource(R.string.main_enabled), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                            else OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.common_go_auth)) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    }
                    OnboardingPermissionRow(context, Manifest.permission.ACCESS_FINE_LOCATION, R.string.main_location, R.string.main_location_desc, locationLauncher)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OnboardingPermissionRow(context, Manifest.permission.CAMERA, R.string.main_camera, R.string.main_camera_desc, cameraLauncher)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.POST_NOTIFICATIONS, R.string.main_notification, R.string.main_notification_desc, notificationLauncher)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.ACTIVITY_RECOGNITION, R.string.main_activity_recognition, R.string.main_activity_recognition_desc, activityRecognitionLauncher)
                    }
                    if (showBluetoothRow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.BLUETOOTH_CONNECT, R.string.main_bluetooth, R.string.main_bluetooth_desc, bluetoothLauncher)
                    }
                    if (showSensitivePermissionBlock) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.main_notification_listener), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.main_notification_listener_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (notificationListenerEnabled) Text(stringResource(R.string.main_enabled), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                            else OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.common_go_auth)) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.READ_CALENDAR, R.string.main_calendar, R.string.main_calendar_desc, calendarLauncher)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OnboardingPermissionRow(context, Manifest.permission.READ_MEDIA_IMAGES, R.string.main_photos_storage, R.string.main_photos_storage_desc, storageLauncher)
                        } else {
                            OnboardingPermissionRow(context, Manifest.permission.READ_EXTERNAL_STORAGE, R.string.main_photos_storage, R.string.main_photos_storage_desc, storageLauncher)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.READ_CONTACTS, R.string.main_contacts, R.string.main_contacts_desc, contactsLauncher)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.main_sms), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.main_sms_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val smsRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                            val smsSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                            if (smsRead && smsSend) Text(stringResource(R.string.main_permission_granted), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                            else OutlinedButton(onClick = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.common_go_auth)) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        OnboardingPermissionRow(context, Manifest.permission.CALL_PHONE, R.string.main_phone, R.string.main_phone_desc, phoneLauncher)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionRow(
    context: android.content.Context,
    permission: String,
    labelRes: Int,
    descRes: Int,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) Text(stringResource(R.string.main_permission_granted), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
        else OutlinedButton(onClick = { launcher.launch(permission) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.common_go_auth)) }
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
    val hostLabel = if (isLikelyIp(connHost)) stringResource(R.string.onboarding_host_ip) else stringResource(R.string.onboarding_host_domain)
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
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = clawpaw_primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_ready),
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
                Text(stringResource(R.string.onboarding_your_choice), style = MaterialTheme.typography.titleSmall, color = clawpaw_primary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.onboarding_mode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            FlavorCommandGate.currentTier() == FlavorCommandGate.Tier.BASIC ->
                                stringResource(R.string.onboarding_summary_build_basic)
                            else -> when (accChoice) {
                                AccessibilityChoice.InfoBasic -> stringResource(R.string.onboarding_get_info)
                                AccessibilityChoice.InfoAll -> stringResource(R.string.onboarding_get_info_all)
                                AccessibilityChoice.Operate -> stringResource(R.string.onboarding_help_operate)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (FlavorCommandGate.hasAccessibilityFlavor() && accChoice == AccessibilityChoice.Operate) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.onboarding_current_status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (accEnabled) stringResource(R.string.main_enabled) else stringResource(R.string.onboarding_not_enabled_later),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (accEnabled) clawpaw_primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.onboarding_connection_step_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                if (useGateway) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.onboarding_node_connect_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$connHost:$connPort ($hostLabel)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (useHttpService) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.onboarding_http_service_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.onboarding_http_on_port), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (hasSshConfig && FlavorCommandGate.hasSshFlavor()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.main_ssh_tunnel), style = MaterialTheme.typography.titleSmall, color = clawpaw_primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.common_connect), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sshConfig.host}:${sshConfig.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.ssh_username), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (sshConfig.username.isNotBlank()) sshConfig.username else "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (sshMappings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.onboarding_port_forward), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sshMappings.forEach { m ->
                            Text(m.displayText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (sshReverseMappings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.onboarding_port_reverse), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sshReverseMappings.forEach { m ->
                            Text(m.displayText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_summary_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
