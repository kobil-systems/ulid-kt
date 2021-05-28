package com.kobil.ulid.blocking

import com.kobil.ulid.ULID

fun ULID.Companion.newULID(): ULID = ULID(defaultGenerator.generateBlocking())
fun ULID.Companion.newULIDString(): String = defaultGenerator.generateBlocking()
