package one.wabbit.random

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class L64X128RandomSpec {
    @Test
    fun long_seed_matches_frozen_next64_vector() {
        val mutable = L64X128Random(0x1000)
        var immutable = L64X128Random.Immutable(0x1000)

        for (expected in next64Vector) {
            assertEquals(expected, mutable.next64())
            val step = immutable.next64()
            assertEquals(expected, step.value)
            immutable = step.generator
        }
    }

    @Test
    fun long_seed_matches_frozen_next32_vector() {
        val mutable = L64X128Random(0x1000)
        var immutable = L64X128Random.Immutable(0x1000)

        for (expected in next32Vector) {
            assertEquals(expected, mutable.next32())
            val step = immutable.next32()
            assertEquals(expected, step.value)
            immutable = step.generator
        }
    }

    @Test
    fun byte_seed_matches_frozen_next64_vector() {
        val bytes = seededBytes()
        val mutable = L64X128Random.seed(bytes)
        var immutable = L64X128Random.Immutable.seed(bytes)

        for (expected in byteSeedNext64Vector) {
            assertEquals(expected, mutable.next64())
            val step = immutable.next64()
            assertEquals(expected, step.value)
            immutable = step.generator
        }
    }

    @Test
    fun fork_matches_frozen_parent_and_child_vectors() {
        val mutableParent = L64X128Random(0x1000)
        val mutableChild = mutableParent.fork()
        val immutableFork = L64X128Random.Immutable(0x1000).fork()
        var immutableParent = immutableFork.generator
        var immutableChild = immutableFork.value

        assertContentEquals(forkParentNext64Vector, next64s(mutableParent, forkParentNext64Vector.size))
        assertContentEquals(forkChildNext64Vector, next64s(mutableChild, forkChildNext64Vector.size))

        for (expected in forkParentNext64Vector) {
            val step = immutableParent.next64()
            assertEquals(expected, step.value)
            immutableParent = step.generator
        }

        for (expected in forkChildNext64Vector) {
            val step = immutableChild.next64()
            assertEquals(expected, step.value)
            immutableChild = step.generator
        }
    }

    @Test
    fun fork_with_brine_matches_frozen_parent_and_child_vectors() {
        val mutableParent = L64X128Random(0x1000)
        val mutableChild = mutableParent.fork(forkBrine)
        val immutableFork = L64X128Random.Immutable(0x1000).fork(forkBrine)
        var immutableParent = immutableFork.generator
        var immutableChild = immutableFork.value

        assertContentEquals(forkBrineParentNext64Vector, next64s(mutableParent, forkBrineParentNext64Vector.size))
        assertContentEquals(forkBrineChildNext64Vector, next64s(mutableChild, forkBrineChildNext64Vector.size))

        for (expected in forkBrineParentNext64Vector) {
            val step = immutableParent.next64()
            assertEquals(expected, step.value)
            immutableParent = step.generator
        }

        for (expected in forkBrineChildNext64Vector) {
            val step = immutableChild.next64()
            assertEquals(expected, step.value)
            immutableChild = step.generator
        }
    }

    @Test
    fun bounded_next64_stays_within_range() {
        var rng = L64X128Random.Immutable(0x1000)
        val bounds = listOf(1L, 2L, 3L, 31L, 32L, 63L, 64L, 127L, 1023L, Long.MAX_VALUE)

        for (bound in bounds) {
            repeat(256) {
                val step = rng.next64(bound)
                assertTrue(step.value >= 0L)
                assertTrue(step.value < bound)
                rng = step.generator
            }
        }
    }

    @Test
    fun random_range_overloads_match_until_overloads_for_power_of_two_widths() {
        val intBounds = listOf(1, 2, 4, 8, 16, 32, 64, 256, 1024, 1 shl 30)
        val longBounds = listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L, 256L, 1024L, 1L shl 62)

        for (bound in intBounds) {
            val untilOnly = L64X128Random(0x1000)
            val ranged = L64X128Random(0x1000)

            repeat(128) {
                assertEquals(untilOnly.nextInt(bound), ranged.nextInt(0, bound), "bound=$bound")
            }
        }

        for (bound in longBounds) {
            val untilOnly = L64X128Random(0x1000)
            val ranged = L64X128Random(0x1000)

            repeat(128) {
                assertEquals(untilOnly.nextLong(bound), ranged.nextLong(0L, bound), "bound=$bound")
            }
        }
    }

    @Test
    fun zero_xbg_state_is_repaired() {
        val mutable = L64X128Random(7L, 11L, 0L, 0L)
        val immutable = L64X128Random.Immutable(7L, 11L, 0L, 0L)

        assertTrue(mutable.x0 != 0L || mutable.x1 != 0L)
        assertTrue(immutable.x0 != 0L || immutable.x1 != 0L)
    }

    @Test
    fun byte_array_seed_is_deterministic() {
        val bytes = seededBytes()
        val left = L64X128Random.seed(bytes).asImmutable()
        val right = L64X128Random.Immutable.seed(bytes)

        assertEquals(left.a, right.a)
        assertEquals(left.s, right.s)
        assertEquals(left.x0, right.x0)
        assertEquals(left.x1, right.x1)
    }

    @Test
    fun max_length_byte_seed_is_allowed() {
        val bytes = ByteArray(32) { (it * 7).toByte() }
        L64X128Random.seed(bytes)
        L64X128Random.Immutable.seed(bytes)
    }

    @Test
    fun oversized_byte_seed_is_rejected() {
        val bytes = ByteArray(33) { it.toByte() }

        assertFailsWith<IllegalArgumentException> {
            L64X128Random.seed(bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            L64X128Random.Immutable.seed(bytes)
        }
    }

    @Test
    fun empty_seed_array_is_allowed() {
        L64X128Random.seed(ByteArray(0))
        L64X128Random.Immutable.seed(ByteArray(0))
    }

    private companion object {
        // Generated from OpenJDK 21.0.9 `L64X128MixRandom`.
        val next64Vector =
            longArrayOf(
                -2606903574450521342L,
                473657167493759974L,
                -8945793699057754977L,
                -1539934763466107010L,
                -1899852364895805362L,
                1976540304179233295L,
                -6059102763655662877L,
                -8434968738984121852L,
            )

        val next32Vector =
            intArrayOf(
                -606967038,
                110281903,
                -2082854905,
                -358544003,
                -442343849,
                460199151,
                -1410744797,
                -1963919201,
            )

        val byteSeedNext64Vector =
            longArrayOf(
                -649369749267753488L,
                5173739620509927809L,
                5293413382626743173L,
                -5477466246044444078L,
                8857332255142917569L,
                2750520669947631357L,
                8362831445095730335L,
                -3499679665444817749L,
            )

        val forkParentNext64Vector =
            longArrayOf(
                -1899852364895805362L,
                1976540304179233295L,
                -6059102763655662877L,
                -8434968738984121852L,
                -7676959426273211149L,
                7762676670946988911L,
                -3636317001801562961L,
                -6873509254513229090L,
            )

        val forkChildNext64Vector =
            longArrayOf(
                1341679015326534604L,
                -3262583508784027708L,
                3999948722276911659L,
                -8621647243253537030L,
                -5122838841778200103L,
                8279365437659107770L,
                8522531337953483349L,
                519638780179472515L,
            )

        const val forkBrine = 0x0123456789abcdefL

        val forkBrineParentNext64Vector =
            longArrayOf(
                -1539934763466107010L,
                -1899852364895805362L,
                1976540304179233295L,
                -6059102763655662877L,
                -8434968738984121852L,
                -7676959426273211149L,
                7762676670946988911L,
                -3636317001801562961L,
            )

        val forkBrineChildNext64Vector =
            longArrayOf(
                6400553748740794309L,
                -5821430792861170200L,
                -6344673980099964314L,
                -7267302611084965064L,
                -1049599778301493958L,
                -2722990677323951520L,
                939887968978958524L,
                -8248177533541383028L,
            )

        fun seededBytes(): ByteArray = ByteArray(17) { (it * 13).toByte() }

        fun next64s(rng: L64X128Random, count: Int): LongArray = LongArray(count) { rng.next64() }
    }
}
