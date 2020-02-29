package example.sangria.subsription

import sangria.macros.derive.GraphQLField

import scala.concurrent.Future

trait Mutation {
  this: Ctx =>

  @GraphQLField
  def createAuthor(firstName: String, lastName: String): Future[Option[Author]] =
    addEvent(authors, AuthorCreated(firstName, lastName))
}
