package queueworker

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

object Producer extends App {
  import common.MessageProtocol._  
  import common.Pub
  
  val system = ActorSystem("PubSub")
  
  val queue = system.actorOf(Queue.props, "Queue")
  
  val westpub = system.actorOf(Pub.props("west", queue, 2 seconds))
  val ostpub = system.actorOf(Pub.props("ost", queue, 5 seconds))
  
  queue ! Subscribe( system.actorOf(Worker.props, "Worker1"))
  queue ! Subscribe( system.actorOf(Worker.props, "Worker2"))
  queue ! Subscribe( system.actorOf(Worker.props, "Worker3"))
  
}