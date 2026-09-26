package com.aielectronics.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedWorkspaceControllerTest {

    private fun workspace(): AdvancedWorkspace =
        AdvancedWorkspace(
            projectId = "p1",
            selectedPath = "src/project.cpp",
            files = listOf(
                WorkspaceFile(
                    path = "src/project.cpp",
                    language = EditorLanguage.CPP,
                    baselineContent = "int value = 1;\nvoid tick() {}",
                ),
                WorkspaceFile(
                    path = "hardware/netlist.txt",
                    language = EditorLanguage.TEXT,
                    baselineContent = "immutable",
                    readOnly = true,
                ),
            ),
        )

    @Test
    fun `editing preserves immutable baseline and creates diff`() {
        val controller = AdvancedWorkspaceController(workspace())

        controller.editSelected("int value = 2;\nvoid tick() {}")

        val file = controller.snapshot().selectedFile!!
        assertEquals("int value = 1;\nvoid tick() {}", file.baselineContent)
        assertEquals("int value = 2;\nvoid tick() {}", file.workingContent)
        assertTrue(file.modified)
        assertTrue(controller.diffSelected().any { it.kind == DiffKind.REMOVED })
        assertTrue(controller.diffSelected().any { it.kind == DiffKind.ADDED })
    }

    @Test
    fun `replace and restore returns generated baseline exactly`() {
        val controller = AdvancedWorkspaceController(workspace())

        controller.replaceInSelected(
            search = "value = 1",
            replacement = "value = 9",
            replaceAll = true,
        )
        assertTrue(controller.snapshot().selectedFile!!.modified)

        controller.restoreSelected()

        val file = controller.snapshot().selectedFile!!
        assertEquals(file.baselineContent, file.workingContent)
        assertFalse(file.modified)
    }

    @Test
    fun `restore all resets every editable file`() {
        val base = workspace().copy(
            files = workspace().files + WorkspaceFile(
                path = "runtime/project.manifest",
                language = EditorLanguage.MANIFEST,
                baselineContent = "meta\tversion\t1",
            )
        )
        val controller = AdvancedWorkspaceController(base)

        controller.editSelected("changed")
        controller.select("runtime/project.manifest")
        controller.editSelected("changed manifest")
        assertEquals(2, controller.snapshot().modifiedFileCount)

        controller.restoreAll()

        assertEquals(0, controller.snapshot().modifiedFileCount)
    }
}
