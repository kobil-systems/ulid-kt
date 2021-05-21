# Universally Unique Lexicographically Sortable Identifier

This is a re-implementation of the ULID library by [Airframe](https://github.com/wvlet/airframe) in Kotlin. It follows
the [ULID spec](https://github.com/ulid/spec).

## Advantages

- 128-bit compatibility with UUID
- 1.21e+24 unique ULIDs per millisecond
- Lexicographically sortable!
- Canonically encoded as a 26 character string, as opposed to the 36 character UUID
- Uses Crockford's base32 for better efficiency and readability (5 bits per character)
- Case-insensitive
- No special characters (URL safe)
- Monotonic sort order (correctly detects and handles the same millisecond)

## API

```kotlin
// Constructors
val newRandomUlid: ULID = ULID.newULID()
val newRandomUlidStr: String = ULID.newULIDString()
val ulidFromString = ULID("01BX5ZZKBKACTAV9WEVGEMMVRZ")
val ulidFromUUID = ULID.fromUUID("f93f9fa5-f760-4341-b62b-508def86f087")
val uuid = ULID.newULID().toUUID()
val ulidFromBytes = ULID.fromBytes(byteArray)
val ulidFromRawData = ULID.of(unixTimeMillis, randHi, randLow)

// Usage
val ulid = ULID.newULID()
println(ulid)                   // 01F51KNV65BQH43X6FMZ3JJT66
println(ulid.epochMillis)       // 1620330605765
println(ulid.toInstant())       // 2021-05-06T19:50:05.765Z
println(ulid.toUUID())          // 0179433a-ecc5-5de2-41f4-cfa7c72968c6
println(ulid.toBytes().asHex()) // 01 79 43 3a ec c5 5d e2 41 f4 cf a7 c7 29 68 c6
println(ULID.isValid("deadbeef"))                   // false
println(ULID.isValid("01F51KNV65BQH43X6FMZ3JJT66")) // true
```

## Performance

Extensive performance review has not yet been performed, but a basic single-threaded throughput test comparing different
existing ULID libraries is discussed here.

The tests were performed on an 2.8 GHz Core i7-7700HQ (MacBook Pro 14,3 2017), each generating 1 million IDs after a
warm-up of 1000 IDs.

```
com.github.guepardoapps.kulid time: 1330 ms
          wvlet.airframe.ulid time: 1040 ms
        de.huxhorn.sulky.ulid time: 9856 ms
               java.util.UUID time: 676  ms
               com.kobil.ulid time: 545  ms
```

## Building

Tests:

```
./gradlew clean test
```

Packaging

```
./gradlew clean assemble
```

Publishing (don't forget the appropriate credentials in `gradle.properties`)

```
./gradlew publishAllPublicationsToNexusRepository
```

## Changelog

### 1.0.3
Added fromUUIDString/toUUIDString functions

### 1.0.2

Add equals/hashcode implementation to fix equality bug

### 1.0.1

Use SecureRandom#nextBytes instead of #generateSeed

### 1.0.0

Initial release
