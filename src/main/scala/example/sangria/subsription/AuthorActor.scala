package example.sangria.subsription

import akka.actor.Actor
import akka.stream.scaladsl.SourceQueue
import example.sangria.subsription.AuthorActor.AuthorEvent

class AuthorActor(queue: SourceQueue[AuthorEvent]) extends Actor {
  import AuthorActor._

  def receive: Receive = {
    case AddAuthorEvent(event @ AuthorCreated(firstName, lastName)) =>
      queue.offer(event)
      sender() ! AuthorAdded(Author(firstName, lastName))
  }
}

object AuthorActor {
  case class AddAuthorEvent(event: AuthorEvent)
  case class AuthorAdded(author: Author)
  case class Author(firstName: String, lastName: String)
  sealed trait AuthorEvent
  case class AuthorCreated(firstName: String, lastName: String) extends AuthorEvent
}