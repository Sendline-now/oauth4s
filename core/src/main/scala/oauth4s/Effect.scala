package oauth4s

import java.time.Instant

import scala.annotation.implicitNotFound
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.FiniteDuration

@implicitNotFound(
  "No Effect[${F}]. Import the instances of the runtime you use: `oauth4s.runtime.cats.CatsInstances.given` (any F with cats.effect.Async), " +
    "`oauth4s.runtime.zio.ZioInstances.given` (zio.Task), `oauth4s.runtime.kyo.KyoInstances.given` (oauth4s.runtime.kyo.KIO) or " +
    "`oauth4s.runtime.rapid.RapidInstances.given` (rapid.Task). scala.util.Try needs no import and blocks the calling thread."
)
trait Effect[F[*]]:
  def pure[A](a: A): F[A]
  def delay[A](a: => A): F[A]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def raiseError[A](error: Throwable): F[A]
  def handleErrorWith[A](fa: F[A])(f: Throwable => F[A]): F[A]
  def sleep(duration: FiniteDuration): F[Unit]

  def realTime: F[Instant]       = delay(Instant.now())
  def blocking[A](a: => A): F[A] = delay(a)

  def suspend[A](fa: => F[A]): F[A] = flatMap(delay(()))(_ => fa)
  def unit: F[Unit]                 = pure(())
  def void[A](fa: F[A]): F[Unit]    = map(fa)(_ => ())

  def attempt[A](fa: F[A]): F[Either[Throwable, A]] =
    handleErrorWith(
      map(fa) { a =>
        Right(a): Either[Throwable, A]
      }
    )(error => pure(Left(error)))

object Effect:
  inline def apply[F[*]](using F: Effect[F]): F.type = F

  def fromEither[F[*], A](either: Either[Throwable, A])(using F: Effect[F]): F[A] =
    either.fold(F.raiseError, F.pure)

  given blocking: Effect[Try] = new Effect[Try]:
    def pure[A](a: A): Try[A]                                          = Success(a)
    def delay[A](a: => A): Try[A]                                      = Try(a)
    def map[A, B](fa: Try[A])(f: A => B): Try[B]                       = fa.map(f)
    def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B]              = fa.flatMap(f)
    def raiseError[A](error: Throwable): Try[A]                        = Failure(error)
    def handleErrorWith[A](fa: Try[A])(f: Throwable => Try[A]): Try[A] = fa.recoverWith { case error => f(error) }
    def sleep(duration: FiniteDuration): Try[Unit]                     = Try(Thread.sleep(duration.toMillis))

    override def suspend[A](fa: => Try[A]): Try[A] = Try(fa).flatten
