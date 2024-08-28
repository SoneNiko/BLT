package com.sonefall.blt

import io.ktor.http.*

fun Url.isSimilarHost(other: Url) =
    host.replace("^www\\.".toRegex(), "") == other.host.replace("^www\\.".toRegex(), "")

fun Sequence<String>.mapToAbsoluteUrls(onlyForDomainFrom: Url, dropFragment: Boolean = false, allowedProtocols: List<URLProtocol>) =
    asSequence().map(::Url).filter { it.protocol in allowedProtocols }.map {
        if (it.host == "localhost") {
            // if the host is localhost, the link is relative
            return@map URLBuilder(onlyForDomainFrom).apply {
                // encodedPath of relative url "#comment-1234" is "/", but actual path is still relative
                if (it.encodedPath != "/" && it.encodedPath.startsWith("/")) {
                    parameters.clear()
                    encodedPathSegments = it.pathSegments
                } else {
                    appendEncodedPathSegments(it.pathSegments)
                }
                fragment = if (dropFragment) {
                    ""
                } else {
                    it.fragment
                }
                parameters.appendAll(it.parameters)
            }.build()
        } else {
            it
        }
    }.map(Url::toString)