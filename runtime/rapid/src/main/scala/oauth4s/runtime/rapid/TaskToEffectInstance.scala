package oauth4s.runtime.rapid

import scala.concurrent.duration.FiniteDuration

import rapid.Task

import oauth4s.Effect

trait TaskToEffectInstance:

  given rapidEffect: Effect[Task] = new Effect[Task]:
    def pure[A](a: A): Task[A]                                            = Task.pure(a)
    def delay[A](a: => A): Task[A]                                        = Task(a)
    def map[A, B](fa: Task[A])(f: A => B): Task[B]                        = fa.map(f)
    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B]              = fa.flatMap(f)
    def raiseError[A](error: Throwable): Task[A]                          = Task.error(error)
    def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] = fa.handleError(f)
    def sleep(duration: FiniteDuration): Task[Unit]                       = Task.pure(()).sleep(duration)

    override def suspend[A](fa: => Task[A]): Task[A] = Task.pure(()).flatMap(_ => fa)

object TaskToEffectInstance extends TaskToEffectInstance
