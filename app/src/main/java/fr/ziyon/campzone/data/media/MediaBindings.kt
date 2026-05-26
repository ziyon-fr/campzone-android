package fr.ziyon.campzone.data.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaBindings {
    @Binds
    @Singleton
    abstract fun bindImageUploader(
        uploader: CloudinaryImageUploader,
    ): ImageUploader

    @Binds
    @Singleton
    abstract fun bindAudioUploader(
        uploader: CloudinaryImageUploader,
    ): AudioUploader
}
