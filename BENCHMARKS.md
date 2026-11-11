# Benchmarks

[← back to the README](README.md)

What the library costs outside the network. An OAuth client spends its time waiting for an authorization server, so
these numbers answer a narrower question: is anything the library does per request, or per token, expensive enough to
notice? The benchmarks are in
[`benchmarks/`](benchmarks/src/main/scala/oauth4s/benchmarks/ParsingBenchmark.scala).

```
sbt "benchmarks/Jmh/run -i 5 -wi 5 -w 1s -r 1s -f 2 -t 1 .*ParsingBenchmark.*"
```

Intel Core i7-13700KF, forks pinned to the performance cores, OpenJDK 25.0.1, one thread, throughput in operations per
millisecond (higher is better). The input is a token response of about 900 bytes that carries an RS256 ID token:

| Operation | Throughput | Per operation |
| --- | ---: | ---: |
| Token response, text to tree, built-in codec | 1160 ± 114 | 0.9 µs |
| Token response, text to tree, circe | 1195 ± 102 | 0.8 µs |
| Token response, text to `TokenResponse`, built-in codec | 780 ± 116 | 1.3 µs |
| ID token, split and parsed, signature not checked | 818 ± 72 | 1.2 µs |
| ID token, verified: RS256 with a 2048-bit key, and the claims | 33.0 ± 6.7 | 30 µs |
| Key set of one RSA key, tree to `Jwks` | 1359 ± 283 | 0.7 µs |

**The built-in codec is not what you would replace for speed.** It reads this document as fast as circe does, within
the noise of the run. The reason to import a JSON library's instances is to use its decoders for your own types, and to
have one parser in the process instead of two.

**Verifying an ID token is the only step with a cost worth naming**, and it is the RSA signature: thirty microseconds
on this machine, once per sign-in. A resource server that verifies a bearer token on every request pays it per
request, about thirty thousand verifications per second per core. ES256 tokens cost more to verify than RS256 ones;
that is the nature of the two algorithms, not of the library.

**Nothing is cached that you did not ask to be cached.** The provider's key set is fetched once and kept
(see [the provider's keys](README.md#the-providers-keys)); the public keys are rebuilt from it for each verification,
which is part of the thirty microseconds.

The error bars are wide because the machine was doing other work during the run. Read the table for orders of
magnitude; it is not a promise about any other machine.
