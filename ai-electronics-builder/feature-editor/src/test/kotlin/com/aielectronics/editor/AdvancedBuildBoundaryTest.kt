package com.aielectronics.editor

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdvancedBuildBoundaryTest {

    @Test
    fun `default build service never pretends firmware was created`() {
        val workspace = AdvancedWorkspace(
            projectId = "p1",
            selectedPath = "src/project.cpp",
            files = listOf(
                WorkspaceFile(
                    path = "src/project.cpp",
                    language = EditorLanguage.CPP,
                    baselineContent = "void tick() {}",
                )
            ),
        )

        val result = UnavailableAdvancedBuildService().build(workspace)

        assertIs<AdvancedBuildResult.ServiceUnavailable>(result)
    }

    @Test
    fun `workspace validation catches missing manifest`() {
        val workspace = AdvancedWorkspace(
            projectId = "p1",
            selectedPath = "src/project.cpp",
            files = listOf(
                WorkspaceFile(
                    path = "src/project.cpp",
                    language = EditorLanguage.CPP,
                    baselineContent = "void tick() {}",
                )
            ),
        )

        val result = AdvancedWorkspaceValidator.validate(workspace)

        assertTrue(!result.valid)
        assertTrue(result.messages.any { it.contains("Manifest") })
    }
}
