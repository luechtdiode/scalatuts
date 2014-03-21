package ch.seidel.pdm.client

import scala.concurrent.duration.Duration.Undefined
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorRef
import akka.actor.ActorSelection.toScala
import akka.actor.Address
import akka.actor.Identify
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import ch.seidel.akka.Log4JLogging
import ch.seidel.pdm.PDMPattern.AlreadySubscribed
import ch.seidel.pdm.PDMPattern.Message
import ch.seidel.pdm.PDMPattern.Subscribe
import ch.seidel.pdm.PDMPattern.Subscription

object SubscriptionClientActor {
  def props(contacts: Set[Address]): Props = Props(classOf[SubscriptionClientActor], contacts)
}

/**
 * Implementation of the client-side Subscriber
 */
class SubscriptionClientActor(contacts: Set[Address]) extends Actor with Log4JLogging {
  var connected = false
  var controller: Option[ActorRef] = None
  var publisher: Option[ActorRef] = None
  var pending: List[Any] = List.empty
  
  override def preStart {
    tryConnectToSubscription
  }
  
  override def postStop {
    logger.info("is stopping")
  }
  
  private def repeat(target: ActorRef, msg: Message) = target ! msg
  
  def wrappedReceive = {
    case s: Subscribe => 
      controller = Some(sender)
      publisher foreach (repeat(_, s))
    case s: AlreadySubscribed => controller foreach (repeat(_, s))
    case s: Subscription => controller foreach  (repeat(_, s))
    
    case Terminated(actor) if(publisher.exists(_ == actor)) =>
      connected = false
      publisher = None
      context.stop(context.unwatch(actor))
      logger.info(actor + " terminated.")
      tryConnectToSubscription

    case Terminated(actor) if(controller.exists(_ == actor)) =>
      connected = false
      controller = None
      context.stop(context.unwatch(actor))
      logger.info(actor + " terminated.")
      context.stop(self)
  }
  
  def connecting: Receive = {
    case ActorIdentity(addr: Address, None) =>
      logger.info(s"$addr is not reachable")
      
    case ActorIdentity(addr: Address, pub) =>
      logger.info(s"connect acked from $addr")
      context.setReceiveTimeout(Undefined)
      connected = true
      publisher = pub
      context.watch(sender)
      context.become(receive)
      pending foreach {self ! _}
      pending = List.empty
      
    case ReceiveTimeout =>
      connected = false
      publisher foreach {context.unwatch(_)}
      publisher = None
      logger.info("ReceiveTimeout received.")
      tryConnectToSubscription
      
    case x: Any => pending = pending :+ x
  }
  
  def tryConnectToSubscription: Unit = {
    if(!connected) {
      context.setReceiveTimeout(10.seconds)
      sendIdentifyMsgs
      context.become(connecting)
    }
    else {
      context.become(receive)
    }
  }
  
  def sendIdentifyMsgs {
    for(address <- contacts) {
      logger.info(s"  identifying ${address}")
      context.system.actorSelection(RootActorPath(address) / "user" / "publisher_proxy") ! Identify(address)
    }
  }
}