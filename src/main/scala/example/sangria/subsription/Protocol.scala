package example.sangria.subsription

object Protocol {
  trait Event
  case class PriceTick(ticker: String, bidPrice: Double, offerPrice: Double) extends Event
}
