package ch.seidel.pdm.system

import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import scala.collection.mutable
import scala.collection.mutable.Queue
import scala.concurrent.duration.Duration.Undefined
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.SupervisorStrategy.Resume
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import ch.seidel.pdm.PDMPattern.GiveWork
import ch.seidel.pdm.PDMPattern.NowWorking
import ch.seidel.pdm.PDMPattern.RegisterAck
import ch.seidel.pdm.PDMPattern.RegisterWorker
import ch.seidel.pdm.PDMPattern.Subscription
import ch.seidel.pdm.PDMPattern.Work
import ch.seidel.pdm.PDMPattern.Abonnement
import ch.seidel.pdm.PDMPattern.WorkAvailable
import ch.seidel.pdm.PDMSystemPattern.Publish
import ch.seidel.pdm.PDMSystemPattern.Published
import ch.seidel.pdm.PDMSystemPattern._
import java.io.FileInputStream
import java.io.ObjectInputStream
import akka.contrib.pattern.ClusterReceptionistExtension
import scala.collection.JavaConversions
import ch.seidel.akka.Log4JLogging

object PDMSubscription {
  def props(init: Subscription, persImplName: String): Props = Props(classOf[PDMSubscription], init, persImplName)
}

class PDMSubscription(init: Subscription, persImplName: String) extends Actor with Log4JLogging {
  import akka.util.Timeout
  val persistence = {
    try {
      val clazz = Class.forName(persImplName)
      Some(clazz.newInstance().asInstanceOf[Persistence[Abonnement, Work]])
    }
    catch {
      case e: Throwable => 
        logger.error(s"Could not load Persistence-Impl $persImplName", e);
        None;
    }
  }
  val changeevents = Queue[Work]()
  var workers = mutable.Map.empty[ActorRef, (Option[Work], Boolean)]
  
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 30 minute) {
    case _ => Resume
  }
  
  override def preStart() {
    ClusterReceptionistExtension(context.system).registerService(self)
    loadQueue
    val publisher = context.parent
    logger.info(self.path.toString + " subscribing to " + publisher)
    publisher ! Subscription(init.aboId)
  }
  
  override def postStop {
    logger.info(s"stopping PDMSubscription for Abonnement ${init.aboId}")
    ClusterReceptionistExtension(context.system).unregisterService(self)
    // if there is not-finished Work, take it back to the queue
    workers.values foreach {
      case (Some(work), false) =>
        val nq = work +: changeevents
        changeevents.clear
        changeevents ++= nq
      case _ =>
    }
    saveQueue
    super.postStop
  }

  def wrappedReceive = {
    case RegisterWorker(worker) =>
      logger.info(s"worker ${worker.path.name} for Abonnement ${init.aboId} registered")
      context.setReceiveTimeout(10.seconds)
      context.watch(worker)
      workers += (worker -> (None, false))
      worker ! RegisterAck
      
    case Terminated(worker) => 
      logger.info(s"worker ${worker.path.name} for Abonnement ${init.aboId} died - taking off the set of workers")
      context.unwatch(worker)
      // if there is not-finished Work, take it back to the queue
      workers(worker) match {
        case (Some(work), ack) =>
          val nq = work +: changeevents
          changeevents.clear
          changeevents ++= nq
        case _ =>
      }
      workers.remove(worker)
      
    case c @ Publish(items, seqId) => 
      logger.info("change published: " + items)
      val work = Work(items, seqId)
      changeevents.enqueue(work)
      saveQueue
      // send acknowledge to sender
      sender ! Published(init.aboId, seqId)
      workers filter (_._2._1 == None) foreach { _._1 ! WorkAvailable }
      
    case GiveWork =>
      logger.info(s"worker ${sender} asks for work for Abonnement ${init.aboId}")
      // remove pending work from worker      
      workers(sender) match {
        case (Some(work), _) =>
          workers += (sender -> (None, false))
        case _ =>
      }
      if(changeevents.nonEmpty) {
        // send new work to worker
        val work = changeevents.dequeue
        workers += (sender -> (Some(work), false))
        sender ! work
        logger.info(s"sent work of Abonnement ${init.aboId} to Worker ${work}")
      }
      printWorkerState
      resetTimeoutIfNeeded
    
    case ReceiveTimeout =>
      logger.info(s"ReceiveTimeout received for Abonnement ${init.aboId}. Resend work")
      // take all not acknowledged work back in queue
      workers filter (_._2._1 != None) foreach {
        case (worker, (Some(work), false)) =>
          context.setReceiveTimeout(10.seconds)
          worker ! work
          logger.info("resent work to Worker " + work)
        case _ =>
      }
      printWorkerState
      resetTimeoutIfNeeded
      
    case NowWorking(seqId) => 
      logger.info(s"worker ${sender.path.name} is now working with $seqId.")
      // acknowledge of sended work
      workers(sender) match {
        case (Some(work), false) =>
          workers += (sender -> (Some(work), true))
        case _ =>
      }
      resetTimeoutIfNeeded
      printWorkerState
      
    case ls @ ListSubscription(aboid) =>
      val ws = workers.toList.map {
        case (actor, w) => (actor.toString, w._1, w._2)
      }
      sender ! SubscriptionState(changeevents.toList, ws)
  }
  
  def resetTimeoutIfNeeded = {
    context.setReceiveTimeout(Undefined)
    workers filter (_._2._1 != None) foreach {
      case (worker, (Some(work), false)) =>
        context.setReceiveTimeout(10.seconds)
      case _ =>
    }
  }
  
  def printWorkerState = {
    //logger.info("Workerstates: " + workers.map(w => w._2).toString)
  }
  
  def loadQueue {
    persistence match {
      case Some(pers) =>
        val restored = JavaConversions.collectionAsScalaIterable(
        pers.loadQueue(init.aboId))
        restored foreach(changeevents.enqueue(_))
      case None =>
    }
  }
  
  def saveQueue {
    persistence match {
      case Some(pers) =>
        pers.saveQueue(init.aboId, JavaConversions.mutableSeqAsJavaList(changeevents))
      case None =>
    }
  }
}

