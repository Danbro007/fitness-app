package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.ExerciseManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseManifestParserTest {
    @Test
    fun parsesManifestAndFindsSmithBenchPress() {
        val manifest = ExerciseManifestParser.parse(sampleManifest)

        assertEquals(2, manifest.counts.localOrDownloaded)
        assertEquals(0, manifest.counts.failed)
        assertEquals("exercise-gifs-pack-001", manifest.packageStrategy.packs.single().id)

        val smith = ExerciseManifestParser.findSmithBenchPress(manifest)
        assertEquals("0748", smith.exerciseId)
        assertEquals("smith bench press", smith.name)
        assertEquals("gifs/0748-trqKQv2.gif", smith.localPath)
    }

    @Test
    fun realManifestHasDownloadableGifRecords() {
        val manifestFile = File("src/main/assets/exercise-media/manifest.json")
        assertTrue("manifest should exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val manifest = ExerciseManifestParser.parse(manifestFile.readText())
        assertEquals(1324, manifest.counts.datasetRecordsWithMedia)
        assertEquals(1324, manifest.counts.localOrDownloaded)
        assertEquals(0, manifest.counts.failed)
        assertTrue(manifest.packageStrategy.packCount >= 1)

        val smith = ExerciseManifestParser.findSmithBenchPress(manifest)
        assertEquals("0748", smith.exerciseId)
        assertTrue("smith gif should have a remote URL", smith.remoteUrl.startsWith("https://"))
        assertTrue("smith gif should target local gifs directory", smith.localPath.startsWith("gifs/"))
        assertTrue("smith gif should have a sha256 checksum", smith.sha256.length == 64)
    }

    private val sampleManifest = """
        {
          "counts": {
            "datasetRecordsWithMedia": 2,
            "localOrDownloaded": 2,
            "failed": 0
          },
          "packageStrategy": {
            "maxPackSizeMb": 150,
            "packCount": 1,
            "packs": [
              {
                "id": "exercise-gifs-pack-001",
                "bytes": 200,
                "sizeMb": 0.2,
                "fileCount": 2
              }
            ]
          },
          "files": [
            {
              "exerciseId": "0748",
              "name": "smith bench press",
              "bodyPart": "chest",
              "equipment": "smith machine",
              "target": "pectorals",
              "muscleGroup": "chest",
              "mediaId": "trqKQv2",
              "remoteUrl": "https://static.exercisedb.dev/media/trqKQv2.gif",
              "localPath": "gifs/0748-trqKQv2.gif",
              "bytes": 100,
              "sha256": "abc",
              "status": "downloaded"
            },
            {
              "exerciseId": "0001",
              "name": "3/4 sit-up",
              "bodyPart": "waist",
              "equipment": "body weight",
              "target": "abs",
              "muscleGroup": "hip flexors",
              "mediaId": "2gPfomN",
              "remoteUrl": "https://static.exercisedb.dev/media/2gPfomN.gif",
              "localPath": "gifs/0001-2gPfomN.gif",
              "bytes": 100,
              "sha256": "def",
              "status": "downloaded"
            }
          ]
        }
    """.trimIndent()
}
