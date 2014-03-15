package ch.seidel.pdm.client

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.contrib.pattern._
import akka.pattern._
import akka.util.Timeout
import ch.seidel.pdm.PDMPattern._
import ch.seidel.pdm.PDMPattern
import scala.collection.JavaConversions
import scala.collection.immutable.Seq
import com.typesafe.config.Config
import java.net.InetAddress
import org.apache.log4j.Logger

object PDMWorkerClient extends App {
  val logger = Logger.getLogger(this.getClass())
  
  // port kann somit übersteuert werden
  if (args.nonEmpty)
    System.setProperty("akka.remote.netty.tcp.port", args(0))
  
  private lazy val fallbackconfig = {
    ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load.getConfig("subscriber"))
  }
  
  private lazy val configDEV = secureCookieConfig("dev").withFallback(fallbackconfig)
  
  PDMWorkerClient.createDEV("System-for-Client").startCLI

  def createDEV(systemname: String) = {
    new PDMWorkerClient(
        systemname, 
        JavaConversions.seqAsJavaList(PDMPattern.ClusterNodesDEV), 
        configDEV)
  }
  
  def create(config: Config = ConfigFactory.empty(), 
      systemname: String,
      securecookie: String,
      hostname1: String, 
      hostname2: String,
      port: Int) = {
    new PDMWorkerClient(
        systemname, 
        JavaConversions.seqAsJavaList(clusterNodes(hostname1, hostname2, port)), 
        config.withFallback(secureCookieConfig(securecookie)).withFallback(fallbackconfig))
  }
  
}

class PDMWorkerClient(systemname: String, initContacts: java.util.List[Address], config: Config) {
  import PDMWorkerClient.logger
  
  private val system = ActorSystem(systemname, config)
  
  private val initialContacts: Set[ActorSelection] = 
    JavaConversions.asScalaIterator(initContacts.iterator()).map {
      adr => system.actorSelection(RootActorPath(adr) / "user" / "receptionist")}.toSet
      
  private val clusterClient: ActorRef = 
    system.actorOf(ClusterClient.props(initialContacts), "pdm-subscriber")
    
  //var subscription: Option[Subscription] = None
  private var workers: List[ActorRef] = List.empty
  private var workerMethods: Map[ActorRef, WorkerMethod[Work]] = Map.empty
  
  def subscribe(abo: Abonnement) = {
    implicit val t = Timeout(10, SECONDS)
    (clusterClient ? ClusterClient.Send("/user/publisher", Subscribe(abo), false)) onComplete {
      case t: Any =>
        logger.info(t)
    }
  }
  
  def unsubscribe(aboId: String) = {
    implicit val t = Timeout(10, SECONDS)
    (clusterClient ? ClusterClient.Send("/user/publisher", Unsubscribe(aboId), false))
  }
  
  def addWorker(worker: WorkerMethod[Work], subscr: Subscription) = {
    val sic = JavaConversions.collectionAsScalaIterable(initContacts)
    val contacts = sic.foldLeft(Seq[Address]())((acc, e) => acc :+ e )
    val w = system.actorOf(ChangeWorker.props(subscr, worker, contacts))
    workers = workers :+ w
    workerMethods += (w -> worker)
  }
  
  private def busyworkers = workerMethods.filter(t => !t._2.isWorking())
  
  private def removeWorker(actor: ActorRef) {
    workerMethods -= actor
    workers = workers.filter(a => a != actor)
    actor ! PoisonPill
  }

  def reduceWorkers = {
    val busy = busyworkers
    val workeractor: Option[ActorRef] = 
      if(busy.size > 0) {Some(busyworkers.keys.head)}
      else if(workerMethods.size > 0) {Some(workerMethods.keys.last)}
      else {None}
      
    workeractor match {
      case Some(actor) =>
        removeWorker(actor)
      case None =>
        logger.info("tryed to remove worker but there were no worker registered")
    }
  }
  
  def reduceAllWorkers = workerMethods foreach {case (actor, _) => removeWorker(actor)}
  
  def startCLI {
    val master = List("eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn")
    val itemfilter = master(Random.nextInt(9))
    val aboId = "Subscriber-72"// + math.abs(Random.nextInt(100))
    var abo: Option[Abonnement] = None
    
    while(true) {
      Console.in.readLine() match {
        case "stop" => 
          system.shutdown
          system.awaitTermination
          System.exit(0)
          
        case "more" => abo match {
          case Some(a) =>
            val wm = new WorkerMethod[Work]() {
              var working = false;
              override def doWork(work: Work) {
                working = true;
                try {
                  logger.info(s"Starting some hard work: $work")
                  Thread.sleep(5000)
                  logger.info(s"Finished with hard work: $work")
                }
                finally {
                  working = false;
                }
              }
              override def isWorking = working
            }
            addWorker(wm, Subscription(a.id))
          case _ =>
        }        
        
        case "less" => reduceWorkers
        
        case "none" => reduceAllWorkers
          
        case "list" => logger.info(workers.mkString("\n"))
        
        case "unsubscribe" => abo match {
          case Some(a) =>
            abo = None
            unsubscribe(a.id)
          
          case None =>
        }
  
        case "subscribe" => abo match {
          case Some(a) => 
            unsubscribe(a.id)
            abo = Some(Abonnement(aboId, 0, Some(itemfilter)))
            subscribe(abo.get)
          case None => 
            abo = Some(Abonnement(aboId, 0, Some(itemfilter)))
            subscribe(abo.get)
        }
        
        case s: String if(s.matches("subscribe\\(\\S*\\)")) => abo match {
          case None =>
          case Some(a) =>
            unsubscribe(a.id)
          }
          val id = "\\(\\S*\\)".r.findFirstIn(s).get.replace("(","").replace(")","")
          if(master.indexOf(id) < 0) {
            logger.info("unknown Filtername")
          }
          else {
            abo = Some(Abonnement(id, master.indexOf(id), Some(id)))
            subscribe(abo.get)
          }
          
        case s: Any => logger.info("Unknown command: " + s.toString())
      }
    }
  }
}

