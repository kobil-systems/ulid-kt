package com.kobil.ulid

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.matchers.shouldBe

class CrockfordBase32Test : FunSpec({
  test("Encoding Long Pairs") {
    forAll<Long, Long> { hi, low ->
      val encoded = CrockfordBase32.encode128bits(hi, low)
      val (hi_d, low_d) = CrockfordBase32.decode128bits(encoded)
      Pair(hi, low) shouldBe Pair(hi_d, low_d)
    }
  }
})
