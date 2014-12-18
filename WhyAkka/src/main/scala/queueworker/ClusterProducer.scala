package queueworker

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

object ClusterProducer extends App {
  import common.MessageProtocol._  
  import common.Pub
  
  val system = ActorSystem("PubSub", 
      ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load.getConfig("publisher")))
  
  val queue = system.actorOf(Queue.props, "Queue")
  
  val westpub = system.actorOf(Pub.props("west", queue, 2 seconds))
  val ostpub = system.actorOf(Pub.props("ost", queue, 5 seconds))
  
}