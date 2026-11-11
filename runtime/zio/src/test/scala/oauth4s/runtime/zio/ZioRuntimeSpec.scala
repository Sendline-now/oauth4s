package oauth4s.runtime.zio

import zio.{Runtime, Task, Unsafe}

import oauth4s.testkit.RuntimeSuite

import oauth4s.runtime.zio.ZioInstances.given

final class ZioRuntimeSpec extends RuntimeSuite[Task]("zio Task"):
  protected def unsafeRun[A](fa: Task[A]): A = Unsafe.unsafe(unsafe ?=> Runtime.default.unsafe.run(fa).getOrThrow())
