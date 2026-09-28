package com.aielectronics.parts

import com.aielectronics.core.model.BoardSpec
import com.aielectronics.core.model.ComponentSpec
import com.aielectronics.core.model.PowerSupplySpec

class MutableResearchedComponentCatalog : EngineeringCatalog {
    private val records = linkedMapOf<String, ResearchedComponentRecord>()
    private val aliases = linkedMapOf<String, String>()

    @Synchronized
    fun upsert(record: ResearchedComponentRecord) {
        records[record.canonicalId] = record
        removeAliasesFor(record.canonicalId)

        buildSet {
            add(record.canonicalId)
            add(record.requestedName)
            add(record.spec.displayName)
            addAll(record.aliases)
        }
            .map(::normalizeAlias)
            .filter { it.isNotBlank() }
            .forEach { aliases[it] = record.canonicalId }
    }

    @Synchronized
    fun record(canonicalId: String): ResearchedComponentRecord? =
        records[canonicalId]

    @Synchronized
    fun recordByAlias(alias: String): ResearchedComponentRecord? =
        aliases[normalizeAlias(alias)]?.let(records::get)

    @Synchronized
    fun records(): List<ResearchedComponentRecord> = records.values.toList()

    @Synchronized
    override fun components(): List<ComponentSpec> =
        records.values
            .asSequence()
            .filter { it.evaluation.status == ComponentUsabilityStatus.READY }
            .map { record ->
                if (record.spec.designReady) record.spec
                else record.spec.copy(designReady = true)
            }
            .sortedBy { it.componentId }
            .toList()

    override fun boards(): List<BoardSpec> = emptyList()

    override fun powerSupplies(): List<PowerSupplySpec> = emptyList()

    @Synchronized
    private fun removeAliasesFor(canonicalId: String) {
        aliases.entries.removeAll { it.value == canonicalId }
    }

    private fun normalizeAlias(value: String): String =
        value.trim().lowercase()
}

class CompositeEngineeringCatalog(
    private val primary: EngineeringCatalog,
    private val overlay: EngineeringCatalog,
) : EngineeringCatalog {
    override fun components(): List<ComponentSpec> {
        val primaryComponents = primary.components()
        val primaryIds = primaryComponents.mapTo(linkedSetOf()) { it.componentId }
        return primaryComponents + overlay.components().filter { it.componentId !in primaryIds }
    }

    override fun boards(): List<BoardSpec> = primary.boards()

    override fun powerSupplies(): List<PowerSupplySpec> = primary.powerSupplies()

    override fun component(componentId: String): ComponentSpec? =
        primary.component(componentId) ?: overlay.component(componentId)
}
