# ixy.java Patterns Performance

This captures a few patterns that [ixy.java](https://github.com/ixy-languages/ixy.java) might use to run better.

## Build and Run

The benchmark use JMH, and require JDK 11+ to compile and run. 

```
$ mvn clean install
$ java -jar target/benchmarks.jar [test regexp, if needed]
```

## Profiling

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

The key to `Unsafe` performance is idiomatic use: it relies heavily on the JIT compiler being able
to nuke down everything to the raw memory accesses. Part of that story is null-checking the `Unsafe` instance itself. When compiler is not sure about it, compiler would emit null-checks and then barrier coalescing would break.

Sample perfasm for `test_final`:

```
         ↗  0x00007f14385240f0: mov    0xc(%r8),%r10d      ; get field $unsafe
         │  0x00007f14385240f4: mov    0x78(%r9),%r11      ; get field $addr
         │  0x00007f14385240f8: test   %r10d,%r10d         ; null-check Unsafe object
         │  0x00007f14385240fb: je     0x00007f1438524185
  1.29%  │  0x00007f1438524101: mov    %r11,%r10
         │  0x00007f1438524104: movb   $0x2a,(%r10)        ; store!
         │  0x00007f1438524108: lock addl $0x0,-0x40(%rsp) ; <membar>
 28.77%  │  0x00007f143852410e: mov    0x78(%r9),%r10      ; get field $addr
         │  0x00007f1438524112: mov    0xc(%r8),%ecx       ; get field $unsafe
         │  0x00007f1438524116: test   %ecx,%ecx           ; null-check Unsafe object
         │  0x00007f1438524118: je     0x00007f1438524196
  0.70%  │  0x00007f143852411a: movb   $0x2b,0x40(%r10)    ; store
         │  0x00007f143852411f: lock addl $0x0,-0x40(%rsp) ; <membar>
 24.21%  │  0x00007f1438524125: mov    0x78(%r9),%r10      ; get field $addr
         │  0x00007f1438524129: mov    0xc(%r8),%ecx       ; get field $unsafe
         │  0x00007f143852412d: test   %ecx,%ecx           ; null-check Unsafe object
         │  0x00007f143852412f: je     0x00007f14385241aa  
  0.70%  │  0x00007f1438524131: add    $0x1,%rbp            
         │  0x00007f1438524135: movb   $0x2c,0x80(%r10)    ; store!
         │  0x00007f143852413d: lock addl $0x0,-0x40(%rsp) ; <membar>
 23.51%  │  0x00007f1438524143: movzbl 0x94(%rdi),%ecx     ; ...benchmark infra follows...
         │  0x00007f143852414a: mov    0x108(%r15),%r11   
         │  0x00007f1438524151: test   %eax,(%r11)        
  1.05%  │  0x00007f1438524154: test   %ecx,%ecx
         ╰  0x00007f1438524156: je     0x00007f14385240f0 
```                            

Sample perfasm for `test_static_final`:

```
         ↗  0x00007f76585242a0: mov    0x78(%rcx),%r10     ; get field $addr
         │  0x00007f76585242a4: add    $0x1,%rbp          
         │  0x00007f76585242a8: movb   $0x2a,(%r10)        ; store!
  3.38%  │  0x00007f76585242ac: mov    0x78(%rcx),%r10     ; get field $addr
         │  0x00007f76585242b0: movb   $0x2b,0x40(%r10)    ; store!
         │  0x00007f76585242b5: mov    0x78(%rcx),%r10     ; get field $addr
         │  0x00007f76585242b9: movb   $0x2c,0x80(%r10)    ; store!
  2.94%  │  0x00007f76585242c1: lock addl $0x0,-0x40(%rsp) ; <membar>
 65.47%  │  0x00007f76585242c7: movzbl 0x94(%rdi),%r9d     ; ...benchmark infra follows...
         │  0x00007f76585242cf: mov    0x108(%r15),%r11    
         │  0x00007f76585242d6: test   %eax,(%r11)         
  3.81%  │  0x00007f76585242d9: test   %r9d,%r9d
         ╰  0x00007f76585242dc: je     0x00007f76585242a0  
```

Reported as [ixy.java #5](https://github.com/ixy-languages/ixy.java/issues/5).

### Unsafe Problem 2: Access Modes

```
Benchmark                     Mode  Cnt   Score   Error  Units         
UnsafeProblem2.test_opaque    avgt    9  0.837 ± 0.004  ns/op
UnsafeProblem2.test_plain     avgt    9  0.836 ± 0.003  ns/op
UnsafeProblem2.test_release   avgt    9  0.841 ± 0.011  ns/op
UnsafeProblem2.test_volatile  avgt    9  6.966 ± 0.026  ns/op  <--- this is what ixy.java does now 
```

Unsafe provides the variety of access modes, and going volatile is excessive on many paths. Notably, when C-like volatile access is needed, it is cheaper to do opaque Unsafe access to match both semantics and memory ordering requirements better. If memory ordering is still needed, then release might be a better option for acquire-release semantics, which skips the heavy-weight memory barrier on x86_64.

Really, _any other_ access mode is more performant than full-blown `volatile`, which implies sequential consistency
and makes very heavy barriers.

Sample perfasm for `test_volatile`:

```
         ↗  0x00007f79b8522400: mov    0x78(%r8),%r10      ; get field $addr
         │  0x00007f79b8522404: add    $0x1,%rbp           
         │  0x00007f79b8522408: movb   $0x2a,(%r10)        ; store!
  1.68%  │  0x00007f79b852240c: lock addl $0x0,-0x40(%rsp) ; <membar>
 68.70%  │  0x00007f79b8522412: movzbl 0x94(%rcx),%r11d    ; ...benchmark infra follows...
         │  0x00007f79b852241a: mov    0x108(%r15),%r10   
         │  0x00007f79b8522421: test   %eax,(%r10)         
  2.94%  │  0x00007f79b8522424: test   %r11d,%r11d
         ╰  0x00007f79b8522427: je     0x00007f79b8522400  
```                                                        

Sample perfasm for `test_release`:

```
 26.24%  ↗  0x00007fe1a4522380: mov    0x78(%r11),%r10     ; get field $addr
         │  0x00007fe1a4522384: add    $0x1,%rbp           
         │  0x00007fe1a4522388: movb   $0x2a,(%r10)        ; store!
 51.55%  │  0x00007fe1a452238c: movzbl 0x94(%rcx),%r8d     ; ...benchmark infra follows...
         │  0x00007fe1a4522394: mov    0x108(%r15),%r10   
         │  0x00007fe1a452239b: test   %eax,(%r10)        
  0.12%  │  0x00007fe1a452239e: test   %r8d,%r8d
         ╰  0x00007fe1a45223a1: je     0x00007fe1a4522380  
```

Reported as [ixy.java #6](https://github.com/ixy-languages/ixy.java/issues/6).
