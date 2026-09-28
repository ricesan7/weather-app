package com.aielectronics.bench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.runtime.RuntimeTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BenchGateViewModel(
    private val bundle: ReleaseBundle,
    private val transport: RuntimeTransport,
) : ViewModel() {

    private val _state = MutableStateFlow(BenchGateState())
    val state: StateFlow<BenchGateState> = _state.asStateFlow()

    fun runBench() {
        if (_state.value.running) return

        _state.value = BenchGateState(running = true)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    BenchE2ERunner(transport).run(bundle)
                }
            }.onSuccess { report ->
                _state.value = BenchGateState(
                    running = false,
                    report = report,
                )
            }.onFailure { throwable ->
                _state.value = BenchGateState(
                    running = false,
                    error = throwable.message ?: "ベンチ試験を実行できませんでした。",
                )
            }
        }
    }

    fun clear() {
        _state.update { BenchGateState() }
    }

    class Factory(
        private val bundle: ReleaseBundle,
        private val transport: RuntimeTransport,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BenchGateViewModel::class.java))
            return BenchGateViewModel(
                bundle = bundle,
                transport = transport,
            ) as T
        }
    }
}
