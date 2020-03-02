package example.sangria.subsription

import akka.util.Timeout
import example.sangria.subsription.Protocol.{Event, PriceTick}
import sangria.macros.derive._
import sangria.marshalling.FromInput
import sangria.schema._
import sangria.util.tag.@@

import scala.concurrent.ExecutionContext

class PriceSchemaContainer(implicit timeout: Timeout, ec: ExecutionContext) extends SubscriptionSchemaContainer {

  val AuthorCreatedType: ObjectType[Any, PriceTick] = deriveObjectType[Any, PriceTick]()

  val TickersArg: Argument[Seq[String @@ FromInput.CoercedScalaResult]] = Argument("tickers", ListInputType(StringType))

  def subscriptionFieldName(event: Event): Option[String] = event match {
    case _: PriceTick => Some("priceUpdate")
    case _            => None
  }

  override def filter(event: Event, args: Args): Boolean = event match {
    case tick: PriceTick if args.arg(TickersArg).contains(tick.ticker) => true
    case _                                                             => false
  }

  val schema: Schema[Any, Any] = {

    val QueryType = ObjectType(
      "Query",
      fields[Any, Any](
        Field("foo", StringType, resolve = _ => "bar")
      )
    )

    val SubscriptionType: ObjectType[Any, Any] = ObjectType(
      "Subscription",
      List(
        Field(
          "priceUpdate",
          OptionType(AuthorCreatedType),
          arguments = TickersArg :: Nil,
          resolve = (ctx: Context[Any, Any]) => Some(ctx.value.asInstanceOf[PriceTick])
        )
      )
    )

    Schema(QueryType, subscription = Some(SubscriptionType))
  }
}
