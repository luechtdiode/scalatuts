package ch.seidel.pdm

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.actor.PoisonPill
import akka.actor.DeadLetter
import akka.testkit.TestProbe
import akka.actor.DeadLetter
import akka.testkit.TestActorRef
import akka.actor.ActorRef
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import ch.seidel.pdm.system.Publisher
import basic.AkkaSpec
import ch.seidel.akka.ActorStack
import ch.seidel.pdm.system.PublisherProxy
import scala.collection.immutable.Seq

@RunWith(classOf[JUnitRunner])
class PDMSpec extends AkkaSpec {
  import AkkaSpec._
  import PDMPattern._
  
  "Publisher" must {
    "restart after an Exception" in {
      val restartingActor = TestActorRef(Props(new Publisher("pdm-persistence") with SelfKillingActor))
      restartingActor ! Restart
      fishForMessage() {
        case Response => true
        case _ => false
      }
    }
    "acknowledge after successfull abo-registration msg" in {
      val publisher = system.actorOf(Publisher.props("pdm-persistence"))
      publisher ! Subscribe(Abonnement("test", 1, Some("test")))
      fishForMessage() {
        case Subscription("test") => true
        case _ => false
      }
    }
    "acknowledge alreadySubscribed after repeated abo-registration msg" in {
      val publisher = system.actorOf(Publisher.props("pdm-persistence"))
      publisher ! Subscribe(Abonnement("test", 1, Some("test")))
      publisher ! Subscribe(Abonnement("test", 1, Some("test")))
      fishForMessage() {
        case AlreadySubscribed("test") => true
        case _ => false
      }
    }
  }

  "PublisherProxy" must {
    "restart after an Exception" in {
      val restartingActor = TestActorRef(Props(new PublisherProxy(Seq(), "pdm-persistence") with SelfKillingActor))
      restartingActor ! Restart
      fishForMessage() {
        case Response => true
        case _ => false
      }
    }
  }
}
