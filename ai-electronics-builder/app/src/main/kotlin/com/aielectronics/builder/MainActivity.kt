package com.aielectronics.builder

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aielectronics.ble.android.AndroidBlePermissionPolicy
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
    val viewModel: BuilderAppViewModel = viewModel(
        factory = BuilderAppViewModel.Factory(repository)
    )
    val state by viewModel.state.collectAsState()

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
        onBuildProgress = viewModel::updateBuildProgress,
        onResumeProject = viewModel::resumeProject,
        onDeleteProject = viewModel::deleteProject,
        onNewProject = viewModel::newProject,
        onOpenBuildStep = viewModel::openBuildStep,
        onClearError = viewModel::clearError,
    )
}
