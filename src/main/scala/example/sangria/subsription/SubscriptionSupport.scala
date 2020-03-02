package example.sangria.subsription

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.model.ws._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.util.Timeout
import spray.json._

import scala.util._

trait SubscriptionSupport {

  import SubscriptionActor._

  def graphQlSubscriptionSocket(publisher: ActorRef, schemaContainer: SubscriptionSchemaContainer)(
    implicit system: ActorSystem,
    materializer: ActorMaterializer,
    timeout: Timeout
  ): Flow[Message, TextMessage.Strict, NotUsed] = {

    val subscriptionActor = system.actorOf(Props(new SubscriptionActor(publisher, schemaContainer)))

    // Transform any incoming messages into Subscribe messages and let the subscription actor know about it
    val incoming: Sink[Message, NotUsed] =
      Flow[Message]
        .collect {
          case TextMessage.Strict(input) =>
            Try(input.parseJson.convertTo[Subscribe])
        }
        .collect { case Success(subscription) => subscription }
        .to(Sink.actorRef[Subscribe](subscriptionActor, PoisonPill))

    // connect the subscription actor with the outgoing WebSocket actor and transform a result into a WebSocket message.
    val outgoing: Source[TextMessage.Strict, NotUsed] =
      Source
        .actorRef[QueryResult](10, OverflowStrategy.fail)
        .mapMaterializedValue { outputActor =>
          subscriptionActor ! Connected(outputActor)
          NotUsed
        }
        .map { msg: SubscriptionMessage =>
          msg match {
            case result: QueryResult     => TextMessage(result.json.compactPrint)
            case _: SubscriptionAccepted => TextMessage("subscription accepted.")
          }
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }

}
