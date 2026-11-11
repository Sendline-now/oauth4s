package oauth4s.runtime.rapid

import rapid.Task

import oauth4s.testkit.RuntimeSuite

import oauth4s.runtime.rapid.RapidInstances.given

final class RapidRuntimeSpec extends RuntimeSuite[Task]("rapid Task"):
  protected def unsafeRun[A](fa: Task[A]): A = fa.sync()
