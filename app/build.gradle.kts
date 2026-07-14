import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    jacoco
}

jacoco {
    toolVersion = "0.8.15"
}

val includeLicensedExerciseMedia = providers
    .gradleProperty("includeLicensedExerciseMedia")
    .map(String::toBoolean)
    .getOrElse(false)
val exerciseMediaLicenseReference = providers
    .gradleProperty("exerciseMediaLicenseReference")
    .orNull

if (includeLicensedExerciseMedia && exerciseMediaLicenseReference.isNullOrBlank()) {
    error("includeLicensedExerciseMedia requires -PexerciseMediaLicenseReference=<rights-holder record>")
}

val compliantAssetsDir = layout.buildDirectory.dir("generated/compliant-assets")
val prepareCompliantAssets by tasks.registering(Copy::class) {
    from("src/main/assets/exercise-media/manifest.json") {
        into("exercise-media")
    }
    into(compliantAssetsDir)
}

android {
    namespace = "com.shanqijie.fitnessapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shanqijie.fitnessapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "boolean",
            "EXERCISE_MEDIA_ENABLED",
            includeLicensedExerciseMedia.toString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(
                if (includeLicensedExerciseMedia) {
                    listOf("src/main/assets")
                } else {
                    listOf(compliantAssetsDir)
                },
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

if (!includeLicensedExerciseMedia) {
    tasks.named("preBuild").configure {
        dependsOn(prepareCompliantAssets)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val generatedAnonymousClassExcludes = (0..99).map { index -> "**/*\$${index}.class" }
val coverageClassExcludes = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*\$DefaultImpls.*",
    "**/*\$WhenMappings.*",
    "**/*\$\$serializer.*",
    "**/data/*Entity\$Companion.*",
    "**/data/BodyMeasurement\$Companion.*",
    "**/data/FitnessBackupPayload\$Companion.*",
    "**/domain/ExerciseAsset\$Companion.*",
    "**/domain/ExerciseAssetPack\$Companion.*",
    "**/domain/ExerciseMediaManifest\$Companion.*",
    "**/domain/ManifestCounts\$Companion.*",
    "**/domain/PackageStrategy\$Companion.*",
    "**/domain/WorkoutReviewMetadata\$Companion.*",
    "**/*ComposableSingletons\$*.*",
    "**/*\$inlined\$*.*",
    "**/*\$sam\$*.*",
) + generatedAnonymousClassExcludes

val debugCoverageClasses = files(
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(coverageClassExcludes)
    },
    fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
        exclude(coverageClassExcludes)
    },
)
val debugCoverageSources = files("src/main/java", "src/main/kotlin")
val debugCoverageExecutionData = fileTree(layout.buildDirectory) {
    include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
    )
}

tasks.register<JacocoReport>("jacocoDebugCombinedReport") {
    group = "verification"
    description = "Runs JVM and connected-device tests and generates one source coverage report."
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
    classDirectories.setFrom(debugCoverageClasses)
    sourceDirectories.setFrom(debugCoverageSources)
    additionalSourceDirs.setFrom(debugCoverageSources)
    executionData.setFrom(debugCoverageExecutionData)
    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/debugCombined/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/debugCombined/report.xml"))
        csv.required.set(true)
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/debugCombined/report.csv"))
    }
}

tasks.register("verifyJacocoDebugCombinedCoverage") {
    group = "verification"
    description = "Requires 100% attributable source class, method, branch, and line coverage."
    dependsOn("jacocoDebugCombinedReport")
    val xmlReport = layout.buildDirectory.file("reports/jacoco/debugCombined/report.xml")
    val sourceAwareSummary = layout.buildDirectory.file("reports/jacoco/debugCombined/source-aware-summary.txt")
    inputs.file(xmlReport)
    inputs.files(debugCoverageSources)
    outputs.file(sourceAwareSummary)

    doLast {
        val reportFile = xmlReport.get().asFile
        check(reportFile.isFile) { "Missing JaCoCo XML report: $reportFile" }
        val documentFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = documentFactory.newDocumentBuilder().parse(reportFile)

        fun Element.directChildren(tagName: String): List<Element> = buildList {
            for (index in 0 until childNodes.length) {
                val node = childNodes.item(index)
                if (node is Element && node.tagName == tagName) add(node)
            }
        }

        fun Element.counter(type: String): Pair<Int, Int> {
            val counter = directChildren("counter").single { it.getAttribute("type") == type }
            return counter.getAttribute("missed").toInt() to counter.getAttribute("covered").toInt()
        }

        val report = document.documentElement
        val (classMissed, classCovered) = report.counter("CLASS")
        val (lineMissed, lineCovered) = report.counter("LINE")
        check(classMissed == 0) { "Source class coverage is not 100%: missed=$classMissed covered=$classCovered" }
        check(lineMissed == 0) { "Source line coverage is not 100%: missed=$lineMissed covered=$lineCovered" }

        fun isGeneratedMethod(name: String): Boolean =
            name.contains("\$lambda\$") ||
                name.contains("\$default") ||
                name.contains("\$accessor") ||
                Regex("get[A-Z].*").matches(name)
        var sourceMethodMissed = 0
        var sourceMethodCovered = 0
        var generatedMethodsExcluded = 0
        report.directChildren("package")
            .flatMap { it.directChildren("class") }
            .flatMap { it.directChildren("method") }
            .forEach { method ->
                val (missed, covered) = method.counter("METHOD")
                if (isGeneratedMethod(method.getAttribute("name"))) {
                    generatedMethodsExcluded += missed + covered
                } else {
                    sourceMethodMissed += missed
                    sourceMethodCovered += covered
                }
            }
        check(sourceMethodMissed == 0) {
            "Source method coverage is not 100%: missed=$sourceMethodMissed covered=$sourceMethodCovered"
        }

        var sourceBranchMissed = 0
        var sourceBranchCovered = 0
        var compilerBranchesExcluded = 0
        val unexpectedBranchLines = mutableListOf<String>()
        val staleExemptions = mutableListOf<String>()
        report.directChildren("package").forEach { packageElement ->
            val packagePath = packageElement.getAttribute("name")
            packageElement.directChildren("sourcefile").forEach { sourceFileElement ->
                val sourceName = sourceFileElement.getAttribute("name")
                val sourceFile = sequenceOf(
                    file("src/main/java/$packagePath/$sourceName"),
                    file("src/main/kotlin/$packagePath/$sourceName"),
                ).firstOrNull { it.isFile }
                    ?: error("Cannot resolve JaCoCo source file: $packagePath/$sourceName")
                val sourceLines = sourceFile.readLines()
                val reportLines = sourceFileElement.directChildren("line").associateBy {
                    it.getAttribute("nr").toInt()
                }
                sourceLines.forEachIndexed { index, sourceLine ->
                    if ("coverage-exempt: compiler-generated" in sourceLine) {
                        val reportLine = reportLines[index + 1]
                        val missed = reportLine?.getAttribute("mb")?.toIntOrNull() ?: 0
                        if (missed == 0) staleExemptions += "${sourceFile.relativeTo(projectDir)}:${index + 1}"
                    }
                }
                reportLines.forEach { (lineNumber, reportLine) ->
                    val missed = reportLine.getAttribute("mb").toIntOrNull() ?: 0
                    val covered = reportLine.getAttribute("cb").toIntOrNull() ?: 0
                    val sourceLine = sourceLines.getOrNull(lineNumber - 1).orEmpty()
                    if ("coverage-exempt: compiler-generated" in sourceLine) {
                        compilerBranchesExcluded += missed + covered
                    } else {
                        sourceBranchMissed += missed
                        sourceBranchCovered += covered
                        if (missed > 0) {
                            unexpectedBranchLines += "${sourceFile.relativeTo(projectDir)}:$lineNumber (missed=$missed)"
                        }
                    }
                }
            }
        }
        check(staleExemptions.isEmpty()) {
            "Stale compiler coverage exemptions must be removed:\n${staleExemptions.joinToString("\n")}"
        }
        check(unexpectedBranchLines.isEmpty() && sourceBranchMissed == 0) {
            "Source branch coverage is not 100%:\n${unexpectedBranchLines.joinToString("\n")}"
        }

        val summary = """
            Attributable source coverage: 100%
            CLASS: covered=$classCovered missed=$classMissed
            METHOD: covered=$sourceMethodCovered missed=$sourceMethodMissed generated_excluded=$generatedMethodsExcluded
            BRANCH: covered=$sourceBranchCovered missed=$sourceBranchMissed compiler_excluded=$compilerBranchesExcluded
            LINE: covered=$lineCovered missed=$lineMissed
            Raw JaCoCo report: ${reportFile.relativeTo(projectDir)}
        """.trimIndent() + "\n"
        sourceAwareSummary.get().asFile.apply {
            parentFile.mkdirs()
            writeText(summary)
        }
        logger.lifecycle(summary.trim())
    }
}
