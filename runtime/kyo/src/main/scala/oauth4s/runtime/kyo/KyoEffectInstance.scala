package oauth4s.runtime.kyo

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import kyo.*

import oauth4s.Effect

trait KyoEffectInstance:

  given kyoEffect: Effect[KIO] = new Effect[KIO]:
    def pure[A](a: A): KIO[A]                                          = a
    def delay[A](a: => A): KIO[A]                                      = Sync.defer(Abort.catching[Throwable](a))
    def map[A, B](fa: KIO[A])(f: A => B): KIO[B]                       = fa.map(a => Abort.catching[Throwable](f(a)))
    def flatMap[A, B](fa: KIO[A])(f: A => KIO[B]): KIO[B]              = fa.map(a => Abort.catching[Throwable](f(a)))
    def raiseError[A](error: Throwable): KIO[A]                        = Abort.fail(error)
    def handleErrorWith[A](fa: KIO[A])(f: Throwable => KIO[A]): KIO[A] = Abort.recover[Throwable](f, f)(fa)
    def sleep(duration: FiniteDuration): KIO[Unit]                     = Async.sleep(Duration.fromScala(duration))

    override def realTime: KIO[Instant]            = Clock.now.map(_.toJava)
    override def suspend[A](fa: => KIO[A]): KIO[A] = Sync.defer(fa)

object KyoEffectInstance extends KyoEffectInstance
