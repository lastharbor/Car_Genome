package com.cargenome.app.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * NHTSA vPIC. Free, no key, no published rate limit, and authoritative for cars
 * sold in the United States. Outside that market it thins out quickly, which is
 * why the offline WMI table stays the base layer and this only enriches it.
 */
interface NhtsaVpicApi {

    @GET("vehicles/DecodeVinValues/{vin}")
    suspend fun decodeVin(
        @Path("vin") vin: String,
        @Query("format") format: String = FORMAT_JSON,
    ): VpicDecodeResponse

    /** Fallback list for when the VIN alone does not pin down the model. */
    @GET("vehicles/GetModelsForMakeYear/make/{make}/modelyear/{year}")
    suspend fun modelsForMakeYear(
        @Path("make") make: String,
        @Path("year") year: Int,
        @Query("format") format: String = FORMAT_JSON,
    ): VpicModelsResponse

    companion object {
        const val BASE_URL = "https://vpic.nhtsa.dot.gov/api/"
        const val FORMAT_JSON = "json"
    }
}
