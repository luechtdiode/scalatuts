package pubsub

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated

class Topic extends Actor {
  import common.MessageProtocol._
  
  // private state
  var subscribers = Set[ActorRef]()
  
  override def receive = {
    
    case Subscribe(sub) => 
      subscribers = subscribers + sub
      context.watch(sub)
      println("new subscriber registered: " + sub + ", " + subscribers)
      
    case msg: NewsMessage => 
      subscribers foreach(_ ! msg)
      println("new message published from: " + sender.path)
      
    case Terminated(sub) =>
      subscribers = subscribers - sub
      context.unwatch(sub)
      println("subscriber terminated: " + sub)
  }
}

object Topic {
  def props = Props(classOf[Topic])
}