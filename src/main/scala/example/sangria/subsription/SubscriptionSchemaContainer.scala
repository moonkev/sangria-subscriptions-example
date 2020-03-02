package example.sangria.subsription

import example.sangria.subsription.Protocol.Event
import sangria.schema.{Args, Schema}

trait SubscriptionSchemaContainer {

  def schema: Schema[Any, Any]

  def subscriptionFieldName(event: Event): Option[String]

  def filter(event: Event, args: Args): Boolean
}
