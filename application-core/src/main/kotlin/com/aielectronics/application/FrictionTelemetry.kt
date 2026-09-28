package com.aielectronics.application

enum class FrictionEventType {
    GOAL_SUBMITTED,
    QUESTION_PRESENTED,
    QUESTION_ANSWERED,
    SCREEN_TRANSITION,
    TECHNICAL_CHOICE_PRESENTED,
    MANUAL_TECHNICAL_SETTING,
    GUIDED_BUILD_CONFIRMATION,
    DEVICE_CONNECT_ACTION,
    DEPLOY_ACTION,
}

data class FrictionEvent(
    val type: FrictionEventType,
    val detail: String? = null,
    val fromScreen: String? = null,
    val toScreen: String? = null,
    val userInitiated: Boolean = true,
)

data class FrictionSnapshot(
    val questionsPresented: Int = 0,
    val questionsAnswered: Int = 0,
    val technicalChoicesPresented: Int = 0,
    val screenTransitions: Int = 0,
    val userInitiatedScreenTransitions: Int = 0,
    val manualTechnicalSettings: Int = 0,
    val goalSubmissions: Int = 0,
    val guidedBuildConfirmations: Int = 0,
    val deviceConnectActions: Int = 0,
    val deployActions: Int = 0,
    val totalEvents: Int = 0,
) {
    val avoidableTechnicalActions: Int
        get() = technicalChoicesPresented + manualTechnicalSettings

    val essentialUserActions: Int
        get() = goalSubmissions +
            questionsAnswered +
            userInitiatedScreenTransitions +
            guidedBuildConfirmations +
            deviceConnectActions +
            deployActions
}

class FrictionTelemetryRecorder {
    private val events = mutableListOf<FrictionEvent>()

    @Synchronized
    fun recordGoalSubmitted() {
        events += FrictionEvent(FrictionEventType.GOAL_SUBMITTED)
    }

    @Synchronized
    fun recordQuestionPresented(slotId: String) {
        events += FrictionEvent(
            type = FrictionEventType.QUESTION_PRESENTED,
            detail = slotId,
            userInitiated = false,
        )
    }

    @Synchronized
    fun recordQuestionAnswered(slotId: String) {
        events += FrictionEvent(
            type = FrictionEventType.QUESTION_ANSWERED,
            detail = slotId,
        )
    }

    @Synchronized
    fun recordScreenTransition(
        from: String,
        to: String,
        userInitiated: Boolean,
    ) {
        if (from == to) return
        events += FrictionEvent(
            type = FrictionEventType.SCREEN_TRANSITION,
            fromScreen = from,
            toScreen = to,
            userInitiated = userInitiated,
        )
    }

    @Synchronized
    fun recordTechnicalChoicePresented(choiceId: String) {
        events += FrictionEvent(
            type = FrictionEventType.TECHNICAL_CHOICE_PRESENTED,
            detail = choiceId,
            userInitiated = false,
        )
    }

    @Synchronized
    fun recordManualTechnicalSetting(settingId: String) {
        events += FrictionEvent(
            type = FrictionEventType.MANUAL_TECHNICAL_SETTING,
            detail = settingId,
        )
    }

    @Synchronized
    fun recordGuidedBuildConfirmation(connectionId: String? = null) {
        events += FrictionEvent(
            type = FrictionEventType.GUIDED_BUILD_CONFIRMATION,
            detail = connectionId,
        )
    }

    @Synchronized
    fun recordDeviceConnectAction() {
        events += FrictionEvent(FrictionEventType.DEVICE_CONNECT_ACTION)
    }

    @Synchronized
    fun recordDeployAction() {
        events += FrictionEvent(FrictionEventType.DEPLOY_ACTION)
    }

    @Synchronized
    fun snapshot(): FrictionSnapshot {
        fun count(type: FrictionEventType): Int = events.count { it.type == type }

        return FrictionSnapshot(
            questionsPresented = count(FrictionEventType.QUESTION_PRESENTED),
            questionsAnswered = count(FrictionEventType.QUESTION_ANSWERED),
            technicalChoicesPresented = count(FrictionEventType.TECHNICAL_CHOICE_PRESENTED),
            screenTransitions = count(FrictionEventType.SCREEN_TRANSITION),
            userInitiatedScreenTransitions = events.count {
                it.type == FrictionEventType.SCREEN_TRANSITION && it.userInitiated
            },
            manualTechnicalSettings = count(FrictionEventType.MANUAL_TECHNICAL_SETTING),
            goalSubmissions = count(FrictionEventType.GOAL_SUBMITTED),
            guidedBuildConfirmations = count(FrictionEventType.GUIDED_BUILD_CONFIRMATION),
            deviceConnectActions = count(FrictionEventType.DEVICE_CONNECT_ACTION),
            deployActions = count(FrictionEventType.DEPLOY_ACTION),
            totalEvents = events.size,
        )
    }

    @Synchronized
    fun eventLog(): List<FrictionEvent> = events.toList()

    @Synchronized
    fun reset() {
        events.clear()
    }
}

data class GoldenFrictionBudget(
    val questionsPresented: Int = 0,
    val technicalChoicesPresented: Int = 0,
    val manualTechnicalSettings: Int = 0,
    val screenTransitions: Int = 6,
    val appSwitches: Int = 0,
)

data class FrictionBudgetResult(
    val metric: String,
    val actual: Int,
    val target: Int,
    val passed: Boolean,
)

fun GoldenFrictionBudget.evaluate(
    snapshot: FrictionSnapshot,
    appSwitches: Int = 0,
): List<FrictionBudgetResult> = listOf(
    FrictionBudgetResult(
        "questions_presented",
        snapshot.questionsPresented,
        questionsPresented,
        snapshot.questionsPresented == questionsPresented,
    ),
    FrictionBudgetResult(
        "technical_choices_presented",
        snapshot.technicalChoicesPresented,
        technicalChoicesPresented,
        snapshot.technicalChoicesPresented == technicalChoicesPresented,
    ),
    FrictionBudgetResult(
        "manual_technical_settings",
        snapshot.manualTechnicalSettings,
        manualTechnicalSettings,
        snapshot.manualTechnicalSettings == manualTechnicalSettings,
    ),
    FrictionBudgetResult(
        "screen_transitions",
        snapshot.screenTransitions,
        screenTransitions,
        snapshot.screenTransitions == screenTransitions,
    ),
    FrictionBudgetResult(
        "workflow_app_switches",
        appSwitches,
        this.appSwitches,
        appSwitches == this.appSwitches,
    ),
)
