package fr.ziyon.campzone.data.media

object PreviewMediaUploader : ImageUploader, AudioUploader, CloudinaryAssetDeleter {
    override suspend fun uploadImage(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult =
        CloudinaryUploadResult(
            secureUrl = "https://example.com/$assetIdPrefix.$fileExtension",
            publicId = "$folder/$assetIdPrefix",
        )

    override suspend fun uploadAudio(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult =
        CloudinaryUploadResult(
            secureUrl = "https://example.com/$assetIdPrefix.$fileExtension",
            publicId = "$folder/$assetIdPrefix",
        )

    override suspend fun deleteAsset(publicId: String, resourceType: String) = Unit
}
