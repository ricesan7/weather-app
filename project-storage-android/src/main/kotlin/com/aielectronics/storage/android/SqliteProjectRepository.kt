package com.aielectronics.storage.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aielectronics.application.ProjectRepository
import com.aielectronics.application.SavedProject
import com.aielectronics.application.SavedProjectSummary
import org.json.JSONArray
import org.json.JSONObject

class SqliteProjectRepository(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
), ProjectRepository {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE projects (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                goal_text TEXT NOT NULL,
                clarifications_json TEXT NOT NULL,
                completed_connections_json TEXT NOT NULL,
                current_build_step_index INTEGER NOT NULL,
                last_screen TEXT NOT NULL,
                deployed INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX projects_updated_at_idx ON projects(updated_at DESC)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    @Synchronized
    override fun list(): List<SavedProjectSummary> {
        val result = mutableListOf<SavedProjectSummary>()

        readableDatabase.query(
            "projects",
            arrayOf(
                "id",
                "title",
                "goal_text",
                "last_screen",
                "completed_connections_json",
                "deployed",
                "updated_at",
            ),
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val goalIndex = cursor.getColumnIndexOrThrow("goal_text")
            val screenIndex = cursor.getColumnIndexOrThrow("last_screen")
            val completedIndex = cursor.getColumnIndexOrThrow("completed_connections_json")
            val deployedIndex = cursor.getColumnIndexOrThrow("deployed")
            val updatedIndex = cursor.getColumnIndexOrThrow("updated_at")

            while (cursor.moveToNext()) {
                result += SavedProjectSummary(
                    id = cursor.getString(idIndex),
                    title = cursor.getString(titleIndex),
                    goalText = cursor.getString(goalIndex),
                    lastScreen = cursor.getString(screenIndex),
                    completedConnectionCount = decodeSet(
                        cursor.getString(completedIndex)
                    ).size,
                    deployed = cursor.getInt(deployedIndex) != 0,
                    updatedAtEpochMs = cursor.getLong(updatedIndex),
                )
            }
        }

        return result
    }

    @Synchronized
    override fun load(id: String): SavedProject? {
        readableDatabase.query(
            "projects",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null

            return SavedProject(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                goalText = cursor.getString(cursor.getColumnIndexOrThrow("goal_text")),
                clarificationValues = decodeMap(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("clarifications_json")
                    )
                ),
                completedConnectionIds = decodeSet(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("completed_connections_json")
                    )
                ),
                currentBuildStepIndex = cursor.getInt(
                    cursor.getColumnIndexOrThrow("current_build_step_index")
                ),
                lastScreen = cursor.getString(
                    cursor.getColumnIndexOrThrow("last_screen")
                ),
                deployed = cursor.getInt(
                    cursor.getColumnIndexOrThrow("deployed")
                ) != 0,
                createdAtEpochMs = cursor.getLong(
                    cursor.getColumnIndexOrThrow("created_at")
                ),
                updatedAtEpochMs = cursor.getLong(
                    cursor.getColumnIndexOrThrow("updated_at")
                ),
            )
        }
    }

    @Synchronized
    override fun save(project: SavedProject): SavedProject {
        val values = ContentValues().apply {
            put("id", project.id)
            put("title", project.title)
            put("goal_text", project.goalText)
            put("clarifications_json", encodeMap(project.clarificationValues))
            put(
                "completed_connections_json",
                encodeSet(project.completedConnectionIds),
            )
            put("current_build_step_index", project.currentBuildStepIndex)
            put("last_screen", project.lastScreen)
            put("deployed", if (project.deployed) 1 else 0)
            put("created_at", project.createdAtEpochMs)
            put("updated_at", project.updatedAtEpochMs)
        }

        writableDatabase.insertWithOnConflict(
            "projects",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )

        return project
    }

    @Synchronized
    override fun delete(id: String) {
        writableDatabase.delete(
            "projects",
            "id = ?",
            arrayOf(id),
        )
    }

    private fun encodeMap(values: Map<String, String>): String {
        val json = JSONObject()
        values.toSortedMap().forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString()
    }

    private fun decodeMap(json: String): Map<String, String> {
        val objectValue = JSONObject(json)
        val result = linkedMapOf<String, String>()
        val keys = objectValue.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = objectValue.optString(key)
        }

        return result
    }

    private fun encodeSet(values: Set<String>): String {
        val array = JSONArray()
        values.sorted().forEach(array::put)
        return array.toString()
    }

    private fun decodeSet(json: String): Set<String> {
        val array = JSONArray(json)
        return buildSet {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "ai_electronics_projects.db"
        const val DATABASE_VERSION = 1
    }
}
