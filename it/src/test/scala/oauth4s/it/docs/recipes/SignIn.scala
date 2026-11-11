package oauth4s.it.docs.recipes.signin

import cats.effect.{IO, Ref}

import oauth4s.providers.{Login, Provider}
import oauth4s.{AuthorizationCallback, AuthorizationFlow, RedirectUri, State}

final class SignIn(provider: Provider[IO], redirect: RedirectUri, pending: Ref[IO, Map[State, AuthorizationFlow]]):

  def begin: IO[String] =
    provider.authorize(redirect).flatMap(flow => pending.update(_ + (flow.state -> flow)).as(flow.url))

  def finish(callback: AuthorizationCallback): IO[Login] =
    val waiting = callback.state.fold(IO.none[AuthorizationFlow])(state => pending.modify(flows => (flows - state, flows.get(state))))

    waiting.flatMap {
      case Some(flow) => provider.login(flow, callback)
      case None       => IO.raiseError(IllegalStateException("no sign-in is waiting for this callback"))
    }

object SignIn:
  def of(provider: Provider[IO], redirect: RedirectUri): IO[SignIn] =
    Ref.of[IO, Map[State, AuthorizationFlow]](Map.empty).map(SignIn(provider, redirect, _))
