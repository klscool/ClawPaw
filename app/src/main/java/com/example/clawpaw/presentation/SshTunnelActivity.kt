package com.example.clawpaw.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clawpaw.R
import com.example.clawpaw.ssh.PortMapping
import com.example.clawpaw.ssh.ReversePortMapping
import com.example.clawpaw.ssh.SshTunnelConfig
import com.example.clawpaw.ssh.SshTunnelViewModel
import com.example.clawpaw.ui.theme.ClawPawTheme

class SshTunnelActivity : LocaleAwareActivity() {
    private val viewModel: SshTunnelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClawPawTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SshTunnelScreen(
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTunnelScreen(
    viewModel: SshTunnelViewModel,
    onBack: () -> Unit
) {
    val config: SshTunnelConfig by viewModel.config.collectAsStateWithLifecycle()
    val mappings: List<PortMapping> by viewModel.mappings.collectAsStateWithLifecycle()
    val reverseMappings: List<ReversePortMapping> by viewModel.reverseMappings.collectAsStateWithLifecycle()

    var host by remember(config) { mutableStateOf(config.host) }
    var portStr by remember(config.port) { mutableStateOf(config.port.toString()) }
    var username by remember(config) { mutableStateOf(config.username) }
    var password by remember(config) { mutableStateOf(config.password) }
    var showProxy by remember { mutableStateOf(config.proxyPort > 0) }
    var proxyHost by remember(config.proxyHost) { mutableStateOf(config.proxyHost) }
    var proxyPortStr by remember(config.proxyPort) { mutableStateOf(if (config.proxyPort > 0) config.proxyPort.toString() else "") }


    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ssh_title), style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 卡片一：SSH 连接配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.ssh_config), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.ssh_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { s -> if (s.isEmpty() || s.all { c -> c.isDigit() }) portStr = s },
                        label = { Text(stringResource(R.string.common_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.ssh_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.ssh_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showProxy = !showProxy }) {
                        Text(
                            text = if (showProxy) stringResource(R.string.ssh_proxy_collapse) else stringResource(R.string.ssh_proxy_optional),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (showProxy) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.ssh_proxy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text(stringResource(R.string.ssh_proxy_host)) },
                            placeholder = { Text(stringResource(R.string.ssh_proxy_host_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = proxyPortStr,
                            onValueChange = { s -> if (s.isEmpty() || s.all { c -> c.isDigit() }) proxyPortStr = s },
                            label = { Text(stringResource(R.string.ssh_proxy_port)) },
                            placeholder = { Text(stringResource(R.string.ssh_proxy_port_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val proxyPort = proxyPortStr.trim().toIntOrNull()?.takeIf { it in 0..65535 } ?: 0
                            viewModel.updateConfig(
                                SshTunnelConfig(
                                    host = host.trim(),
                                    port = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: 22,
                                    username = username.trim(),
                                    password = password,
                                    proxyHost = proxyHost.trim().ifEmpty { "127.0.0.1" },
                                    proxyPort = proxyPort
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 卡片二：正向映射（本地 → 远程）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.ssh_forward_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ssh_forward_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    mappings.forEachIndexed { index: Int, m: PortMapping ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m.displayText(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeMappingAt(index) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_delete))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    var localPort by remember { mutableStateOf("18789") }
                    var remoteHost by remember { mutableStateOf("127.0.0.1") }
                    var remotePort by remember { mutableStateOf("18789") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = localPort,
                            onValueChange = { localPort = it },
                            label = { Text(stringResource(R.string.ssh_local_port)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = remoteHost,
                            onValueChange = { remoteHost = it },
                            label = { Text(stringResource(R.string.ssh_remote_host)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = remotePort,
                            onValueChange = { remotePort = it },
                            label = { Text(stringResource(R.string.ssh_remote_port)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val lp = localPort.toIntOrNull() ?: return@OutlinedButton
                            val rp = remotePort.toIntOrNull() ?: return@OutlinedButton
                            if (lp in 1..65535 && rp in 1..65535) {
                                viewModel.addMapping(PortMapping(lp, remoteHost.trim().ifEmpty { "127.0.0.1" }, rp))
                                localPort = ""
                                remoteHost = "127.0.0.1"
                                remotePort = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ssh_add_forward))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 卡片二乙：反向映射（远程 → 本机）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.ssh_reverse_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ssh_reverse_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    reverseMappings.forEachIndexed { index: Int, m: ReversePortMapping ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m.displayText(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeReverseMappingAt(index) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_delete))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    var revRemotePort by remember { mutableStateOf("8765") }
                    var revLocalHost by remember { mutableStateOf("127.0.0.1") }
                    var revLocalPort by remember { mutableStateOf("8765") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = revRemotePort,
                            onValueChange = { s -> if (s.isEmpty() || s.all { c -> c.isDigit() }) revRemotePort = s },
                            label = { Text(stringResource(R.string.ssh_server_port)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = revLocalHost,
                            onValueChange = { revLocalHost = it },
                            label = { Text(stringResource(R.string.ssh_local_host)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = revLocalPort,
                            onValueChange = { s -> if (s.isEmpty() || s.all { c -> c.isDigit() }) revLocalPort = s },
                            label = { Text(stringResource(R.string.ssh_local_port_label)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val rp = revRemotePort.toIntOrNull() ?: return@OutlinedButton
                            val lp = revLocalPort.toIntOrNull() ?: return@OutlinedButton
                            if (rp in 1..65535 && lp in 1..65535) {
                                viewModel.addReverseMapping(
                                    ReversePortMapping(rp, revLocalHost.trim().ifEmpty { "127.0.0.1" }, lp)
                                )
                                revRemotePort = ""
                                revLocalHost = "127.0.0.1"
                                revLocalPort = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ssh_add_reverse))
                    }
                }
            }

        }
    }
}
