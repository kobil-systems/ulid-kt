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

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ULID internal constructor(private val ulid: String) : Comparable<ULID> {

  init {
    // This is a basic sanity check. The constructor is `internal` and therefore not part of the API
    require(ulid.length == 26) { "Private constructor called with sting of invalid length" }
  }

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

  fun toUUIDString(): String {
    return toUUID().toString()
  }

  fun toBytes(): ByteArray {
    val (hi, low) = CrockfordBase32.decode128bits(ulid)
    val b = ByteArray(16)
    for (i in 0..8) {
      b[i] = ((hi.ushr(64 - (i + 1) * 8)) and 0xffL).toByte()
    }
    for (i in 0..7) {
      b[i + 8] = ((low.ushr(64 - (i + 1) * 8)) and 0xffL).toByte()
    }
    return b
  }

  override fun compareTo(other: ULID): Int {
    return this.ulid.compareTo(other.ulid)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ULID

    if (ulid != other.ulid) return false
    if (timestamp != other.timestamp) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ulid.hashCode()
    result = 31 * result + timestamp.hashCode()
    return result
  }

  companion object {
    const val minTime = 0L
    const val maxTime = (0L.inv()).ushr(64 - 48) // Timestamp uses 48-bit range
    val maxValue: ULID = ULID("7ZZZZZZZZZZZZZZZZZZZZZZZZZ")
    val nullValue: ULID = ULID("00000000000000000000000000")

    private val random = SecureRandom()

    internal val defaultGenerator = ULIDGenerator { bs -> random.nextBytes(bs); bs }

    fun fromString(ulid: String): Either<Error, ULID> {
      if (ulid.length != 26) {
        return Error.IncorrectStringLength.left()
      }
      if (CrockfordBase32.isValidBase32(ulid).not()) {
        return Error.NonBase32Char.left()
      }
      return ULID(ulid).right()
    }

    fun fromUUID(uuid: UUID): Either<Error, ULID> {
      val b = ByteBuffer.wrap(ByteArray(16))
      b.putLong(uuid.mostSignificantBits)
      b.putLong(uuid.leastSignificantBits)
      return fromBytes(b.array())
    }

    fun fromUUIDString(uuid: String): Either<Error, ULID> {
      return fromUUID(UUID.fromString(uuid))
    }

    /**
     * Create an ULID from a given timestamp (48-bit) and a random value (80-bit)
     * @param unixTimeMillis 48-bit unix time millis
     * @param randHi  16-bit hi-part of 80-bit random value
     * @param randLow 64-bit low-part of 80-bit random value
     * @return
     */
    fun of(unixTimeMillis: Long, randHi: Long, randLow: Long): Either<Error, ULID> {
      if (unixTimeMillis < 0L || unixTimeMillis > maxTime) {
        return Error.TimeOutOfBounds.left()
      }
      val hi: Long = (unixTimeMillis.shl(64 - 48)) or (randHi and 0xffff)
      val low: Long = randLow
      return ULID(CrockfordBase32.encode128bits(hi, low)).right()
    }

    /**
     * Create a ne ULID from a byte sequence (16-bytes)
     */
    fun fromBytes(bytes: ByteArray): Either<Error, ULID> = fromBytes(bytes, 0)

    /**
     * Create a ne ULID from a byte sequence (16-bytes)
     */
    fun fromBytes(bytes: ByteArray, offset: Int): Either<Error, ULID> {
      if (offset + 16 > bytes.size) {
        return Error.BytesOutOfBounds.left()
      }

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
      return ULID(CrockfordBase32.encode128bits(hi, low)).right()
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
     * @param rng a function that returns a 80-bit random values in ByteArray (size:10)
     */
    internal class ULIDGenerator(val rng: (ByteArray) -> ByteArray) {
      private val baseSystemTimeMillis = System.currentTimeMillis()
      private val baseNanoTime = System.nanoTime()

      private val lastValue = AtomicReference(Pair(0L, 0L))

      private fun currentTimeInMillis(): Long {
        // Avoid unexpected rollback of the system clock
        return baseSystemTimeMillis + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baseNanoTime)
      }

      /**
       * Generate ULID string. This method is synchronized so that only a single-thread can generate ULID based on the
       * previous value
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
      fun generateBlocking(): String {
        val unixTimeMillis: Long = currentTimeInMillis()
        if (unixTimeMillis > maxTime) {
          throw IllegalStateException("The current time is outside range (48bit). This error cannot be recovered from.")
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
                generateBlocking()
              } else {
                nextHi = nextHi or unixTimeMillis.shl(64 - 48)
                generateFrom(nextHi, 0)
              }
            }
          } else {
            // No conflict at millisecond level. We can generate a new ULID safely
            val bs = ByteArray(10)
            return generateFrom(unixTimeMillis, rng(bs))
          }
        }
      }

      /**
       * Generate ULID String in a coroutine. This call needs to be guarded by a mutex, because ULID generation needs
       * to happen sequentially to assure ordering
       */
      internal suspend fun generate(): String {
        val unixTimeMillis: Long = currentTimeInMillis()
        if (unixTimeMillis > maxTime) {
          throw IllegalStateException("The current time is outside range (48bit). This error cannot be recovered from.")
        }

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
              delay(1)
              generate()
            } else {
              nextHi = nextHi or unixTimeMillis.shl(64 - 48)
              generateFrom(nextHi, 0)
            }
          }
        } else {
          // No conflict at millisecond level. We can generate a new ULID safely
          val bs = ByteArray(10)
          return generateFrom(unixTimeMillis, rng(bs))
        }
      }

      private fun generateFrom(unixTimeMillis: Long, rand: ByteArray): String {
        // We need a 80-bit random value here.
        if (rand.size != 10) {
          throw IllegalStateException(
            "generateFrom was called with an incorrect size of random bytes, meaning the maintainer messed something up"
          )
        }

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
