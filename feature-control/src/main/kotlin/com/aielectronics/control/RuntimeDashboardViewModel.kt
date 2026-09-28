package com.aielectronics.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aielectronics.core.model.DiagnosticSpec
import com.aielectronics.core.model.DiagramSpec
import com.aielectronics.core.model.TestSpec
import com.aielectronics.core.model.UiSpec
import com.aielectronics.diagnostics.DiagnosticCorrelator
import com.aielectronics.runtime.RuntimeTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class RuntimeDashboardViewModel(
    transport: RuntimeTransport,
    private val uiSpec: UiSpec,
    tests: List<TestSpec>,
    private val diagnostics: List<DiagnosticSpec> = emptyList(),
    private val diagramSpec: DiagramSpec? = null,
) : ViewModel() {

    private val client = RuntimeControlClient(transport)
    private val correlator = DiagnosticCorrelator()
    private val historySequence = AtomicLong(1)

    private val _state = MutableStateFlow(
        ControlDashboardState(
            testResults = tests.associate { it.id to TestRunState.IDLE },
        )
    )
    val state: StateFlow<ControlDashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return

        _state.update {
            it.copy(
                refreshing = true,
                lastError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val telemetry = client.telemetry()
                    val settings = client.loadSettings(uiSpec)
                    telemetry to settings
                }
            }.onSuccess { (telemetry, settings) ->
                _state.update { current ->
                    current.copy(
                        connection = DeviceConnectionState.CONNECTED,
                        telemetry = telemetry,
                        settings = settings,
                        history = appendHistory(
                            current.history,
                            telemetry,
                        ),
                        refreshing = false,
                        lastError = null,
                    )
                }
            }.onFailure(::handleFailure)
        }
    }

    fun setSetting(
        settingId: String,
        value: String,
    ) {
        if (settingId in _state.value.pendingSettingIds) return

        _state.update {
            it.copy(
                pendingSettingIds = it.pendingSettingIds + settingId,
                lastError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.setSetting(settingId, value)
                }
            }.onSuccess { acceptedValue ->
                _state.update {
                    it.copy(
                        connection = DeviceConnectionState.CONNECTED,
                        settings = it.settings + (settingId to acceptedValue),
                        pendingSettingIds = it.pendingSettingIds - settingId,
                        lastError = null,
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        pendingSettingIds = it.pendingSettingIds - settingId,
                    )
                }
                handleFailure(throwable)
            }
        }
    }

    fun runTest(testId: String) {
        if (_state.value.testResults[testId] == TestRunState.RUNNING) return

        _state.update {
            it.copy(
                testResults = it.testResults + (testId to TestRunState.RUNNING),
                diagnosticFindings = it.diagnosticFindings - testId,
                lastError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.runTest(testId)
                }
            }.onSuccess { passed ->
                val finding =
                    if (!passed && diagramSpec != null) {
                        correlator.correlateTestFailure(
                            testId = testId,
                            diagnostics = diagnostics,
                            diagramSpec = diagramSpec,
                        )
                    } else {
                        null
                    }

                _state.update {
                    it.copy(
                        connection = DeviceConnectionState.CONNECTED,
                        testResults = it.testResults + (
                            testId to if (passed) {
                                TestRunState.PASSED
                            } else {
                                TestRunState.FAILED
                            }
                        ),
                        diagnosticFindings = when {
                            passed -> it.diagnosticFindings - testId
                            finding != null -> it.diagnosticFindings + (testId to finding)
                            else -> it.diagnosticFindings
                        },
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        testResults = it.testResults + (testId to TestRunState.FAILED),
                    )
                }
                handleFailure(throwable)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(lastError = null) }
    }

    private fun appendHistory(
        current: Map<String, List<ChartSample>>,
        telemetry: Map<String, String>,
    ): Map<String, List<ChartSample>> {
        val sequence = historySequence.getAndIncrement()
        val next = current.toMutableMap()

        telemetry.forEach { (key, rawValue) ->
            val numeric = rawValue.toDoubleOrNull() ?: return@forEach
            val previous = next[key].orEmpty()
            next[key] = (previous + ChartSample(sequence, numeric))
                .takeLast(MAX_HISTORY_SAMPLES)
        }

        return next
    }

    private fun handleFailure(throwable: Throwable) {
        _state.update {
            it.copy(
                connection = DeviceConnectionState.ERROR,
                refreshing = false,
                lastError = throwable.message ?: "装置との通信に失敗しました。",
            )
        }
    }

    class Factory(
        private val transport: RuntimeTransport,
        private val uiSpec: UiSpec,
        private val tests: List<TestSpec>,
        private val diagnostics: List<DiagnosticSpec> = emptyList(),
        private val diagramSpec: DiagramSpec? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RuntimeDashboardViewModel::class.java))
            return RuntimeDashboardViewModel(
                transport = transport,
                uiSpec = uiSpec,
                tests = tests,
                diagnostics = diagnostics,
                diagramSpec = diagramSpec,
            ) as T
        }
    }

    private companion object {
        const val MAX_HISTORY_SAMPLES = 240
    }
}
