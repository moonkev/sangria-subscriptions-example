package example.sangria.subsription


import akka.util.Timeout
import example.sangria.subsription.AuthorActor.{Author, AuthorCreated, AuthorEvent}
import sangria.macros.derive._
import sangria.schema._

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag


object schema {
  case class SubscriptionField[+T : ClassTag](tpe: ObjectType[Any, T @uncheckedVariance]) {
    lazy val clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

    def value(v: Any): Option[T] = v match {
      case value if clazz.isAssignableFrom(value.getClass) => Some(value.asInstanceOf[T])
      case _ => None
    }
  }


  val AuthorCreatedType: ObjectType[Unit, AuthorCreated] = deriveObjectType[Unit, AuthorCreated]()

  val SubscriptionFields: ListMap[String, SubscriptionField[AuthorEvent]] = ListMap[String, SubscriptionField[AuthorEvent]](
    "authorCreated" -> SubscriptionField(AuthorCreatedType))

  def subscriptionFieldName(event: AuthorEvent): Option[String] =
    SubscriptionFields.find(_._2.clazz.isAssignableFrom(event.getClass)).map(_._1)

  def createSchema(implicit timeout: Timeout, ec: ExecutionContext): Schema[Any, Any] = {

    implicit val AuthorType: ObjectType[Unit, Author] = deriveObjectType[Unit, Author]()


    val QueryType = ObjectType("Query",fields[Any, Any](
      Field("foo", StringType, resolve = _ => "Foo Bar")
    ))

    val SubscriptionType = ObjectType("Subscription",
      SubscriptionFields.toList.map { case (name, field) =>
        Field(name, OptionType(field.tpe), resolve = (c: Context[Any, Any]) => field.value(c.value))
      })

    Schema(QueryType, subscription = Some(SubscriptionType))
  }
}
