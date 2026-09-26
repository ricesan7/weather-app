package com.aielectronics.assembly

import com.aielectronics.core.model.GuidedBuildPlan
import com.aielectronics.core.model.GuidedBuildStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidedBuildStateMachineTest {

    private val plan = GuidedBuildPlan(
        steps = (1..3).map { n ->
            GuidedBuildStep(
                order = n,
                connectionId = "conn_00" + n,
                title = "配線 " + n,
                instruction = "接続 " + n,
                diagramViewId = "step_0" + n,
            )
        }
    )

    @Test
    fun `one completed connection advances one screen`() {
        val machine = GuidedBuildStateMachine(plan)

        assertEquals("conn_001", machine.state().currentStep?.connectionId)
        machine.markCurrentCompleted()
        assertEquals("conn_002", machine.state().currentStep?.connectionId)
        assertEquals(1, machine.state().completedCount)
    }

    @Test
    fun `snapshot resumes build progress`() {
        val first = GuidedBuildStateMachine(plan)
        first.markCurrentCompleted()
        val snapshot = first.snapshot()

        val resumed = GuidedBuildStateMachine(plan, snapshot)

        assertEquals("conn_002", resumed.state().currentStep?.connectionId)
        assertTrue("conn_001" in resumed.state().completedConnectionIds)
    }

    @Test
    fun `finishing all connections marks build complete`() {
        val machine = GuidedBuildStateMachine(plan)
        repeat(3) { machine.markCurrentCompleted() }

        assertEquals(GuidedBuildStatus.COMPLETE, machine.state().status)
        assertEquals(1.0, machine.state().progress)
        assertEquals(null, machine.state().currentStep)
    }
}
