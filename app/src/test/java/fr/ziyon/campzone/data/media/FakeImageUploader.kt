package fr.ziyon.campzone.data.media

/** In-memory [ImageUploader] for ViewModel tests; records the last folder used. */
class FakeImageUploader(
    var shouldFail: Boolean = false,
) : ImageUploader {
    var lastFolder: String? = null
        private set

    override suspend fun uploadImage(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult {
        if (shouldFail) error("The fake image uploader was configured to fail.")
        lastFolder = folder
        return CloudinaryUploadResult(
            secureUrl = "https://cdn.example/$assetIdPrefix.$fileExtension",
            publicId = "$folder/$assetIdPrefix",
        )
    }
}
