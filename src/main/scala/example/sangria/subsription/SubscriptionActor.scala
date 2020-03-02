package example.sangria.subsription

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.Timeout
import example.sangria.subsription.Protocol.Event
import sangria.ast.OperationType
import sangria.execution.{Executor, PreparedQuery}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.schema.Args
import spray.json._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object SubscriptionActor extends DefaultJsonProtocol {
  sealed trait SubscriptionMessage
  case class Subscribe(query: String, operation: Option[String])
  case class SubscriptionAccepted() extends SubscriptionMessage
  case class QueryResult(json: JsValue) extends SubscriptionMessage
  case class Connected(outgoing: ActorRef)
  case class PreparedQueryContext(query: PreparedQuery[Any, Any, JsObject])

  implicit val subscribeProtocol: RootJsonFormat[Subscribe] = jsonFormat2(Subscribe)
}

class SubscriptionActor(publisher: ActorRef, schemaContainer: SubscriptionSchemaContainer)(implicit timeout: Timeout)
    extends Actor
    with ActorLogging {

  import SubscriptionActor._

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  val executor: Executor[Any, Any] = Executor(schemaContainer.schema)
  var subscriptions: Map[String, Set[(PreparedQueryContext, Args)]] = Map.empty

  override def receive: Receive = {
    case Connected(outgoing) =>
      publisher ! SubscriptionEventPublisher.Join
      context.become(connected(outgoing))
  }

  def connected(outgoing: ActorRef): Receive = {
    case e: Subscribe =>
      log.info(s"Got sub: $e")
      prepareQuery(e)

    case context: PreparedQueryContext =>
      log.info(s"Query is prepared: $context")
      outgoing ! SubscriptionAccepted()
      context.query.fields.foreach { field =>
        subscriptions = subscriptions.updated(field.field.name, subscriptions.get(field.field.name) match {
          case Some(contexts) => contexts + (context -> field.args)
          case _              => Set(context -> field.args)
        })
      }

    case event: Event =>
      for {
        fieldName <- schemaContainer.subscriptionFieldName(event)
        contextSet <- subscriptions.get(fieldName)
        (ctx, args) <- contextSet if schemaContainer.filter(event, args)
      } {
        ctx.query.execute(root = event).map { result =>
          outgoing ! QueryResult(result)
        }
      }
  }

  def prepareQuery(subscription: Subscribe): Unit = {
    QueryParser.parse(subscription.query) match {
      case Success(ast) =>
        ast.operationType(subscription.operation) match {
          case Some(OperationType.Subscription) =>
            executor.prepare(ast, (), (), subscription.operation, JsObject.empty).map { query =>
              self ! PreparedQueryContext(query)
            }
          case x =>
            log.warning(s"OperationType: $x not supported with WebSockets. Use HTTP POST")
        }

      case Failure(e) =>
        log.warning(e.getMessage)
    }
  }
}
