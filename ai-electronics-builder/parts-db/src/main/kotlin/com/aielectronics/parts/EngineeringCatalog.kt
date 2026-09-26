package com.aielectronics.parts

import com.aielectronics.core.model.*

interface EngineeringCatalog {
    fun components(): List<ComponentSpec>
    fun boards(): List<BoardSpec>
    fun powerSupplies(): List<PowerSupplySpec>

    fun component(componentId: String): ComponentSpec? =
        components().firstOrNull { it.componentId == componentId }

    fun board(boardId: String): BoardSpec? =
        boards().firstOrNull { it.boardId == boardId }

    fun componentsProviding(capability: CapabilityId): List<ComponentSpec> =
        components().filter { capability in it.providesCapabilities }

    fun supportComponents(tag: String): List<ComponentSpec> =
        components().filter { tag in it.tags }
}
