package fr.ziyon.campzone.data.profile

import fr.ziyon.campzone.data.media.CloudinaryImageUploader
import fr.ziyon.campzone.data.media.CloudinaryUploadResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile-photo upload, delegating to the shared [CloudinaryImageUploader] with
 * the avatar folder/tags. Kept as a thin wrapper so the profile layer has a
 * focused entry point.
 */
@Singleton
class CloudinaryAvatarUploader @Inject constructor(
    private val imageUploader: CloudinaryImageUploader,
) {
    suspend fun uploadAvatar(
        uid: String,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult =
        imageUploader.uploadImage(
            assetIdPrefix = uid,
            folder = AvatarFolder,
            tags = listOf("campzone", "avatar"),
            bytes = bytes,
            mimeType = mimeType,
            fileExtension = fileExtension,
        )

    private companion object {
        const val AvatarFolder = "campzone/avatars"
    }
}
