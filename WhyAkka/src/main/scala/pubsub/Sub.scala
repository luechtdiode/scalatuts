package pubsub

import akka.actor.Props
import akka.actor.Actor

class Sub extends Actor {
  override def receive = {
    case msg => println(msg + " received from " + sender.path)
  }
}

object Sub {
  def props = Props(classOf[Sub])
}