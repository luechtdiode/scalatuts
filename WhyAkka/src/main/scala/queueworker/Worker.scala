package queueworker

import akka.actor.Props
import akka.actor.Actor

class Worker extends Actor {
  import common.MessageProtocol._
  
  override def receive = {
    case msg => 
      println(self.path + " received " + msg + " from " + sender.path)
      sender ! MessageProcessed
  }
}

object Worker {
  def props = Props(classOf[Worker])
}