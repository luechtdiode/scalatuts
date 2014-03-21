package ch.seidel.pdm.client

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Undefined
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorSelection.toScala
import akka.actor.Address
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import ch.seidel.akka.Log4JLogging
import ch.seidel.pdm.PDMPattern.GiveWork
import ch.seidel.pdm.PDMPattern.NowWorking
import ch.seidel.pdm.PDMPattern.RegisterAck
import ch.seidel.pdm.PDMPattern.RegisterWorker
import ch.seidel.pdm.PDMPattern.Subscription
import ch.seidel.pdm.PDMPattern.Work
import ch.seidel.pdm.PDMPattern.WorkAvailable

object ChangeWorker {
  def props(init: Subscription, workerMethod: WorkerMethod[Work], contacts: Seq[Address]): Props = Props(classOf[ChangeWorker], init, workerMethod, contacts)
}

/**
 * Example-Implementation of the client-side ChangeWorker
 */
class ChangeWorker(init: Subscription, workerMethod: WorkerMethod[Work], contacts: Seq[Address]) extends Actor with Log4JLogging {
  var working = false
  var connected = false
  
  override def preStart {
    tryConnectToSubscription
  }
  
  override def postStop {
    logger.info("is stopping")
  }

  def wrappedReceive = {
    case WorkAvailable =>
      sender ! GiveWork

    case work: Work =>
      working = true
      val repl = sender
      doWork(work) onComplete { 
        case _ =>
          working = false
          repl ! GiveWork
      }
      sender ! NowWorking(work.seqId)

    case ReceiveTimeout =>
      logger.info("ReceiveTimeout received.")
      tryConnectToSubscription
      
    case Terminated(actor) =>
      connected = false
      context.stop(context.unwatch(actor))
      logger.info(actor + " terminated.")
      tryConnectToSubscription
  }
  
  def connecting: Receive = {
    case RegisterAck =>
      logger.info("connect acked from " + sender.path.address)
      context.setReceiveTimeout(Undefined)
      connected = true
      context.watch(sender)
      sender ! GiveWork
      context.become(receive)
      
    case ReceiveTimeout =>
      logger.info("ReceiveTimeout received.")
      tryConnectToSubscription
  }
  
  def tryConnectToSubscription: Unit = {
    if(!connected) {
      context.setReceiveTimeout(10.seconds)
      for(address <- contacts) {
        val path = RootActorPath(address) / "user" / "publisher" / s"subscr-${init.aboId}"
        logger.info(s"  (re)try to connect with ${path} ...")
        context.system.actorSelection(path) ! RegisterWorker(self)
      }
      context.become(connecting)
    }
    else {
      context.become(receive)
    }
  }
  
  def doWork(work: Work): Future[_] = Future {
    workerMethod.doWork(work)
  }
}