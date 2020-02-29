package example.sangria.subsription


// Model

case class Author(firstName: String, lastName: String)

// events

sealed trait AuthorEvent

case class AuthorCreated(firstName: String, lastName: String) extends AuthorEvent
