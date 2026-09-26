package com.aielectronics.builder

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerErrorPresenter
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.ble.android.AndroidBleRuntimeConnector
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.runtime.CanonicalManifestEncoder
import com.aielectronics.runtime.RuntimeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuilderAppViewModel(
    private val interpreter: BeginnerIntentInterpreter = BeginnerIntentInterpreter(),
    private val engine: ApplicationProjectEngine = ApplicationProjectEngine(),
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderAppState())
    val state: StateFlow<BuilderAppState> = _state.asStateFlow()

    fun setGoal(text: String) {
        _state.update { it.copy(goalText = text) }
    }

    fun startDesign() {
        val goal = _state.value.goalText.trim()
        if (goal.isBlank()) {
            _state.update { it.copy(error = "作りたいものを入力してください。") }
            return
        }
        resolveAndCompile()
    }

    fun answerQuestion(slotId: String, answer: String) {
        if (answer.isBlank()) return
        _state.update {
            it.copy(
                clarificationValues = it.clarificationValues + (slotId to answer.trim()),
                error = null,
            )
        }
        resolveAndCompile()
    }

    fun open(screen: AppScreen) {
        _state.update { it.copy(screen = screen, error = null) }
    }

    fun connect(context: Context) {
        if (_state.value.busy) return

        _state.update {
            it.copy(
                busy = true,
                error = null,
                deployMessage = "装置を探しています…",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidBleRuntimeConnector(context.applicationContext)
                        .scanAndConnect()
                        .getOrThrow()
                }
            }.onSuccess { connection ->
                _state.update {
                    it.copy(
                        busy = false,
                        connection = connection,
                        deployMessage = "装置に接続しました。",
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = BeginnerErrorPresenter.connectionFailure(),
                        deployMessage = "",
                    )
                }
            }
        }
    }

    fun deploy() {
        val current = _state.value
        val bundle = current.bundle ?: return
        val connection = current.connection ?: run {
            _state.update { it.copy(error = "先に装置へ接続してください。") }
            return
        }
        val manifest = bundle.manifest ?: run {
            _state.update { it.copy(error = "装置設定データがありません。") }
            return
        }

        if (current.busy) return

        _state.update {
            it.copy(
                busy = true,
                error = null,
                deployProgress = 5,
                deployMessage = "装置を確認しています…",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = RuntimeClient(connection.transport)
                    updateDeploy(35, "設計データを送っています…")
                    client.deployManifest(
                        requestId = "app-deploy",
                        manifestVersion = manifest.version,
                        payload = CanonicalManifestEncoder().encode(manifest),
                        projectId = manifest.projectId,
                    ).getOrThrow()

                    updateDeploy(65, "設定を確認しています…")
                    client.verifyProject(
                        requestId = "app-verify",
                        projectId = manifest.projectId,
                    ).getOrThrow()

                    val requiredTests = bundle.testPlan.tests.filter { it.required }
                    requiredTests.forEachIndexed { index, test ->
                        val base = 70
                        val span = 25
                        val progress =
                            base + ((index + 1) * span / requiredTests.size.coerceAtLeast(1))
                        updateDeploy(progress, test.name + "を確認しています…")
                        client.runTest(
                            requestId = "app-test-" + test.id,
                            testId = test.id,
                        ).getOrThrow()
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        busy = false,
                        deployed = true,
                        deployProgress = 100,
                        deployMessage = "設定が完了しました。",
                        screen = AppScreen.CONTROL,
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = BeginnerErrorPresenter.deploymentFailure(),
                        deployMessage = "要確認",
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun resolveAndCompile() {
        val current = _state.value
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val intent = interpreter.interpret(
                        text = current.goalText,
                        clarifications = current.clarificationValues,
                    )
                    when (val resolution = engine.resolve(intent)) {
                        is RequirementResolution.NeedUserInput ->
                            ResolutionResult.Questions(resolution.missing)

                        is RequirementResolution.Ready -> {
                            when (val compile = engine.compile(resolution.requirements)) {
                                is CompileResult.Success ->
                                    ResolutionResult.Success(
                                        resolution.requirements,
                                        compile.bundle,
                                    )

                                is CompileResult.NeedUserInput ->
                                    ResolutionResult.Questions(compile.questions)

                                is CompileResult.Blocked ->
                                    ResolutionResult.Error(
                                        BeginnerErrorPresenter.validationBlocked(
                                            compile.report
                                        )
                                    )

                                is CompileResult.Failed ->
                                    ResolutionResult.Error(
                                        BeginnerErrorPresenter.compileFailure(
                                            compile.error
                                        )
                                    )
                            }
                        }
                    }
                }
            }.onSuccess { result ->
                when (result) {
                    is ResolutionResult.Questions -> _state.update {
                        it.copy(
                            busy = false,
                            pendingQuestions = result.questions,
                            screen = AppScreen.HOME,
                        )
                    }

                    is ResolutionResult.Success -> _state.update {
                        it.copy(
                            busy = false,
                            pendingQuestions = emptyList(),
                            requirements = result.requirements,
                            bundle = result.bundle,
                            screen = AppScreen.DESIGN,
                        )
                    }

                    is ResolutionResult.Error -> _state.update {
                        it.copy(
                            busy = false,
                            error = result.message,
                        )
                    }
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = throwable.message ?: "設計処理に失敗しました。",
                    )
                }
            }
        }
    }

    private fun updateDeploy(progress: Int, message: String) {
        _state.update {
            it.copy(
                deployProgress = progress,
                deployMessage = message,
            )
        }
    }

    private sealed interface ResolutionResult {
        data class Questions(
            val questions: List<com.aielectronics.core.model.MissingRequirement>,
        ) : ResolutionResult

        data class Success(
            val requirements: com.aielectronics.core.model.ResolvedRequirements,
            val bundle: com.aielectronics.core.model.ReleaseBundle,
        ) : ResolutionResult

        data class Error(val message: String) : ResolutionResult
    }
}
