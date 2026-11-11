package oauth4s.runtime.cats

import cats.effect.IO

import oauth4s.testkit.RuntimeSuite

import cats.effect.unsafe.implicits.global
import oauth4s.runtime.cats.CatsInstances.given

final class CatsRuntimeSpec extends RuntimeSuite[IO]("cats IO"):
  protected def unsafeRun[A](fa: IO[A]): A = fa.unsafeRunSync()
