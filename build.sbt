import Dependencies.Versions
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts

lazy val binaryCompatibleWith = Set("0.1.0")

lazy val requiredJdk = 25

lazy val checkedJdk: Int = {
  val running = sys.props.getOrElse("java.specification.version", "unknown")
  val major   = running.split('.').headOption.flatMap(_.toIntOption).getOrElse(0)

  if (major < requiredJdk) {
    sys.error(
      s"oauth4s requires JDK $requiredJdk or newer; this build is running on JDK $running. " +
        "kyo ships Java 25 class files, so oauth4s-kyo cannot be compiled by an older JDK. " +
        "Point JAVA_HOME at a JDK 25 install (`cs java --jvm 25`, or `brew install openjdk@25`) and retry."
    )
  }

  major
}

Global / onLoad := {
  val _ = checkedJdk
  (Global / onLoad).value
}

lazy val sourceLevel = settingKey[String]("Scala -source level the build compiles against")

lazy val commonSettings = Seq(
  organization           := "now.sendline",
  organizationName       := "Sendline",
  organizationHomepage   := Some(uri("https://oss.sendline.now/")),
  homepage               := Some(uri("https://oss.sendline.now/oauth4s/")),
  description            := "OAuth 2.0 / 2.1 and OpenID Connect client for Scala 3: any runtime, any JSON codec, any HTTP client",
  version                := "0.1.1",
  versionScheme          := Some("semver-spec"),
  scalaVersion           := Versions.scala3,
  parallelExecution      := true,
  publishMavenStyle      := true,
  Test / publishArtifact := false,
  licenses               := List(License.Apache2),
  pomIncludeRepository   := { _ => false },
  publishTo              := localStaging.value,
  scmInfo                := Some(
    ScmInfo(
      uri("https://github.com/sendline-now/oauth4s"),
      "git@github.com:sendline-now/oauth4s.git",
    )
  ),
  developers             := List(
    Developer(
      "shadowsmind",
      "Alexandr Oshlakov",
      "shadowsmind.dev@gmail.com",
      uri("https://github.com/shadowsmind"),
    )
  ),
  libraryDependencies ++= Dependencies.Testing.all,
  exportJars             := false,
  sourceLevel            := "3.9",
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-java-output-version",
    "17",
    s"-source:${sourceLevel.value}",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xcheck-macros",
    "-Wunused:all",
    "-Wvalue-discard",
    "-WunstableInlineAccessors",
    "-Wshadow:all",
    "-Winfer-union",
    "-Wimplausible-patterns",
    "-Wrecurse-with-default",
  ),
  scalacOptions ++= (if (sourceLevel.value == "future") Nil
                     else Seq("-Werror", "-Wconf:msg=@nowarn annotation does not suppress any warnings:s", "-Wconf:msg=is not declared infix:s")),
  credentials ++= Seq(Path.userHome / ".sbt" / "sonatype_credentials").filter(_.isFile).map(Credentials(_)),
  mimaPreviousArtifacts  := binaryCompatibleWith.map(organization.value %% moduleName.value % _),
)

lazy val aggregateSettings = Seq(
  publish / skip        := true,
  mimaPreviousArtifacts := Set.empty,
)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-core"
  )

lazy val jwt = project
  .in(file("jwt"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-jwt"
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val oidc = project
  .in(file("oidc"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-oidc"
  )
  .dependsOn(core % "compile->compile;test->test", jwt, testkit % Test)

lazy val providers = project
  .in(file("providers"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-providers"
  )
  .dependsOn(core % "compile->compile;test->test", oidc, testkit % Test)

lazy val jdkHttp = project
  .in(file("http/jdk"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-http-jdk"
  )
  .dependsOn(core, testkit % "test->test")

lazy val http4s = project
  .in(file("http/http4s"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-http4s",
    libraryDependencies ++= Dependencies.Http4s.all ++ Dependencies.Cats.all,
  )
  .dependsOn(core, cats % Test, testkit % "test->test")

lazy val sttp = project
  .in(file("http/sttp"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-sttp",
    libraryDependencies ++= Dependencies.Sttp.all,
  )
  .dependsOn(core, testkit % "test->test")

lazy val http = project
  .in(file("http"))
  .settings(commonSettings)
  .settings(aggregateSettings)
  .aggregate(jdkHttp, http4s, sttp)

lazy val cats = project
  .in(file("runtime/cats"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-cats",
    libraryDependencies ++= Dependencies.Cats.all,
  )
  .dependsOn(core, testkit % "test->test")

lazy val zio = project
  .in(file("runtime/zio"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-zio",
    libraryDependencies ++= Dependencies.Zio.all,
  )
  .dependsOn(core, testkit % "test->test")

lazy val kyo = project
  .in(file("runtime/kyo"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-kyo",
    libraryDependencies ++= Dependencies.Kyo.all,
  )
  .dependsOn(core, testkit % "test->test")

lazy val rapid = project
  .in(file("runtime/rapid"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-rapid",
    libraryDependencies ++= Dependencies.Rapid.all,
  )
  .dependsOn(core, testkit % "test->test")

lazy val runtime = project
  .in(file("runtime"))
  .settings(commonSettings)
  .settings(aggregateSettings)
  .aggregate(cats, zio, kyo, rapid)

lazy val circe = project
  .in(file("json/circe"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-circe",
    libraryDependencies ++= Dependencies.Json.circe,
  )
  .dependsOn(core, testkit % "test->test")

lazy val zioJson = project
  .in(file("json/zio"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-zio-json",
    libraryDependencies += Dependencies.Json.zioJson,
  )
  .dependsOn(core, testkit % "test->test")

lazy val jsoniter = project
  .in(file("json/jsoniter"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-jsoniter",
    libraryDependencies ++= Dependencies.Json.jsoniter,
  )
  .dependsOn(core, testkit % "test->test")

lazy val json = project
  .in(file("json"))
  .settings(commonSettings)
  .settings(aggregateSettings)
  .aggregate(circe, zioJson, jsoniter)

lazy val testkit = project
  .in(file("testkit"))
  .settings(commonSettings)
  .settings(
    name := "oauth4s-testkit"
  )
  .dependsOn(core, jwt)

lazy val examples = project
  .in(file("examples"))
  .settings(commonSettings)
  .settings(aggregateSettings)
  .settings(
    name       := "oauth4s-examples",
    run / fork := true,
  )
  .dependsOn(cats, zio, jdkHttp, circe, oidc, providers, testkit)

lazy val it = project
  .in(file("it"))
  .settings(commonSettings)
  .settings(aggregateSettings)
  .settings(
    name              := "oauth4s-it",
    scalaVersion      := Versions.scala3Next,
    sourceLevel       := "3.10",
    Test / fork       := true,
    parallelExecution := false,
    Test / javaOptions ++= Seq(
      s"-Doauth4s.version=${version.value}",
      s"-Doauth4s.root=${(ThisBuild / baseDirectory).value}",
      s"-Doauth4s.examples=${(Test / scalaSource).value / "oauth4s" / "it" / "docs"}",
    ),
  )
  .dependsOn(core, cats, zio, jdkHttp, http4s, sttp, circe, oidc, providers, testkit % "compile->compile;test->test")

lazy val benchmarks = project
  .in(file("benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(aggregateSettings)
  .settings(
    name := "oauth4s-benchmarks"
  )
  .dependsOn(core, jwt, circe, testkit)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(aggregateSettings)
  .settings(
    name := "oauth4s"
  )
  .aggregate(core, jwt, oidc, providers, http, runtime, json, testkit, examples, benchmarks)
