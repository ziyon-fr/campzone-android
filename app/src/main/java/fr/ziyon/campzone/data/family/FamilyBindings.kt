package fr.ziyon.campzone.data.family

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FamilyBindings {
    @Binds
    @Singleton
    abstract fun bindFamilyRepository(
        repository: FirebaseFamilyRepository,
    ): FamilyRepository
}
