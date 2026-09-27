package com.aielectronics.builder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.ble.android.AndroidBlePermissionPolicy
import com.aielectronics.parts.CompositeEngineeringCatalog
import com.aielectronics.parts.GoldenEngineeringCatalog
import com.aielectronics.storage.android.SqliteComponentResearchStore
import com.aielectronics.storage.android.SqliteProjectRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    BuilderAppHost()
                }
            }
        }
    }
}

@Composable
private fun BuilderAppHost() {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SqliteProjectRepository(context.applicationContext)
    }
    val componentResearchStore =
        remember(context.applicationContext) {
            SqliteComponentResearchStore(
                context.applicationContext
            )
        }
    val engineeringCatalog =
        remember(componentResearchStore) {
            CompositeEngineeringCatalog(
                base = GoldenEngineeringCatalog,
                researched = componentResearchStore,
            )
        }
    val projectEngine =
        remember(engineeringCatalog) {
            ApplicationProjectEngine(engineeringCatalog)
        }
    val componentResearchClient =
        remember(
            BuildConfig.AI_GATEWAY_URL,
            BuildConfig.AI_GATEWAY_TOKEN,
        ) {
            if (BuildConfig.AI_GATEWAY_URL.isBlank()) {
                null
            } else {
                GatewayComponentResearchClient(
                    revisionEndpoint =
                        BuildConfig.AI_GATEWAY_URL,
                    gatewayToken =
                        BuildConfig.AI_GATEWAY_TOKEN,
                )
            }
        }
    val bridgeCredentialStore = remember(context.applicationContext) {
        Base44BridgeCredentialStore(
            context.applicationContext.getSharedPreferences(
                "base44_hardware_bridge",
                Context.MODE_PRIVATE,
            )
        )
    }
    val revisionAssistant = remember(
        BuildConfig.AI_GATEWAY_URL,
        BuildConfig.AI_GATEWAY_TOKEN,
    ) {
        if (BuildConfig.AI_GATEWAY_URL.isBlank()) {
            LocalRevisionLanguageAssistant()
        } else {
            GatewayRevisionLanguageAssistant(
                endpoint = BuildConfig.AI_GATEWAY_URL,
                gatewayToken = BuildConfig.AI_GATEWAY_TOKEN,
            )
        }
    }
    val viewModel: BuilderAppViewModel = viewModel(
        factory = BuilderAppViewModel.Factory(
            projectRepository = repository,
            revisionAssistant = revisionAssistant,
            bridgeCredentialStore = bridgeCredentialStore,
            engine = projectEngine,
            componentResearchClient =
                componentResearchClient,
            componentResearchStore =
                componentResearchStore,
        )
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.projectId) {
        val projectId = state.projectId
        if (
            projectId != null &&
            bridgeCredentialStore.load(projectId) != null
        ) {
            Base44BridgeKeepAliveService.start(
                context.applicationContext,
                projectId,
            )
        } else if (projectId == null) {
            Base44BridgeKeepAliveService.stop(
                context.applicationContext,
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.connect(context)
        }
    }

    BuilderAppScreen(
        state = state,
        onGoalChange = viewModel::setGoal,
        onAdditionalRequestChange = viewModel::setAdditionalRequest,
        onApplyAdditionalRequest = viewModel::applyAdditionalRequest,
        onStartDesign = viewModel::startDesign,
        onAnswerQuestion = viewModel::answerQuestion,
        onOpen = viewModel::open,
        onConnect = {
            val required = AndroidBlePermissionPolicy.runtimePermissions()
            val missing = required.filter {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isEmpty()) {
                viewModel.connect(context)
            } else {
                permissionLauncher.launch(missing.toTypedArray())
            }
        },
        onDeploy = viewModel::deploy,
        onBridgePairingCodeChange = viewModel::setBridgePairingCode,
        onPairBase44 = { viewModel.pairBase44(context) },
        onReceiveBase44Design = {
            viewModel.receiveBase44Design(context)
        },
        onRetryComponentResearch =
            viewModel::retryPendingComponentResearch,
        onChangeResearchComponent =
            viewModel::prepareResearchComponentChange,
        onBuildProgress = viewModel::updateBuildProgress,
        onResumeProject = viewModel::resumeProject,
        onDeleteProject = viewModel::deleteProject,
        onNewProject = viewModel::newProject,
        onOpenBuildStep = viewModel::openBuildStep,
        onGraphNodeSelect = viewModel::selectGraphNode,
        onGraphNodeMove = viewModel::moveGraphNode,
        onGraphNodeMoveFinished = viewModel::finishGraphNodeMove,
        onGraphLayoutUndo = viewModel::undoGraphLayout,
        onGraphLayoutRedo = viewModel::redoGraphLayout,
        onGraphAddElement = viewModel::addGraphElement,
        onGraphChangeNode = viewModel::changeGraphNode,
        onGraphDeleteNode = viewModel::deleteGraphNode,
        onClearError = viewModel::clearError,
    )
}
