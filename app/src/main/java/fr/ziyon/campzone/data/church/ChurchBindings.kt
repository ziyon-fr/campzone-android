package fr.ziyon.campzone.data.church

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChurchBindings {
    @Binds
    @Singleton
    abstract fun bindChurchDirectory(
        repository: ChurchDatabaseRepository,
    ): ChurchDirectory
}
