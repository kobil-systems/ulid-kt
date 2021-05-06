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

object CrockfordBase32 {
  private val ENCODING_CHARS = arrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P',
    'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z'
  )

  private val DECODING_CHARS = arrayOf<Byte>(
    -1, -1, -1, -1, -1, -1, -1, -1, // 0
    -1, -1, -1, -1, -1, -1, -1, -1, // 8
    -1, -1, -1, -1, -1, -1, -1, -1, // 16
    -1, -1, -1, -1, -1, -1, -1, -1, // 24
    -1, -1, -1, -1, -1, -1, -1, -1, // 32
    -1, -1, -1, -1, -1, -1, -1, -1, // 40
    0, 1, 2, 3, 4, 5, 6, 7, // 48
    8, 9, -1, -1, -1, -1, -1, -1, // 56
    -1, 10, 11, 12, 13, 14, 15, 16, // 64
    17, 1, 18, 19, 1, 20, 21, 0, // 72
    22, 23, 24, 25, 26, -1, 27, 28, // 80
    29, 30, 31, -1, -1, -1, -1, -1, // 88
    -1, 10, 11, 12, 13, 14, 15, 16, // 96
    17, 1, 18, 19, 1, 20, 21, 0, // 104
    22, 23, 24, 25, 26, -1, 27, 28, // 112
    29, 30, 31 // 120
  )

  fun decode(ch: Char): Byte {
    return DECODING_CHARS[(ch.code and 0x7f)]
  }

  fun encode(i: Int): Char {
    return ENCODING_CHARS[i and 0x1f]
  }

  fun decode128bits(s: String): Pair<Long, Long> {
    /**
     * |      hi (64-bits)     |    low (64-bits)    |
     */
    val len = s.length
    if (len != 26) {
      throw IllegalArgumentException("String length must be 26: $s (length: $len)")
    }
    var i = 0
    var hi = 0L
    var low = 0L

    val carryMask = ((0L).inv().ushr(5)).inv()
    while (i < 26) {
      val v = decode(s[i])
      val carry = (low and carryMask).ushr(64 - 5)
      low = low.shl(5)
      low = low or v.toLong()
      hi = hi.shl(5)
      hi = hi or carry
      i += 1
    }
    return Pair(hi, low)
  }

  fun encode128bits(hi: Long, low: Long): String {
    val s = StringBuilder(26)
    var i = 0
    var h = hi
    var l = low
    // encode from lower 5-bit
    while (i < 26) {
      s.append(encode((l and 0x1fL).toInt()))
      val carry = (h and 0x1fL).shl(64 - 5)
      l = l.ushr(5)
      l = l or carry
      h = h.ushr(5)
      i += 1
    }
    return s.reverse().toString()
  }

  fun decode48bits(s: String): Long {
    val len = s.length
    if (len != 10) {
      throw IllegalArgumentException("String size must be 10: $s (length:$len)")
    }
    var l: Long = decode(s[0]).toLong()
    var i = 1
    while (i < len) {
      l = l.shl(5)
      l = l or decode(s[i]).toLong()
      i += 1
    }
    return l
  }

  fun isValidBase32(s: String): Boolean {
    return s.all { decode(it) != (-1).toByte() }
  }
}
