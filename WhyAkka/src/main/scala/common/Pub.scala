package common

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

class Pub(origin: String, target: ActorRef, interval: FiniteDuration) extends Actor {
  import common.MessageProtocol._
  var counter = 0
  
  override def preStart = {
    context.system.scheduler.schedule(interval, interval, self, "tick")
  }
  
  override def receive = {
    case "tick" => 
      target ! NewsMessage(s"information #$counter from $origin")
      counter += 1
  }
}

object Pub {
  def props(origin: String, target: ActorRef, interval: FiniteDuration) = 
    Props(classOf[Pub], origin, target, interval)
}