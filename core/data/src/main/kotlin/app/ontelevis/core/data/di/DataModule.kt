package app.ontelevis.core.data.di

import android.content.Context
import androidx.room.Room
import app.ontelevis.core.data.ChannelRepository
import app.ontelevis.core.data.EpgRepository
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.data.RoomChannelRepository
import app.ontelevis.core.data.RoomEpgRepository
import app.ontelevis.core.data.RoomPlaylistRepository
import app.ontelevis.core.data.RoomVodRepository
import app.ontelevis.core.data.VodRepository
import app.ontelevis.core.data.db.ChannelDao
import app.ontelevis.core.data.db.ChannelGroupDao
import app.ontelevis.core.data.db.OntelevisDatabase
import app.ontelevis.core.data.db.PlaylistDao
import app.ontelevis.core.data.db.ProgrammeDao
import app.ontelevis.core.data.db.VodDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): OntelevisDatabase =
        Room
            .databaseBuilder(context, OntelevisDatabase::class.java, "ontelevis.db")
            // Versions 2 -> 3 and later migrate (see autoMigrations). Only the very first development
            // schema (version 1) is still wiped.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun playlistDao(database: OntelevisDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun channelDao(database: OntelevisDatabase): ChannelDao = database.channelDao()

    @Provides
    fun channelGroupDao(database: OntelevisDatabase): ChannelGroupDao = database.channelGroupDao()

    @Provides
    fun programmeDao(database: OntelevisDatabase): ProgrammeDao = database.programmeDao()

    @Provides
    fun vodDao(database: OntelevisDatabase): VodDao = database.vodDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Big playlists can take a while to arrive from slow panels.
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun playlistRepository(impl: RoomPlaylistRepository): PlaylistRepository

    @Binds
    abstract fun channelRepository(impl: RoomChannelRepository): ChannelRepository

    @Binds
    abstract fun epgRepository(impl: RoomEpgRepository): EpgRepository

    @Binds
    abstract fun vodRepository(impl: RoomVodRepository): VodRepository
}
