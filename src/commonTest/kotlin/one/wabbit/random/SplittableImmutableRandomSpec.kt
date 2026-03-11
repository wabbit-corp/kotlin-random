package one.wabbit.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplittableImmutableRandomSpec {
    @Test
    fun same_seed_produces_same_stream() {
        var left = SplittableImmutableRandom(0x1000)
        var right = SplittableImmutableRandom(0x1000)

        repeat(128) {
            val leftStep = left.next64()
            val rightStep = right.next64()
            assertEquals(leftStep.first, rightStep.first)
            left = leftStep.second
            right = rightStep.second
        }
    }

    @Test
    fun fork_is_deterministic() {
        val left = SplittableImmutableRandom(0x1000).fork()
        val right = SplittableImmutableRandom(0x1000).fork()

        assertEquals(left.first.seed, right.first.seed)
        assertEquals(left.first.gamma, right.first.gamma)
        assertEquals(left.second.seed, right.second.seed)
        assertEquals(left.second.gamma, right.second.gamma)
    }

    @Test
    fun bounded_next64_stays_within_range() {
        var rng = SplittableImmutableRandom(0x1000)
        val bounds = listOf(0L, 1L, 2L, 3L, 31L, 32L, 63L, 64L, 127L, 1023L, Long.MAX_VALUE)

        for (bound in bounds) {
            repeat(256) {
                val step = rng.next64(bound)
                assertTrue(step.first <= bound)
                rng = step.second
            }
        }
    }

    @Test
    fun join_is_deterministic() {
        val left = SplittableImmutableRandom(0x1000).fork()
        val right = SplittableImmutableRandom(0x2000).fork()
        val joined1 = left.first.join(right.second)
        val joined2 = left.first.join(right.second)

        assertEquals(joined1.seed, joined2.seed)
        assertEquals(joined1.gamma, joined2.gamma)
    }

    @Test
    fun byte_array_seed_is_deterministic() {
        val bytes = ByteArray(17) { (it * 13).toByte() }
        val left = SplittableImmutableRandom.seed(bytes)
        val right = SplittableImmutableRandom.seed(bytes)

        assertEquals(left.seed, right.seed)
        assertEquals(left.gamma, right.gamma)
    }

    @Test
    fun empty_seed_array_is_allowed() {
        SplittableImmutableRandom.seed(ByteArray(0))
    }
}
