package com.kobil.ulid
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This is forked from https://github.com/wvlet/airframe/blob/master/airframe-ulid/
 */

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ULID(private val ulid: String) : Comparable<ULID> {

  override fun toString(): String {
    return ulid
  }

  val epochMillis: Long by lazy { CrockfordBase32.decode48bits(ulid.substring(0, 10)) }

  val timestamp = epochMillis

  fun randomness(): Pair<Long, Long> {
    val (hi, low) = CrockfordBase32.decode128bits(ulid)
    return Pair(hi and 0xffffL, low)
  }

  fun toInstant(): Instant {
    return Instant.ofEpochMilli(epochMillis)
  }

  fun toUUID(): UUID {
    val (hi, low) = CrockfordBase32.decode128bits(ulid)
    return UUID(hi, low)
  }

  fun toBytes(): ByteArray {
    val (hi, low) = CrockfordBase32.decode128bits(ulid)
    val b = ByteArray(16)
    for (i in 0..8) {
      b[i] = ((hi.ushr(64 - (i + 1) * 8)) and 0xffL).toByte()
    }
    for (i in 0..8) {
      b[i + 8] = ((low.ushr(64 - (i + 1) * 8)) and 0xffL).toByte()
    }
    return b
  }

  override fun compareTo(other: ULID): Int {
    return this.ulid.compareTo(other.ulid)
  }

  companion object {
    const val minTime = 0L
    const val maxTime = (0L.inv()).ushr(64 - 48) // Timestamp uses 48-bit range
    val maxValue: ULID = ULID("7ZZZZZZZZZZZZZZZZZZZZZZZZZ")

    private val random = SecureRandom.getInstanceStrong()

    private val defaultGenerator = ULIDGenerator { random.generateSeed(10) }

    fun newULID(): ULID = ULID(defaultGenerator.generate())

    fun newULIDString(): String = defaultGenerator.generate()

    fun fromString(ulid: String): ULID {
      require(ulid.length == 26) { "ULID must have 26 characters: $ulid (length: ${ulid.length})" }
      require(CrockfordBase32.isValidBase32(ulid)) { "Invalid Base32 character is found in $ulid" }
      return ULID(ulid)
    }

    fun fromUUID(uuid: UUID): ULID {
      val b = ByteBuffer.wrap(ByteArray(16))
      b.putLong(uuid.mostSignificantBits)
      b.putLong(uuid.leastSignificantBits)
      return fromBytes(b.array())
    }

    /**
     * Create an ULID from a given timestamp (48-bit) and a random value (80-bit)
     * @param unixTimeMillis 48-bit unix time millis
     * @param randHi  16-bit hi-part of 80-bit random value
     * @param randLow 64-bit low-part of 80-bit random value
     * @return
     */
    fun of(unixTimeMillis: Long, randHi: Long, randLow: Long): ULID {
      if (unixTimeMillis < 0L || unixTimeMillis > maxTime) {
        throw IllegalArgumentException("unixtime must be between 0 to $maxTime%,d: $unixTimeMillis%,d")
      }
      val hi: Long = (unixTimeMillis.shl(64 - 48)) or (randHi and 0xffff)
      val low: Long = randLow
      return ULID(CrockfordBase32.encode128bits(hi, low))
    }

    /**
     * Create a ne ULID from a byte sequence (16-bytes)
     */
    fun fromBytes(bytes: ByteArray): ULID = fromBytes(bytes, 0)

    /**
     * Create a ne ULID from a byte sequence (16-bytes)
     */
    fun fromBytes(bytes: ByteArray, offset: Int): ULID {
      require(offset + 16 <= bytes.size) { "ULID needs 16 bytes. offset:$offset, size:${bytes.size}" }
      var i = 0
      var hi = 0L
      while (i < 8) {
        hi = hi.shl(8)
        hi = hi or (bytes[offset + i].toLong() and 0xffL)
        i += 1
      }
      var low = 0L
      while (i < 16) {
        low = low.shl(8)
        low = low or (bytes[offset + i].toLong() and 0xffL)
        i += 1
      }
      return ULID(CrockfordBase32.encode128bits(hi, low))
    }

    /**
     * check a given string is valid as ULID
     * @param ulid
     * @return
     */
    fun isValid(ulid: String): Boolean {
      return ulid.length == 26 && CrockfordBase32.isValidBase32(ulid)
    }

    /**
     * ULID generator.
     * @param rnd a function that returns a 80-bit random values in ByteArray (size:10)
     */
    private class ULIDGenerator(val rnd: () -> ByteArray) {
      private val baseSystemTimeMillis = System.currentTimeMillis()
      private val baseNanoTime = System.nanoTime()

      private val lastValue = AtomicReference(Pair(0L, 0L))

      private fun currentTimeInMillis(): Long {
        // Avoid unexpected rollback of the system clock
        return baseSystemTimeMillis + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baseNanoTime)
      }

      /**
       * Generate ULID string.
       *
       * Tips for optimizing performance:
       *
       * 1. Reduce the number of Random number generation. SecureRandom is quite slow, so within the same milliseconds, just incrementing the randomness part will provide
       * better performance.
       * 2. Generate random in Array[Byte] (10 bytes = 80 bits). Regular Random uses 48-bit seed, so calling Random.nextInt (32 bits) x 3 is faster, but
       * SecureRandom has optimization for Array[Byte] generation, which is much faster than calling nextInt three times.
       * 3. ULIDs are often used in the string value form (e.g., transaction IDs, object IDs which can be embedded to URLs, etc.). Generating ULID String from the beginning
       * is ideal.
       * 4. In base32 encoding/decoding, use bit-shift operators as much as possible to utilize CPU registers and memory cache.
       */
      fun generate(): String {
        val unixTimeMillis: Long = currentTimeInMillis()
        if (unixTimeMillis > maxTime) {
          throw IllegalStateException("unixtime should be less than: $maxTime: $unixTimeMillis")
        }

        // Add a guard so that only a single-thread can generate ULID based on the previous value
        synchronized(this) {
          val (hi, low) = lastValue.get()
          val lastUnixTime = (hi.ushr(16)) and 0xffffffffffffL
          if (lastUnixTime == unixTimeMillis) {
            // do increment
            return if (low != 0L.inv()) {
              generateFrom(hi, low + 1L)
            } else {
              var nextHi = (hi and (0L.inv().shl(16)).inv()) + 1
              if ((nextHi and (0L.inv().shl(16))) != 0L) {
                // Random number overflow. Wait for one millisecond and retry
                Thread.sleep(1)
                generate()
              } else {
                nextHi = nextHi or unixTimeMillis.shl(64 - 48)
                generateFrom(nextHi, 0)
              }
            }
          } else {
            // No conflict at millisecond level. We can generate a new ULID safely
            return generateFrom(unixTimeMillis, rnd())
          }
        }
      }

      private fun generateFrom(unixTimeMillis: Long, rand: ByteArray): String {
        // We need a 80-bit random value here.
        require(rand.size == 10) { "random value array must have length 10, but was ${rand.size}" }

        val hi = ((unixTimeMillis and 0xffffffffffffL).shl(64 - 48)) or
          (rand[0].toLong() and 0xffL).shl(8) or (rand[1].toLong() and 0xffL)
        val low: Long =
          ((rand[2].toLong() and 0xffL).shl(56)) or
            ((rand[3].toLong() and 0xffL).shl(48)) or
            ((rand[4].toLong() and 0xffL).shl(40)) or
            ((rand[5].toLong() and 0xffL).shl(32)) or
            ((rand[6].toLong() and 0xffL).shl(24)) or
            ((rand[7].toLong() and 0xffL).shl(16)) or
            ((rand[8].toLong() and 0xffL).shl(8)) or
            (rand[9].toLong() and 0xffL)
        return generateFrom(hi, low)
      }

      private fun generateFrom(hi: Long, low: Long): String {
        lastValue.set(Pair(hi, low))
        return CrockfordBase32.encode128bits(hi, low)
      }
    }
  }
}
