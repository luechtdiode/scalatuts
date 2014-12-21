package common

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestActorRef
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.remote.Ack

import common._
import pubsub._
import common.MessageProtocol.NewsMessage

@RunWith(classOf[JUnitRunner])
class PubSubSpec extends TestKit(ActorSystem("AkkaTestSystem", ConfigFactory.parseString("""
    akka.actor.provider="akka.cluster.ClusterActorRefProvider"
    """)))
    with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  import MessageProtocol._
  
  "Pub" must {
    "publish" in {
      TestActorRef(Pub.props("test", self, 1 seconds))
      expectMsgClass(classOf[NewsMessage])
    }
  }
  
  "Topic" must {
    "receive and publish" in {
      val testactor = TestActorRef(Topic.props)
      testactor ! Subscribe(self)
      testactor ! NewsMessage("Hello World")
      expectMsgClass(classOf[NewsMessage])
    }
    
    "Sub" must {
      "receive" in {
        val testactor = TestActorRef(Sub.props)
        testactor ! NewsMessage("Hello World")
        expectMsg(Ack)
      }
    }
  }
}
