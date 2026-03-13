#!/usr/bin/env python3

try:
    import jax
    import jax.numpy as jnp
    import numpy as np
    from jax._src import prng
    from numpy.random import Philox
except ImportError as exc:
    raise SystemExit(
        "This script requires numpy and jax. Install them first, for example: "
        "python -m pip install numpy 'jax[cpu]'"
    ) from exc


jax.config.update("jax_enable_x64", True)


def signed64(value: int) -> int:
    value &= (1 << 64) - 1
    return value - (1 << 64) if value >= 1 << 63 else value


def signed32(value: int) -> int:
    value &= (1 << 32) - 1
    return value - (1 << 32) if value >= 1 << 31 else value


def rotl(value: int, shift: int) -> int:
    return ((value << shift) & ((1 << 64) - 1)) | (value >> (64 - shift))


def philox_vectors() -> None:
    def increment_counter(counter: list[int]) -> list[int]:
        words = counter[:]
        carry = 1
        for index in range(4):
            if carry == 0:
                break
            words[index] = (words[index] + carry) & ((1 << 64) - 1)
            carry = 1 if words[index] == 0 else 0
        return words

    vectors = [
        ([0, 0, 0, 0], [0, 0]),
        ([1, 0, 0, 0], [0, 0]),
        ([0, 1, 0, 0], [0, 0]),
        ([0, 0, 0, 0], [1, 0]),
        (
            [0x0123456789ABCDEF, 0xFEDCBA9876543210, 0x1111111111111111, 0x2222222222222222],
            [0x3333333333333333, 0x4444444444444444],
        ),
    ]

    print("Philox NumPy-state vectors")
    for numpy_state_counter, key in vectors:
        raw = Philox(counter=np.array(numpy_state_counter, dtype=np.uint64), key=np.array(key, dtype=np.uint64)).random_raw(4)
        print("numpy_state_counter =", [signed64(x) for x in numpy_state_counter])
        print("exact_counter =", [signed64(x) for x in increment_counter(numpy_state_counter)])
        print("key =", [signed64(x) for x in key])
        print("expected =", [signed64(int(x)) for x in raw])

    print("Philox stream8 =", [signed64(int(x)) for x in Philox(counter=[0, 0, 0, 0], key=[0, 0]).random_raw(8)])
    print("Philox jumped =", [signed64(int(x)) for x in Philox(counter=[0, 0, 0, 0], key=[0, 0]).jumped().random_raw(4)])


def threefry_vectors() -> None:
    vectors = [
        ((0, 0), (0, 0)),
        ((1, 2), (3, 4)),
        ((0xFFFFFFFF, 0), (0, 1)),
        ((0x12345678, 0x9ABCDEF0), (0x11111111, 0x22222222)),
    ]

    print("Threefry hash vectors")
    for key, count in vectors:
        output = prng.threefry_2x32(jnp.array(key, dtype=jnp.uint32), jnp.array(count, dtype=jnp.uint32))
        print("key =", [signed32(int(x)) for x in key])
        print("count =", [signed32(int(x)) for x in count])
        print("expected =", [signed32(int(x)) for x in output])

    split = prng.threefry_split(jnp.array([0, 0], dtype=jnp.uint32), (4,))
    print("Threefry split =", [[signed32(int(y)) for y in row] for row in split])
    fold = prng.threefry_fold_in(jnp.array([0, 0], dtype=jnp.uint32), jnp.uint32(7))
    print("Threefry fold =", [signed32(int(x)) for x in fold])


def xoshiro_next_state(state: list[int]) -> list[int]:
    s0, s1, s2, s3 = state
    t = (s1 << 17) & ((1 << 64) - 1)
    s2 ^= s0
    s3 ^= s1
    s1 ^= s2
    s0 ^= s3
    s2 ^= t
    s3 = rotl(s3, 45)
    return [s0 & ((1 << 64) - 1), s1 & ((1 << 64) - 1), s2 & ((1 << 64) - 1), s3 & ((1 << 64) - 1)]


def xoshiro_plus_plus(state: list[int]) -> int:
    return (rotl((state[0] + state[3]) & ((1 << 64) - 1), 23) + state[0]) & ((1 << 64) - 1)


def xoshiro_star_star(state: list[int]) -> int:
    return (rotl((state[1] * 5) & ((1 << 64) - 1), 7) * 9) & ((1 << 64) - 1)


def xoshiro_jump(state: list[int], polynomial: list[int]) -> list[int]:
    acc = [0, 0, 0, 0]
    current = state[:]
    for word in polynomial:
        bits = word
        for _ in range(64):
            if bits & 1:
                acc = [a ^ c for a, c in zip(acc, current)]
            current = xoshiro_next_state(current)
            bits >>= 1
    return [x & ((1 << 64) - 1) for x in acc]


def splitmix64_state(seed: int) -> list[int]:
    gamma = 0x9E3779B97F4A7C15
    state = seed & ((1 << 64) - 1)
    words = []
    while len(words) < 4:
        state = (state + gamma) & ((1 << 64) - 1)
        z = state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & ((1 << 64) - 1)
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & ((1 << 64) - 1)
        z ^= z >> 31
        words.append(z & ((1 << 64) - 1))
    return words


def emit_xoshiro_sequence(state: list[int], output_fn, count: int) -> list[int]:
    current = state[:]
    values = []
    for _ in range(count):
        values.append(signed64(output_fn(current)))
        current = xoshiro_next_state(current)
    return values


def xoshiro_vectors() -> None:
    jump = [0x180EC6D33CFD0ABA, 0xD5A61266F0C9392C, 0xA9582618E03FC9AA, 0x39ABDC4529B1661C]
    long_jump = [0x76E15D3EFEFDCBBF, 0xC5004E441C522FB3, 0x77710069854EE241, 0x39109BB02ACBE635]
    base_state = [1, 2, 3, 4]

    print("Xoshiro256++ next =", emit_xoshiro_sequence(base_state, xoshiro_plus_plus, 8))
    print("Xoshiro256** next =", emit_xoshiro_sequence(base_state, xoshiro_star_star, 8))

    jumped = xoshiro_jump([1, 2, 3, 4], jump)
    print("Xoshiro256 jump state =", [signed64(x) for x in jumped])
    print("Xoshiro256++ jump next =", emit_xoshiro_sequence(jumped, xoshiro_plus_plus, 4))
    print("Xoshiro256** jump next =", emit_xoshiro_sequence(jumped, xoshiro_star_star, 4))

    long_jumped = xoshiro_jump([1, 2, 3, 4], long_jump)
    print("Xoshiro256 long jump state =", [signed64(x) for x in long_jumped])
    print("Xoshiro256++ long jump next =", emit_xoshiro_sequence(long_jumped, xoshiro_plus_plus, 4))
    print("Xoshiro256** long jump next =", emit_xoshiro_sequence(long_jumped, xoshiro_star_star, 4))

    print("Xoshiro256 seed(1) state =", [signed64(x) for x in splitmix64_state(1)])


if __name__ == "__main__":
    philox_vectors()
    threefry_vectors()
    xoshiro_vectors()
