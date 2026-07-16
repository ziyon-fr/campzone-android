package fr.ziyon.campzone.data.media

import java.io.File

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

    override suspend fun uploadImageFile(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        file: File,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult = uploadImage(assetIdPrefix, folder, tags, file.readBytes(), mimeType, fileExtension)

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

    override suspend fun uploadAudioFile(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        file: File,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult = uploadAudio(assetIdPrefix, folder, tags, file.readBytes(), mimeType, fileExtension)

    override suspend fun deleteAsset(publicId: String, resourceType: String) = Unit
}
