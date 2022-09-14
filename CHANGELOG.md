# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.2] - 2022-09-06

### Changed

- Internal build process changes

## [1.2.1] - 2022-06-18

### Added

- Added `nullValue` constant to the `ULID` class for easier comparison in library users.

##  [1.2.0] - 2022-06-18

### Changed

- Constructors that can fail (i.e., accepting external input) now return `Either<Error, ULID>`

## [1.1.1] - 2022-05-28

### Fixed

- Use `SecureRandom()` instead of `SecureRandom.getInstanceStrong()` to avoid blocking when entropy is low

## [1.1.0] - 2022-05-28

### Added

- Suspending API for `newULID` and `newULIDString`

## [1.0.3] - 2022-05-21

### Added

- fromUUIDString/toUUIDString functions

## [1.0.2] - 2022-05-14

### Added

- Equals/hashcode implementation to fix equality bug

## [1.0.1] - 2022-05-06

### Fixed

- Use SecureRandom#nextBytes instead of #generateSeed

## [1.0.0] - 2022-05-06

### Added

- Initial release
