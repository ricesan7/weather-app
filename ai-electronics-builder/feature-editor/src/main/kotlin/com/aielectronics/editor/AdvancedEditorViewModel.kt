package com.aielectronics.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aielectronics.core.model.ReleaseBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdvancedEditorState(
    val workspace: AdvancedWorkspace,
    val searchText: String = "",
    val replaceText: String = "",
    val lastBuildResult: AdvancedBuildResult? = null,
)

class AdvancedEditorViewModel(
    bundle: ReleaseBundle,
    private val buildService: AdvancedBuildService = UnavailableAdvancedBuildService(),
) : ViewModel() {

    private val controller = AdvancedWorkspaceController(
        AdvancedWorkspaceGenerator().generate(bundle)
    )

    private val _state = MutableStateFlow(
        AdvancedEditorState(controller.snapshot())
    )
    val state: StateFlow<AdvancedEditorState> = _state.asStateFlow()

    fun select(path: String) {
        _state.value = _state.value.copy(
            workspace = controller.select(path),
        )
    }

    fun edit(content: String) {
        _state.value = _state.value.copy(
            workspace = controller.editSelected(content),
        )
    }

    fun setSearch(value: String) {
        _state.value = _state.value.copy(searchText = value)
    }

    fun setReplacement(value: String) {
        _state.value = _state.value.copy(replaceText = value)
    }

    fun replaceNext() {
        val current = _state.value
        _state.value = current.copy(
            workspace = controller.replaceInSelected(
                search = current.searchText,
                replacement = current.replaceText,
                replaceAll = false,
            )
        )
    }

    fun replaceAll() {
        val current = _state.value
        _state.value = current.copy(
            workspace = controller.replaceInSelected(
                search = current.searchText,
                replacement = current.replaceText,
                replaceAll = true,
            )
        )
    }

    fun restoreSelected() {
        _state.value = _state.value.copy(
            workspace = controller.restoreSelected(),
        )
    }

    fun restoreAll() {
        _state.value = _state.value.copy(
            workspace = controller.restoreAll(),
        )
    }

    fun validateWorkspace() {
        val validation = AdvancedWorkspaceValidator.validate(
            controller.snapshot()
        )
        val message = if (validation.valid) {
            "ローカル構成チェックに成功しました。"
        } else {
            validation.messages.joinToString("\n")
        }
        val level = if (validation.valid) LogLevel.INFO else LogLevel.ERROR
        val workspace = controller.appendLog(level, message)

        _state.value = _state.value.copy(workspace = workspace)
    }

    fun build() {
        val result = buildService.build(controller.snapshot())
        val pair = when (result) {
            is AdvancedBuildResult.Success ->
                LogLevel.INFO to result.log
            is AdvancedBuildResult.Failed ->
                LogLevel.ERROR to result.log
            is AdvancedBuildResult.ServiceUnavailable ->
                LogLevel.WARNING to result.message
        }

        _state.value = _state.value.copy(
            workspace = controller.appendLog(pair.first, pair.second),
            lastBuildResult = result,
        )
    }

    fun diff(): List<DiffLine> = controller.diffSelected()

    class Factory(
        private val bundle: ReleaseBundle,
        private val buildService: AdvancedBuildService = UnavailableAdvancedBuildService(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AdvancedEditorViewModel::class.java))
            return AdvancedEditorViewModel(
                bundle = bundle,
                buildService = buildService,
            ) as T
        }
    }
}
