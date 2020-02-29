package example.sangria.subsription.generic

import akka.actor.Actor
import example.sangria.subsription.{Author, AuthorCreated}

class AuthorView extends Actor {

  def receive: PartialFunction[Any, Unit] = {
    case event: AuthorCreated =>
      Author(event.firstName, event.lastName)
  }
}
