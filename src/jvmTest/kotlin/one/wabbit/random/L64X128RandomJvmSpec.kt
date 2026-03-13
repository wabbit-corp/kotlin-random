package one.wabbit.random

import java.util.random.RandomGenerator
import java.util.random.RandomGeneratorFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class L64X128RandomJvmSpec {
    @Test
    fun next64_matches_jdk_for_long_seeds() {
        val seeds = listOf(0L, 1L, -1L, 0x1000L, Long.MIN_VALUE, Long.MAX_VALUE)

        for (seed in seeds) {
            val jdk = jdkFromLong(seed)
            val ours = L64X128Random(seed)
            repeat(256) {
                assertEquals(jdk.nextLong(), ours.next64(), "seed=$seed")
            }
        }
    }

    @Test
    fun next64_matches_jdk_for_byte_seeds() {
        val seeds =
            listOf(
                byteArrayOf(),
                byteArrayOf(1),
                byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9),
                ByteArray(17) { (it * 13).toByte() },
            )

        for (seed in seeds) {
            val jdk = jdkFromBytes(seed)
            val ours = L64X128Random(seed)
            repeat(256) {
                assertEquals(jdk.nextLong(), ours.next64(), "seed=${seed.contentToString()}")
            }
        }
    }

    @Test
    fun next32_matches_jdk_next_int() {
        val jdk = jdkFromLong(0x1000)
        val ours = L64X128Random(0x1000)

        repeat(256) {
            assertEquals(jdk.nextInt(), ours.next32())
        }
    }

    @Test
    fun next_float_matches_jdk() {
        val jdk = jdkFromLong(0x1000)
        val ours = L64X128Random(0x1000)

        repeat(256) {
            assertEquals(jdk.nextFloat(), ours.nextFloat())
        }
    }

    @Test
    fun next_double_matches_jdk() {
        val jdk = jdkFromLong(0x1000)
        val ours = L64X128Random(0x1000)

        repeat(256) {
            assertEquals(jdk.nextDouble(), ours.nextDouble())
        }
    }

    @Test
    fun next_bytes_matches_jdk() {
        val jdk = jdkFromLong(0x1000)
        val ours = L64X128Random(0x1000)

        repeat(32) {
            val jdkBytes = ByteArray(19)
            val ourBytes = ByteArray(19)
            jdk.nextBytes(jdkBytes)
            ours.nextBytes(ourBytes)
            assertContentEquals(jdkBytes, ourBytes)
        }
    }

    @Test
    fun bounded_next64_matches_jdk_next_long_bound() {
        val bounds = listOf(1L, 2L, 3L, 5L, 31L, 32L, 63L, 64L, 127L, 1023L, Long.MAX_VALUE)

        for (bound in bounds) {
            val jdk = jdkFromLong(0x1000)
            val ours = L64X128Random(0x1000)
            repeat(256) {
                assertEquals(jdk.nextLong(bound), ours.next64(bound), "bound=$bound")
            }
        }
    }

    @Test
    fun random_range_overloads_match_jdk() {
        val intBounds = listOf(1, 2, 3, 4, 5, 31, 32, 63, 64, 127, 1023, 1 shl 30)
        val longBounds = listOf(1L, 2L, 3L, 4L, 5L, 31L, 32L, 63L, 64L, 127L, 1023L, 1L shl 62)

        for (bound in intBounds) {
            val jdkUntil = jdkFromLong(0x1000)
            val oursUntil = L64X128Random(0x1000)
            val jdkRange = jdkFromLong(0x1000)
            val oursRange = L64X128Random(0x1000)

            repeat(256) {
                assertEquals(jdkUntil.nextInt(bound), oursUntil.nextInt(bound), "nextInt(bound), bound=$bound")
                assertEquals(jdkRange.nextInt(0, bound), oursRange.nextInt(0, bound), "nextInt(range), bound=$bound")
            }
        }

        for (bound in longBounds) {
            val jdkUntil = jdkFromLong(0x1000)
            val oursUntil = L64X128Random(0x1000)
            val jdkRange = jdkFromLong(0x1000)
            val oursRange = L64X128Random(0x1000)

            repeat(256) {
                assertEquals(jdkUntil.nextLong(bound), oursUntil.nextLong(bound), "nextLong(bound), bound=$bound")
                assertEquals(jdkRange.nextLong(0L, bound), oursRange.nextLong(0L, bound), "nextLong(range), bound=$bound")
            }
        }
    }

    @Test
    fun wide_range_overloads_match_jdk() {
        val intFrom = -(1 shl 30)
        val intUntil = 1 shl 30
        val longFrom = -(1L shl 62)
        val longUntil = 1L shl 62

        val jdkInt = jdkFromLong(0x1000)
        val oursInt = L64X128Random(0x1000)
        repeat(256) {
            assertEquals(jdkInt.nextInt(intFrom, intUntil), oursInt.nextInt(intFrom, intUntil))
        }

        val jdkLong = jdkFromLong(0x1000)
        val oursLong = L64X128Random(0x1000)
        repeat(256) {
            assertEquals(jdkLong.nextLong(longFrom, longUntil), oursLong.nextLong(longFrom, longUntil))
        }
    }

    @Test
    fun mutable_fork_matches_jdk_split() {
        val jdkParent = jdkFromLong(0x1000) as RandomGenerator.SplittableGenerator
        val ourParent = L64X128Random(0x1000)
        val jdkChild = jdkParent.split()
        val ourChild = ourParent.fork()

        repeat(256) {
            assertEquals(jdkParent.nextLong(), ourParent.next64())
            assertEquals(jdkChild.nextLong(), ourChild.next64())
        }
    }

    @Test
    fun immutable_fork_matches_jdk_split() {
        val jdkParent = jdkFromLong(0x1000) as RandomGenerator.SplittableGenerator
        val jdkChild = jdkParent.split()
        val fork = L64X128Random.Immutable(0x1000).fork()
        var ourParent = fork.generator
        var ourChild = fork.value

        repeat(256) {
            val parentStep = ourParent.next64()
            val childStep = ourChild.next64()
            assertEquals(jdkParent.nextLong(), parentStep.value)
            assertEquals(jdkChild.nextLong(), childStep.value)
            ourParent = parentStep.generator
            ourChild = childStep.generator
        }
    }

    @Test
    fun fork_with_brine_matches_jdk_split_with_explicit_brine() {
        val brine = 0x0123456789abcdefL
        val jdkParent = jdkFromLong(0x1000) as RandomGenerator.SplittableGenerator
        val jdkChild = jdkParent.split(BrineSource(brine, jdkParent))

        val ourMutableParent = L64X128Random(0x1000)
        val ourMutableChild = ourMutableParent.fork(brine)

        repeat(256) {
            assertEquals(jdkParent.nextLong(), ourMutableParent.next64())
            assertEquals(jdkChild.nextLong(), ourMutableChild.next64())
        }

        val immutableFork = L64X128Random.Immutable(0x1000).fork(brine)
        var ourImmutableParent = immutableFork.generator
        var ourImmutableChild = immutableFork.value

        val jdkParentAgain = jdkFromLong(0x1000) as RandomGenerator.SplittableGenerator
        val jdkChildAgain = jdkParentAgain.split(BrineSource(brine, jdkParentAgain))

        repeat(256) {
            val parentStep = ourImmutableParent.next64()
            val childStep = ourImmutableChild.next64()
            assertEquals(jdkParentAgain.nextLong(), parentStep.value)
            assertEquals(jdkChildAgain.nextLong(), childStep.value)
            ourImmutableParent = parentStep.generator
            ourImmutableChild = childStep.generator
        }
    }

    private fun jdkFromLong(seed: Long): RandomGenerator =
        RandomGeneratorFactory.of<RandomGenerator>("L64X128MixRandom").create(seed)

    private fun jdkFromBytes(seed: ByteArray): RandomGenerator =
        RandomGeneratorFactory.of<RandomGenerator>("L64X128MixRandom").create(seed)

    private class BrineSource(
        private val brine: Long,
        private val delegate: RandomGenerator.SplittableGenerator,
    ) : RandomGenerator.SplittableGenerator {
        private var first = true

        override fun nextLong(): Long =
            if (first) {
                first = false
                brine
            } else {
                delegate.nextLong()
            }

        override fun split(): RandomGenerator.SplittableGenerator =
            throw UnsupportedOperationException("split() is not used in this test")

        override fun split(source: RandomGenerator.SplittableGenerator): RandomGenerator.SplittableGenerator =
            throw UnsupportedOperationException("split(source) is not used in this test")

        override fun splits(streamSize: Long): java.util.stream.Stream<RandomGenerator.SplittableGenerator> =
            throw UnsupportedOperationException("splits(long) is not used in this test")

        override fun splits(source: RandomGenerator.SplittableGenerator): java.util.stream.Stream<RandomGenerator.SplittableGenerator> =
            throw UnsupportedOperationException("splits(source) is not used in this test")

        override fun splits(
            streamSize: Long,
            source: RandomGenerator.SplittableGenerator,
        ): java.util.stream.Stream<RandomGenerator.SplittableGenerator> =
            throw UnsupportedOperationException("splits(long, source) is not used in this test")
    }
}
