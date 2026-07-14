package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.FitnessBackupCodec
import com.shanqijie.fitnessapp.data.FitnessBackupPayload
import com.shanqijie.fitnessapp.data.PlannedExerciseEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseView
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionExerciseEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.domain.ExerciseAsset
import com.shanqijie.fitnessapp.domain.ExerciseAssetPack
import com.shanqijie.fitnessapp.domain.ExerciseMediaManifest
import com.shanqijie.fitnessapp.domain.ManifestCounts
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.NutritionReference
import com.shanqijie.fitnessapp.domain.NutritionSummary
import com.shanqijie.fitnessapp.domain.PackageStrategy
import com.shanqijie.fitnessapp.domain.WorkoutAdjustmentDirection
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeParseException

class ProductionModelCoverageTest {
    @Test
    fun exerciseManifestModelsSupportDefaultsSerializationAndDataClassContracts() {
        val minimalAsset = ExerciseAsset(
            exerciseId = "0748",
            name = "smith bench press",
            mediaId = "media-1",
            remoteUrl = "https://example.invalid/0748.gif",
            localPath = "exercise-media/gifs/0748.gif",
        )
        val completeAsset = minimalAsset.copy(
            bodyPart = "chest",
            equipment = "smith machine",
            target = "pectorals",
            muscleGroup = "chest",
            bytes = 42L,
            sha256 = "abc",
            downloadStatus = "local",
        )
        val pack = ExerciseAssetPack("pack-1", 42L, 0.1, 1)
        val strategy = PackageStrategy(100.0, 1, listOf(pack))
        val counts = ManifestCounts(1, 1, 0)
        val manifest = ExerciseMediaManifest(counts, strategy, listOf(completeAsset))
        val encoded = Json.encodeToString(manifest)

        assertTrue(encoded.contains("smith machine"))
        assertEquals("pack-1", pack.component1())
        assertEquals(42L, pack.component2())
        assertEquals(0.1, pack.component3(), 0.0)
        assertEquals(1, pack.component4())
        assertEquals(listOf(pack), strategy.component3())
        assertEquals(1, manifest.component3().size)
        assertNotEquals(minimalAsset, completeAsset)
        assertNotEquals(minimalAsset.hashCode(), completeAsset.hashCode())
        assertTrue(completeAsset.toString().contains("0748"))
    }

    @Test
    fun remainingProductionModelsExerciseDefaultAndCopyContracts() {
        val media = ExerciseMediaEntity(
            exerciseId = "0748",
            name = "smith bench press",
            bodyPart = "chest",
            equipment = "smith machine",
            target = "pectorals",
            mediaId = "media-1",
            localPath = "exercise-media/gifs/0748.gif",
            assetPackId = "pack-1",
            bytes = 42L,
            sha256 = "abc",
        )
        val copiedMedia = media.copy(name = "史密斯机卧推")
        val summaryWithoutReference = NutritionSummary(500, 40.0, 50.0, 15.0)
        val reference = NutritionReference(2200, 150.0, 250.0, 70.0)
        val summaryWithReference = summaryWithoutReference.copy(reference = reference)

        assertEquals("史密斯机卧推", copiedMedia.component2())
        assertEquals("0748", copiedMedia.component1())
        assertEquals(null, summaryWithoutReference.component5())
        assertEquals(reference, summaryWithReference.component5())
        assertTrue(summaryWithReference.toString().contains("2200"))
    }

    @Test
    fun repositoryAndBackupModelsCoverDefaultsAndSerializationContracts() {
        val state = FitnessAppState(
            venue = null,
            venues = emptyList(),
            equipment = emptyList(),
            equipmentForSelectedVenue = emptyList(),
            venueEquipment = emptyList(),
            plannedWorkouts = emptyList(),
            plannedExercises = emptyList(),
            plannedExerciseViews = emptyList(),
            workoutSessions = emptyList(),
            unfinishedSessions = emptyList(),
            workoutSetLogs = emptyList(),
            userProfile = null,
            onboardingCompleted = false,
            foodLogs = emptyList(),
            aiDrafts = emptyList(),
            trainingAdjustments = emptyList(),
            smithMachineExercises = emptyList(),
            exercises = emptyList(),
            aiProviders = emptyList(),
        )
        val payload = FitnessBackupPayload(
            version = 3,
            exportedAt = 1L,
            userProfile = null,
            venues = emptyList(),
            equipment = emptyList(),
            plannedWorkouts = emptyList(),
            plannedExercises = emptyList(),
            workoutSessions = emptyList(),
            setLogs = emptyList(),
            foodLogs = emptyList(),
            aiDrafts = emptyList(),
            aiProviders = emptyList(),
        )
        val media = ExerciseMediaEntity(
            exerciseId = "0748",
            name = "smith bench press",
            bodyPart = "chest",
            equipment = "smith machine",
            target = "pectorals",
            mediaId = "media-1",
            localPath = "exercise-media/gifs/0748.gif",
            assetPackId = "pack-1",
            bytes = 42L,
            sha256 = "abc",
        )

        assertTrue(state.workoutSessionExercises.isEmpty())
        assertTrue(state.preferences.isEmpty())
        assertEquals(payload, FitnessBackupCodec.decode(FitnessBackupCodec.encode(payload)))
        assertEquals(media, Json.decodeFromString<ExerciseMediaEntity>(Json.encodeToString(media)))
    }

    @Test
    fun privatePureHelpersCoverEmptyMetadataAndMaintainReviewText() {
        val owner = Class.forName("com.shanqijie.fitnessapp.data.FitnessRepositoryKt")
        val metadataMethod = owner.declaredMethods.single { it.name == "foodDraftMetadataJson" }.apply {
            isAccessible = true
        }
        val appendReviewMethod = owner.declaredMethods.single { it.name == "appendReviewNote" }.apply {
            isAccessible = true
        }

        assertEquals("", metadataMethod.invoke(null, "", "", ""))
        assertEquals(
            "已有备注 · 训练复盘确认：保持计划",
            appendReviewMethod.invoke(null, "已有备注", WorkoutAdjustmentDirection.MAINTAIN),
        )
    }

    @Test
    fun rootStateMappersCoverResultFallbacksAndLegacySessionRecovery() {
        val owner = Class.forName("com.shanqijie.fitnessapp.ui.FitnessAppRootKt")
        fun method(name: String, parameterCount: Int) = owner.declaredMethods
            .single { it.name == name && it.parameterCount == parameterCount }
            .apply { isAccessible = true }

        val media = ExerciseMediaEntity(
            exerciseId = "0748",
            name = "smith bench press",
            bodyPart = "chest",
            equipment = "smith machine",
            target = "pectorals",
            mediaId = "media-1",
            localPath = "exercise-media/gifs/0748.gif",
            assetPackId = "pack-1",
            bytes = 42L,
            sha256 = "abc",
        )
        val resultSession = WorkoutSessionEntity(
            id = "result-session",
            plannedWorkoutId = null,
            venueId = "venue",
            exerciseId = "0748",
            status = "completed",
            startedAt = 0L,
            endedAt = 1L,
            updatedAt = 1L,
            currentExerciseId = "0748",
        )
        val resultState = emptyAppState().copy(
            workoutSessions = listOf(resultSession),
            exercises = listOf(media),
        )
        assertEquals(
            media.localPath,
            method("heroAssetPath", 2).invoke(null, resultState, HomePrimaryAction.Result(resultSession.id)),
        )
        assertEquals(
            "开始今日训练",
            method("homeHeroTitle", 2).invoke(null, emptyAppState(), HomePrimaryAction.Start("missing")),
        )
        assertEquals("腿", method("toWorkoutLabel", 1).invoke(null, "下肢力量"))
        assertEquals(LocalDate.of(1970, 1, 1), method("toLocalDate", 1).invoke(null, 0L))

        val planned = PlannedExerciseEntity(
            id = "planned-unknown",
            plannedWorkoutId = "legacy-plan",
            exerciseId = "unknown",
            orderIndex = 0,
            targetSets = 3,
            targetReps = "10",
            targetWeightKg = 0.0,
            note = "",
        )
        val legacySession = WorkoutSessionEntity(
            id = "legacy-session",
            plannedWorkoutId = null,
            venueId = "venue",
            exerciseId = "unknown",
            status = "in_progress",
            startedAt = 1L,
            endedAt = null,
            updatedAt = 1L,
            currentExerciseId = "missing-current",
        )
        val legacyState = emptyAppState().copy(
            plannedExerciseViews = listOf(PlannedExerciseView(planned, media.copy(exerciseId = "unknown"))),
            workoutSessions = listOf(legacySession),
            workoutSessionExercises = listOf(
                WorkoutSessionExerciseEntity(
                    id = "snapshot",
                    sessionId = legacySession.id,
                    exerciseId = "unknown",
                    orderIndex = 0,
                    targetSets = 3,
                    targetReps = "10",
                    targetWeightKg = 0.0,
                    status = "pending",
                ),
            ),
        )
        val active = requireNotNull(method("toTrainingActive", 2).invoke(null, legacyState, legacySession.id))
        assertTrue(active.toString().contains("currentExerciseId=unknown"))
        assertTrue(active.toString().contains("planName=自由训练"))
    }

    @Test
    fun rootStateMappersCoverEveryFallbackAndPresentationCategory() {
        val owner = Class.forName("com.shanqijie.fitnessapp.ui.FitnessAppRootKt")
        fun method(name: String, parameterCount: Int) = owner.declaredMethods
            .single { it.name == name && it.parameterCount == parameterCount }
            .apply { isAccessible = true }
        val media = ExerciseMediaEntity(
            exerciseId = "exercise-a",
            name = "exercise a",
            bodyPart = "",
            equipment = "",
            target = "target",
            mediaId = "media-a",
            localPath = "a.gif",
            assetPackId = "pack",
            bytes = 1L,
            sha256 = "a",
        )
        val plan = PlannedWorkoutEntity("plan-a", "胸部力量", "2026-07-14", "venue", "planned", 1L, 1L)
        val plannedExercise = PlannedExerciseEntity("pe-a", plan.id, media.exerciseId, 0, 2, "8", 10.0, "")
        val session = WorkoutSessionEntity(
            id = "session-a",
            plannedWorkoutId = plan.id,
            venueId = "venue",
            exerciseId = media.exerciseId,
            status = "in_progress",
            startedAt = 1L,
            endedAt = null,
            updatedAt = 1L,
            currentExerciseId = media.exerciseId,
        )
        val snapshot = WorkoutSessionExerciseEntity(
            id = "snapshot-a",
            sessionId = session.id,
            exerciseId = media.exerciseId,
            orderIndex = 0,
            targetSets = 2,
            targetReps = "8",
            targetWeightKg = 10.0,
            status = "pending",
        )
        val full = emptyAppState().copy(
            plannedWorkouts = listOf(plan),
            plannedExercises = listOf(plannedExercise),
            plannedExerciseViews = listOf(PlannedExerciseView(plannedExercise, media)),
            workoutSessions = listOf(session),
            workoutSessionExercises = listOf(snapshot),
            workoutSetLogs = listOf(
                WorkoutSetLogEntity("log", session.id, media.exerciseId, 1, 8, 10.0, "合适", true, 1L, snapshot.id),
            ),
            exercises = listOf(media),
        )

        val hero = method("heroAssetPath", 2)
        assertEquals("a.gif", hero.invoke(null, full, HomePrimaryAction.Start(plan.id)))
        assertEquals("a.gif", hero.invoke(null, full, HomePrimaryAction.Resume(session.id)))
        assertEquals("a.gif", hero.invoke(null, full, HomePrimaryAction.CreatePlan))
        assertEquals(null, hero.invoke(null, emptyAppState(), HomePrimaryAction.Start("missing")))
        assertEquals(null, hero.invoke(null, emptyAppState(), HomePrimaryAction.Resume("missing")))
        assertEquals(null, hero.invoke(null, emptyAppState(), HomePrimaryAction.Result("missing")))
        assertEquals(null, hero.invoke(null, emptyAppState(), HomePrimaryAction.CreatePlan))

        val title = method("homeHeroTitle", 2)
        assertEquals("胸部力量", title.invoke(null, full, HomePrimaryAction.Start(plan.id)))
        assertEquals("胸部力量", title.invoke(null, full, HomePrimaryAction.Resume(session.id)))
        assertEquals(
            "继续自由训练",
            title.invoke(
                null,
                full.copy(plannedWorkouts = emptyList()),
                HomePrimaryAction.Resume(session.id),
            ),
        )
        assertEquals("继续自由训练", title.invoke(null, emptyAppState(), HomePrimaryAction.Resume("missing")))
        assertEquals("今天已完成", title.invoke(null, full, HomePrimaryAction.Result(session.id)))
        assertEquals("安排下一次训练", title.invoke(null, full, HomePrimaryAction.CreatePlan))

        val workoutLabel = method("toWorkoutLabel", 1)
        listOf(
            null to "休",
            "胸肌" to "胸",
            "下肢" to "腿",
            "拉力" to "背",
            "手臂" to "练",
        ).forEach { (input, expected) -> assertEquals(expected, workoutLabel.invoke(null, input)) }

        assertEquals(null, method("toTrainingPreparation", 2).invoke(null, emptyAppState(), "missing"))
        assertEquals(
            null,
            method("toTrainingPreparation", 2).invoke(
                null,
                emptyAppState().copy(plannedWorkouts = listOf(plan)),
                plan.id,
            ),
        )
        assertTrue(requireNotNull(method("toTrainingPreparation", 2).invoke(null, full, plan.id)).toString().contains("estimatedMinutes=6"))

        val activeMapper = method("toTrainingActive", 2)
        assertEquals(null, activeMapper.invoke(null, emptyAppState(), "missing"))
        assertEquals(
            null,
            activeMapper.invoke(
                null,
                full.copy(workoutSessions = listOf(session.copy(status = "completed"))),
                session.id,
            ),
        )
        assertEquals(
            null,
            activeMapper.invoke(null, full.copy(workoutSessionExercises = emptyList()), session.id),
        )
        val active = requireNotNull(activeMapper.invoke(null, full, session.id)).toString()
        assertTrue(active.contains("planName=胸部力量"))
        assertTrue(active.contains("completedSets=1"))

        val unknownSnapshot = snapshot.copy(id = "snapshot-unknown", exerciseId = "unknown")
        val unknownSession = session.copy(
            id = "session-unknown",
            plannedWorkoutId = "missing-plan",
            exerciseId = "unknown",
            currentExerciseId = null,
        )
        val unknownState = emptyAppState().copy(
            workoutSessions = listOf(session.copy(id = "other-session"), unknownSession),
            workoutSessionExercises = listOf(unknownSnapshot.copy(sessionId = unknownSession.id)),
            workoutSetLogs = listOf(
                WorkoutSetLogEntity("wrong-session", "other", "unknown", 1, 8, 0.0, "合适", true, 1L),
                WorkoutSetLogEntity("wrong-exercise", unknownSession.id, "other", 1, 8, 0.0, "合适", true, 1L),
                WorkoutSetLogEntity("not-completed", unknownSession.id, "unknown", 1, 8, 0.0, "合适", false, 1L),
            ),
        )
        val unknownActive = requireNotNull(activeMapper.invoke(null, unknownState, unknownSession.id)).toString()
        assertTrue(unknownActive.contains("planName=自由训练"))
        assertTrue(unknownActive.contains("currentExerciseId=unknown"))
        assertTrue(unknownActive.contains("assetPath="))
        assertTrue(unknownActive.contains("completedSets=0"))

        val unrelatedMediaFallback = unknownState.copy(exercises = listOf(media))
        assertTrue(
            requireNotNull(activeMapper.invoke(null, unrelatedMediaFallback, unknownSession.id))
                .toString()
                .contains("assetPath=a.gif"),
        )

        val strip = method("toFourDayStrip", 2)
        val stripState = full.copy(
            plannedWorkouts = listOf(
                plan.copy(name = "胸部", scheduledDate = "2026-07-14"),
                plan.copy(id = "legs", name = "腿部", scheduledDate = "2026-07-15"),
                plan.copy(id = "back", name = "背部拉力", scheduledDate = "2026-07-16"),
                plan.copy(id = "arms", name = "手臂", scheduledDate = "2026-07-17"),
            ),
            workoutSessions = listOf(
                session.copy(status = "completed", endedAt = 1_784_006_400_000L),
                session.copy(id = "completed-no-end", status = "completed", endedAt = null),
                session.copy(id = "completed-other-day", status = "completed", endedAt = 1L),
                session.copy(id = "unfinished", status = "partial", endedAt = null),
            ),
        )
        val days = requireNotNull(strip.invoke(null, stripState, LocalDate.of(2026, 7, 14))).toString()
        assertTrue(days.contains("workoutLabel=胸"))
        assertTrue(days.contains("workoutLabel=腿"))
        assertTrue(days.contains("workoutLabel=背"))
        assertTrue(days.contains("workoutLabel=练"))

        val homeOwner = Class.forName("com.shanqijie.fitnessapp.ui.home.HomeScreenKt")
        val greeting = homeOwner.declaredMethods.single { it.name == "toGreeting" }.apply { isAccessible = true }
        listOf(
            null to "开始今天",
            "胸部力量" to "今天练胸",
            "腿部力量" to "今天练腿",
            "下肢训练" to "今天练腿",
            "背部力量" to "今天练背",
            "拉力训练" to "今天练背",
            "手臂训练" to "今天训练",
        ).forEach { (input, expected) ->
            assertEquals(expected, greeting.invoke(null, *arrayOf(input)))
        }
        val heroTitle = homeOwner.declaredMethods.single { it.name == "toHeroTitle" }.apply { isAccessible = true }
        assertEquals("胸部\n力量 A", heroTitle.invoke(null, "胸部力量 A"))
        assertEquals("腿部力量", heroTitle.invoke(null, "腿部力量"))
    }

    @Test
    fun bodyMeasurementPresentationCoversEveryOptionalMetric() {
        val owner = Class.forName("com.shanqijie.fitnessapp.ui.profile.ProfileScreensKt")
        val hasValues = owner.declaredMethods.single { it.name == "hasValues" }.apply { isAccessible = true }
        val summaryText = owner.declaredMethods.single { it.name == "summaryText" }.apply { isAccessible = true }
        val cases = listOf(
            BodyMeasurement(),
            BodyMeasurement(measuredAt = "2026-07-14"),
            BodyMeasurement(bodyType = "标准型"),
            BodyMeasurement(bodyFatPercentage = 15.0),
            BodyMeasurement(bodyFatMassKg = 11.5),
            BodyMeasurement(skeletalMuscleKg = 32.0),
            BodyMeasurement(bodyWaterKg = 42.5),
            BodyMeasurement(basalMetabolismKcal = 1_650),
            BodyMeasurement(waistHipRatio = 0.85),
            BodyMeasurement(bmi = 22.1),
        )

        assertEquals(false, hasValues.invoke(null, cases.first()))
        cases.drop(1).forEach { assertEquals(true, hasValues.invoke(null, it)) }
        val full = BodyMeasurement(
            bodyFatPercentage = 15.0,
            bodyFatMassKg = 11.5,
            skeletalMuscleKg = 32.0,
            bodyWaterKg = 42.5,
            basalMetabolismKcal = 1_650,
            waistHipRatio = 0.85,
            bmi = 22.1,
        )
        val summary = summaryText.invoke(null, full).toString()
        listOf("体脂率 15%", "体脂肪 11.5 kg", "骨骼肌 32 kg", "身体水分 42.5 kg", "基础代谢 1650 kcal", "腰臀比 0.9", "BMI 22.1")
            .forEach { assertTrue(summary.contains(it)) }
        assertEquals("", summaryText.invoke(null, BodyMeasurement()))
    }

    @Test
    fun searchPlanAndFoodPresentationHelpersCoverAllFallbacks() {
        fun owner(name: String) = Class.forName(name)
        fun method(owner: Class<*>, name: String, parameterCount: Int) = owner.declaredMethods
            .single { it.name == name && it.parameterCount == parameterCount }
            .apply { isAccessible = true }

        val settings = owner("com.shanqijie.fitnessapp.ui.settings.SettingsScreensKt")
        val keywords = method(settings, "equipmentSearchKeywords", 1)
        val equipmentNames = listOf(
            "椭圆机", "龙门架", "拉力器", "夹胸机", "蝴蝶机", "腿举机", "蹬腿机", "高位下拉",
            "推胸机", "胸推机", "推肩机", "推举机", "腿屈伸", "腿弯举", "髋外展", "辅助引体",
            "固定单车", "普通器械",
        )
        equipmentNames.forEachIndexed { index, name ->
            val result = keywords.invoke(null, EquipmentEntity("e-$index", name, "machine", 1L, 1L)) as List<*>
            assertTrue(result.isNotEmpty())
        }
        listOf("machine", "free-weight", "accessory", "cardio", "body-weight", "other").forEachIndexed { index, category ->
            val result = keywords.invoke(null, EquipmentEntity("c-$index", "普通器械", category, 1L, 1L)).toString()
            assertTrue(result.isNotBlank())
        }

        val plan = owner("com.shanqijie.fitnessapp.ui.plan.PlanScreensKt")
        val aiInputItems = method(plan, "aiInputItems", 1)
        val toWeight = method(plan, "toWeight", 1)
        val operationMessage = method(plan, "toPlanOperationMessage", 2)
        assertTrue((aiInputItems.invoke(null, null) as List<*>).single().toString().contains("请先补全"))
        val profile = UserProfileEntity(
            id = "user",
            displayName = "山崎",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 76.5,
            goal = "保持体能",
            injuries = "",
            weeklyTrainingDays = 3,
            preferredMinutes = 45,
            updatedAt = 1L,
        )
        assertTrue(aiInputItems.invoke(null, profile).toString().contains("未填写"))
        val measuredProfile = profile.copy(
            injuries = "右肩注意",
            bodyMeasurement = BodyMeasurement(
                bodyFatPercentage = 15.0,
                bodyFatMassKg = 11.5,
                bmi = 22.1,
                skeletalMuscleKg = 32.0,
                bodyWaterKg = 42.5,
                basalMetabolismKcal = 1_650,
                waistHipRatio = 0.85,
            ),
        )
        assertTrue(aiInputItems.invoke(null, measuredProfile).toString().contains("右肩注意"))
        assertEquals("20", toWeight.invoke(null, 20.0))
        assertEquals("20.5", toWeight.invoke(null, 20.5))
        assertEquals(
            "请输入有效日期（格式 YYYY-MM-DD）",
            operationMessage.invoke(null, DateTimeParseException("bad", "x", 0), "fallback"),
        )
        assertEquals("specific", operationMessage.invoke(null, IllegalArgumentException("specific"), "fallback"))
        assertEquals("fallback", operationMessage.invoke(null, IllegalArgumentException("   "), "fallback"))
        assertEquals("fallback", operationMessage.invoke(null, IllegalArgumentException(), "fallback"))

        val food = owner("com.shanqijie.fitnessapp.ui.food.FoodScreensKt")
        val readBytesLimited = method(food, "readBytesLimited", 2)
        assertEquals(
            listOf<Byte>(1, 2),
            (readBytesLimited.invoke(null, ByteArrayInputStream(byteArrayOf(1, 2)), 2L) as ByteArray).toList(),
        )
        assertTrue(
            runCatching {
                readBytesLimited.invoke(null, ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2L)
            }.exceptionOrNull()?.cause is IllegalArgumentException,
        )
        val firstNumberAfter = method(food, "firstNumberAfter", 4)
        val toMacro = method(food, "toMacro", 1)
        assertEquals("520", firstNumberAfter.invoke(null, "约 520 千卡", "约 ", " 千卡", "0"))
        assertEquals("0", firstNumberAfter.invoke(null, "missing", "约 ", " 千卡", "0"))
        assertEquals("0", firstNumberAfter.invoke(null, "约 520", "约 ", " 千卡", "0"))
        assertEquals("0", firstNumberAfter.invoke(null, "约 abc 千卡", "约 ", " 千卡", "0"))
        assertEquals("10", toMacro.invoke(null, 10.0))
        assertEquals("10.5", toMacro.invoke(null, 10.5))
    }

    @Test
    fun repositoryFoodHelpersCoverEveryEstimateAndMetadataShape() {
        val owner = Class.forName("com.shanqijie.fitnessapp.data.FitnessRepositoryKt")
        fun method(name: String, parameterCount: Int) = owner.declaredMethods
            .single { it.name == name && it.parameterCount == parameterCount }
            .apply { isAccessible = true }
        val estimateFood = method("estimateFood", 1)
        listOf("鸡胸肉米饭", "牛肉饭", "牛肉面", "牛排", "salad", "随便一餐").forEach { description ->
            assertTrue(estimateFood.invoke(null, description).toString().isNotBlank())
        }
        val parseEstimate = method("parseFoodEstimateDraft", 1)
        fun draft(content: String, title: String = "饮食估算：测试") = AiDraftEntity(
            id = "draft",
            type = "food_estimate",
            title = title,
            content = content,
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            confirmedAt = null,
        )
        listOf("", "100", "100 10", "100 10 20", "100 10 20 5").forEach { content ->
            assertTrue(parseEstimate.invoke(null, draft(content)).toString().isNotBlank())
        }
        assertTrue(parseEstimate.invoke(null, draft("", "饮食估算：")).toString().contains("餐食估算"))

        val parseMetadata = method("parseFoodDraftMetadata", 1)
        listOf(
            "",
            "not-json",
            "{}",
            "{\"imageUri\":\"content://meal\"}",
            "{\"providerId\":\"openai\"}",
            "{\"model\":\"gpt\"}",
            "{\"imageUri\":\"content://meal\",\"providerId\":\"openai\",\"model\":\"gpt\"}",
        ).forEach { raw -> assertTrue(parseMetadata.invoke(null, raw).toString().isNotBlank()) }

        assertEquals("10", method("formatMacro", 1).invoke(null, 10.0))
        assertEquals("10.5", method("formatMacro", 1).invoke(null, 10.5))
        assertEquals("20", method("formatWeight", 1).invoke(null, 20.0))
        assertEquals("20.5", method("formatWeight", 1).invoke(null, 20.5))
        val appendReview = method("appendReviewNote", 2)
        assertEquals("训练复盘确认：小幅加量", appendReview.invoke(null, "", WorkoutAdjustmentDirection.INCREASE))
        assertEquals("备注 · 训练复盘确认：降低负荷", appendReview.invoke(null, " 备注 ", WorkoutAdjustmentDirection.REDUCE))
    }

    private fun emptyAppState() = FitnessAppState(
        venue = null,
        venues = emptyList(),
        equipment = emptyList(),
        equipmentForSelectedVenue = emptyList(),
        venueEquipment = emptyList(),
        plannedWorkouts = emptyList<PlannedWorkoutEntity>(),
        plannedExercises = emptyList(),
        plannedExerciseViews = emptyList(),
        workoutSessions = emptyList(),
        unfinishedSessions = emptyList(),
        workoutSetLogs = emptyList(),
        userProfile = null,
        onboardingCompleted = false,
        foodLogs = emptyList(),
        aiDrafts = emptyList(),
        trainingAdjustments = emptyList(),
        smithMachineExercises = emptyList(),
        exercises = emptyList(),
        aiProviders = emptyList(),
    )
}
