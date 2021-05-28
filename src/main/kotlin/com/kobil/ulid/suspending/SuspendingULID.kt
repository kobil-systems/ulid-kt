package com.kobil.ulid.suspending

import com.kobil.ulid.ULID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val mutex = Mutex()
suspend fun ULID.Companion.newULID(): ULID = mutex.withLock { ULID(defaultGenerator.generate()) }
suspend fun ULID.Companion.newULIDString(): String = mutex.withLock { defaultGenerator.generate() }
