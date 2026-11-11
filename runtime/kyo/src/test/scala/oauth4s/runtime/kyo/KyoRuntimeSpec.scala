package oauth4s.runtime.kyo

import kyo.{Duration, KyoApp}

import oauth4s.testkit.RuntimeSuite

import kyo.AllowUnsafe.embrace.danger
import oauth4s.runtime.kyo.KyoInstances.given

final class KyoRuntimeSpec extends RuntimeSuite[KIO]("kyo"):
  protected def unsafeRun[A](fa: KIO[A]): A = KyoApp.Unsafe.runAndBlock(Duration.Infinity)(fa).getOrThrow
