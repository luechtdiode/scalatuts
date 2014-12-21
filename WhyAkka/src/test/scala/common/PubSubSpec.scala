package common

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.remote.Ack
import akka.actor.Terminated
import akka.actor.PoisonPill
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import akka.testkit.ImplicitSender
import akka.testkit.TestActorRef
import akka.testkit.TestKit
import akka.testkit.TestProbe

import common.MessageProtocol.NewsMessage
import common._
import pubsub._
import queueworker._

@RunWith(classOf[JUnitRunner])
class PubSubSpec 
 extends TestKit(ActorSystem("AkkaTestSystem"))
    with ImplicitSender
    with WordSpecLike 
    with Matchers 
    with BeforeAndAfterAll {
  /*
   * for the java-way take a look at
   * http://blog.florian-hopf.de/2012/03/testing-akka-actors-from-java.html
   */
  import MessageProtocol._
  
  "Pub" must {
    "publish in a certain interval" in {
      val testTarget = TestProbe()
      val testactor = TestActorRef(Pub.props("test", testTarget.ref, 1 seconds))
      // give 50 millis delay for the first shot and 10 millis for poor laptop performance
      testTarget.expectMsgClass(1050 milliseconds, classOf[NewsMessage])
      testTarget.expectMsgClass(1010 milliseconds, classOf[NewsMessage])
      testTarget.expectMsgClass(1010 milliseconds, classOf[NewsMessage])
      testTarget.ref ! PoisonPill
      testactor ! PoisonPill
    }
  }
  
  "Topic" must {
    "receive from Pub and publish to Subribers" in {
      val testactor = TestActorRef(Topic.props)
      testactor ! Subscribe(self)
      testactor ! NewsMessage("Hello World")
      expectMsgClass(classOf[NewsMessage])
      testactor ! PoisonPill
    }
    
    "recognize and cleanup subscriber-termination" in {
      val testworker = TestProbe()
      val testactor = TestActorRef(Topic.props)

      testactor ! Subscribe(testworker.ref)
      testactor ! NewsMessage("Hello World")
      testworker.expectMsgClass(classOf[NewsMessage])
      
      testworker.ref ! PoisonPill
      testactor ! NewsMessage("Hello World")
      testworker.expectNoMsg
      testactor ! PoisonPill
    }
  }
  
  "Queue" must {
    "receive from Pub and publish to Workers" in {
      val testactor = TestActorRef(Queue.props)
      testactor ! Subscribe(self)
      testactor ! NewsMessage("Hello World")
      expectMsgClass(classOf[NewsMessage])
      testactor ! PoisonPill
    }
    
    "receive from Pub, queue the msg and publish then later to registered Workers" in {
      val testactor = TestActorRef(Queue.props)
      testactor ! NewsMessage("Hello World")
      testactor ! Subscribe(self)
      expectMsgClass(classOf[NewsMessage])
      testactor ! PoisonPill
    }
    
    "recognize and cleanup subscriber-termination" in {
      val testworker = TestProbe()
      val testactor = TestActorRef(Queue.props)

      testactor ! Subscribe(testworker.ref)
      testactor ! NewsMessage("Hello World")
      testworker.expectMsgClass(classOf[NewsMessage])
      
      testworker.ref ! PoisonPill
      testactor ! NewsMessage("Hello World")
      testworker.expectNoMsg
      testactor ! PoisonPill
    }
    
    "take back unprocessed msg into the queue" in {
      val testworker = TestProbe()
      val testactor = TestActorRef(Queue.props)
      testactor ! NewsMessage("Hello World1")
      testactor ! NewsMessage("Hello World2")
      testactor ! Subscribe(testworker.ref)
      testworker.expectMsg(NewsMessage("Hello World1"))
      testworker.send(testactor, MessageProcessed)
      testworker.expectMsg(NewsMessage("Hello World2"))
      // do not send MessageProcessed and test, if it will be taken back to the queue
      testworker.ref ! PoisonPill
      // register self as Worker and get the backed-up message
      testactor ! Subscribe(self)
      expectMsg(NewsMessage("Hello World2"))
      testactor ! PoisonPill
    }
  }
  
  "Sub" must {
    "receive" in {
      val testactor = TestActorRef(Sub.props)
      testactor ! NewsMessage("Hello World")
      expectMsg(Ack)
      testactor ! PoisonPill
    }
  }
  
  "Worker" must {
    "receive and acknowledge with MessageProcessed" in {
      val testactor = TestActorRef(Worker.props)
      testactor ! NewsMessage("Hello World")
      expectMsg(MessageProcessed)
      testactor ! PoisonPill
    }
  }
}
