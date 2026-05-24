package fr.ziyon.campzone.data.church

/** In-memory [ChurchDirectory] for ViewModel tests. */
class FakeChurchDirectory(
    private val churches: List<SDAChurch> = BundledChurchDatabase.all,
    var shouldFail: Boolean = false,
) : ChurchDirectory {
    override suspend fun loadChurches(): List<SDAChurch> {
        if (shouldFail) error("The fake church directory was configured to fail.")
        return churches
    }
}
