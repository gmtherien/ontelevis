package app.ontelevis.core.data.di

import android.content.Context
import androidx.room.Room
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.data.RoomPlaylistRepository
import app.ontelevis.core.data.db.OntelevisDatabase
import app.ontelevis.core.data.db.PlaylistDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): OntelevisDatabase = Room.databaseBuilder(context, OntelevisDatabase::class.java, "ontelevis.db").build()

    @Provides
    fun playlistDao(database: OntelevisDatabase): PlaylistDao = database.playlistDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun playlistRepository(impl: RoomPlaylistRepository): PlaylistRepository
}
