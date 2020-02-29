package example.sangria.subsription

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.Timeout
import example.sangria.subsription.AuthorActor.AuthorEvent
import sangria.ast.OperationType
import sangria.execution.{Executor, PreparedQuery}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
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

class SubscriptionActor(publisher: ActorRef)(implicit timeout: Timeout) extends Actor with ActorLogging {

  import SubscriptionActor._

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  val executor: Executor[Any, Any] = Executor(schema.createSchema)
  var subscriptions: Map[String, Set[PreparedQueryContext]] = Map.empty

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
      context.query.fields.map(_.field.name).foreach { field =>
        subscriptions = subscriptions.updated(field, subscriptions.get(field) match {
          case Some(contexts) => contexts + context
          case _ => Set(context)
        })
      }

    case event: AuthorEvent =>
      val fieldName = schema.subscriptionFieldName(event)
      queryContextsFor(fieldName) foreach { ctx =>
        ctx.query.execute(root = event) map { result =>
          outgoing ! QueryResult(result)
        }
      }
  }

  def queryContextsFor(fieldName: Option[String]): Set[PreparedQueryContext] = fieldName match {
    case Some(name) => subscriptions.getOrElse(name, Set.empty[PreparedQueryContext])
    case _ => Set.empty[PreparedQueryContext]
  }

  def prepareQuery(subscription: Subscribe): Unit = {
    QueryParser.parse(subscription.query) match {
      case Success(ast) =>
        ast.operationType(subscription.operation) match {
          case Some(OperationType.Subscription) =>
            executor.prepare(ast, (), (), subscription.operation, JsObject.empty).map {
              query => self ! PreparedQueryContext(query)
            }
          case x =>
            log.warning(s"OperationType: $x not supported with WebSockets. Use HTTP POST")
        }

      case Failure(e) =>
        log.warning(e.getMessage)
    }
  }
}
