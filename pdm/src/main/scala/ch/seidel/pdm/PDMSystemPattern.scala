package ch.seidel.pdm

import scala.collection.immutable.List

/**
 * Message-Contract for Inter-PDM-Communication.
 * These Messages are not part of the public Message-API
 */
object PDMSystemPattern {
  import PDMPattern._
  
  sealed trait SystemMessage
  
  // Admin and System-Messages to PDMSystem
  case object BecomeLeader
  case object StartPublishing extends SystemMessage
  case object StopPublishing extends SystemMessage
  case object End extends SystemMessage
  case object ListAbonnements extends SystemMessage
  case class Abonnements(abos: List[Abonnement]) extends SystemMessage
  case class ListSubscription(aboId: String) extends SystemMessage
  case class SubscriptionState(queue: List[Work], workers: List[(String, Option[Work], Boolean)])
  case class InternChange(partnernr: Int, altGeschKey: Option[String]) extends SystemMessage
  
  // PDMSubscription Messages
  case class Publish(change: PartnerChange, seqId: Int) extends SystemMessage
  case class Published(aboId: String, seqId: Int) extends SystemMessage 
}