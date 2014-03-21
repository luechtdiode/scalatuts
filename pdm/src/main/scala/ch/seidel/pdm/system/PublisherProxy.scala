package ch.seidel.pdm.system

import scala.collection.immutable.Seq
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorSelection.toScala
import akka.actor.Address
import akka.actor.Identify
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.SupervisorStrategy.Restart
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member
import akka.cluster.MemberStatus
import ch.seidel.akka.Log4JLogging
import ch.seidel.pdm.PDMSystemPattern.BecomeLeader
import ch.seidel.pdm.PDMSystemPattern.End

object PublisherProxy {
  def props(clusterNodes: Seq[Address], persImplName: String): Props = Props(classOf[PublisherProxy], clusterNodes, persImplName)
}

class PublisherProxy(clusterNodes: Seq[Address], persImplName: String) extends Actor with Log4JLogging {
  import Publisher._
  val role = "pdm-publisher"
  //https://github.com/ngocdaothanh/glokka/blob/76d34aac30ca6155dcdb107c35a575a19b8d20dd/src/main/scala/glokka/ClusterSingletonProxy.scala
  val cluster = Cluster(context.system)
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) => a.isOlderThan(b) }
  var membersByAge: SortedSet[Member] = SortedSet.empty(ageOrdering)
  var stash: List[(ActorRef, Any)] = List.empty
  var currentLeader: Option[ActorRef] = None
  var initMemberstateAsked = false
  
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute) {
    case _: Exception => Restart
  }
   
  override def preStart = {
    logger.info(self + " preStart")
    super.preStart
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[UnreachableMember])
    defineLeader(None)
  }
  
  override def postStop = {
    logger.info(self + " postStop")
    cluster.unsubscribe(self)
    super.postStop
  }

  def waitForSingleton(addresses: Set[Address]): Receive = {
    case state: CurrentClusterState =>
      if(!initMemberstateAsked) {
        initMemberstateAsked = true
        membersByAge = SortedSet.empty(ageOrdering) ++ state.members.collect {
          case m if m.hasRole(role) && m.status == MemberStatus.Up => m
        }
        logger.info("New Memberstate: " + membersByAge.toString)
        defineLeader(None)
      }
      
    case MemberUp(m) => if (m.hasRole(role)) { 
      membersByAge += m
      logger.info("MemberUp: " +(m, m.roles, membersByAge).toString)
      if(addresses.isEmpty) {
        defineLeader(None)
      }
      else {
        val newlist = addresses + m.address
        sendIdentifyMsgs(Set(m.address))
        context.become(waitForSingleton(newlist))
      }
    }
          
    case MemberRemoved(m, _) if (m.hasRole(role)) => 
      membersByAge -= m
      logger.info("MemberRemoved: " + (m, membersByAge).toString)
      currentLeader match {
        case Some(leader) if leader.path.address == m.address => {
          currentLeader = None
          defineLeader(Some(m.address))
        }
        case _ =>
      }
    
    case UnreachableMember(m) =>  
      membersByAge -= m
      logger.info("MemberRemoved: " + (m, membersByAge).toString)
      currentLeader match {
        case Some(leader) if leader.path.address == m.address => {
          currentLeader = None
          defineLeader(Some(m.address))
        }
        case _ =>
      }
    
    case ActorIdentity(addr: Address, None) =>
      val newlist = addresses - addr
      if(newlist.isEmpty) {
        logger.info(s"Publisher seemed to be gone. Create a new one on ${Cluster(context.system).selfAddress}")
        context.system.actorOf(Publisher.props(persImplName), "publisher")
        defineLeader(None)
      } 
      else {
        logger.info(s"No Publisher seen on node ${addr}. Still waiting for ${newlist} ...")
        context.become(waitForSingleton(newlist))
      }

    case ActorIdentity(addr: Address, Some(publisher)) =>
      logger.info(s"Publisher identified at ${addr}. Resending stashed Messages ${stash} ...")
      context.become(wrappedReceive)
      context.watch(publisher)
      publisher ! BecomeLeader
      currentLeader = Some(publisher)
      stash foreach {
        case (actor, msg) =>
          sender.tell(msg, actor)
      }
      stash = List.empty
      
    case Terminated(publisher) =>
      logger.info("publisher has terminated. Define new leader")
      context.unwatch(publisher)
      currentLeader = None
      defineLeader(None)

    case End =>
      context.become(shutDown)
      context.stop(self)
      
    case msg: Any => 
      stash = stash :+ (sender, msg)
  }
  
  def shutDown: Receive = {
    case _ =>
  }
  
  def wrappedReceive = {
      
    case MemberUp(m) if (m.hasRole(role)) => 
      membersByAge += m
      logger.info("MemberUp: " +(m, m.roles, membersByAge).toString)
      
    case MemberRemoved(m, _) if (m.hasRole(role)) => 
      membersByAge -= m
      logger.info("MemberRemoved: " + (m, membersByAge).toString)
      currentLeader match {
        case Some(leader) if leader.path.address == m.address => {
          currentLeader = None
          defineLeader(Some(m.address))
        }
        case _ =>
      }
    
    case UnreachableMember(m) =>  
      membersByAge -= m
      logger.info("MemberRemoved: " + (m, membersByAge).toString)
      currentLeader match {
        case Some(leader) if leader.path.address == m.address => {
          currentLeader = None
          defineLeader(Some(m.address))
        }
        case _ =>
      }

    case ActorIdentity(addr: Address, _) =>
      // mute
    
    case Terminated(publisher) =>
      logger.info("publisher has terminated. Define new leader")
      context.unwatch(publisher)
      currentLeader = None
      defineLeader(Some(publisher.path.address))
      
    case End =>
      context.become(shutDown)
      context.stop(self)
      
    case other               => {
      val co = consumer 
      co foreach {c =>
        logger.info("delegating " + other + " to " + c)
        c.tell(other, sender) }
    }
  }
 
  def consumer: Option[ActorSelection] =
    currentLeader match {
      case Some(leader) => Some(context.actorSelection(leader.path))
      case _ =>  membersByAge.headOption map (m => context.actorSelection(
      RootActorPath(m.address) / "user" /*/ "cs_publisher"*/ / "publisher"))
    }

  def defineLeader(whereNot: Option[Address]) {
    logger.info("Define a leader ...")
    
    val knownClusters = whereNot match {
      case Some(address) => (clusterNodes.toSet ++ membersByAge.map(_.address)) - address
      case None => clusterNodes.toSet ++ membersByAge.map(_.address)
    }
        
    sendIdentifyMsgs(knownClusters)
    logger.info("... and waitForSingleton ...")
    context.become(waitForSingleton(knownClusters))
  }
  
  def sendIdentifyMsgs(knownClusters: Set[Address]) {
    for(address <- knownClusters) {
      logger.info(s"  identifying ${address}")
      context.system.actorSelection(RootActorPath(address) / "user" / "publisher") ! Identify(address)
    }
  }
}