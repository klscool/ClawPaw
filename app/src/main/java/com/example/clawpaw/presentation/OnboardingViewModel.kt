package com.example.clawpaw.presentation

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.data.storage.MainPrefs
import com.example.clawpaw.data.storage.OnboardingPrefs
import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.service.NodeHttpService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OnboardingStep { Welcome, Accessibility, Connection, Authorization, Summary }

/** 引导第一页三选一：获取基础信息 / 获取所有信息（含敏感）/ 帮助操作手机 */
enum class AccessibilityChoice { InfoBasic, InfoAll, Operate }

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentStep = MutableStateFlow(OnboardingStep.Welcome)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    private val _accessibilityChoice = MutableStateFlow(AccessibilityChoice.InfoBasic)
    val accessibilityChoice: StateFlow<AccessibilityChoice> = _accessibilityChoice.asStateFlow()

    private val _connectionHost = MutableStateFlow("127.0.0.1")
    val connectionHost: StateFlow<String> = _connectionHost.asStateFlow()

    private val _connectionPort = MutableStateFlow(18789)
    val connectionPort: StateFlow<Int> = _connectionPort.asStateFlow()

    private val _connectionToken = MutableStateFlow("")
    val connectionToken: StateFlow<String> = _connectionToken.asStateFlow()

    private val _connectionPassword = MutableStateFlow("")
    val connectionPassword: StateFlow<String> = _connectionPassword.asStateFlow()

    /** 第三步：是否通过 Gateway 配对连接 */
    private val _useGateway = MutableStateFlow(true)
    val useGateway: StateFlow<Boolean> = _useGateway.asStateFlow()

    /** 第三步：是否启用 HTTP 服务（默认不启用） */
    private val _useHttpService = MutableStateFlow(false)
    val useHttpService: StateFlow<Boolean> = _useHttpService.asStateFlow()

    /** 无障碍是否已开启（用于第二步「帮助操作手机」时能否下一步） */
    private val _accessibilityEnabled = MutableStateFlow(ClawPawAccessibilityService.getInstance() != null)
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    val totalSteps = 5

    fun refreshAccessibilityState() {
        _accessibilityEnabled.value = ClawPawAccessibilityService.getInstance() != null
    }

    fun stepIndex(): Int = when (_currentStep.value) {
        OnboardingStep.Welcome -> 0
        OnboardingStep.Accessibility -> 1
        OnboardingStep.Connection -> 2
        OnboardingStep.Authorization -> 3
        OnboardingStep.Summary -> 4
        else -> 0
    }

    fun canGoNext(): Boolean = when (_currentStep.value) {
        OnboardingStep.Welcome -> true
        OnboardingStep.Accessibility -> true
        OnboardingStep.Connection -> (_useGateway.value && _connectionHost.value.trim().isNotBlank()) || _useHttpService.value
        OnboardingStep.Authorization -> true
        OnboardingStep.Summary -> true
        else -> true
    }

    fun nextStep() {
        when (_currentStep.value) {
            OnboardingStep.Welcome -> _currentStep.value = OnboardingStep.Accessibility
            OnboardingStep.Accessibility -> _currentStep.value = OnboardingStep.Connection
            OnboardingStep.Connection -> {
                if (_useGateway.value) {
                    val host = _connectionHost.value.trim()
                    if (host.isNotBlank()) {
                        RetrofitClient.setServerHost(host)
                        RetrofitClient.setGatewayPort(_connectionPort.value)
                        RetrofitClient.setOriginalToken(_connectionToken.value.trim())
                        RetrofitClient.setGatewayPassword(_connectionPassword.value.trim())
                    }
                }
                _currentStep.value = OnboardingStep.Authorization
            }
            OnboardingStep.Authorization -> _currentStep.value = OnboardingStep.Summary
            OnboardingStep.Summary -> { /* finish in Activity */ }
            else -> { }
        }
    }

    fun prevStep() {
        when (_currentStep.value) {
            OnboardingStep.Welcome -> { }
            OnboardingStep.Accessibility -> _currentStep.value = OnboardingStep.Welcome
            OnboardingStep.Connection -> _currentStep.value = OnboardingStep.Accessibility
            OnboardingStep.Authorization -> _currentStep.value = OnboardingStep.Connection
            OnboardingStep.Summary -> _currentStep.value = OnboardingStep.Authorization
            else -> { }
        }
    }

    fun setAccessibilityChoice(choice: AccessibilityChoice) {
        _accessibilityChoice.value = choice
        refreshAccessibilityState()
    }
    fun setConnectionHost(host: String) { _connectionHost.value = host }
    fun setConnectionPort(port: Int) { _connectionPort.value = port.coerceIn(1, 65535) }
    fun setConnectionToken(token: String) { _connectionToken.value = token }
    fun setConnectionPassword(password: String) { _connectionPassword.value = password }
    fun setUseGateway(use: Boolean) { _useGateway.value = use }
    fun setUseHttpService(use: Boolean) { _useHttpService.value = use }

    fun isAccessibilityEnabled(): Boolean = ClawPawAccessibilityService.getInstance() != null

    fun completeOnboarding() {
        MainPrefs.init(getApplication())
        val app = getApplication<Application>()
        if (_useHttpService.value) {
            MainPrefs.setHttpServiceEnabled(true)
            val intent = Intent(app, NodeHttpService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent) else app.startService(intent)
        } else {
            MainPrefs.setHttpServiceEnabled(false)
            app.stopService(Intent(app, NodeHttpService::class.java))
        }
        OnboardingPrefs.setCompleted(true)
    }
}
