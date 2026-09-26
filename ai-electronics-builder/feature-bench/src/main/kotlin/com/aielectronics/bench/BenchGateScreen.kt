package com.aielectronics.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.runtime.RuntimeTransport

@Composable
fun BenchGateScreen(
    bundle: ReleaseBundle,
    transport: RuntimeTransport,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(bundle, transport) {
        BenchGateViewModel.Factory(bundle, transport)
    }
    val vm: BenchGateViewModel = viewModel(
        key = "bench-" + bundle.designIr.project.id,
        factory = factory,
    )
    val state by vm.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "実機ベンチE2E",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "接続中の装置に対して、Runtime handshake・Manifest配布・自己診断・telemetry・設定往復を一括確認します。"
        )

        if (state.running) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
            Text("ベンチ試験を実行中…")
        }

        state.error?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        state.report?.let { report ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (report.passed) "PASS" else "FAIL",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text("Project: " + report.projectId)
                    report.testedSettingId?.let {
                        Text("設定往復: " + it)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(report.steps) { step ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stepTitle(step.id))
                                Text(
                                    step.message,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                when (step.status) {
                                    BenchStepStatus.PASS -> "PASS"
                                    BenchStepStatus.FAIL -> "FAIL"
                                    BenchStepStatus.PENDING -> "待機"
                                    BenchStepStatus.RUNNING -> "実行中"
                                    BenchStepStatus.SKIPPED -> "SKIP"
                                }
                            )
                        }
                    }
                }

                if (report.telemetry.isNotEmpty()) {
                    item {
                        Text(
                            "Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(report.telemetry.toList()) { entry ->
                        Text(entry.first + " = " + entry.second)
                    }
                }
            }
        }

        Button(
            onClick = vm::runBench,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.report == null) {
                    "ベンチ試験を開始"
                } else {
                    "もう一度実行"
                }
            )
        }

        OutlinedButton(
            onClick = onBack,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("戻る")
        }
    }
}

private fun stepTitle(id: BenchStepId): String = when (id) {
    BenchStepId.HANDSHAKE -> "Runtime確認"
    BenchStepId.DEPLOY -> "Manifest配布"
    BenchStepId.VERIFY -> "設定検証"
    BenchStepId.SELF_TESTS -> "自己診断"
    BenchStepId.TELEMETRY -> "Telemetry"
    BenchStepId.SETTINGS_ROUNDTRIP -> "設定GET→SET"
}
