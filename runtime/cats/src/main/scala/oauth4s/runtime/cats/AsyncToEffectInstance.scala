package oauth4s.runtime.cats

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import cats.effect.kernel.Async

import oauth4s.Effect

trait AsyncToEffectInstance:
  given asyncEffect: [F[*]] => (F: Async[F]) => Effect[F] = new Effect[F]:
    def pure[A](a: A): F[A]                                      = F.pure(a)
    def delay[A](a: => A): F[A]                                  = F.delay(a)
    def map[A, B](fa: F[A])(f: A => B): F[B]                     = F.map(fa)(f)
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]              = F.flatMap(fa)(f)
    def raiseError[A](error: Throwable): F[A]                    = F.raiseError(error)
    def handleErrorWith[A](fa: F[A])(f: Throwable => F[A]): F[A] = F.handleErrorWith(fa)(f)
    def sleep(duration: FiniteDuration): F[Unit]                 = F.sleep(duration)

    override def realTime: F[Instant]                          = F.realTimeInstant
    override def blocking[A](a: => A): F[A]                    = F.blocking(a)
    override def suspend[A](fa: => F[A]): F[A]                 = F.defer(fa)
    override def attempt[A](fa: F[A]): F[Either[Throwable, A]] = F.attempt(fa)

object AsyncToEffectInstance extends AsyncToEffectInstance
