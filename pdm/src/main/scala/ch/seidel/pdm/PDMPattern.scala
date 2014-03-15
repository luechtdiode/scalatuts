package ch.seidel.pdm

import scala.collection.immutable._
import akka.actor.ActorRef
import akka.actor.Address
import com.typesafe.config.ConfigFactory
import java.net.InetAddress

/**
 * Message-Contract for Client - PDMSystem - Communication.
 * 
 * Based on WorkPullingPattern 
 * (inspired by https://github.com/mpollmeier/akka-patterns)
 */
object PDMPattern {
  val ClusterNodesDEV = Seq(
      Address("akka.tcp", "pdm", InetAddress.getLocalHost().getCanonicalHostName(), 2550),
      Address("akka.tcp", "pdm", InetAddress.getLocalHost().getCanonicalHostName(), 2551))
      
  def clusterNodes(
      hostname1: String, 
      hostname2: String,
      port: Int) = Seq(
      Address("akka.tcp", "pdm", hostname1, port),
      Address("akka.tcp", "pdm", hostname2, port))
      
  def secureCookieConfig(cookie: String) = ConfigFactory.parseString(s"""
      require-cookie = on
      secure-cookie = $cookie
      akka.remote.netty.tcp.hostname = ${InetAddress.getLocalHost().getCanonicalHostName()}
      """)

  // General data types
  /**
   * Describes the Client-ID and the Filter, on witch 
   * attributes the changes should be observed.
   */
  case class Abonnement(id: String, partnernr: Int, altgeschkey: Option[String])
  case class PartnerChange(aboId: String, partnernr: Int, altgeschkey: Option[String])

  // Messages
  sealed trait Message

  // PDMSystem public Messages
  /**
   * The Client subscribes for Partner Data (Change-)Messages (PDM).
   */
  case class Subscribe(abo: Abonnement) extends Message
  /**
   * The PDMSystem reacts with this Message to the client, 
   * when the aboId is already used by an other client.
   */
  case class AlreadySubscribed(aboId: String) extends Message
  /**
   * The PDMSystem reacts with this Message to the client,
   * when the Subscribe-Message was successfully registered.
   * The client should then tell to the subscription-ActorRef some
   * own ChangeWorkers.
   */
  case class Subscription(aboId: String) extends Message
  /**
   * The Client unsubscribes a previously subscribed Abonnement by its aboId.
   */
  case class Unsubscribe(aboId: String) extends Message
  
  // PDMSubscription Messages
  /**
   * ChangeWorker registers himself to the Subscription-Actor
   */
  case class RegisterWorker(worker: ActorRef) extends Message
  case object RegisterAck extends Message
  
  /**
   * The ChangeWorker sends to the Subscription-Actor
   * GiveWork.
   */
  case object GiveWork extends Message
  /**
   * After the Subscription-Actor told to the ChangeWorker some Work,
   * the ChangeWorker sends to the Subscription-Actor NowWorking (-Acknowledge).
   */
  case class NowWorking(seqId: Int) extends Message
  
  // ChangeWorker Messages
  /**
   * The ChangeWorker receives from the Subscription-Actor
   * WorkAvailable, if there is some Work
   */
  case object WorkAvailable extends Message
  /**
   * The ChangeWorker receives from the Subscription-Actor
   * some Work to do.
   * If this Message is received, the ChangeWorker sends
   * immediately the acknowledge Message (NowWorking) to the
   * Sender (Subscription-Actor)
   */
  case class Work(change: PartnerChange, seqId: Int) extends Message
}
