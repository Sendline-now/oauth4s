package oauth4s.runtime.zio

import java.time.Instant

import scala.util.control.NonFatal
import scala.concurrent.duration.FiniteDuration

import zio.{Clock, Duration, Exit, Task, ZIO}

import oauth4s.Effect

trait TaskToEffectInstance:
  given taskEffect: Effect[Task] = new Effect[Task]:
    def pure[A](a: A): Task[A]                               = ZIO.succeed(a)
    def delay[A](a: => A): Task[A]                           = ZIO.attempt(a)
    def map[A, B](fa: Task[A])(f: A => B): Task[B]           = fa.flatMap(a => TaskToEffectInstance.failing(Exit.succeed(f(a))))
    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(a => TaskToEffectInstance.failing(f(a)))
    def raiseError[A](error: Throwable): Task[A]             = ZIO.fail(error)
    def sleep(duration: FiniteDuration): Task[Unit]          = ZIO.sleep(Duration.fromScala(duration))

    def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] = fa.catchAll(error => TaskToEffectInstance.failing(f(error)))

    override def realTime: Task[Instant]             = Clock.instant
    override def blocking[A](a: => A): Task[A]       = ZIO.attemptBlocking(a)
    override def suspend[A](fa: => Task[A]): Task[A] = ZIO.suspend(fa)

object TaskToEffectInstance extends TaskToEffectInstance:
  private inline def failing[A](inline task: Task[A]): Task[A] =
    try task
    catch case NonFatal(error) => Exit.fail(error)
