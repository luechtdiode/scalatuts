package queueworker

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSelection.toScala

object ClusterWorkers extends App {
  import common.MessageProtocol._  

  val system = ActorSystem("PubSub", 
      ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load.getConfig("subscriber")))

  val queueRef = system.actorSelection("akka.tcp://PubSub@127.0.0.1:2551/user/Queue")
  
  queueRef ! Subscribe( system.actorOf(Worker.props, "Worker1"))
  queueRef ! Subscribe( system.actorOf(Worker.props, "Worker2"))
  queueRef ! Subscribe( system.actorOf(Worker.props, "Worker3"))
  
}