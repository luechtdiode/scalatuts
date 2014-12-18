package common

import akka.actor.ActorRef

object MessageProtocol {
  case class Subscribe(sub: ActorRef)
  case class NewsMessage(news: String)
  case object MessageProcessed
}