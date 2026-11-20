import sbt.*

object Dependencies {

  object Versions {
    val scala3     = "3.9.0"
    val scala3Next = "3.10.1"

    val catsEffect        = "3.7.1"
    val catsEffectTesting = "1.8.0"

    val zio = "2.1.27"

    val kyo = "1.0.0-RC7"

    val rapid = "2.9.10"

    val http4s = "0.23.39"

    val sttp = "4.1.3"

    val circe    = "0.14.17"
    val zioJson  = "1.1.3"
    val jsoniter = "2.43.0"

    val scalatest = "3.2.20"
  }

  object Cats {
    val catsEffect        = "org.typelevel" %% "cats-effect"                   % Versions.catsEffect
    val catsEffectTesting = "org.typelevel" %% "cats-effect-testing-scalatest" % Versions.catsEffectTesting % Test

    val all: Seq[ModuleID] = Seq(catsEffect, catsEffectTesting)
  }

  object Zio {
    val zio = "dev.zio" %% "zio" % Versions.zio

    val all: Seq[ModuleID] = Seq(zio)
  }

  object Kyo {
    val kyoCore = ("io.getkyo" %% "kyo-core" % Versions.kyo).excludeAll(ExclusionRule("org.openjdk.jmh"))

    val all: Seq[ModuleID] = Seq(kyoCore)
  }

  object Rapid {
    val rapidCore = "com.outr" %% "rapid-core" % Versions.rapid

    val all: Seq[ModuleID] = Seq(rapidCore)
  }

  object Http4s {
    val client = "org.http4s" %% "http4s-client" % Versions.http4s

    val all: Seq[ModuleID] = Seq(client)
  }

  object Sttp {
    val core = "com.softwaremill.sttp.client4" %% "core" % Versions.sttp

    val all: Seq[ModuleID] = Seq(core)
  }

  object Json {
    val circe: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-parser",
    ).map(_ % Versions.circe)

    val zioJson = "dev.zio" %% "zio-json" % Versions.zioJson

    val jsoniter: Seq[ModuleID] = Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % Versions.jsoniter,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Versions.jsoniter % Test,
    )
  }

  object Testing {
    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
    val scalactic = "org.scalactic" %% "scalactic" % Versions.scalatest % Test

    val all: Seq[ModuleID] = Seq(scalatest, scalactic)
  }

}
