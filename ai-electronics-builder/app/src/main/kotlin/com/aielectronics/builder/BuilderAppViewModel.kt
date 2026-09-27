package com.aielectronics.builder

import android.content.Context
import android.provider.Settings
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
import com.aielectronics.application.SavedGraphNodePosition
import com.aielectronics.application.SavedProject
import com.aielectronics.ble.android.AndroidBleRuntimeConnector
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.AppBridgeDirection
import com.aielectronics.core.model.AppHardwareIntegrationContract
import com.aielectronics.core.model.ProjectGraphNodeKind
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ResolvedRequirements
import com.aielectronics.control.RuntimeControlClient
import com.aielectronics.runtime.CanonicalManifestEncoder
import com.aielectronics.runtime.RuntimeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private val revisionAssistant: RevisionLanguageAssistant = LocalRevisionLanguageAssistant(),
    private val base44BridgeClient: Base44HardwareBridgeClient? =
        BuildConfig.BASE44_BRIDGE_URL
            .takeIf { it.isNotBlank() }
            ?.let(::Base44HardwareBridgeClient),
    private val bridgeCredentialStore: Base44BridgeCredentialStore? = null,
) : ViewModel() {

    private val localRevisionAssistant = LocalRevisionLanguageAssistant()
    private var bridgeJob: Job? = null

    private val _state = MutableStateFlow(BuilderAppState())
    val state: StateFlow<BuilderAppState> = _state.asStateFlow()

    private val _friction = MutableStateFlow(frictionTelemetry.snapshot())
    val friction: StateFlow<FrictionSnapshot> = _friction.asStateFlow()

    init {
        _state.update {
            it.copy(revisionAssistantLabel = revisionAssistant.statusLabel)
        }
        if (projectRepository is InMemoryProjectRepository) {
            _state.update {
                it.copy(savedProjects = projectRepository.list())
            }
        } else {
            refreshSavedProjects()
        }
    }

    fun setGoal(text: String) {
        _state.update { it.copy(goalText = text) }
    }

    fun setAdditionalRequest(text: String) {
        _state.update { it.copy(additionalRequestText = text) }
    }

    fun selectGraphNode(nodeId: String?) {
        val graph = _state.value.bundle?.projectGraph ?: return
        val selected = nodeId?.takeIf { candidate ->
            graph.nodes.any { it.id == candidate }
        }
        _state.update {
            it.copy(
                selectedGraphNodeId = selected,
                error = null,
            )
        }
    }

    fun moveGraphNode(
        nodeId: String,
        position: SavedGraphNodePosition,
    ) {
        val graph = _state.value.bundle?.projectGraph ?: return
        if (graph.nodes.none { it.id == nodeId }) return

        val normalized = SavedGraphNodePosition(
            x = position.x.coerceIn(0.02, 0.98),
            y = position.y.coerceIn(0.10, 0.96),
        )
        _state.update {
            it.copy(
                selectedGraphNodeId = nodeId,
                graphNodePositions =
                    it.graphNodePositions + (nodeId to normalized),
            )
        }
    }

    fun finishGraphNodeMove() {
        persistCurrent()
    }

    fun addGraphElement(description: String) {
        val clean = description.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(error = "追加したいものを入力してください。") }
            return
        }
        submitVisualRevision(
            "Visual Projectから次の要素を追加してください。\n" +
                "追加内容: " + clean + "\n" +
                "既存の回路・電源・ファームウェア・Base44アプリ・Hardware Bridgeとの整合性を保ち、" +
                "必要な変更をすべて同じプロジェクトへ反映してください。"
        )
    }

    fun changeGraphNode(
        nodeId: String,
        instruction: String,
    ) {
        val clean = instruction.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(error = "変更内容を入力してください。") }
            return
        }
        val node = _state.value.bundle
            ?.projectGraph
            ?.nodes
            ?.firstOrNull { it.id == nodeId }
            ?: run {
                _state.update { it.copy(error = "選択した要素が見つかりません。") }
                return
            }

        submitVisualRevision(
            "Visual Project上の要素を変更してください。\n" +
                "対象: " + node.label + " (" + node.kind.name + ")\n" +
                "変更内容: " + clean + "\n" +
                "変更後は回路・BOM・配線・動作・ファームウェア・Base44アプリ・Hardware Bridgeを" +
                "必要に応じて再生成し、安全検証を通してください。"
        )
    }

    fun deleteGraphNode(nodeId: String) {
        val node = _state.value.bundle
            ?.projectGraph
            ?.nodes
            ?.firstOrNull { it.id == nodeId }
            ?: run {
                _state.update { it.copy(error = "選択した要素が見つかりません。") }
                return
            }

        if (
            node.kind == ProjectGraphNodeKind.BOARD ||
            node.kind == ProjectGraphNodeKind.RUNTIME
        ) {
            _state.update {
                it.copy(
                    error =
                        "マイコン本体とHardware Runtimeは直接削除できません。" +
                            "置き換えたい場合は「変更」を使用してください。"
                )
            }
            return
        }

        submitVisualRevision(
            "Visual Project上の次の要素を削除してください。\n" +
                "対象: " + node.label + " (" + node.kind.name + ")\n" +
                "この要素に依存する配線・電源・動作ルール・ファームウェア・Base44 UIも確認し、" +
                "不要になった依存要素だけを安全に整理してください。"
        )
    }

    private fun submitVisualRevision(request: String) {
        val current = _state.value
        if (current.projectId == null || current.bundle == null) {
            _state.update { it.copy(error = "先に設計を作成してください。") }
            return
        }
        if (current.busy) return

        _state.update {
            it.copy(
                screen = AppScreen.REVISION,
                additionalRequestText = request,
                revisionReturnScreen = AppScreen.GRAPH,
                revisionCandidateGoalText =
                    it.revisionCandidateGoalText.ifBlank { it.goalText },
                error = null,
            )
        }
        applyAdditionalRequest()
    }

    fun setBridgePairingCode(text: String) {
        _state.update {
            it.copy(
                bridgePairingCode = text.uppercase().replace(" ", ""),
                error = null,
            )
        }
    }

    fun pairBase44(context: Context) {
        val bridge = base44BridgeClient ?: run {
            _state.update { it.copy(error = "Base44 Bridge URLが設定されていません。") }
            return
        }
        val localProjectId = _state.value.projectId ?: run {
            _state.update { it.copy(error = "先にプロジェクトを作成してください。") }
            return
        }
        val code = _state.value.bridgePairingCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(error = "CircuitFlowで発行した接続コードを入力してください。") }
            return
        }

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()

        _state.update {
            it.copy(
                base44BridgeStatus = "CircuitFlowへ接続しています…",
                base44BridgeOnline = false,
                error = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bridge.pair(code, deviceId)
                }
            }.onSuccess { credentials ->
                bridgeCredentialStore?.save(localProjectId, credentials)
                _state.update {
                    it.copy(
                        bridgePairingCode = "",
                        base44BridgeStatus = "CircuitFlowとペアリングしました。",
                        base44BridgeOnline = true,
                    )
                }
                startBridgeSync(credentials)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        base44BridgeStatus = "CircuitFlowとのペアリングに失敗しました。",
                        base44BridgeOnline = false,
                        error = throwable.message ?: "Base44 Bridgeへの接続に失敗しました。",
                    )
                }
            }
        }
    }

    fun applyAdditionalRequest() {
        val current = _state.value
        val requestText = current.additionalRequestText.trim()
        if (requestText.isBlank()) {
            _state.update { it.copy(error = "追加したい要望や回答を入力してください。") }
            return
        }
        if (current.projectId == null || current.bundle == null) {
            _state.update { it.copy(error = "先に設計を作成してください。") }
            return
        }
        if (current.busy) return

        val userMessage = RevisionChatMessage(
            id = UUID.randomUUID().toString(),
            speaker = RevisionSpeaker.USER,
            text = requestText,
        )
        val history = current.revisionMessages + userMessage
        val candidateGoal =
            current.revisionCandidateGoalText.ifBlank { current.goalText }
        val revisedClarifications =
            current.revisionPendingSlotId?.let { slotId ->
                current.revisionClarificationValues + (slotId to requestText)
            } ?: current.revisionClarificationValues

        _state.update {
            it.copy(
                additionalRequestText = "",
                revisionMessages = history,
                revisionClarificationValues = revisedClarifications,
                revisionPendingSlotId = null,
                busy = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val languageRequest = RevisionLanguageRequest(
                    currentGoal = candidateGoal,
                    userMessage = requestText,
                    history = history,
                )
                val primary = withContext(Dispatchers.IO) {
                    revisionAssistant.refine(languageRequest)
                }
                val languageResult =
                    if (primary.isSuccess) {
                        primary.getOrThrow()
                    } else {
                        val fallback = withContext(Dispatchers.Default) {
                            localRevisionAssistant.refine(languageRequest).getOrThrow()
                        }
                        fallback.copy(
                            assistantMessage =
                                "AIゲートウェイへ接続できなかったため、ローカル確認に切り替えました。\n" +
                                    fallback.assistantMessage,
                        )
                    }

                if (languageResult.needsClarification) {
                    val assistantText = listOf(
                        languageResult.assistantMessage,
                        languageResult.clarificationQuestion,
                    )
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString("\n")

                    _state.update {
                        it.copy(
                            busy = false,
                            revisionCandidateGoalText = languageResult.updatedGoal,
                            revisionClarificationValues = revisedClarifications,
                            revisionMessages =
                                it.revisionMessages + assistantMessage(assistantText),
                        )
                    }
                    return@launch
                }

                when (
                    val result = withContext(Dispatchers.Default) {
                        resolveRevisionCandidate(
                            goal = languageResult.updatedGoal,
                            clarifications = revisedClarifications,
                        )
                    }
                ) {
                    is ResolutionResult.Questions -> {
                        val question = result.questions.firstOrNull()
                        if (question == null) {
                            _state.update {
                                it.copy(
                                    busy = false,
                                    revisionCandidateGoalText = languageResult.updatedGoal,
                                    revisionMessages =
                                        it.revisionMessages + assistantMessage(
                                            languageResult.assistantMessage
                                        ),
                                )
                            }
                            return@launch
                        }

                        recordFriction {
                            recordQuestionPresented(question.slotId)
                        }
                        val assistantText = listOf(
                            languageResult.assistantMessage,
                            "確認です。" + question.userQuestion,
                        )
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("\n")

                        _state.update {
                            it.copy(
                                busy = false,
                                revisionCandidateGoalText = languageResult.updatedGoal,
                                revisionClarificationValues = revisedClarifications,
                                revisionPendingSlotId = question.slotId,
                                revisionMessages =
                                    it.revisionMessages + assistantMessage(assistantText),
                            )
                        }
                    }

                    is ResolutionResult.Success -> {
                        commitRevision(
                            updatedGoal = languageResult.updatedGoal,
                            clarifications = revisedClarifications,
                            requirements = result.requirements,
                            bundle = result.bundle,
                        )
                    }

                    is ResolutionResult.Error -> {
                        val assistantText = listOf(
                            languageResult.assistantMessage,
                            result.message,
                            "条件を修正して続けて入力してください。現在の安全な設計は変更していません。",
                        )
                            .filter { it.isNotBlank() }
                            .joinToString("\n")

                        _state.update {
                            it.copy(
                                busy = false,
                                revisionCandidateGoalText = languageResult.updatedGoal,
                                revisionClarificationValues = revisedClarifications,
                                revisionMessages =
                                    it.revisionMessages + assistantMessage(assistantText),
                            )
                        }
                    }
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = throwable.message ?: "追加要望の対話処理に失敗しました。",
                    )
                }
            }
        }
    }

    fun newProject() {
        bridgeJob?.cancel()
        bridgeJob = null
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
        _state.update {
            if (screen == AppScreen.REVISION && it.revisionMessages.isEmpty()) {
                it.copy(
                    screen = screen,
                    error = null,
                    additionalRequestText = "",
                    revisionCandidateGoalText = it.goalText,
                    revisionClarificationValues = emptyMap(),
                    revisionPendingSlotId = null,
                    revisionStatusMessage = "",
                    revisionReturnScreen = null,
                    revisionMessages = listOf(
                        assistantMessage(
                            "追加したい機能や変更したい条件を教えてください。" +
                                "必要な確認をしながら仕様を固め、揃った時点で自動的に再設計します。"
                        )
                    ),
                )
            } else {
                it.copy(screen = screen, error = null)
            }
        }
        persistCurrent()
    }

    fun confirmBuildStep(connectionId: String? = null) {
        if (connectionId != null) {
            recordFriction { recordGuidedBuildConfirmation(connectionId) }
        }
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
                buildTargetStepOrder = null,
            )
        }

        newlyCompleted.forEach { connectionId ->
            recordFriction { recordGuidedBuildConfirmation(connectionId) }
        }
        persistCurrent()
    }

    fun openBuildStep(order: Int) {
        val from = _state.value.screen
        recordFriction {
            recordScreenTransition(
                from = from.name,
                to = AppScreen.BUILD.name,
                userInitiated = true,
            )
        }
        _state.update {
            it.copy(
                screen = AppScreen.BUILD,
                buildTargetStepOrder = order,
                error = null,
            )
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
                bridgeJob?.cancel()
                bridgeJob = null
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
                maybeStartBridgeSync()
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

    private fun maybeStartBridgeSync() {
        val projectId = _state.value.projectId ?: return
        val credentials = bridgeCredentialStore?.load(projectId) ?: return
        startBridgeSync(credentials)
    }

    private fun startBridgeSync(credentials: Base44BridgeCredentials) {
        val bridge = base44BridgeClient ?: return
        bridgeJob?.cancel()

        bridgeJob = viewModelScope.launch(Dispatchers.IO) {
            var acknowledgements = emptyList<Base44BridgeAck>()

            while (isActive) {
                val snapshot = _state.value
                val bundle = snapshot.bundle

                if (bundle == null) {
                    _state.update {
                        it.copy(
                            base44BridgeOnline = false,
                            base44BridgeStatus =
                                "CircuitFlow連携済み。設計データの準備を待っています。",
                        )
                    }
                    delay(BRIDGE_SYNC_INTERVAL_MS)
                    continue
                }

                val contract = visualAppLayoutContract(
                    bundle = bundle,
                    baseContract =
                        bundle.softwarePlan.base44Handoff?.integration,
                    positions = snapshot.graphNodePositions,
                )
                val connection = snapshot.connection

                if (connection == null) {
                    runCatching {
                        bridge.sync(
                            credentials = credentials,
                            telemetry = emptyMap(),
                            settings = emptyMap(),
                            contract = contract,
                            hardwareConnected = false,
                            acknowledgements = acknowledgements,
                        )
                    }.onSuccess {
                        acknowledgements = emptyList()
                        _state.update {
                            it.copy(
                                base44BridgeOnline = true,
                                base44BridgeStatus =
                                    "CircuitFlowと画面構成を同期中。実機は未接続です。",
                                bridgeLastSyncAtEpochMs =
                                    System.currentTimeMillis(),
                            )
                        }
                    }.onFailure { throwable ->
                        _state.update {
                            it.copy(
                                base44BridgeOnline = false,
                                base44BridgeStatus =
                                    "CircuitFlow同期エラー: " +
                                        (throwable.message ?: "通信失敗"),
                            )
                        }
                    }

                    delay(BRIDGE_SYNC_INTERVAL_MS)
                    continue
                }

                runCatching {
                    val runtime = RuntimeControlClient(connection.transport)
                    val telemetry = runtime.telemetry()
                    val settings = runtime.loadSettings(bundle.uiSpec)
                    bridge.sync(
                        credentials = credentials,
                        telemetry = telemetry,
                        settings = settings,
                        contract = contract,
                        hardwareConnected = true,
                        acknowledgements = acknowledgements,
                    )
                }.onSuccess { sync ->
                    val runtime = RuntimeControlClient(connection.transport)
                    val nextAcks = sync.commands.map { command ->
                        executeBridgeCommand(
                            runtime = runtime,
                            command = command,
                            contract = contract,
                        )
                    }
                    acknowledgements = nextAcks
                    _state.update {
                        it.copy(
                            base44BridgeOnline = true,
                            base44BridgeStatus =
                                "CircuitFlowと実機データを同期中",
                            bridgeLastSyncAtEpochMs =
                                System.currentTimeMillis(),
                        )
                    }
                }.onFailure { throwable ->
                    acknowledgements = emptyList()
                    _state.update {
                        it.copy(
                            base44BridgeOnline = false,
                            base44BridgeStatus =
                                "CircuitFlow同期エラー: " +
                                    (throwable.message ?: "通信失敗"),
                        )
                    }
                }

                delay(BRIDGE_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun visualAppLayoutContract(
        bundle: ReleaseBundle,
        baseContract: AppHardwareIntegrationContract?,
        positions: Map<String, SavedGraphNodePosition>,
    ): AppHardwareIntegrationContract? {
        val contract = baseContract ?: return null
        if (positions.isEmpty() || contract.pages.isEmpty()) return contract

        val graph = bundle.projectGraph
        val pageNodeByPageId = graph.nodes
            .filter { it.kind == ProjectGraphNodeKind.UI_PAGE }
            .mapNotNull { node ->
                node.referenceId?.let { pageId -> pageId to node.id }
            }
            .toMap()

        val widgetNodeByKey = graph.nodes
            .filter { it.kind == ProjectGraphNodeKind.UI_WIDGET }
            .mapNotNull { node ->
                val pageId = node.metadata["pageId"] ?: return@mapNotNull null
                val widgetId = node.referenceId ?: return@mapNotNull null
                (pageId + "::" + widgetId) to node.id
            }
            .toMap()

        val orderedPages = contract.pages
            .sortedWith(
                compareBy(
                    { page ->
                        pageNodeByPageId[page.id]
                            ?.let(positions::get)
                            ?.y
                            ?: (page.order + 1).toDouble()
                    },
                    { it.order },
                )
            )
            .mapIndexed { pageIndex, page ->
                val orderedWidgets = page.widgets
                    .sortedWith(
                        compareBy(
                            { widget ->
                                widgetNodeByKey[page.id + "::" + widget.id]
                                    ?.let(positions::get)
                                    ?.y
                                    ?: (widget.order + 1).toDouble()
                            },
                            { it.order },
                        )
                    )
                    .mapIndexed { widgetIndex, widget ->
                        widget.copy(order = widgetIndex)
                    }

                page.copy(
                    order = pageIndex,
                    widgets = orderedWidgets,
                )
            }

        return contract.copy(pages = orderedPages)
    }

    private fun executeBridgeCommand(
        runtime: RuntimeControlClient,
        command: Base44BridgeCommand,
        contract: AppHardwareIntegrationContract?,
    ): Base44BridgeAck =
        runCatching {
            val allowed = contract
                ?.channels
                ?.any {
                    it.binding == command.binding &&
                        it.direction == AppBridgeDirection.BASE44_TO_HARDWARE
                } == true

            require(allowed) {
                "Project ContractにないBridgeコマンドを拒否しました。"
            }

            when {
                command.binding.startsWith("settings.") -> {
                    val settingId = command.binding.removePrefix("settings.")
                    runtime.setSetting(settingId, command.value)
                }

                else -> error(
                    "未対応のBridge bindingです: " + command.binding
                )
            }
        }.fold(
            onSuccess = {
                Base44BridgeAck(
                    commandId = command.commandId,
                    ok = true,
                )
            },
            onFailure = { throwable ->
                Base44BridgeAck(
                    commandId = command.commandId,
                    ok = false,
                    error = throwable.message ?: "runtime_command_failed",
                )
            },
        )

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
            when (savedScreen) {
                AppScreen.CONTROL -> AppScreen.CONNECT
                AppScreen.REVISION -> AppScreen.DESIGN
                else -> savedScreen
            }
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
                graphNodePositions = result.saved.graphNodePositions.filterKeys { nodeId ->
                    result.bundle.projectGraph.nodes.any { it.id == nodeId }
                },
                selectedGraphNodeId = null,
            )
        }
    }

    private fun resolveRevisionCandidate(
        goal: String,
        clarifications: Map<String, String>,
    ): ResolutionResult {
        val intent = interpreter.interpret(
            text = goal,
            clarifications = clarifications,
        )
        return when (val resolution = engine.resolve(intent)) {
            is RequirementResolution.NeedUserInput ->
                ResolutionResult.Questions(resolution.missing)

            is RequirementResolution.Ready ->
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
                            BeginnerErrorPresenter.validationBlocked(compile.report)
                        )

                    is CompileResult.Failed ->
                        ResolutionResult.Error(
                            BeginnerErrorPresenter.compileFailure(compile.error)
                        )
                }
        }
    }

    private fun commitRevision(
        updatedGoal: String,
        clarifications: Map<String, String>,
        requirements: ResolvedRequirements,
        bundle: ReleaseBundle,
    ) {
        bridgeJob?.cancel()
        bridgeJob = null
        val previous = _state.value
        val currentConnectionIds =
            bundle.circuitGraph.connections.map { it.id }.toSet()
        val preservedCompleted =
            previous.completedConnectionIds.intersect(currentConnectionIds)
        val lastIndex =
            (bundle.diagramSpec.buildPlan?.steps?.lastIndex ?: 0).coerceAtLeast(0)
        val returnScreen = previous.revisionReturnScreen ?: AppScreen.DESIGN
        val validGraphNodeIds = bundle.projectGraph.nodes.map { it.id }.toSet()
        val preservedGraphPositions =
            previous.graphNodePositions.filterKeys { it in validGraphNodeIds }

        recordFriction {
            recordScreenTransition(
                from = previous.screen.name,
                to = returnScreen.name,
                userInitiated = false,
            )
        }

        _state.update {
            it.copy(
                busy = false,
                goalText = updatedGoal,
                clarificationValues = clarifications,
                pendingQuestions = emptyList(),
                requirements = requirements,
                bundle = bundle,
                screen = returnScreen,
                deployed = false,
                connection = null,
                deployProgress = 0,
                deployMessage = "設計を更新したため、装置への再設定が必要です。",
                completedConnectionIds = preservedCompleted,
                currentBuildStepIndex =
                    it.currentBuildStepIndex.coerceIn(0, lastIndex),
                revisionStatusMessage =
                    "追加要望を対話で確定し、安全確認を通して設計を更新しました。",
                revisionMessages = emptyList(),
                revisionCandidateGoalText = "",
                revisionClarificationValues = emptyMap(),
                revisionPendingSlotId = null,
                additionalRequestText = "",
                graphNodePositions = preservedGraphPositions,
                selectedGraphNodeId =
                    it.selectedGraphNodeId?.takeIf { nodeId ->
                        nodeId in validGraphNodeIds
                    },
                revisionReturnScreen = null,
                error = null,
            )
        }
        persistCurrent()
    }

    private fun assistantMessage(text: String): RevisionChatMessage =
        RevisionChatMessage(
            id = UUID.randomUUID().toString(),
            speaker = RevisionSpeaker.ASSISTANT,
            text = text,
        )

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
            graphNodePositions = snapshotState.graphNodePositions,
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
        private val revisionAssistant: RevisionLanguageAssistant =
            LocalRevisionLanguageAssistant(),
        private val bridgeCredentialStore: Base44BridgeCredentialStore? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BuilderAppViewModel::class.java))
            return BuilderAppViewModel(
                projectRepository = projectRepository,
                revisionAssistant = revisionAssistant,
                bridgeCredentialStore = bridgeCredentialStore,
            ) as T
        }
    }

    private companion object {
        const val BRIDGE_SYNC_INTERVAL_MS = 3_000L
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
