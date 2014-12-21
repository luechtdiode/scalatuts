package pubsub

import akka.actor.Props
import akka.actor.Actor
import akka.remote.Ack

class Sub extends Actor {
  override def receive = {
    case msg => 
      println(msg + " received from " + sender.path)
      sender ! Ack
  }
}

object Sub {
  def props = Props(classOf[Sub])
}