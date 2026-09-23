package tw.app.taiwanweather.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CwaApi {
    @GET("api/v1/rest/datastore/{dataset}")
    suspend fun forecast(
        @Path("dataset") dataset: String,
        @Query("Authorization") key: String,
        @Query("format") format: String = "JSON",
        @Query("locationName") locationName: String? = null
    ): JsonObject

    @GET("api/v1/rest/datastore/O-A0003-001")
    suspend fun observations(
        @Query("Authorization") key: String,
        @Query("format") format: String = "JSON"
    ): JsonObject
}

interface MoenvApi {
    @GET("api/v2/aqx_p_432")
    suspend fun airQuality(
        @Query("api_key") key: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 1000,
        @Query("format") format: String = "json"
    ): Response<ResponseBody>
}

object ApiProvider {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val converter = json.asConverterFactory("application/json".toMediaType())

    val cwa: CwaApi = Retrofit.Builder()
        .baseUrl("https://opendata.cwa.gov.tw/")
        .addConverterFactory(converter)
        .build()
        .create(CwaApi::class.java)

    val moenv: MoenvApi = Retrofit.Builder()
        .baseUrl("https://data.moenv.gov.tw/")
        .addConverterFactory(converter)
        .build()
        .create(MoenvApi::class.java)
}
