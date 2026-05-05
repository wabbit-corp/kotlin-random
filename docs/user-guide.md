# User Guide

## Mutable Generators

Mutable generators extend Kotlin's `Random`:

```kotlin
import one.wabbit.random.L64X128Random

val rng = L64X128Random(seed = 7L)

val value = rng.nextInt(10)

check(value in 0 until 10)
```

Use them when existing APIs expect `kotlin.random.Random` or when mutable state is simpler than
passing explicit continuation values.

## Immutable Generators

Immutable generators return `RandomResult`:

```kotlin
import one.wabbit.random.ThreefryRandom

val start = ThreefryRandom.Immutable(seed = 99L)
val first = start.next32()
val second = first.generator.next32()

check(first.generator != start)
check(first.value != second.value)
```

Store `RandomResult.generator` and use it for the next sample.

## Forking and Splitting

`L64X128Random` supports forking:

```kotlin
val parent = L64X128Random(seed = 1L)
val child = parent.fork()

check(child.nextLong() != parent.nextLong())
```

`ThreefryRandom` supports JAX-style key derivation:

```kotlin
val key = ThreefryRandom(seed = 1L)
val (left, right) = key.split2()
val folded = key.foldIn(123)

check(left != right)
check(folded != key)
```

## Counter-Based Blocks

`PhiloxRandom.nextBlock()` returns the next 4x64 block and advances the exact counter:

```kotlin
import one.wabbit.random.PhiloxRandom

val philox = PhiloxRandom(key0 = 1L, key1 = 2L)
val block = philox.nextBlock()

check(block.size == 4)
```

Use `advance` or `jumped` to move through the counter space without sampling intermediate blocks.

## Serializable Checkpoints

Immutable classes are serializable with `kotlinx.serialization`:

```kotlin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.wabbit.random.Xoshiro256StarStarRandom

val snapshot = Xoshiro256StarStarRandom(seed = 42L).asImmutable()
val encoded = Json.encodeToString(snapshot)
val decoded = Json.decodeFromString<Xoshiro256StarStarRandom.Immutable>(encoded)

check(decoded == snapshot)
```

## Reproducibility

For exact reproducibility, keep the same generator type, seed/state, version, and consumption
pattern. Mixing `next32`, `next64`, `nextBytes`, and block APIs may intentionally change alignment.
