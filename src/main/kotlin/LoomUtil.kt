package com.sonefall.blt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private val LoomDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
suspend fun <T> blocking(block: suspend CoroutineScope.() -> T) = withContext(LoomDispatcher, block)
