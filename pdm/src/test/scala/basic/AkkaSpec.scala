package basic

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.duration.DurationInt
import scala.collection.mutable
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, FunSpec }
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import akka.actor.{ Actor, ActorSystem, Props, DeadLetter }
import akka.util.Timeout
import akka.testkit.{ TestKit, ImplicitSender }
import akka.actor.DeadLetter
import org.scalatest.matchers.BePropertyMatchResult
import org.scalatest.matchers.BePropertyMatcher
import akka.actor.UnhandledMessage
import org.mockito.Mockito
import org.mockito.verification.VerificationMode
import org.slf4j.LoggerFactory
import org.scalatest.WordSpecLike
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.typesafe.config.ConfigFactory
import ch.seidel.akka.ActorStack
//import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
abstract class AkkaSpec extends TestKit(ActorSystem("AkkaTestSystem", ConfigFactory.parseString("""
    akka.actor.provider="akka.cluster.ClusterActorRefProvider"
    """)))
    with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  val log = LoggerFactory.getLogger(getClass)
  
  override def afterAll() { 
    TestKit.shutdownActorSystem(system)
  }
  
  implicit val timeout = Timeout(10 seconds)

  val listener = system.actorOf(Props(new Actor {
    def receive = {
      case m: DeadLetter =>
        log.warn("Received a dead letter: " + m)
      case m: UnhandledMessage =>
        log.warn("Some message wasn't delivered: check that your actor's receive methods handle all messages you need: " + m)
    }
  }))
  system.eventStream.subscribe(listener, classOf[DeadLetter])
  system.eventStream.subscribe(listener, classOf[UnhandledMessage])
  
  def expectMsgAllOfIgnoreOthers[T](max: Duration, expected: T*) {
    val outstanding = mutable.Set(expected: _*)
    fishForMessage(max) {
      case msg: T if outstanding.contains(msg) =>
        outstanding.remove(msg)
        outstanding.isEmpty
      case _ => false
    }
  }

  def expectMsgAllOfIgnoreOthers[T](expected: T*) {
    expectMsgAllOfIgnoreOthers(3 seconds, expected: _*)
  }

  def time(func: => Unit): Duration = {
    val start = System.currentTimeMillis
    func
    val millis = System.currentTimeMillis - start
    Duration(millis, MILLISECONDS)
  }

  def maxMillis(maxMillis: Long)(func: => Unit) {
    val start = System.currentTimeMillis
    func
    val millis = System.currentTimeMillis - start
    millis should be < (maxMillis)
  }
  
  trait SelfKillingActor extends Actor with ActorStack {
    import AkkaSpec._
    
    val scheduledMsg = context.system.scheduler.schedule(100 millis, 100 millis) {
      self ! MessageToSelf
    }
    override def receive: Receive = {
      case MessageToSelf => 
        testActor ! Response
      case Restart => 
        throw new Exception("provoke restart")
      case x =>
        super.receive(x)
    }
  
    override def postStop = scheduledMsg.cancel()
  }
}
object AkkaSpec {
  object MessageToSelf
  object Restart
  object Response
}
