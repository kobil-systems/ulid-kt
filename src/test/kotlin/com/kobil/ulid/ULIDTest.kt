package com.kobil.ulid

import arrow.core.Either
import com.kobil.ulid.blocking.newULID
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.comparables.shouldNotBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID
import com.kobil.ulid.suspending.newULID as suspendingNewULID

class ULIDTest : FunSpec({
  test("Check basic construction") {
    for (i in 1..10) {
      val ulid = ULID.newULID()
      val str = ulid.toString()

      ULID.fromString(str) shouldBeRight { parsed ->
        parsed shouldBeEqualComparingTo ulid
        parsed shouldBeLessThanOrEqualTo ULID.maxValue
      }
    }
  }

  test("Check toString/toBytes") {
    forAll<Long, Long, Long> { a, rh, rl ->
      val unixTime = a and ((0L).inv().shr((64 - 48)))
      // Identity
      ULID.of(unixTime, rh, rl) shouldBeRight { ulid ->
        ulid.compareTo(ulid) shouldBe 0
        ULID.fromString(ulid.toString()) shouldBe ulid
        ULID.fromBytes(ulid.toBytes()) shouldBe ulid

        // Basic conditions
        ulid.epochMillis shouldBe unixTime
        ulid.timestamp shouldBe unixTime
        ulid.randomness() shouldBe Pair(rh and 0xffffL, rl)
        ULID.isValid(ulid.toString()) shouldBe true
      }
    }
  }

  test("Check generating monotonically increasing ULIDs") {
    val start = System.currentTimeMillis() - 1
    val lst = (0..10000).map { ULID.newULID() }
    val end = System.currentTimeMillis()
    lst.forEach { x ->
      (x.epochMillis in start..end) shouldBe true
    }

    lst.windowed(2).all { pair ->
      pair[0] < pair[1] &&
        pair[0].epochMillis <= pair[1].epochMillis
    } shouldBe true
  }

  test("Check epochMillis should equal unixTime") {
    forAll<Long> { timeMillis ->
      val unixTime = timeMillis and 0xffffffffffffL
      ULID.of(unixTime, 0, 0) shouldBeRight { ulid ->
        ulid.epochMillis shouldBe unixTime
      }
    }
  }

  test("Check constructors") {
    ULID.of(ULID.minTime, 0, 0) shouldBeRight { it shouldBeEqualComparingTo ULID("00000000000000000000000000") }
    ULID.of(ULID.minTime, 0, 0) shouldBeRight { it shouldBeEqualComparingTo ULID.nullValue }
    ULID.of(1L, 0, 0) shouldBeRight { it shouldBeEqualComparingTo ULID("00000000010000000000000000") }
    ULID.of(ULID.maxTime, 0, 0) shouldBeRight { it shouldBeEqualComparingTo ULID("7ZZZZZZZZZ0000000000000000") }
    ULID.of(ULID.maxTime, 0.inv(), 0.inv()) shouldBeRight { it shouldBeEqualComparingTo ULID.maxValue }
    ULID.of(0L, 0, 0.inv()) shouldBeRight { it shouldBeEqualComparingTo ULID("0000000000000FZZZZZZZZZZZZ") }
    ULID.of(0L, 0.inv(), 0.inv()) shouldBeRight { it shouldBeEqualComparingTo ULID("0000000000ZZZZZZZZZZZZZZZZ") }
  }

  test("Check Validation") {
    shouldThrow<IllegalArgumentException> {
      // testing the private constructor
      ULID("deafbeef")
    }
    val u1 = ULID.of(ULID.minTime - 1L, 0, 0)
    val u2 = ULID.of(ULID.maxTime + 1L, 0, 0)

    (u1 as Either.Left).value shouldBe Error.TimeOutOfBounds
    (u2 as Either.Left).value shouldBe Error.TimeOutOfBounds
  }

  test("Check encoded timestamp should be correct") {
    val ulid = ULID.newULID()
    val ts = ulid.epochMillis
    val tsString = ulid.toString().substring(0, 10)
    val decodedTs = CrockfordBase32.decode48bits(tsString)
    ts shouldBe decodedTs
  }

  test("Check from UUID") {
    val uuid = UUID.randomUUID()
    ULID.fromUUID(uuid) shouldBeRight { ulid ->
      ulid.toUUID() shouldBeEqualComparingTo uuid
    }
  }

  test("Check from UUID String") {
    val uuidString = UUID.randomUUID().toString()
    ULID.fromUUIDString(uuidString) shouldBeRight { ulid ->
      ulid.toUUID().toString() shouldBeEqualComparingTo uuidString
    }
  }

  test("Check to UUID") {
    val ulid = ULID.newULID()
    val uuid = ulid.toUUID()
    ULID.fromUUID(uuid) shouldBeRight { it shouldBeEqualComparingTo ulid }
  }

  test("Check to UUID String") {
    val ulid = ULID.newULID()
    val uuidString = ulid.toUUIDString()
    uuidString shouldBeEqualComparingTo ulid.toUUID().toString()
  }

  test("Check equality and comparisons") {
    val ulid1 = ULID.fromString("01F5PCMVASGS6PWN00F9VX3TEK") as Either.Right
    val ulid2 = ULID.fromString("01F5PCMVASGS6PWN00F9VX3TEK") as Either.Right
    val ulid3 = ULID.fromString("01F5PCMVATGS6PWN00F9VX3TEK") as Either.Right // timestamp + 1

    ulid1.value shouldBeEqualComparingTo ulid2.value
    ulid1 shouldBe ulid2
    ulid2 shouldNotBe ulid3
    ulid2.value shouldNotBeEqualComparingTo ulid3.value
    (ulid1.value < ulid2.value) shouldBe false
    (ulid1.value > ulid2.value) shouldBe false
    (ulid2.value < ulid3.value) shouldBe true
    (ulid2.value > ulid3.value) shouldBe false
  }

  test("Check to/from bytes") {
    val ulid = ULID.newULID()
    val bytes = ulid.toBytes()
    ULID.fromBytes(bytes) shouldBeRight { it shouldBeEqualComparingTo ulid }
  }

  test("Check parallel suspend constructor") {
    val ulids = (1..1000).map {
      // Be aware that this is aliased in this file to "suspendingNewULID" to avoid name clashes with the blocking
      // version used in all other tests
      ULID.suspendingNewULID()
    }
    ulids.zipWithNext().forEach {
      (it.first < it.second) shouldBe true
    }
  }
})
