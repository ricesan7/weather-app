package com.aielectronics.builder

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerErrorPresenter
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.application.FrictionSnapshot
import com.aielectronics.application.FrictionTelemetryRecorder
import com.aielectronics.application.InMemoryProjectRepository
import com.aielectronics.application.ProjectRepository
import com.aielectronics.application.ProjectTitle
import com.aielectronics.application.SavedProject
import com.aielectronics.ble.android.AndroidBleRuntimeConnector
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ResolvedRequirements
import com.aielectronics.runtime.CanonicalManifestEncoder
import com.aielectronics.runtime.RuntimeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BuilderAppViewModel(
    private val interpreter: BeginnerIntentInterpreter = BeginnerIntentInterpreter(),
    private val engine: ApplicationProjectEngine = ApplicationProjectEngine(),
    private val frictionTelemetry: FrictionTelemetryRecorder = FrictionTelemetryRecorder(),
    private val projectRepository: ProjectRepository = InMemoryProjectRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderAppState())
    val state: StateFlow<BuilderAppState> = _state.asStateFlow()

    private val _friction = MutableStateFlow(frictionTelemetry.snapshot())
    val friction: StateFlow<FrictionSnapshot> = _friction.asStateFlow()

    init {
        refreshSavedProjects()
    }

    fun setGoal(text: String) {
        _state.update { it.copy(goalText = text) }
    }

    fun newProject() {
        val saved = _state.value.savedProjects
        _state.value = BuilderAppState(savedProjects = saved)
    }

    fun startDesign() {
        val goal = _state.value.goalText.trim()
        if (goal.isBlank()) {
            _state.update { it.copy(error = "作りたいものを入力してください。") }
            return
        }
        recordFriction { recordGoalSubmitted() }
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
        recordFriction { recordQuestionAnswered(slotId) }
        resolveAndCompile()
    }

    fun open(screen: AppScreen) {
        val from = _state.value.screen
        recordFriction {
            recordScreenTransition(
                from = from.name,
                to = screen.name,
                userInitiated = true,
            )
        }
        _state.update { it.copy(screen = screen, error = null) }
        persistCurrent()
    }

    fun updateBuildProgress(
        completedConnectionIds: Set<String>,
        currentStepIndex: Int,
    ) {
        val previousCompleted = _state.value.completedConnectionIds
        val newlyCompleted = completedConnectionIds - previousCompleted

        _state.update {
            it.copy(
                completedConnectionIds = completedConnectionIds,
                currentBuildStepIndex = currentStepIndex,
            )
        }

        newlyCompleted.forEach { connectionId ->
            recordFriction { recordGuidedBuildConfirmation(connectionId) }
        }
        persistCurrent()
    }

    fun resumeProject(projectId: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val saved = projectRepository.load(projectId)
                        ?: error("保存したプロジェクトが見つかりません。")
                    val intent = interpreter.interpret(
                        text = saved.goalText,
                        clarifications = saved.clarificationValues,
                    )

                    when (val resolution = engine.resolve(intent)) {
                        is RequirementResolution.NeedUserInput ->
                            ResumeResult.NeedsInput(saved, resolution)

                        is RequirementResolution.Ready ->
                            when (val compile = engine.compile(resolution.requirements)) {
                                is CompileResult.Success ->
                                    ResumeResult.Ready(
                                        saved = saved,
                                        requirements = resolution.requirements,
                                        bundle = compile.bundle,
                                    )

                                is CompileResult.NeedUserInput ->
                                    ResumeResult.NeedsCompileInput(
                                        saved = saved,
                                        questions = compile.questions,
                                    )

                                is CompileResult.Blocked ->
                                    ResumeResult.Error(
                                        BeginnerErrorPresenter.validationBlocked(
                                            compile.report
                                        )
                                    )

                                is CompileResult.Failed ->
                                    ResumeResult.Error(
                                        BeginnerErrorPresenter.compileFailure(
                                            compile.error
                                        )
                                    )
                            }
                    }
                }
            }.onSuccess { result ->
                when (result) {
                    is ResumeResult.Ready -> restoreReadyProject(result)
                    is ResumeResult.NeedsInput -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                projectId = result.saved.id,
                                projectTitle = result.saved.title,
                                projectCreatedAtEpochMs = result.saved.createdAtEpochMs,
                                goalText = result.saved.goalText,
                                clarificationValues = result.saved.clarificationValues,
                                pendingQuestions = result.resolution.missing,
                                completedConnectionIds = result.saved.completedConnectionIds,
                                currentBuildStepIndex = result.saved.currentBuildStepIndex,
                                deployed = result.saved.deployed,
                                screen = AppScreen.HOME,
                                lastSavedAtEpochMs = result.saved.updatedAtEpochMs,
                            )
                        }
                    }

                    is ResumeResult.NeedsCompileInput -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                projectId = result.saved.id,
                                projectTitle = result.saved.title,
                                projectCreatedAtEpochMs = result.saved.createdAtEpochMs,
                                goalText = result.saved.goalText,
                                clarificationValues = result.saved.clarificationValues,
                                pendingQuestions = result.questions,
                                completedConnectionIds = result.saved.completedConnectionIds,
                                currentBuildStepIndex = result.saved.currentBuildStepIndex,
                                deployed = result.saved.deployed,
                                screen = AppScreen.HOME,
                                lastSavedAtEpochMs = result.saved.updatedAtEpochMs,
                            )
                        }
                    }

                    is ResumeResult.Error -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                error = result.message,
                            )
                        }
                    }
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = throwable.message
                            ?: "保存したプロジェクトを開けませんでした。",
                    )
                }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val savedProjects = withContext(Dispatchers.IO) {
                projectRepository.delete(projectId)
                projectRepository.list()
            }

            if (_state.value.projectId == projectId) {
                _state.value = BuilderAppState(savedProjects = savedProjects)
            } else {
                _state.update { it.copy(savedProjects = savedProjects) }
            }
        }
    }

    fun connect(context: Context) {
        if (_state.value.busy) return

        recordFriction { recordDeviceConnectAction() }

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
            }.onFailure {
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

        recordFriction { recordDeployAction() }

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
                val from = _state.value.screen
                recordFriction {
                    recordScreenTransition(
                        from = from.name,
                        to = AppScreen.CONTROL.name,
                        userInitiated = false,
                    )
                }
                _state.update {
                    it.copy(
                        busy = false,
                        deployed = true,
                        deployProgress = 100,
                        deployMessage = "設定が完了しました。",
                        screen = AppScreen.CONTROL,
                    )
                }
                persistCurrent()
            }.onFailure {
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
                    is ResolutionResult.Questions -> {
                        result.questions.firstOrNull()?.let { question ->
                            recordFriction {
                                recordQuestionPresented(question.slotId)
                            }
                        }
                        _state.update {
                            it.copy(
                                busy = false,
                                pendingQuestions = result.questions,
                                screen = AppScreen.HOME,
                            )
                        }
                    }

                    is ResolutionResult.Success -> {
                        val previous = _state.value
                        val now = System.currentTimeMillis()
                        val projectId = previous.projectId ?: UUID.randomUUID().toString()
                        val createdAt = previous.projectCreatedAtEpochMs ?: now
                        val currentConnectionIds =
                            result.bundle.circuitGraph.connections.map { it.id }.toSet()
                        val preservedCompleted =
                            previous.completedConnectionIds.intersect(currentConnectionIds)

                        val from = previous.screen
                        recordFriction {
                            recordScreenTransition(
                                from = from.name,
                                to = AppScreen.DESIGN.name,
                                userInitiated = false,
                            )
                        }

                        _state.update {
                            it.copy(
                                busy = false,
                                pendingQuestions = emptyList(),
                                requirements = result.requirements,
                                bundle = result.bundle,
                                screen = AppScreen.DESIGN,
                                projectId = projectId,
                                projectTitle = ProjectTitle.fromGoal(it.goalText),
                                projectCreatedAtEpochMs = createdAt,
                                completedConnectionIds = preservedCompleted,
                                currentBuildStepIndex =
                                    it.currentBuildStepIndex.coerceIn(
                                        0,
                                        (result.bundle.diagramSpec.buildPlan
                                            ?.steps
                                            ?.lastIndex
                                            ?: 0).coerceAtLeast(0),
                                    ),
                            )
                        }
                        persistCurrent()
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

    private fun restoreReadyProject(result: ResumeResult.Ready) {
        val validConnections =
            result.bundle.circuitGraph.connections.map { it.id }.toSet()
        val completed =
            result.saved.completedConnectionIds.intersect(validConnections)
        val lastIndex =
            (result.bundle.diagramSpec.buildPlan?.steps?.lastIndex ?: 0)
                .coerceAtLeast(0)
        val restoredScreen = runCatching {
            AppScreen.valueOf(result.saved.lastScreen)
        }.getOrDefault(AppScreen.DESIGN).let { savedScreen ->
            if (savedScreen == AppScreen.CONTROL) AppScreen.CONNECT else savedScreen
        }

        _state.update {
            it.copy(
                busy = false,
                error = null,
                projectId = result.saved.id,
                projectTitle = result.saved.title,
                projectCreatedAtEpochMs = result.saved.createdAtEpochMs,
                goalText = result.saved.goalText,
                clarificationValues = result.saved.clarificationValues,
                pendingQuestions = emptyList(),
                requirements = result.requirements,
                bundle = result.bundle,
                completedConnectionIds = completed,
                currentBuildStepIndex =
                    result.saved.currentBuildStepIndex.coerceIn(0, lastIndex),
                deployed = result.saved.deployed,
                connection = null,
                deployProgress = 0,
                deployMessage =
                    if (result.saved.deployed) {
                        "前回の設定済みプロジェクトです。装置へ再接続してください。"
                    } else {
                        ""
                    },
                screen = restoredScreen,
                lastSavedAtEpochMs = result.saved.updatedAtEpochMs,
            )
        }
    }

    private fun persistCurrent() {
        val snapshotState = _state.value
        val projectId = snapshotState.projectId ?: return
        if (snapshotState.goalText.isBlank() || snapshotState.bundle == null) return

        val now = System.currentTimeMillis()
        val project = SavedProject(
            id = projectId,
            title = snapshotState.projectTitle
                ?: ProjectTitle.fromGoal(snapshotState.goalText),
            goalText = snapshotState.goalText,
            clarificationValues = snapshotState.clarificationValues,
            completedConnectionIds = snapshotState.completedConnectionIds,
            currentBuildStepIndex = snapshotState.currentBuildStepIndex,
            lastScreen = snapshotState.screen.name,
            deployed = snapshotState.deployed,
            createdAtEpochMs =
                snapshotState.projectCreatedAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectRepository.save(project)
            }
            _state.update {
                it.copy(
                    projectTitle = project.title,
                    projectCreatedAtEpochMs = project.createdAtEpochMs,
                    lastSavedAtEpochMs = now,
                )
            }
            refreshSavedProjects()
        }
    }

    private fun refreshSavedProjects() {
        viewModelScope.launch {
            val projects = withContext(Dispatchers.IO) {
                projectRepository.list()
            }
            _state.update { it.copy(savedProjects = projects) }
        }
    }

    private fun recordFriction(
        action: FrictionTelemetryRecorder.() -> Unit,
    ) {
        frictionTelemetry.action()
        _friction.value = frictionTelemetry.snapshot()
    }

    private fun updateDeploy(progress: Int, message: String) {
        _state.update {
            it.copy(
                deployProgress = progress,
                deployMessage = message,
            )
        }
    }

    class Factory(
        private val projectRepository: ProjectRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BuilderAppViewModel::class.java))
            return BuilderAppViewModel(
                projectRepository = projectRepository,
            ) as T
        }
    }

    private sealed interface ResolutionResult {
        data class Questions(
            val questions: List<com.aielectronics.core.model.MissingRequirement>,
        ) : ResolutionResult

        data class Success(
            val requirements: ResolvedRequirements,
            val bundle: ReleaseBundle,
        ) : ResolutionResult

        data class Error(val message: String) : ResolutionResult
    }

    private sealed interface ResumeResult {
        data class Ready(
            val saved: SavedProject,
            val requirements: ResolvedRequirements,
            val bundle: ReleaseBundle,
        ) : ResumeResult

        data class NeedsInput(
            val saved: SavedProject,
            val resolution: RequirementResolution.NeedUserInput,
        ) : ResumeResult

        data class NeedsCompileInput(
            val saved: SavedProject,
            val questions: List<com.aielectronics.core.model.MissingRequirement>,
        ) : ResumeResult

        data class Error(val message: String) : ResumeResult
    }
}
