package ch.seidel.pdm.system

import java.io.File

import scala.collection.JavaConversions
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration.Undefined
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.SupervisorStrategy.Resume
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import ch.seidel.akka.Log4JLogging
import ch.seidel.pdm.PDMPattern.Abonnement
import ch.seidel.pdm.PDMPattern.AlreadySubscribed
import ch.seidel.pdm.PDMPattern.PartnerChange
import ch.seidel.pdm.PDMPattern.Subscribe
import ch.seidel.pdm.PDMPattern.Subscription
import ch.seidel.pdm.PDMPattern.Unsubscribe
import ch.seidel.pdm.PDMPattern.Work
import ch.seidel.pdm.PDMSystemPattern.Abonnements
import ch.seidel.pdm.PDMSystemPattern.BecomeLeader
import ch.seidel.pdm.PDMSystemPattern.End
import ch.seidel.pdm.PDMSystemPattern.InternChange
import ch.seidel.pdm.PDMSystemPattern.ListAbonnements
import ch.seidel.pdm.PDMSystemPattern.ListSubscription
import ch.seidel.pdm.PDMSystemPattern.Publish
import ch.seidel.pdm.PDMSystemPattern.Published
import ch.seidel.pdm.PDMSystemPattern.StartPublishing
import ch.seidel.pdm.PDMSystemPattern.StopPublishing
import ch.seidel.pdm.PDMSystemPattern.SubscriptionState

object Publisher {
  val topic = "pdm-publisher"
  def props(persImplName: String): Props = Props(classOf[Publisher], persImplName)
//  def singletonProps(persImplName: String): Props = ClusterSingletonManager.props(
//      maxHandOverRetries = 10,
//      maxTakeOverRetries = 5,
//      singletonProps = hod => {
//        println("using singletonProps ...")
//        props(persImplName)},
//      singletonName = "publisher",
//      terminationMessage = End,
//      role = Some(topic))
}
// extends EventsourcedProcessor 
class Publisher(persImplName: String) extends Actor with Log4JLogging {
  import Publisher._
  
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
  var running: Option[ActorRef] = None
  var sequenceFactory = Iterator.from(Int.MinValue)
  var active = false
  var pendingAboSubscriber = Map[String, ActorRef]()
  var subscriber = Map[String, ActorRef]()
  var abonnements = Map[String, Abonnement]()
  var pendingpubs = Map[Int, Publish]()

//  var lastItems = List[String]()
  var items = List[InternChange]()
  
  logger.info("started: " + self)
  
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 30 minute) {
    case _ => {
      Resume
    }
  }
  
  override def preStart {
    //ClusterReceptionistExtension(context.system).registerService(self)
  }
  
  override def postStop {
    logger.info("stopping Publisher")
    //ClusterReceptionistExtension(context.system).unregisterService(self)
  }
  
  def wrappedReceive = {

    case s @ Subscribe(abo) => withSubscriber(abo.id) {
      case Some(subscriber) =>
        sender ! AlreadySubscribed(abo.id) 
      case None =>
        context.watch(sender)
        pendingAboSubscriber += (abo.id -> sender)
        abonnements += (abo.id -> s.abo)
        saveAbos
        val subscr = Subscription(abo.id)
        logger.info(s"subscribing $s")
        subscriber += (abo.id -> context.actorOf(PDMSubscription.props(subscr, persImplName), s"subscr-${abo.id}"))
    }
    
    case s: Subscription => {
      logger.info("subscribe acknowledged: " + s)
      // acknowledge of subscribe to PDMSubscription-Actor
      pendingAboSubscriber.get(s.aboId) match {
        case Some(client) => 
          // acknowledge to client
          client ! s
          context.unwatch(client)
          pendingAboSubscriber -= s.aboId
        case None =>
      }
      // implicit start publishing
      publishChanges
    }
    
    case Terminated(client) => pendingAboSubscriber find(s => s._2 == client) match {
      case Some((aboId, _)) => pendingAboSubscriber -= aboId
      case None =>
    }
    
    case ReceiveTimeout => {
      logger.info("Timeout ???")
    }
    
    case s @ Unsubscribe(aboId) => 
      withSubscriber(aboId) {
        case Some(subscr) =>
          logger.info("unsubscribing " + s)
          subscr ! PoisonPill
          subscriber -= aboId
          abonnements -= aboId
          saveAbos
        case None =>
      }
      sender ! "OK"
  
    case StartPublishing => 
      publishChanges
      logger.info("publishing started")
      
    case p @ Published(aboId: String, seqId: Int) => 
      pendingpubs -= seqId
      if(pendingpubs.nonEmpty) 
        context.setReceiveTimeout(10.seconds)
      else 
        context.setReceiveTimeout(Undefined)
    
    case StopPublishing => 
      active = false
      logger.info("publishing stopped")
      
    case i: InternChange =>
      logger.info(i.toString)
      items = items :+ i
      if(subscriber.nonEmpty && running.isDefined && active) processDeltas
      
    case ListAbonnements =>
      val l = abonnements.map(m => m._2).toList
      sender ! Abonnements(l)
    
    case "Tick" => if(subscriber.nonEmpty && running.isDefined && active) processDeltas
    
    case ls @ ListSubscription(aboid) => withSubscriber(aboid) {
      case Some(subscr) => subscr.forward(ls)
      case _ => sender ! SubscriptionState(List.empty, List.empty)
    }
    
    case BecomeLeader => 
      becomeLeader
    
    case End => 
      context.stop(self)
      
  } // receive -> wrappedReceive
  
  def becomeLeader = running match {
    case None => 
      logger.info("becoming running")
      running = Option(self)
      loadAbos
      for(abo <- abonnements.values) {
        val subscr = Subscription(abo.id)
        logger.info(s"subscribing $subscr")
        subscriber += (abo.id -> context.actorOf(PDMSubscription.props(subscr, persImplName), s"subscr-${abo.id}"))
      }
      publishChanges
    case _ =>
  }
  
  def publishChanges = running match {
      case Some(_) => 
        active = true
        if(subscriber.nonEmpty) processDeltas
      case _ =>
    }
  
  def processDeltas = {
    //logger.info("processDeltas")
    if(active && items.nonEmpty) {
      val item = items.head
      items = items.tail
      abonnements.values.groupBy(x => (x.partnernr, x.altgeschkey)).foreach {
        case (filter, receivers) => {
          receivers.filter(r => r.altgeschkey == item.altGeschKey || r.partnernr == item.partnernr).
                   foreach(r => subscriber.get(r.id) match {
            case Some(subscr) => 
              logger.info("sending deltas to " + subscr)
              val sequenceid = sequenceFactory.next
              val pub =  Publish(PartnerChange(r.id, item.partnernr, item.altGeschKey), sequenceid)
              pendingpubs += (sequenceid -> pub)
              subscr ! pub
            case None =>
              logger.info(s"none subscription found for ${r.id}")
          }) // match.foreach
        } 
      }
      if(pendingpubs.nonEmpty) 
        context.setReceiveTimeout(10.seconds)
      else 
        context.setReceiveTimeout(Undefined)
    }
    if(subscriber.nonEmpty && running.isDefined && active) {
      context.system.scheduler.scheduleOnce(1000 millis, context.self, "Tick")
    }
  }
  
  def withSubscriber(aboId: String)(fun: Option[ActorRef] => Unit) = 
    subscriber.get(aboId) match {
      case Some(s) => fun(Some(s))
      case None => fun(None)
    }

  lazy val aboFile = {
    val queDir = new File(File.createTempFile("pdm", "abos").getParent() + "/abos")
    if(!queDir.exists()) {
      queDir.mkdir()
    }
    new File(s"${queDir}/abonnements.pub")
  }
  
  def loadAbos {
    persistence match {
      case Some(pers) =>
        abonnements = JavaConversions.mapAsScalaMap(pers.loadAbos()).foldLeft(abonnements){
          (acc, m) => acc + m
        }
      case None =>
    }
  }
  
  def saveAbos {
    persistence match {
      case Some(pers) =>
        pers.saveAbos(JavaConversions.mapAsJavaMap(abonnements))
      case None =>
    }
  }
  
}