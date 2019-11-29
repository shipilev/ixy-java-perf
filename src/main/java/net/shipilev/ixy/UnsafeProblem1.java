package net.shipilev.ixy;

import org.openjdk.jmh.annotations.*;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@State(Scope.Benchmark)
public class UnsafeProblem1 {

    // Finality matters:
    static final Unsafe SF_U;
    static       Unsafe s_u;
           final Unsafe f_u;
                 Unsafe u;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            SF_U = (Unsafe) field.get(null);
            s_u = SF_U;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        addr = SF_U.allocateMemory(1000);
    }

    public UnsafeProblem1() {
        f_u = SF_U;
        u = SF_U;
    }

    private static long addr;

    @Benchmark
    public void test_static_final() {
        SF_U.putByteVolatile(null, addr, (byte) 42);
        SF_U.putByteVolatile(null, addr + 64, (byte) 43);
        SF_U.putByteVolatile(null, addr + 128, (byte) 44);
    }

    @Benchmark
    public void test_static() {
        s_u.putByteVolatile(null, addr, (byte) 42);
        s_u.putByteVolatile(null, addr + 64, (byte) 43);
        s_u.putByteVolatile(null, addr + 128, (byte) 44);
    }

    @Benchmark
    public void test_final() {
        f_u.putByteVolatile(null, addr, (byte) 42);
        f_u.putByteVolatile(null, addr + 64, (byte) 43);
        f_u.putByteVolatile(null, addr + 128, (byte) 44);
    }

    @Benchmark
    public void test() {
        u.putByteVolatile(null, addr, (byte) 42);
        u.putByteVolatile(null, addr + 64, (byte) 43);
        u.putByteVolatile(null, addr + 128, (byte) 44);
    }
}
