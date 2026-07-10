package com.shanqijie.fitnessapp.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExerciseMediaManifest(
    val counts: ManifestCounts,
    val packageStrategy: PackageStrategy,
    val files: List<ExerciseAsset>,
)

@Serializable
data class ManifestCounts(
    val datasetRecordsWithMedia: Int,
    val localOrDownloaded: Int,
    val failed: Int,
)

@Serializable
data class PackageStrategy(
    val maxPackSizeMb: Double,
    val packCount: Int,
    val packs: List<ExerciseAssetPack>,
)

@Serializable
data class ExerciseAssetPack(
    val id: String,
    val bytes: Long,
    val sizeMb: Double,
    val fileCount: Int,
)

@Serializable
data class ExerciseAsset(
    val exerciseId: String,
    val name: String,
    val bodyPart: String = "",
    val equipment: String = "",
    val target: String = "",
    val muscleGroup: String = "",
    val mediaId: String,
    val remoteUrl: String,
    val localPath: String,
    val bytes: Long = 0,
    val sha256: String = "",
    @SerialName("status") val downloadStatus: String = "",
)

object ExerciseManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): ExerciseMediaManifest = json.decodeFromString(rawJson)

    fun findSmithBenchPress(manifest: ExerciseMediaManifest): ExerciseAsset =
        manifest.files.firstOrNull { it.name.equals("smith bench press", ignoreCase = true) }
            ?: manifest.files.first { it.equipment.equals("smith machine", ignoreCase = true) }
}
