package pubsub

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

object ClusterPub extends App {
  import common.MessageProtocol._  
  import pubsub._
  import common._
  
  val system = ActorSystem("PubSub", 
      ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load.getConfig("publisher")))
  
  val topic = system.actorOf(Topic.props, "Topic")
  
  val westpub = system.actorOf(Pub.props("west", topic, 2 seconds))
  val ostpub = system.actorOf(Pub.props("ost", topic, 5 seconds))
  
}