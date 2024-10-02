@file:UseSerializers(HttpStatusCodeSerializer::class)
package com.sonefall.blt

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class LinkResult(
    val parent: String?,
    val url: String,
    val status: HttpStatusCode? = null,
    val errorMsg: String? = null,
    val redirect: String? = null
)