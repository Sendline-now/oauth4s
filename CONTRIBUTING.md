# Contributing to oauth4s

[← back to the README](README.md)

Thanks for taking the time to contribute. This page covers what you need to build the project, run its tests, where
common kinds of changes go, and how a release is cut.

## Building

**JDK 25 is required to build**, and the build checks it when it loads: `oauth4s-kyo` compiles kyo 1.0, whose class
files are Java 25, and scalac has to load them. `cs java --jvm 25` or `brew install openjdk@25` gets you one; CI uses
the same.

What the build produces runs on JDK 17: `-java-output-version 17` writes Java 17 class files and refuses any JDK API
newer than 17. The exception is `oauth4s-kyo`'s runtime dependency on kyo, which needs 25 because kyo does.

Every published module compiles on `Versions.scala3` (`3.9`, the LTS) in `project/Dependencies.scala`. `it` is the
exception on purpose: it compiles on `Versions.scala3Next` (`3.10`) against the library compiled by `3.9`.

```bash
sbt compile                 # compile everything
sbt "testOnly *"            # run the tests of every published module
sbt "it/testOnly *"         # the flows over real HTTP, the README's examples and the recipes, on the next Scala
sbt "scalafmtCheckAll; scalafmtSbtCheck; it/scalafmtCheckAll"  # verify formatting, as CI does
sbt "examples/runMain oauth4s.examples.CatsEffectExample"
```

sbt 2 takes several tasks as one quoted string separated by `;`. It also caches test runs and replays a cached run's
log, so a repeated `test` with unchanged inputs runs nothing; `testOnly *` is what CI uses, and changing the filter
forces a real run.

To run one module's tests:

```bash
sbt core/test
sbt "it/testOnly oauth4s.it.OidcFlowItSpec"
sbt "jwt/testOnly oauth4s.jwt.JwtSpec"
```

Publishing credentials live in `~/.sbt/sonatype_credentials`, outside the repository. A checkout without the file
builds and tests exactly the same; the build only adds the credentials when the file is there.

## Project layout

| Path | What lives there |
| --- | --- |
| `core` | the protocol model, the client, discovery, the errors, the built-in JSON codec, and the `Effect`, `HttpClient` and `JsonCodec` SPIs |
| `jwt` | JWT parsing, verification and signing, JWK, JWKS and the cached remote key source |
| `oidc` | `OidcClient`: ID token validation, UserInfo, logout |
| `providers` | one object per provider behind `Provider[F]` |
| `runtime/cats`, `runtime/zio`, `runtime/kyo`, `runtime/rapid` | `given Effect[F]` per runtime |
| `http/jdk`, `http/http4s`, `http/sttp` | `HttpClient[F]` per client |
| `json/circe`, `json/zio`, `json/jsoniter` | `given JsonCodec` and the decoder bridge per JSON library |
| `testkit` | `StubHttpClient`, `TestIssuer`, `FakeAuthorizationServer`; its tests hold the suites the modules above share |
| `it` | an OpenID Connect login over real HTTP, and the examples of the README and the recipes, compiled on the next Scala release and run |
| `examples` | one runnable main per runtime, each against `FakeAuthorizationServer` |
| `benchmarks` | JMH benchmarks of parsing and ID token verification |

`core` has no dependencies and must keep having none. A new JSON library, runtime or HTTP client is a new module and a
new `given`, never a dependency of `core`.

## What a change looks like

- **A new protocol value** is an opaque type in [`values.scala`](core/src/main/scala/oauth4s/values.scala) with an
  `apply` and a `value` extension. If it can be wrong, it is checked there, not where it is used.
- **A new error code** is a case of `ErrorCode` and an entry in its `registered` list.
- **A new option** is a field and a `withX` method on the builder it belongs to — `OAuthClientConfig`,
  `AuthorizationOptions`, `OidcConfig`, `JwtValidation` — never a new constructor parameter of a public case class.
- **A new flow** is a method on the `OAuthClient` trait and its implementation in
  [`OAuthClientImpl`](core/src/main/scala/oauth4s/internal/OAuthClientImpl.scala). Every response document has one
  `fromJson` that returns `Either[OAuthError, ...]`.
- **A new provider** is an object in `providers` with `config`, `apply`, `from` and a pure `profile(json)`, a case in
  `ProvidersSpec`, and a row in the README table. Check the endpoints against the provider's documentation, not against
  another library. If the provider deviates in a way the knobs of `OAuthClientConfig` cannot express, add a knob, not a
  special case in the client.
- **A new runtime** is a module with a `given Effect[F]`, an `XInstances` trait and object, and a spec that extends
  `RuntimeSuite[F]`.
- **A new HTTP client** is a module with an `HttpClient[F]` and a spec that extends `HttpClientSuite[F]`.
- **A new JSON library** is a module with a `given JsonCodec`, a bridge from its decoders to `JsonDecoder`, its codecs
  for `Json` and `TokenResponse`, and a spec that extends `JsonCodecSuite`.

## Testing

Tests are scalatest, `AnyWordSpec` and `Matchers`, with names that describe the behaviour and the reason for it. `core`
is tested on `scala.util.Try` with a clock moved by hand (`ManualTime`) and a recording HTTP client, so no test sleeps
and none depends on a runtime.

The suite of a runtime, a JSON bridge or an HTTP client is the shared one: if the behaviour you are adding belongs to
the SPI and not to one implementation, it goes in `RuntimeSuite`, `JsonCodecSuite` or `HttpClientSuite`, so every
implementation is held to it.

## Documentation

- Every Scala block in [README.md](README.md) and [RECIPES.md](RECIPES.md) is copied from an example under
  `it/src/test/scala/oauth4s/it/docs`, which the `it` module compiles with the same warnings as the library.
  `DocsItSpec` fails on a block that matches no example, whitespace aside, and on a dependency line whose version is
  not the one `build.sbt` publishes. `RecipesItSpec` runs the recipes that do something, against
  `FakeAuthorizationServer`. Change the example and the block together.
- There is no documentation site in this repository. [oss.sendline.now/oauth4s](https://oss.sendline.now/oauth4s/) is
  built in its own repository from [README.md](README.md) and the files it links: every H2 section of the README is a
  page, and a heading that is renamed there has to be renamed in that site's configuration. Link between the documents
  with relative paths.
- The logos in `logos/` are generated by the same repository (`tools/brand.mjs`). Do not edit them by hand.
- A fact about a provider — an endpoint, a scope, the shape of a response — comes from the provider's documentation,
  and [COMPATIBILITY.md](COMPATIBILITY.md) says when it was last checked. Update the date when you check again.
- Numbers in [BENCHMARKS.md](BENCHMARKS.md) come from `sbt "benchmarks/Jmh/run"`; rerun it when parsing or
  verification changes.

The code carries no comments and no scaladoc. Where the logic is subtle enough to need explaining, the explanation
belongs in [README.md](README.md), [DESIGN.md](DESIGN.md) or [COMPATIBILITY.md](COMPATIBILITY.md), not in a comment
nobody reading the docs will find. The examples under `it/…/docs` are the exception: their comments are the captions
of the README.

## Formatting

`scalafmt` runs from the build; `maxColumn = 170`, `align.preset = most`. Do not reformat files you did not touch. The
house style beyond what scalafmt enforces — colon bodies, import blocks ordered by line length, `if ... then` — is what
the existing code already does; match it.

## Cutting a release

1. Update `version` in `build.sbt` and `CHANGELOG.md`.
2. `sbt "testOnly *"`, `sbt "it/testOnly *"` and `sbt "scalafmtCheckAll; scalafmtSbtCheck; it/scalafmtCheckAll"`.
3. `sbt publishSigned` (stages to Sonatype), then `sonatypeReleaseAll` once the staging repository is validated.
4. Tag the commit `vX.Y.Z` and publish the GitHub release with the changelog section.

`mimaPreviousArtifacts` is empty until the first release; after that, add the released version to
`binaryCompatibleWith` and let `mimaReportBinaryIssues` guard the public API.
