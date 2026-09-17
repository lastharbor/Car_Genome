package com.cargenome.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DecodeVinValues flattens its answer into a single object of roughly 140
 * string fields, and NHTSA adds to that list over time. Keeping it as a map
 * means a new field is carried through instead of dropped, and the raw JSON can
 * be cached verbatim and re-read by a later version of the app.
 */
@Serializable
data class VpicDecodeResponse(
    @SerialName("Count") val count: Int = 0,
    @SerialName("Message") val message: String = "",
    @SerialName("Results") val results: List<Map<String, String?>> = emptyList(),
)

@Serializable
data class VpicModelsResponse(
    @SerialName("Count") val count: Int = 0,
    @SerialName("Message") val message: String = "",
    @SerialName("Results") val results: List<VpicModel> = emptyList(),
)

@Serializable
data class VpicModel(
    @SerialName("Make_ID") val makeId: Int = 0,
    @SerialName("Make_Name") val makeName: String = "",
    @SerialName("Model_ID") val modelId: Int = 0,
    @SerialName("Model_Name") val modelName: String = "",
)
