# kotlin-random

`kotlin-random` is a Kotlin Multiplatform library of deterministic pseudo-random generators with
both mutable `kotlin.random.Random` implementations and serializable immutable state snapshots.

It is designed for reproducible simulation, testing, procedural generation, and cross-platform
streams where the exact generator state matters. It is not a cryptographic random-number library.

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("one.wabbit:kotlin-random:1.1.0")
}
```

## Quick Start

```kotlin
import one.wabbit.random.L64X128Random

val rng = L64X128Random(seed = 1234L)

val id = rng.nextLong()
val bounded = rng.nextInt(100)

check(bounded in 0 until 100)
```

The mutable generators extend Kotlin's `Random`, so they work with APIs that accept
`kotlin.random.Random`.

## Immutable State

Immutable snapshots return the sampled value and the continuation generator:

```kotlin
import one.wabbit.random.Xoshiro256PlusPlusRandom

val start = Xoshiro256PlusPlusRandom.Immutable(seed = 42L)
val first = start.next64()
val second = first.generator.next64()

check(first.generator != start)
check(first.value != second.value)
```

Immutable states are `@Serializable`, which makes them useful for checkpointing reproducible
streams.

## Included Generators

- `L64X128Random`: mutable LXM generator compatible with Java's L64X128 stepping and seeding model.
- `L64X128Random.Immutable`: serializable immutable L64X128 state.
- `PhiloxRandom`: mutable Philox 4x64 counter-based generator with exact-counter APIs.
- `PhiloxRandom.Immutable`: serializable immutable Philox state, including buffered output.
- `ThreefryRandom`: mutable Threefry 2x32 generator with JAX-style `split` and `foldIn`.
- `ThreefryRandom.Immutable`: serializable immutable Threefry state.
- `Xoshiro256PlusPlusRandom` and `Xoshiro256StarStarRandom`: mutable xoshiro256 variants.
- `Xoshiro256PlusPlusRandom.Immutable` and `Xoshiro256StarStarRandom.Immutable`: serializable
  immutable xoshiro256 states.

## State and Alignment

Some generators expose raw block APIs in addition to Kotlin `Random` primitives. Mixed 32-bit,
64-bit, byte, and block consumption can affect stream alignment by design. Use one consumption style
per stream when exact reproducibility across implementations matters.

## Status

This library is stable enough for deterministic application streams and tests. It does not promise
cryptographic security, and generator-specific bit streams are part of the library's compatibility
surface.

## Documentation

- [User guide](docs/user-guide.md)
- [API reference notes](docs/api-reference.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Development](docs/development.md)

Generated API docs can be built locally with Dokka. See [API reference notes](docs/api-reference.md)
for the command.

## Release Notes

- [CHANGELOG.md](CHANGELOG.md)

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open
source use.

For commercial use, contact Wabbit Consulting Corporation at `wabbit@wabbit.one`.

## Contributing

Before contributions can be merged, contributors need to agree to the repository CLA.
