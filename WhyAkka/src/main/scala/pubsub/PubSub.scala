package pubsub

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

object PubSub extends App {
  import common.MessageProtocol._  
  import common.Pub
  
  val system = ActorSystem("PubSub")
  
  val topic = system.actorOf(Topic.props, "Topic")
  
  val westpub = system.actorOf(Pub.props("west", topic, 2 seconds))
  val ostpub = system.actorOf(Pub.props("ost", topic, 5 seconds))
  
  val sub = system.actorOf(Sub.props, "Sub")
  
  topic ! Subscribe(sub)
  
}