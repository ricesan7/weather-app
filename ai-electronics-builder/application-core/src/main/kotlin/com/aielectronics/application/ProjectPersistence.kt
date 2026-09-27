package com.aielectronics.application

import com.aielectronics.core.model.ProjectGraphPosition

data class SavedProject(
    val id: String,
    val title: String,
    val goalText: String,
    val clarificationValues: Map<String, String>,
    val completedConnectionIds: Set<String>,
    val currentBuildStepIndex: Int,
    val lastScreen: String,
    val deployed: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val graphNodePositions: Map<String, ProjectGraphPosition> = emptyMap(),
)

data class SavedProjectSummary(
    val id: String,
    val title: String,
    val goalText: String,
    val lastScreen: String,
    val completedConnectionCount: Int,
    val deployed: Boolean,
    val updatedAtEpochMs: Long,
)

interface ProjectRepository {
    fun list(): List<SavedProjectSummary>
    fun load(id: String): SavedProject?
    fun save(project: SavedProject): SavedProject
    fun delete(id: String)
}

class InMemoryProjectRepository : ProjectRepository {
    private val projects = linkedMapOf<String, SavedProject>()

    override fun list(): List<SavedProjectSummary> =
        projects.values
            .sortedByDescending { it.updatedAtEpochMs }
            .map { project ->
                SavedProjectSummary(
                    id = project.id,
                    title = project.title,
                    goalText = project.goalText,
                    lastScreen = project.lastScreen,
                    completedConnectionCount = project.completedConnectionIds.size,
                    deployed = project.deployed,
                    updatedAtEpochMs = project.updatedAtEpochMs,
                )
            }

    override fun load(id: String): SavedProject? = projects[id]

    override fun save(project: SavedProject): SavedProject {
        projects[project.id] = project
        return project
    }

    override fun delete(id: String) {
        projects.remove(id)
    }
}

object ProjectTitle {
    fun fromGoal(goal: String, maxLength: Int = 32): String {
        val normalized = goal
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (normalized.isBlank()) {
            return "新しい電子工作"
        }

        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength).trimEnd() + "…"
        }
    }
}
