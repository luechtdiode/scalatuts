package ch.seidel.pdm.system

import scala.collection.immutable.List
import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.SECONDS
import scala.util.Random

import org.apache.log4j.Logger

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Cancellable
import akka.actor.actorRef2Scala
import akka.cluster.Cluster
import akka.pattern.ask
import akka.util.Timeout
import ch.seidel.pdm.PDMPattern.ClusterNodesDEV
import ch.seidel.pdm.PDMPattern.clusterNodes
import ch.seidel.pdm.PDMPattern.secureCookieConfig
import ch.seidel.pdm.PDMSystemPattern.Abonnements
import ch.seidel.pdm.PDMSystemPattern.End
import ch.seidel.pdm.PDMSystemPattern.InternChange
import ch.seidel.pdm.PDMSystemPattern.ListAbonnements
import ch.seidel.pdm.PDMSystemPattern.ListSubscription
import ch.seidel.pdm.PDMSystemPattern.StartPublishing
import ch.seidel.pdm.PDMSystemPattern.StopPublishing
import ch.seidel.pdm.PDMSystemPattern.SubscriptionState

object PDMSystem extends App {
  import Publisher._
  val logger: Logger = Logger.getLogger("APPL." + getClass.getName())// LogManager.getLogger(getClass)
  
  if (args.nonEmpty) try {
    val port = "" + args(0).toInt
    logger.info("port override: " + port)
    System.setProperty("akka.remote.netty.tcp.port", port)
  }
  catch {
    case e: NumberFormatException =>
  }

  lazy val fallbackconfig = 
    ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load.getConfig("publisher"))
      
  createDEV().startCLI
  
  def createDEV(config: Config = ConfigFactory.empty()) = 
    new PDMSystem(config.withFallback(secureCookieConfig("dev")).withFallback(fallbackconfig), ClusterNodesDEV)
  
  def create(config: Config = ConfigFactory.empty(), 
      securecookie: String,
      hostname1: String, 
      hostname2: String,
      port: Int) = 
    new PDMSystem(
        config.withFallback(secureCookieConfig(securecookie)).withFallback(fallbackconfig), 
        clusterNodes(hostname1, hostname2, port))
}

class PDMSystem(config: Config, clusterNodes: Seq[Address]) {
  import PDMSystem._
  val logger: Logger = Logger.getLogger("APPL." + getClass.getName())// LogManager.getLogger(getClass)
  
  val master = List("eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn")
  
  var system: ActorSystem = null
  var cluster: Cluster = null
  var publisher: Option[ActorRef] = None
  var context: Option[Cancellable] = None
  implicit val t = Timeout(5, SECONDS)
  
  startup
  
  def startup = if(system == null) {
    logger.info("starting PDM-System with Confing\n" + config)
    system = ActorSystem("pdm", config)
    cluster = Cluster(system)
    val started = Promise[Boolean]
    cluster.registerOnMemberUp {
      logger.info("cluster.onMemberUp")
      Future {
        Thread.sleep(1000)
        started.success(true)
      }
    }
    cluster.joinSeedNodes(clusterNodes)
    try {
      Await.ready(started.future, 10.seconds)
      publisher = Some(system.actorOf(PublisherProxy.props(clusterNodes, config.getString("pdm-persistence")), "publisher_proxy"))
    }
    catch {
      // First node of cluster? => register on selfAddress
      case e: Exception if(publisher.isEmpty)=>
        logger.info("No nodes found. Join self to cluster ...")
        cluster.join(cluster.selfAddress)
        started.future.onComplete {
          case _ =>
            publisher = Some(system.actorOf(PublisherProxy.props(clusterNodes, config.getString("pdm-persistence")), "publisher_proxy"))
        }
      case unknown: Exception => logger.info(unknown)
    }
    "PDM-System is running"
  }
  
  def stop = {
    (publisher.get ? End).onComplete {
      case _ =>
        logger.info("PDM-System terminate...")
        stopChanger
        publisher.get ! End
        publisher = None
        logger.info("publisher should terminate...")
        Cluster(system).leave(cluster.selfAddress)
        Cluster(system).down(cluster.selfAddress)
        logger.info("cluster should be leaved ...")
        system.shutdown
        logger.info("system should terminate...")
        val finished = Promise[Boolean]()
        Future {
          Thread.sleep(5000)
          if(!finished.isCompleted) {
            logger.info("PDM-System shutdown timed out")
          }
          system = null;
          cluster = null;
        }
        system.awaitTermination
        finished.success(true);
        system = null;
        cluster = null;
        logger.info("PDM-System terminated properly")
    }
    if(system != null) {
      system.awaitTermination
    }
    "PDM-System stopped."
  }
  
  def listAbonnements: Abonnements = {
    val p = Promise[String]
    val f = (publisher.get ? ListAbonnements).asInstanceOf[Future[Abonnements]]
    val abos = Await.result(f, t.duration)
    abos
  }
  
  def listAbodetails(aboId: String) = {
    val f = (publisher.get ? ListSubscription(aboId)).asInstanceOf[Future[SubscriptionState]]
    val state = Await.result(f, t.duration)
    state
  }
  
  private def stopChanger = context match {
    case Some(ctx) => 
      ctx.cancel
      context = None
      "changer stopped"
    case _ => ""
  }

  def interpreteCommand(cmd: String): String = 
    if(publisher == None) {
      if(cmd == "start") {
        startup
        "System started ..."
      }
      else {
        "Publisher is not ready. Please try later"
      }
    }
    else cmd match {
    case "stop" => stop
    
    case "pause" =>
      publisher.get ! StopPublishing
      "publishing paused"
      
    case "run" =>
      publisher.get ! StartPublishing
      "publishing started"
      
    case "list" => 
      listAbonnements.toString
      
    case "change" => context match {
      case None =>
        context = Some(system.scheduler.schedule(0.seconds, 2.seconds){
          val partnerNr = Random.nextInt(9)
          val altgeschkey = master(partnerNr)
          if(publisher.isEmpty) {
            stopChanger
          }
          else {
            publisher.get ! InternChange(partnerNr, Some(altgeschkey))
          }
        })
        "changer started"
        
      case Some(ctx) => stopChanger
    }
      
    case s: String if(s.matches("list\\(\\S*\\)")) => 
      val aboid = "\\(\\S*\\)".r.findFirstIn(s).get.replace("(","").replace(")","")
      val state = listAbodetails(aboid)
      "SubscriptionState of " + aboid + "\n" +
      "- State of Workers:"+ "\n" +
      state.workers.mkString("  ", "\n  ", "")+ "\n" +
      "- State of the queue:"+ "\n" +
      state.queue.mkString("  ", "\n  ", "")
    
    case s: String =>
      publisher.get ! s
      s
  }
    
  def startCLI = {
    logger.info("startCLI")
    while(true) {
      try {
        logger.info(interpreteCommand(Console.in.readLine()))
      }
      catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }
}