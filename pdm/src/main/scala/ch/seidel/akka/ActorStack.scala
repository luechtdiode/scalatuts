package ch.seidel.akka

import akka.actor.Actor

trait ActorStack extends Actor {
  def wrappedReceive: Receive
  
  def receive: Receive = {
    case x => if (wrappedReceive.isDefinedAt(x)) wrappedReceive(x) else unhandled(x)
  }
}