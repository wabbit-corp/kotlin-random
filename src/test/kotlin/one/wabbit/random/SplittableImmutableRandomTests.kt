package one.wabbit.random

import org.junit.Assert
import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.*

class SplittableImmutableRandomTests {
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

    for (i in 0..1000) {
      s = s.split()
      sp = sp.fork().second

      val p = sp.next64()
      sp = p.second

      assertEquals(s.nextLong(), p.first)
    }
  }

  @Test
  fun next64IsBounded1() {
    val s = SplittableRandom(0x1000)

    var sp = SplittableImmutableRandom(0x1000)

    for (i in 0..100) {
      val b = s.nextLong(Long.MAX_VALUE)

      for (j in 0..1000) {
        val p = sp.next64(b)
        sp = p.second
        Assert.assertTrue(p.first <= b)
      }
    }
  }

  @Test
  fun next64IsBounded2() {
    val s = SplittableRandom(0x1000)

    var sp = SplittableImmutableRandom(0x1000)

    for (i in 0..100) {
      val b = s.nextLong()

      for (j in 0..1000) {
        val p = sp.next64(b)
        sp = p.second
        if (b >= 0) Assert.assertTrue(p.first <= b)
        else Assert.assertTrue(p.first >= 0 || p.first <= b)
      }
    }
  }

  @Test
  fun next64IsBounded3() {
    val s = SplittableRandom(0x1000)

    var sp = SplittableImmutableRandom(0x1000)

    for (i in 0..100) {
      val b = 1L shl s.nextInt(31)

      for (j in 0..1000) {
        val p = sp.next64(b)
        sp = p.second
        if (b >= 0) Assert.assertTrue(p.first <= b)
        else Assert.assertTrue(p.first >= 0 || p.first <= b)
      }
    }
  }

  @Test
  fun seedWithEmptyArray() {
      SplittableImmutableRandom.seed(ByteArray(0))
  }

  @Test
  fun seedWithArray() {
    val s = SplittableRandom(0x1000)
    for (i in 0..100) {
        SplittableImmutableRandom.seed(ByteArray(s.nextInt(64)) { it.toByte() })
    }
  }
}
