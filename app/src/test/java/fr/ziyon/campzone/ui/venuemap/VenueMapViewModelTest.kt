package fr.ziyon.campzone.ui.venuemap

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.media.CloudinaryUploadResult
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.VenueCategory
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.venuemap.FakeVenueMapService
import fr.ziyon.campzone.data.venuemap.ParsedGpxPoint
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VenueMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val admin = user("admin-1", UserRole.Admin)
    private val participant = user("u1", UserRole.User)

    @Test
    fun loadExposesMapAndManagerFlagForLeadership() {
        val vm = viewModel(initial = mapWith(point("p1", 0.5, 0.5)))

        vm.load("camp-1", admin)

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertTrue(ready.canManage)
        assertEquals(listOf("p1"), ready.map.points.map { it.id })
        assertEquals(45.0, ready.campLatitude!!, 0.0001)
    }

    @Test
    fun nonManagerLoadsReadOnly() {
        val vm = viewModel(initial = mapWith(point("p1", 0.5, 0.5)))

        vm.load("camp-1", participant)

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertFalse(ready.canManage)
    }

    @Test
    fun savePointAddsPinWithPlacedPosition() {
        val vm = viewModel(initial = VenueMap(campingId = "camp-1"))
        vm.load("camp-1", admin)

        vm.savePoint(VenuePointForm(name = "Stage", category = VenueCategory.Stage), editingId = null, imageX = 0.4, imageY = 0.6)

        val ready = vm.uiState.value as VenueMapUiState.Ready
        val point = ready.map.points.single()
        assertEquals("Stage", point.name)
        assertEquals(VenueCategory.Stage, point.category)
        assertEquals(0.4, point.imageX!!, 0.0001)
        assertEquals(0.6, point.imageY!!, 0.0001)
    }

    @Test
    fun savePointEditsMetadataKeepingPosition() {
        val vm = viewModel(initial = mapWith(point("p1", 0.2, 0.3)))
        vm.load("camp-1", admin)

        vm.savePoint(VenuePointForm(name = "Renamed", category = VenueCategory.Dining, note = "Hot meals"), editingId = "p1")

        val ready = vm.uiState.value as VenueMapUiState.Ready
        val point = ready.map.points.single()
        assertEquals("Renamed", point.name)
        assertEquals("Hot meals", point.note)
        assertEquals(0.2, point.imageX!!, 0.0001) // position preserved
    }

    @Test
    fun deletePointRemovesIt() {
        val vm = viewModel(initial = mapWith(point("p1", 0.5, 0.5), point("p2", 0.6, 0.6)))
        vm.load("camp-1", admin)

        vm.deletePoint("p1")

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertEquals(listOf("p2"), ready.map.points.map { it.id })
    }

    @Test
    fun movePointUpdatesPosition() {
        val vm = viewModel(initial = mapWith(point("p1", 0.1, 0.1)))
        vm.load("camp-1", admin)

        vm.movePoint("p1", 0.8, 0.9)

        val point = (vm.uiState.value as VenueMapUiState.Ready).map.points.single()
        assertEquals(0.8, point.imageX!!, 0.0001)
        assertEquals(0.9, point.imageY!!, 0.0001)
    }

    @Test
    fun setCoordinateSetsThenClears() {
        val vm = viewModel(initial = mapWith(point("p1", 0.5, 0.5)))
        vm.load("camp-1", admin)

        vm.setCoordinate("p1", 12.0, 34.0)
        assertEquals(12.0, (vm.uiState.value as VenueMapUiState.Ready).map.points.single().latitude!!, 0.0001)

        vm.setCoordinate("p1", null, null)
        assertNull((vm.uiState.value as VenueMapUiState.Ready).map.points.single().latitude)
    }

    @Test
    fun removeSiteImageClearsImageReference() {
        val vm = viewModel(
            initial = VenueMap(campingId = "camp-1", imageUrl = "https://x/site.jpg", imagePublicId = "pid"),
        )
        vm.load("camp-1", admin)

        vm.removeSiteImage()

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertNull(ready.map.imageUrl)
        assertNull(ready.map.imagePublicId)
    }

    @Test
    fun uploadSiteImageStoresReturnedUrl() {
        val vm = viewModel(initial = VenueMap(campingId = "camp-1"))
        vm.load("camp-1", admin)

        vm.uploadSiteImage(byteArrayOf(1, 2, 3), "image/png", "png")

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertEquals("https://cdn/uploaded.png", ready.map.imageUrl)
        assertEquals("campzone/uploaded", ready.map.imagePublicId)
        assertFalse(ready.isUploadingImage)
    }

    @Test
    fun savePointAtCapacityIsRejected() {
        val fullMap = VenueMap(
            campingId = "camp-1",
            points = (0 until VenueMap.MaxPoints).map { point("p$it", 0.5, 0.5) },
        )
        val vm = viewModel(initial = fullMap)
        vm.load("camp-1", admin)

        vm.savePoint(VenuePointForm(name = "One too many", category = VenueCategory.Stage), editingId = null)

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertEquals(VenueMap.MaxPoints, ready.map.points.size)
        assertTrue(ready.operationError!!.contains("120-location limit"))
    }

    @Test
    fun importGpxPointsTruncatesToRemainingCapacity() {
        val nearlyFull = VenueMap(
            campingId = "camp-1",
            points = (0 until VenueMap.MaxPoints - 2).map { point("p$it", 0.5, 0.5) },
        )
        val vm = viewModel(initial = nearlyFull)
        vm.load("camp-1", admin)

        vm.importGpxPoints(
            (0 until 10).map {
                ParsedGpxPoint(name = "WP $it", latitude = 45.0 + it / 1_000.0, longitude = 6.0)
            },
            VenueCategory.Dining,
        )

        val ready = vm.uiState.value as VenueMapUiState.Ready
        assertEquals(VenueMap.MaxPoints, ready.map.points.size)
        assertEquals(
            listOf(VenueCategory.Dining, VenueCategory.Dining),
            ready.map.points.takeLast(2).map { it.category },
        )
        assertEquals("GPX locations imported until the map reached its location limit.", ready.operationMessage)
    }

    // --- builders ---

    private fun viewModel(initial: VenueMap) = VenueMapViewModel(
        service = FakeVenueMapService(listOf(initial)),
        campingService = FakeCampingService(initial = listOf(camping())),
        gameService = FakeGameService(),
        imageUploader = FakeImageUploader,
    )

    private fun camping() = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "Fun",
        startDate = Date(1_000_000),
        endDate = Date(2_000_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Annecy",
        locationLatitude = 45.0,
        locationLongitude = 6.0,
        registrationStatus = CampingRegistrationStatus.Open,
        createdByUid = "admin-1",
    )

    private fun mapWith(vararg points: VenuePoint) =
        VenueMap(campingId = "camp-1", points = points.toList())

    private fun point(id: String, x: Double, y: Double) =
        VenuePoint(id = id, name = id, category = VenueCategory.Other, imageX = x, imageY = y)

    private fun user(uid: String, role: UserRole) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = uid,
        photoUrl = null,
        role = role,
        church = "Paris Central SDA",
        age = 30,
        preferredLanguage = "en",
        gender = UserGender.Male,
        onboardingCompleted = true,
    )

    private object FakeImageUploader : ImageUploader {
        override suspend fun uploadImage(
            assetIdPrefix: String,
            folder: String,
            tags: List<String>,
            bytes: ByteArray,
            mimeType: String,
            fileExtension: String,
        ): CloudinaryUploadResult = CloudinaryUploadResult(
            secureUrl = "https://cdn/uploaded.png",
            publicId = "campzone/uploaded",
        )
    }
}
