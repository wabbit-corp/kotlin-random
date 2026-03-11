package one.wabbit.random

import java.util.SplittableRandom
import kotlin.test.Test
import kotlin.test.assertEquals

class SplittableImmutableRandomJvmSpec {
    @Test
    fun stdGenIsCompatibleWithSplittableRandom1() {
        val s = SplittableRandom(0x1000)
        val sp = SplittableImmutableRandom(0x1000)

        val (r1, sp1) = sp.next64()
        val (r2, sp2) = sp1.next64()
        val (r3, sp3) = sp2.next32()
        val (r4, sp4) = sp3.next32()
        val (sp5_1, sp5_2) = sp4.fork()

        assertEquals(s.nextLong(), r1)
        assertEquals(s.nextLong(), r2)
        assertEquals(s.nextInt(), r3)
        assertEquals(s.nextInt(), r4)

        val s2 = s.split()
        assertEquals(s.nextLong(), sp5_1.next64().first)
        assertEquals(s2.nextLong(), sp5_2.next64().first)
    }

    @Test
    fun stdGenIsCompatibleWithSplittableRandom2() {
        var s = SplittableRandom(0x1000)
        var sp = SplittableImmutableRandom(0x1000)

        repeat(1001) {
            s = s.split()
            sp = sp.fork().second

            val step = sp.next64()
            sp = step.second

            assertEquals(s.nextLong(), step.first)
        }
    }

    @Test
    fun seed_matches_existing_big_endian_chunking() {
        val seeded = SplittableImmutableRandom.seed(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertEquals(424968459718238173L, seeded.seed)
        assertEquals(-5065044720681808427L, seeded.gamma)
    }
}
