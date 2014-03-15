package basic

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
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

@RunWith(classOf[JUnitRunner])
class SchedulerSpec extends AkkaSpec {
  import AkkaSpec._
  "Akka scheduler" must {
    "continues to deliver to restarted actor" in {
      val restartingActor = TestActorRef(Props(new SelfSendingActor(testActor)))
      restartingActor ! Restart
      expectMsg(Response)
    }
  }
}
class SelfSendingActor(testActor: ActorRef) extends Actor {
  import AkkaSpec._
  val scheduledMsg = context.system.scheduler.schedule(100 millis, 100 millis) {
    self ! MessageToSelf
  }
  def receive = {
    case MessageToSelf => testActor ! Response
    case Restart => throw new Exception("provoke restart")
  }

  override def postStop = scheduledMsg.cancel()
}