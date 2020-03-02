package example.sangria.subsription

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import example.sangria.subsription.Protocol.{Event, PriceTick}
import org.reactivestreams.Publisher
import sangria.ast.OperationType
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.sprayJson._
import sangria.parser.{QueryParser, SyntaxError}
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

object Server extends App with SubscriptionSupport {
  implicit val system: ActorSystem = ActorSystem("server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val logger = Logging(system, getClass)

  import system.dispatcher

  implicit val timeout: Timeout = Timeout(10 seconds)

  val (queue, eventStorePublisher): (SourceQueueWithComplete[Event], Publisher[Event]) =
    Source
      .queue[Event](10000, OverflowStrategy.backpressure)
      .toMat(Sink.asPublisher[Event](fanout = true))(Keep.both)
      .run()

  //Create a data source that generates some random streaming data
  Source
    .tick(0.second, 2.seconds, NotUsed)
    .mapConcat { _ =>
      val aaplMid = 100 + 5 * Random.nextDouble()
      val aaplSpread = 5 * Random.nextDouble()

      val ibmMid = 115 + 7 * Random.nextDouble()
      val ibmSpread = 4 * Random.nextDouble()

      val msftMid = 95 + 6 * Random.nextDouble()
      val msftSpread = 3 * Random.nextDouble()
      List(
        PriceTick("AAPL", aaplMid - aaplSpread, aaplMid + aaplSpread),
        PriceTick("IBM", ibmMid - ibmSpread, ibmMid + ibmSpread),
        PriceTick("MSFT", msftMid - msftSpread, msftMid + msftSpread),
      )
    }
    .runForeach(queue.offer)

  val subscriptionEventPublisher = system.actorOf(Props(new SubscriptionEventPublisher(eventStorePublisher)))

  val schemaContainer = new PriceSchemaContainer()

  val executor = Executor(schemaContainer.schema)

  def executeQuery(query: String, operation: Option[String], variables: JsObject = JsObject.empty) =
    QueryParser.parse(query) match {
      case Success(queryAst) =>
        queryAst.operationType(operation) match {

          case Some(OperationType.Subscription) =>
            complete(
              ToResponseMarshallable(BadRequest -> JsString("Subscriptions not supported via HTTP. Use WebSockets"))
            )

          // all other queries will just return normal JSON response
          case _ =>
            complete(
              executor
                .execute(queryAst, (), (), operation, variables)
                .map(OK -> _)
                .recover {
                  case error: QueryAnalysisError => BadRequest -> error.resolveError
                  case error: ErrorWithResolver  => InternalServerError -> error.resolveError
                }
            )
        }

      case Failure(error: SyntaxError) =>
        complete(
          ToResponseMarshallable(
            BadRequest -> JsObject(
              "syntaxError" -> JsString(error.getMessage),
              "locations" -> JsArray(
                JsObject(
                  "line" -> JsNumber(error.originalError.position.line),
                  "column" -> JsNumber(error.originalError.position.column)
                )
              )
            )
          )
        )

      case Failure(error) =>
        complete(ToResponseMarshallable(InternalServerError -> JsString(error.getMessage)))
    }

  val route: Route =
    path("graphql") {
      post {
        entity(as[JsValue]) { requestJson =>
          val JsObject(fields) = requestJson

          val JsString(query) = fields("query")

          val operation = fields.get("operationName").collect {
            case JsString(op) => op
          }

          val vars = fields.get("variables") match {
            case Some(obj: JsObject) => obj
            case _                   => JsObject.empty
          }

          executeQuery(query, operation, vars)
        }
      } ~
        get(handleWebSocketMessages(graphQlSubscriptionSocket(subscriptionEventPublisher, schemaContainer)))
    } ~
      (get & path("client")) {
        getFromResource("web/client.html")
      } ~
      get {
        getFromResource("web/graphiql.html")
      }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
