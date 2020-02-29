package example.sangria.subsription.generic

import akka.actor.Actor
import akka.stream.scaladsl.SourceQueue
import example.sangria.subsription.{Author, AuthorCreated, AuthorEvent}

class MemoryAuthorStore(queue: SourceQueue[AuthorEvent]) extends Actor {
  import MemoryAuthorStore._

  def receive: Receive = {
    case AddAuthorEvent(event @ AuthorCreated(firstName, lastName)) =>
      queue.offer(event)
      sender() ! AuthorAdded(Author(firstName, lastName))
  }
}

object MemoryAuthorStore {
  case class AddAuthorEvent(event: AuthorEvent)
  case class AuthorAdded(author: Author)

}