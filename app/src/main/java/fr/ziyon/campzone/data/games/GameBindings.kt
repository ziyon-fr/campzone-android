package fr.ziyon.campzone.data.games

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GameBindings {
    @Binds
    @Singleton
    abstract fun bindGameService(impl: FirestoreGameService): GameService
}
