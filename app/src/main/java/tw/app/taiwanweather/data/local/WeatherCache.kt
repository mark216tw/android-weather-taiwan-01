package tw.app.taiwanweather.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "weather_cache", primaryKeys = ["kind", "key"])
data class CachedPayload(
    val kind: String,
    val key: String,
    val payload: String,
    val fetchedAt: Long,
    val schemaVersion: Int
)

interface WeatherCache {
    suspend fun get(kind: String, key: String): CachedPayload?
    suspend fun put(value: CachedPayload)
}

object NoOpWeatherCache : WeatherCache {
    override suspend fun get(kind: String, key: String) = null
    override suspend fun put(value: CachedPayload) = Unit
}

class InMemoryWeatherCache : WeatherCache {
    private val values = mutableMapOf<Pair<String, String>, CachedPayload>()
    override suspend fun get(kind: String, key: String) = synchronized(values) { values[kind to key] }
    override suspend fun put(value: CachedPayload) {
        synchronized(values) { values[value.kind to value.key] = value }
    }
}

@Dao
internal interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE kind = :kind AND `key` = :key LIMIT 1")
    suspend fun get(kind: String, key: String): CachedPayload?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: CachedPayload)
}

@Database(entities = [CachedPayload::class], version = 1, exportSchema = true)
internal abstract class WeatherCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): WeatherCacheDao
}

internal class RoomWeatherCache(private val dao: WeatherCacheDao) : WeatherCache {
    override suspend fun get(kind: String, key: String) = dao.get(kind, key)
    override suspend fun put(value: CachedPayload) = dao.put(value)
}
