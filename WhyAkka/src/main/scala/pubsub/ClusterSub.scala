package pubsub

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSelection.toScala

object ClusterSub extends App {
  import common.MessageProtocol._  
  
  val system = ActorSystem("PubSub", 
      ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load.getConfig("subscriber")))

  
  val topicRef = system.actorSelection("akka.tcp://PubSub@127.0.0.1:2551/user/Topic")
  val sub = system.actorOf(Sub.props, "Sub")
  
  topicRef ! Subscribe(sub)
}