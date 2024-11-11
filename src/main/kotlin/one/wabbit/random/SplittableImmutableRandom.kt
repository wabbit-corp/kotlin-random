package one.wabbit.random

import java.nio.ByteBuffer

/**
 * An immutable splittable random number generator that provides deterministic randomization
 * with the ability to fork into independent streams.
 *
 * @property seed The current state of the random number generator
 * @property gamma A parameter that determines the sequence of random numbers
 */
class SplittableImmutableRandom @JvmOverloads constructor(
    val seed: Long,
    val gamma: Long = GOLDEN_GAMMA
) {
    /**
     * Generates the next 64-bit random number.
     * @return Pair of the random number and the next state
     */
    fun next64(): Pair<Long, SplittableImmutableRandom> {
        val s = seed + gamma
        return Pair(mix64(s), SplittableImmutableRandom(s, gamma))
    }

    fun next64(max: Long): Pair<Long, SplittableImmutableRandom> {
        var s = seed + gamma
        s = mix64(s)
        val bound = max + 1
        return if (max and max + 1 == 0L) {
            // If bounded by a power of two.
            Pair(
                s and max,
                SplittableImmutableRandom(s, gamma)
            )
        } else if (max > 0) {
            val max1 = Long.MAX_VALUE / bound * bound
            var r = s ushr 1
            while (true) {
                if (r >= max1) {
                    r = mix64(r)
                    continue
                }
                r = r % bound
                break
            }
            Pair(r, SplittableImmutableRandom(s, gamma))
        } else {
            var r = s
            while (true) {
                if (r > max) {
                    r = mix64(r)
                    continue
                }
                break
            }
            Pair(r, SplittableImmutableRandom(s, gamma))
        }
    }

    fun next32(): Pair<Int, SplittableImmutableRandom> {
        val s = seed + gamma
        return Pair(mix32(s), SplittableImmutableRandom(s, gamma))
    }

    fun fork(): Pair<SplittableImmutableRandom, SplittableImmutableRandom> {
        var s = seed + gamma
        val r1 = mix64(s)
        s += gamma
        val r2 = mixGamma(s)
        return Pair(
            SplittableImmutableRandom(s, gamma),
            SplittableImmutableRandom(r1, r2)
        )
    }

    fun join(that: SplittableImmutableRandom): SplittableImmutableRandom {
        return SplittableImmutableRandom(
            mix64(seed) xor mix64(that.seed),
            mixGamma(mix64(gamma) xor mix64(that.gamma))
        )
    }

    companion object {
        fun seed(array: ByteArray): SplittableImmutableRandom {
            val bb = ByteBuffer.wrap(array)
            var h: Long = 0
            var i = 0
            while (i < array.size) {
                if (i + 8 <= array.size) {
                    h = h xor mix64(bb.getLong(i))
                    i += 8
                } else if (i + 4 <= array.size) {
                    h = h xor mix64(bb.getInt(i).toLong())
                    i += 4
                } else if (i + 2 <= array.size) {
                    h = h xor mix64(bb.getShort(i).toLong())
                    i += 2
                } else {
                    h = h xor mix64(bb[i].toLong())
                    i += 1
                }
            }
            return SplittableImmutableRandom(h, mixGamma(h + GOLDEN_GAMMA))
        }

        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        private const val A = -0x40a7b892e31b1a47L
        private const val B = -0x6b2fb644ecceee15L
        private const val C = 0x62a9d9ed799705f5L
        private const val D = -0x34db2f5a3773ca4dL
        private const val E = -0xae502812aa7333L
        private const val F = -0x3b314601e57a13adL

        /**
         * Computes Stafford variant 13 of 64bit mix function.
         */
        private fun mix64(z0: Long): Long {
            var z = z0
            z = (z xor (z ushr 30)) * A
            z = (z xor (z ushr 27)) * B
            return z xor (z ushr 31)
        }

        /**
         * Returns the 32 high bits of Stafford variant 4 mix64 function as int.
         */
        private fun mix32(z0: Long): Int {
            var z = z0
            z = (z xor (z ushr 33)) * C
            return ((z xor (z ushr 28)) * D ushr 32).toInt()
        }

        /**
         * Returns the gamma value to use for a new split instance.
         */
        private fun mixGamma(z0: Long): Long {
            // MurmurHash3 mix constants
            var z = z0
            z = (z xor (z ushr 33)) * E
            z = (z xor (z ushr 33)) * F
            // force to be odd
            z = z xor (z ushr 33) or 1L
            // ensure enough transitions
            val n = java.lang.Long.bitCount(z xor (z ushr 1))
            return if (n < 24) z xor -0x5555555555555556L else z
        }
    }
}
