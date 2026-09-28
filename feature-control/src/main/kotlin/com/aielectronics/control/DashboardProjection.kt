package com.aielectronics.control

import com.aielectronics.core.model.TestSpec
import com.aielectronics.core.model.UiPage
import com.aielectronics.core.model.UiSpec
import com.aielectronics.core.model.UiWidget

object DashboardProjection {

    fun pages(
        uiSpec: UiSpec,
        tests: List<TestSpec>,
    ): List<UiPage> {
        val basePages = uiSpec.pages.filterNot { it.id == "diagnostics" }

        if (tests.isEmpty()) {
            return basePages
        }

        val diagnosticWidgets = tests.map { test ->
            UiWidget.Button(
                id = "diagnostic_" + test.id,
                binding = "tests." + test.id,
                label = test.name,
            )
        }

        return basePages + UiPage(
            id = "diagnostics",
            title = "診断",
            widgets = diagnosticWidgets,
        )
    }

    fun mutableSettingIds(uiSpec: UiSpec): Set<String> =
        uiSpec.pages
            .flatMap { it.widgets }
            .mapNotNull { widget ->
                when {
                    widget.binding.startsWith("settings.") ->
                        widget.binding.removePrefix("settings.")

                    widget.binding.startsWith("controls.") ->
                        widget.binding.removePrefix("controls.")

                    else -> null
                }
            }
            .toSet()
}
