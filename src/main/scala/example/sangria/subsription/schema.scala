package example.sangria.subsription


import akka.util.Timeout
import sangria.execution.UserFacingError
import sangria.macros.derive._
import sangria.schema._

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag


object schema {
  case class MutationError(message: String) extends Exception(message) with UserFacingError
  case class SubscriptionField[+T : ClassTag](tpe: ObjectType[Ctx, T @uncheckedVariance]) {
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

  def createSchema(implicit timeout: Timeout, ec: ExecutionContext): Schema[Ctx, Any] = {

    implicit val AuthorType: ObjectType[Unit, Author] = deriveObjectType[Unit, Author]()


    val QueryType = ObjectType("Query",fields[Ctx, Any](
      Field("foo", StringType, resolve = _ => "Foo Bar")
    ))

    val MutationType = deriveContextObjectType[Ctx, Mutation, Any](identity)

    val SubscriptionType = ObjectType("Subscription",
      SubscriptionFields.toList.map { case (name, field) =>
        Field(name, OptionType(field.tpe), resolve = (c: Context[Ctx, Any]) => field.value(c.value))
      })

    Schema(QueryType, Some(MutationType), Some(SubscriptionType))
  }
}
