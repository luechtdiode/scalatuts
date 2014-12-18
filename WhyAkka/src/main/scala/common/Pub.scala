package common

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

class Pub(origin: String, target: ActorRef, interval: FiniteDuration) extends Actor {
  import common.MessageProtocol._
 
  override def preStart = {
    context.system.scheduler.schedule(interval, interval, self, "tick")
  }
  
  override def receive = {
    case "tick" => target ! NewsMessage(s"new information from $origin")
  }
}

object Pub {
  def props(origin: String, target: ActorRef, interval: FiniteDuration) = 
    Props(classOf[Pub], origin, target, interval)
}