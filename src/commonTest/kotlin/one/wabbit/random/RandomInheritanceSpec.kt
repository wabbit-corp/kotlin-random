package one.wabbit.random

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class RandomInheritanceSpec {
    @Test
    fun mutable_generators_are_kotlin_randoms() {
        assertIs<Random>(L64X128Random(1L))
        assertIs<Random>(PhiloxRandom(key0 = 1L, key1 = 2L))
        assertIs<Random>(ThreefryRandom(1L))
        assertIs<Random>(Xoshiro256PlusPlusRandom(1L))
        assertIs<Random>(Xoshiro256StarStarRandom(1L))
    }

    @Test
    fun l64x128_random_methods_follow_existing_stream_methods() {
        val ints = L64X128Random(0x1234)
        val intsReference = L64X128Random(0x1234)
        val longs = L64X128Random(0x5678)
        val longsReference = L64X128Random(0x5678)

        repeat(8) {
            assertEquals(intsReference.next32(), ints.nextInt())
            assertEquals(longsReference.next64(), longs.nextLong())
        }
    }

    @Test
    fun philox_random_methods_follow_existing_stream_methods() {
        val ints = PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L)
        val intsReference = PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L)
        val longs = PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L)
        val longsReference = PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L)

        repeat(8) {
            assertEquals(intsReference.next32(), ints.nextInt())
            assertEquals(longsReference.next64(), longs.nextLong())
        }
    }

    @Test
    fun threefry_random_methods_follow_existing_stream_methods() {
        val ints = ThreefryRandom(0x1234)
        val intsReference = ThreefryRandom(0x1234)
        val longs = ThreefryRandom(0x5678)
        val longsReference = ThreefryRandom(0x5678)

        repeat(8) {
            assertEquals(intsReference.next32(), ints.nextInt())
            assertEquals(longsReference.next64(), longs.nextLong())
        }
    }

    @Test
    fun xoshiro_plus_plus_random_methods_follow_existing_stream_methods() {
        val ints = Xoshiro256PlusPlusRandom(0x1234)
        val intsReference = Xoshiro256PlusPlusRandom(0x1234)
        val longs = Xoshiro256PlusPlusRandom(0x5678)
        val longsReference = Xoshiro256PlusPlusRandom(0x5678)

        repeat(8) {
            assertEquals(intsReference.next32(), ints.nextInt())
            assertEquals(longsReference.next64(), longs.nextLong())
        }
    }

    @Test
    fun xoshiro_star_star_random_methods_follow_existing_stream_methods() {
        val ints = Xoshiro256StarStarRandom(0x1234)
        val intsReference = Xoshiro256StarStarRandom(0x1234)
        val longs = Xoshiro256StarStarRandom(0x5678)
        val longsReference = Xoshiro256StarStarRandom(0x5678)

        repeat(8) {
            assertEquals(intsReference.next32(), ints.nextInt())
            assertEquals(longsReference.next64(), longs.nextLong())
        }
    }

    @Test
    fun higher_level_random_methods_follow_raw_streams() {
        assertHigherLevelMethods(
            create = { L64X128Random(0x1234) },
            next32 = { it.next32() },
            next64 = { it.next64() },
        )
        assertHigherLevelMethods(
            create = { PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L) },
            next32 = { it.next32() },
            next64 = { it.next64() },
        )
        assertHigherLevelMethods(
            create = { ThreefryRandom(0x1234) },
            next32 = { it.next32() },
            next64 = { it.next64() },
        )
        assertHigherLevelMethods(
            create = { Xoshiro256PlusPlusRandom(0x1234) },
            next32 = { it.next32() },
            next64 = { it.next64() },
        )
        assertHigherLevelMethods(
            create = { Xoshiro256StarStarRandom(0x1234) },
            next32 = { it.next32() },
            next64 = { it.next64() },
        )
    }

    @Test
    fun next_bits_rejects_invalid_bit_counts() {
        val generators =
            listOf<Random>(
                L64X128Random(1L),
                PhiloxRandom(key0 = 1L, key1 = 2L),
                ThreefryRandom(1L),
                Xoshiro256PlusPlusRandom(1L),
                Xoshiro256StarStarRandom(1L),
            )

        for (generator in generators) {
            assertFailsWith<IllegalArgumentException> {
                generator.nextBits(-1)
            }
            assertFailsWith<IllegalArgumentException> {
                generator.nextBits(33)
            }
        }
    }

    @Test
    fun mutable_generators_have_value_equality() {
        assertMutableEquality(create = { L64X128Random(0x1234) }, advance = { it.next64() })
        assertMutableEquality(
            create = { PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L) },
            advance = { it.next32() },
        )
        assertMutableEquality(create = { ThreefryRandom(0x1234) }, advance = { it.next32() })
        assertMutableEquality(create = { Xoshiro256PlusPlusRandom(0x1234) }, advance = { it.next64() })
        assertMutableEquality(create = { Xoshiro256StarStarRandom(0x1234) }, advance = { it.next64() })
    }

    @Test
    fun immutable_generators_have_value_equality() {
        assertImmutableEquality(
            create = { L64X128Random.Immutable(0x1234) },
            advance = { it.next64().generator },
        )
        assertImmutableEquality(
            create = { PhiloxRandom.Immutable(key0 = 0x1234, key1 = 0x5678, counter0 = 1L) },
            advance = { it.next32().generator },
        )
        assertImmutableEquality(
            create = { ThreefryRandom.Immutable(0x1234) },
            advance = { it.next32().generator },
        )
        assertImmutableEquality(
            create = { Xoshiro256PlusPlusRandom.Immutable(0x1234) },
            advance = { it.next64().generator },
        )
        assertImmutableEquality(
            create = { Xoshiro256StarStarRandom.Immutable(0x1234) },
            advance = { it.next64().generator },
        )
    }

    @Test
    fun buffered_hidden_state_participates_in_equality() {
        val philox = PhiloxRandom(key0 = 0x1234, key1 = 0x5678, counter0 = 1L)
        philox.next32()
        assertEquals(philox.asImmutable().asMutable(), philox)
        assertNotEquals(
            PhiloxRandom(
                key0 = philox.key0,
                key1 = philox.key1,
                counter0 = philox.counter0,
                counter1 = philox.counter1,
                counter2 = philox.counter2,
                counter3 = philox.counter3,
            ),
            philox,
        )
        val philoxSnapshot = philox.asImmutable()
        assertNotEquals(
            PhiloxRandom.Immutable(
                key0 = philoxSnapshot.key0,
                key1 = philoxSnapshot.key1,
                counter0 = philoxSnapshot.counter0,
                counter1 = philoxSnapshot.counter1,
                counter2 = philoxSnapshot.counter2,
                counter3 = philoxSnapshot.counter3,
            ),
            philoxSnapshot,
        )

        val threefry = ThreefryRandom(0x1234)
        threefry.next32()
        assertEquals(threefry.asImmutable().asMutable(), threefry)
        assertNotEquals(
            ThreefryRandom(
                key0 = threefry.key0,
                key1 = threefry.key1,
                counter = threefry.counter,
            ),
            threefry,
        )
        val threefrySnapshot = threefry.asImmutable()
        assertNotEquals(
            ThreefryRandom.Immutable(
                key0 = threefrySnapshot.key0,
                key1 = threefrySnapshot.key1,
                counter = threefrySnapshot.counter,
            ),
            threefrySnapshot,
        )
    }

    private fun <R : Random> assertHigherLevelMethods(
        create: () -> R,
        next32: (R) -> Int,
        next64: (R) -> Long,
    ) {
        val booleans = create()
        val booleansReference = create()
        repeat(8) {
            assertEquals(next32(booleansReference) < 0, booleans.nextBoolean())
        }

        val floats = create()
        val floatsReference = create()
        repeat(8) {
            assertEquals(randomFloatFromInt(next32(floatsReference)), floats.nextFloat())
        }

        val doubles = create()
        val doublesReference = create()
        repeat(8) {
            assertEquals(randomDoubleFromLong(next64(doublesReference)), doubles.nextDouble())
        }

        val bytes = create()
        val bytesReference = create()
        repeat(4) {
            val expected = expectedBytesFromNext64(bytesReference, next64, 19)
            val actual = ByteArray(19)
            bytes.nextBytes(actual)
            assertContentEquals(expected, actual)
        }
    }

    private fun <R> expectedBytesFromNext64(
        generator: R,
        next64: (R) -> Long,
        size: Int,
    ): ByteArray {
        val bytes = ByteArray(size)
        var position = 0
        while (position < bytes.size) {
            var word = next64(generator)
            repeat(Long.SIZE_BYTES) {
                if (position < bytes.size) {
                    bytes[position] = word.toByte()
                    position += 1
                    word = word ushr Byte.SIZE_BITS
                }
            }
        }
        return bytes
    }

    private fun <R : Any> assertMutableEquality(create: () -> R, advance: (R) -> Unit) {
        val left = create()
        val right = create()

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())

        advance(left)
        assertNotEquals(left, right)

        advance(right)
        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    private fun <R : Any> assertImmutableEquality(create: () -> R, advance: (R) -> R) {
        val left = create()
        val right = create()

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())

        val advancedLeft = advance(left)
        assertNotEquals(advancedLeft, right)

        val advancedRight = advance(right)
        assertEquals(advancedLeft, advancedRight)
        assertEquals(advancedLeft.hashCode(), advancedRight.hashCode())
    }
}
