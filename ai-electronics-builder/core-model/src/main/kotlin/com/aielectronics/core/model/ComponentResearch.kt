package com.aielectronics.core.model

enum class ComponentVerificationStatus {
    UNREGISTERED,
    DISCOVERED,
    EXTRACTED,
    VERIFIED,
    DESIGN_READY,
    REJECTED,
}

enum class ResearchSourceAuthority {
    MANUFACTURER_DATASHEET,
    MANUFACTURER_PRODUCT_PAGE,
    AUTHORIZED_DISTRIBUTOR,
    OTHER,
}

data class RequestedComponent(
    val rawName: String,
    val quantity: Int = 1,
    val categoryHint: String? = null,
    val sourceText: String? = null,
)

data class ComponentResearchSource(
    val url: String,
    val title: String = "",
    val authority: ResearchSourceAuthority = ResearchSourceAuthority.OTHER,
)

data class ComponentResearchRequest(
    val requestId: String,
    val requested: RequestedComponent,
    val requiredCapabilities: Set<CapabilityId> = emptySet(),
    val projectGoal: String,
)

data class ComponentResearchRecord(
    val requestId: String,
    val requestedName: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val component: ComponentSpec? = null,
    val status: ComponentVerificationStatus,
    val sources: List<ComponentResearchSource> = emptyList(),
    val missingFields: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val researchedAtEpochMs: Long = 0L,
)

class ComponentResearchRequiredException(
    val requests: List<ComponentResearchRequest>,
) : IllegalStateException(
    "Component research required: " +
        requests.joinToString { it.requested.rawName }
)
