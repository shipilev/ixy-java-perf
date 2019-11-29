package net.shipilev.ixy;

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = {"--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class UnsafeProblem2 {

    static final Unsafe U;
    private static long addr;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            U = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        addr = U.allocateMemory(1000);
    }

    @Benchmark
    public void test_plain() {
        U.putByte(null, addr, (byte) 42);
    }

    @Benchmark
    public void test_opaque() {
        U.putByteOpaque(null, addr, (byte) 42);
    }

    @Benchmark
    public void test_release() {
        U.putByteRelease(null, addr, (byte) 42);
    }

    @Benchmark
    public void test_volatile() {
        U.putByteVolatile(null, addr, (byte) 42);
    }

}
