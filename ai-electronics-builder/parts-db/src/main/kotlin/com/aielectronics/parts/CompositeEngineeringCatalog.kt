package com.aielectronics.parts

import com.aielectronics.core.model.*

interface ResearchedComponentSource {
    fun researchedComponents(): List<ComponentSpec>
}

interface ComponentResearchStore : ResearchedComponentSource {
    fun researchRecords(): List<ComponentResearchRecord>
    fun saveResearchRecord(record: ComponentResearchRecord)
}

class CompositeEngineeringCatalog(
    private val base: EngineeringCatalog,
    private val researched: ResearchedComponentSource,
) : EngineeringCatalog {

    override fun components(): List<ComponentSpec> {
        val merged = linkedMapOf<String, ComponentSpec>()
        base.components().forEach {
            merged[it.componentId] = it
        }
        researched.researchedComponents().forEach {
            merged[it.componentId] = it
        }
        return merged.values.toList()
    }

    override fun boards(): List<BoardSpec> = base.boards()

    override fun powerSupplies(): List<PowerSupplySpec> =
        base.powerSupplies()
}
