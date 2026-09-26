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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aielectronics.ble.android.AndroidBlePermissionPolicy

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
private fun BuilderAppHost(
    viewModel: BuilderAppViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

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
        onBuildStepCompleted = viewModel::confirmBuildStep,
        onClearError = viewModel::clearError,
    )
}
