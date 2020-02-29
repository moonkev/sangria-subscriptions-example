package example.sangria.subsription

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import example.sangria.subsription.generic.MemoryAuthorStore._

import scala.concurrent.{ExecutionContext, Future}

case class Ctx(authors: ActorRef, eventStore: ActorRef, ec: ExecutionContext, to: Timeout) extends Mutation {
  implicit def executionContext: ExecutionContext = ec
  implicit def timeout: Timeout = to

  def addEvent(view: ActorRef, event: AuthorCreated): Future[Option[Author]] =
    (eventStore ? AddAuthorEvent(event)).map {
      case AuthorAdded(author) => Some(author)
    }
}
