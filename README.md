# ixy.java Patterns Performance

This captures a few patterns that ixy.java might use to run better.

## Running the benchmark

```
$ mvn clean install
$ java -jar target/benchmarks.jar [test regexp, if needed]
```

## Profiling the benchmark

Get hsdis disassembly plugin, and then run:

```
$ java -jar target/benchmarks.jar -f 1 -prof perfasm
```

## Captured Problems

### Unsafe Problem 1: Finality

```
Benchmark                         Mode  Cnt   Score   Error  Units
UnsafeProblem1.test               avgt    9  19.489 ± 0.288  ns/op
UnsafeProblem1.test_final         avgt    9  19.397 ± 0.189  ns/op <--- this is what ixy.java does now
UnsafeProblem1.test_static        avgt    9  19.492 ± 0.118  ns/op
UnsafeProblem1.test_static_final  avgt    9   6.355 ± 0.036  ns/op <--- this is what it should be doing
```

The key to `Unsafe` performance is idiomatic use: the instance should be in `static final`.
After that, the weakest (but not weaker) mode has to be chosen. But even if overly strong
mode is chosen, `static final`-ing the `Unsafe` instance would enable some optimizations.

### Unsafe Problem 2: Access Modes

```
Benchmark                         Mode  Cnt   Score   Error  Units
UnsafeProblem2.test_opaque        avgt    9   0.988 ± 0.008  ns/op
UnsafeProblem2.test_plain         avgt    9   0.991 ± 0.013  ns/op
UnsafeProblem2.test_release       avgt    9   0.986 ± 0.006  ns/op
UnsafeProblem2.test_volatile      avgt    9   6.990 ± 0.042  ns/op <--- this is what ixy.java does now
```

Really, _any other_ access mode is better than full-blown `volatile` (that implies sequential consistency
and makes very heavy barriers) is more useful. For driver use, `opaque` or `release` should be sufficient. 

