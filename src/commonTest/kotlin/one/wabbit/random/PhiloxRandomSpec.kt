package one.wabbit.random

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhiloxRandomSpec {
    @Test
    fun exact_counter_api_matches_numpy_vectors_after_counter_translation() {
        for ((numpyStateCounter, key, expected) in blockVectors) {
            val exactCounter = exactCounterFromNumpyState(numpyStateCounter)
            val rng =
                PhiloxRandom(
                    key0 = key[0],
                    key1 = key[1],
                    counter0 = exactCounter[0],
                    counter1 = exactCounter[1],
                    counter2 = exactCounter[2],
                    counter3 = exactCounter[3],
                )
            val immutable =
                PhiloxRandom.Immutable(
                    key0 = key[0],
                    key1 = key[1],
                    counter0 = exactCounter[0],
                    counter1 = exactCounter[1],
                    counter2 = exactCounter[2],
                    counter3 = exactCounter[3],
                )

            assertContentEquals(
                expected,
                PhiloxRandom.block(
                    counter0 = exactCounter[0],
                    counter1 = exactCounter[1],
                    counter2 = exactCounter[2],
                    counter3 = exactCounter[3],
                    key0 = key[0],
                    key1 = key[1],
                ),
            )
            assertContentEquals(expected, rng.nextBlock())
            assertContentEquals(expected, immutable.nextBlock().value)
        }
    }

    @Test
    fun next64_stream_matches_exported_numpy_stream() {
        val mutable = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L)
        var immutable = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L)

        for (expected in streamVector) {
            assertEquals(expected, mutable.next64())
            val step = immutable.next64()
            assertEquals(expected, step.value)
            immutable = step.generator
        }
    }

    @Test
    fun next32_stream_matches_numpy_low_then_high_word_order() {
        val mutable = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L)
        var immutable = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L)

        for (word in streamVector) {
            val expectedLow = word.toInt()
            val expectedHigh = (word ushr 32).toInt()

            assertEquals(expectedLow, mutable.next32())
            var step = immutable.next32()
            assertEquals(expectedLow, step.value)
            immutable = step.generator

            assertEquals(expectedHigh, mutable.next32())
            step = immutable.next32()
            assertEquals(expectedHigh, step.value)
            immutable = step.generator
        }
    }

    @Test
    fun next64_discards_cached_uint32_and_stays_word_aligned() {
        val mutable = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L)
        var immutable = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L)

        assertEquals(streamVector[0].toInt(), mutable.next32())
        val first32 = immutable.next32()
        assertEquals(streamVector[0].toInt(), first32.value)
        immutable = first32.generator

        assertEquals(streamVector[1], mutable.next64())
        val step = immutable.next64()
        assertEquals(streamVector[1], step.value)
    }

    @Test
    fun advance_matches_numpy_progression() {
        val mutable = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L)
        val immutable = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L).advance(1uL)

        mutable.advance(1uL)

        assertContentEquals(advancedBlockVector, mutable.nextBlock())
        assertContentEquals(advancedBlockVector, immutable.nextBlock().value)
    }

    @Test
    fun jumped_matches_numpy() {
        val mutable = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L).jumped()
        val immutable = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L).jumped()

        assertContentEquals(jumpedBlockVector, mutable.nextBlock())
        assertContentEquals(jumpedBlockVector, immutable.nextBlock().value)
    }

    @Test
    fun signed_long_overloads_match_unsigned_paths() {
        val mutableUnsigned = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L)
        val mutableSigned = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L)
        val immutableUnsigned = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L)
        val immutableSigned = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L)

        mutableUnsigned.advance(2uL)
        mutableSigned.advance(2L)
        assertContentEquals(mutableUnsigned.nextBlock(), mutableSigned.nextBlock())

        val immutableAdvancedUnsigned = immutableUnsigned.advance(2uL)
        val immutableAdvancedSigned = immutableSigned.advance(2L)
        assertContentEquals(immutableAdvancedUnsigned.nextBlock().value, immutableAdvancedSigned.nextBlock().value)

        val jumpedUnsigned = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L).jumped(3uL)
        val jumpedSigned = PhiloxRandom(key0 = 0L, key1 = 0L, counter0 = 1L).jumped(3L)
        assertContentEquals(jumpedUnsigned.nextBlock(), jumpedSigned.nextBlock())

        val immutableJumpedUnsigned = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L).jumped(3uL)
        val immutableJumpedSigned = PhiloxRandom.Immutable(key0 = 0L, key1 = 0L, counter0 = 1L).jumped(3L)
        assertContentEquals(immutableJumpedUnsigned.nextBlock().value, immutableJumpedSigned.nextBlock().value)
    }

    @Test
    fun signed_long_overloads_reject_negative_values() {
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom(key0 = 0L, key1 = 0L).advance(-1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom.Immutable(key0 = 0L, key1 = 0L).advance(-1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom(key0 = 0L, key1 = 0L).jumped(-1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom.Immutable(key0 = 0L, key1 = 0L).jumped(-1L)
        }
    }

    @Test
    fun immutable_snapshot_round_trips_partial_buffer_and_uint32_cache() {
        val seeded =
            PhiloxRandom(
                key0 = 0x3333333333333333L,
                key1 = 0x4444444444444444L,
                counter0 = 0x0123456789abcdefL,
                counter1 = -0x123456789abcdf0L,
                counter2 = 0x1111111111111111L,
                counter3 = 0x2222222222222222L,
            )
        seeded.next64()
        seeded.next32()

        var original = seeded.asImmutable()
        var restored =
            PhiloxRandom.Immutable(
                key0 = original.key0,
                key1 = original.key1,
                counter0 = original.counter0,
                counter1 = original.counter1,
                counter2 = original.counter2,
                counter3 = original.counter3,
                block0 = original.block0,
                block1 = original.block1,
                block2 = original.block2,
                block3 = original.block3,
                blockIndex = original.blockIndex,
                hasUInt32 = original.hasUInt32,
                uinteger = original.uinteger,
            )

        repeat(4) {
            val left32 = original.next32()
            val right32 = restored.next32()
            assertEquals(left32.value, right32.value)
            original = left32.generator
            restored = right32.generator

            val left64 = original.next64()
            val right64 = restored.next64()
            assertEquals(left64.value, right64.value)
            original = left64.generator
            restored = right64.generator
        }
    }

    @Test
    fun mutable_and_immutable_stay_in_lockstep() {
        val mutable =
            PhiloxRandom(
                key0 = 0x3333333333333333L,
                key1 = 0x4444444444444444L,
                counter0 = 0x0123456789abcdefL,
                counter1 = -0x123456789abcdf0L,
                counter2 = 0x1111111111111111L,
                counter3 = 0x2222222222222222L,
            )
        var immutable =
            PhiloxRandom.Immutable(
                key0 = 0x3333333333333333L,
                key1 = 0x4444444444444444L,
                counter0 = 0x0123456789abcdefL,
                counter1 = -0x123456789abcdf0L,
                counter2 = 0x1111111111111111L,
                counter3 = 0x2222222222222222L,
            )

        repeat(32) {
            val step = immutable.next64()
            assertEquals(mutable.next64(), step.value)
            immutable = step.generator
        }
    }

    @Test
    fun invalid_restore_block_index_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom(
                key0 = 0L,
                key1 = 0L,
                counter0 = 0L,
                counter1 = 0L,
                counter2 = 0L,
                counter3 = 0L,
                block0 = 0L,
                block1 = 0L,
                block2 = 0L,
                block3 = 0L,
                blockIndex = 99,
                hasUInt32 = false,
                uinteger = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom.Immutable(
                key0 = 0L,
                key1 = 0L,
                counter0 = 0L,
                counter1 = 0L,
                counter2 = 0L,
                counter3 = 0L,
                block0 = 0L,
                block1 = 0L,
                block2 = 0L,
                block3 = 0L,
                blockIndex = -1,
                hasUInt32 = false,
                uinteger = 0,
            )
        }
    }

    @Test
    fun invalid_restore_uint32_cache_state_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom(
                key0 = 0L,
                key1 = 0L,
                counter0 = 0L,
                counter1 = 0L,
                counter2 = 0L,
                counter3 = 0L,
                block0 = 0L,
                block1 = 0L,
                block2 = 0L,
                block3 = 0L,
                blockIndex = 0,
                hasUInt32 = true,
                uinteger = 1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhiloxRandom.Immutable(
                key0 = 0L,
                key1 = 0L,
                counter0 = 0L,
                counter1 = 0L,
                counter2 = 0L,
                counter3 = 0L,
                block0 = 0L,
                block1 = 0L,
                block2 = 0L,
                block3 = 0L,
                blockIndex = 0,
                hasUInt32 = true,
                uinteger = 1,
            )
        }
    }

    private data class BlockVector(
        val numpyStateCounter: LongArray,
        val key: LongArray,
        val expected: LongArray,
    )

    private companion object {
        // Generated by tools/export_random123_vectors.py using NumPy 2.4.3.
        // NumPy stores the last-used Philox counter, so these vectors correspond to this library
        // after translating the stored counter forward by one block.
        val blockVectors =
            listOf(
                BlockVector(
                    numpyStateCounter = longArrayOf(0L, 0L, 0L, 0L),
                    key = longArrayOf(0L, 0L),
                    expected =
                        longArrayOf(
                            213000021201967259L,
                            4455796210202625458L,
                            2055444239878205049L,
                            -8035131997463137060L,
                        ),
                ),
                BlockVector(
                    numpyStateCounter = longArrayOf(1L, 0L, 0L, 0L),
                    key = longArrayOf(0L, 0L),
                    expected =
                        longArrayOf(
                            -9179476085824714813L,
                            5120919030223861725L,
                            -986083750196517449L,
                            -257032389104740420L,
                        ),
                ),
                BlockVector(
                    numpyStateCounter = longArrayOf(0L, 1L, 0L, 0L),
                    key = longArrayOf(0L, 0L),
                    expected =
                        longArrayOf(
                            3908118788357988974L,
                            3976118584364544030L,
                            -4502859756731779371L,
                            -8605339913918450724L,
                        ),
                ),
                BlockVector(
                    numpyStateCounter = longArrayOf(0L, 0L, 0L, 0L),
                    key = longArrayOf(1L, 0L),
                    expected =
                        longArrayOf(
                            5599841837815857887L,
                            -2790830975138001361L,
                            2880178291573394738L,
                            573812481542357666L,
                        ),
                ),
                BlockVector(
                    numpyStateCounter =
                        longArrayOf(
                            81985529216486895L,
                            -81985529216486896L,
                            1229782938247303441L,
                            2459565876494606882L,
                        ),
                    key = longArrayOf(3689348814741910323L, 4919131752989213764L),
                    expected =
                        longArrayOf(
                            1890788554794255550L,
                            7154783839506756165L,
                            4468671694242665521L,
                            799706455248394247L,
                        ),
                ),
            )

        val streamVector =
            longArrayOf(
                213000021201967259L,
                4455796210202625458L,
                2055444239878205049L,
                -8035131997463137060L,
                -9179476085824714813L,
                5120919030223861725L,
                -986083750196517449L,
                -257032389104740420L,
            )

        val advancedBlockVector =
            longArrayOf(
                -9179476085824714813L,
                5120919030223861725L,
                -986083750196517449L,
                -257032389104740420L,
            )

        val jumpedBlockVector =
            longArrayOf(
                -6573741979528564873L,
                1171884530594738689L,
                2219574813360835193L,
                6245020213682626476L,
            )

        fun exactCounterFromNumpyState(numpyStateCounter: LongArray): LongArray {
            require(numpyStateCounter.size == 4)

            val exactCounter = LongArray(4)
            var carry = 1uL
            for (index in numpyStateCounter.indices) {
                val sum = numpyStateCounter[index].toULong() + carry
                exactCounter[index] = sum.toLong()
                carry = if (carry != 0uL && sum == 0uL) 1uL else 0uL
            }
            return exactCounter
        }
    }
}
