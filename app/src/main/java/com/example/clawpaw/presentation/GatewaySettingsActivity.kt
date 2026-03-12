package com.example.clawpaw.presentation

import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clawpaw.R
import com.example.clawpaw.gateway.GatewayConnection
import com.example.clawpaw.ui.theme.ClawPawTheme

class GatewaySettingsActivity : LocaleAwareActivity() {
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
            title = { Text(stringResource(R.string.gateway_title), style = MaterialTheme.typography.titleLarge) },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                        text = stringResource(R.string.gateway_purpose),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.gateway_prereq),
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
                        label = { Text(stringResource(R.string.gateway_node_name)) },
                        placeholder = { Text(stringResource(R.string.gateway_node_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editHost,
                        onValueChange = { editHost = it },
                        label = { Text(stringResource(R.string.gateway_address)) },
                        placeholder = { Text(stringResource(R.string.gateway_address_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.gateway_address_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { s -> editPort = s.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.common_port)) },
                        placeholder = { Text(stringResource(R.string.gateway_port_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.gateway_auth_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.gateway_token_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (token.isEmpty()) stringResource(R.string.gateway_token_not_set) else stringResource(R.string.gateway_token_set),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (token.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (showToken) editToken else maskedToken.ifEmpty { editToken },
                        onValueChange = { editToken = it },
                        label = { Text(stringResource(R.string.gateway_token)) },
                        placeholder = { Text(stringResource(R.string.gateway_token_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) stringResource(R.string.gateway_hide_token) else stringResource(R.string.gateway_show_token), color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.gateway_password_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (password.isEmpty()) stringResource(R.string.gateway_token_not_set) else stringResource(R.string.gateway_token_set),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (showPassword) editPassword else maskedPassword.ifEmpty { editPassword },
                        onValueChange = { editPassword = it },
                        label = { Text(stringResource(R.string.gateway_password)) },
                        placeholder = { Text(stringResource(R.string.gateway_password_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) stringResource(R.string.gateway_hide_password) else stringResource(R.string.gateway_show_password), color = MaterialTheme.colorScheme.secondary)
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
                        Toast.makeText(context, context.getString(R.string.gateway_connecting_toast), Toast.LENGTH_SHORT).show()
                    },
                    enabled = connectionState !is GatewayConnection.ConnectionState.Connected && editHost.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_connect))
                }
                OutlinedButton(
                    onClick = {
                        viewModel.disconnect()
                        Toast.makeText(context, context.getString(R.string.gateway_disconnected_toast), Toast.LENGTH_SHORT).show()
                    },
                    enabled = connectionState is GatewayConnection.ConnectionState.Connected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.common_disconnect))
                }
            }

            Text(
                text = when (connectionState) {
                    is GatewayConnection.ConnectionState.Connected -> stringResource(R.string.gateway_status_connected)
                    is GatewayConnection.ConnectionState.Connecting -> stringResource(R.string.gateway_status_connecting)
                    is GatewayConnection.ConnectionState.Disconnected -> stringResource(R.string.gateway_status_disconnected)
                    is GatewayConnection.ConnectionState.Error -> stringResource(R.string.gateway_status_error)
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
                Text(stringResource(R.string.gateway_clear_keys))
            }
            if (showClearKeysConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearKeysConfirm = false },
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = { Text(stringResource(R.string.gateway_clear_keys_confirm_title), style = MaterialTheme.typography.titleLarge) },
                    text = { Text(stringResource(R.string.gateway_clear_keys_confirm_text), style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showClearKeysConfirm = false
                                viewModel.clearDeviceIdentity()
                                Toast.makeText(context, context.getString(R.string.gateway_cleared_toast), Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearKeysConfirm = false }) {
                            Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

        }
    }
}
