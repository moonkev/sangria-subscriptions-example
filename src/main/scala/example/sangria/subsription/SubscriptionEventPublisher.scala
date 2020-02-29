package example.sangria.subsription

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import example.sangria.subsription.AuthorActor.AuthorEvent
import org.reactivestreams.Publisher

object SubscriptionEventPublisher {
  case object Join
}
class SubscriptionEventPublisher(publisher: Publisher[AuthorEvent]) extends Actor with ActorLogging {

  import SubscriptionEventPublisher._

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  var subscribers: Set[ActorRef] = Set.empty

  Source.fromPublisher(publisher)
    .buffer(100, OverflowStrategy.fail)
    .to(Sink.foreach(e => subscribers.foreach(_ ! e)))
    .run()

  def receive: Receive = {
    case Join =>
      log.info(s"${sender()} joined.")
      subscribers += sender()
      context.watch(sender())

    case Terminated(subscriber) =>
      log.info(s"${sender()} was terminated.")
      subscribers -= subscriber
  }
}
