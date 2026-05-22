package fr.ziyon.campzone.data.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindings {
    @Binds
    abstract fun bindAuthSessionRepository(
        repository: FirebaseAuthSessionRepository,
    ): AuthSessionRepository
}
