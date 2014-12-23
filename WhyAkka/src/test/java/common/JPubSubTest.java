package common;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import pubsub.JSub;
import pubsub.JTopic;
import queueworker.JQueue;
import queueworker.JWorker;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.remote.Ack;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;

import common.JMessageProtocol.NewsMessage;
import common.JMessageProtocol.Subscribe;

public class JPubSubTest {
  static ActorSystem actorSystem;
  
  
  @BeforeClass
  public static void initActorSystem() {
    actorSystem = ActorSystem.apply();
  }
  
  @AfterClass
  public static void terminateActorSystem() {
    actorSystem.shutdown();
    actorSystem.awaitTermination();
  }
  
  @Test
  public void testPub_must_publish_in_a_certain_interval() throws Exception {
    JavaTestKit testTarget = new JavaTestKit(actorSystem);
    ActorRef testactor = TestActorRef.apply(JPub.props("test", testTarget.getRef(), Duration.create(1, TimeUnit.SECONDS)), actorSystem);
      // give 50 millis delay for the first shot and 10 millis for poor laptop performance
    testTarget.expectMsgClass(Duration.create(1050, TimeUnit.MILLISECONDS), NewsMessage.class);
    testTarget.expectMsgClass(Duration.create(1010, TimeUnit.MILLISECONDS), NewsMessage.class);
    testTarget.expectMsgClass(Duration.create(1010, TimeUnit.MILLISECONDS), NewsMessage.class);
    actorSystem.stop(testTarget.getRef());
    actorSystem.stop(testactor);
  }
  
  @Test
  public void testTopic_must_receive_from_Pub_and_publish_to_Subribers() throws Exception{
    JavaTestKit testTarget = new JavaTestKit(actorSystem);
    ActorRef testactor = TestActorRef.apply(JTopic.props(), actorSystem);
    testactor.tell(new Subscribe(testTarget.getRef()), testTarget.getRef());
    testactor.tell(new NewsMessage("Hello World"), testTarget.getRef());
    testTarget.expectMsgClass(NewsMessage.class);
    actorSystem.stop(testactor);
    actorSystem.stop(testTarget.getRef());
  }

  @Test
  public void testTopic_must_recognize_and_cleanup_subscriber_termination() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JTopic.props(), actorSystem);
    testactor.tell(new Subscribe(testworker.ref()), testworker.ref());
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectMsgClass(NewsMessage.class);
    actorSystem.stop(testworker.ref());
    Thread.sleep(20);
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectNoMsg();
    actorSystem.stop(testactor);
  }

  @Test
  public void testQueue_must_receive_from_Pub_and_publish_to_Workers() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JQueue.props(), actorSystem);
    testactor.tell(new Subscribe(testworker.ref()), testworker.ref());
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectMsgClass(NewsMessage.class);
    actorSystem.stop(testactor);
    actorSystem.stop(testworker.ref());
  }

  @Test
  public void testQueue_must_receive_from_Pub_queue_the_msg_and_publish_then_later_to_registered_Workers() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JQueue.props(), actorSystem);
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testactor.tell(new Subscribe(testworker.ref()), testworker.ref());
    testworker.expectMsgClass(NewsMessage.class);
    actorSystem.stop(testactor);
    actorSystem.stop(testworker.ref());
  }

  @Test
  public void testQueue_must_recognize_and_cleanup_subscriber_termination() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JQueue.props(), actorSystem);
    
    testactor.tell(new Subscribe(testworker.ref()), testworker.ref());
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectMsgClass(NewsMessage.class);
    
    actorSystem.stop(testworker.ref());
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectNoMsg();
    
    actorSystem.stop(testactor);
  }

  @Test
  public void testQueue_must_take_back_unprocessed_msg_into_the_queue() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    TestProbe testworkerFallback = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JQueue.props(), actorSystem);
    NewsMessage msg1 = new NewsMessage("Hello World1");
    NewsMessage msg2 = new NewsMessage("Hello World2");
    testactor.tell(msg1, testworker.ref());
    testactor.tell(msg2, testworker.ref());
    testactor.tell(new Subscribe(testworker.ref()), testworker.ref());
    testworker.expectMsg(msg1);
    testworker.send(testactor, JMessageProtocol.MESSAGE_PROCESSED);
    testworker.expectMsg(msg2);
    // do not send MessageProcessed and test, if it will be taken back to the queue
    actorSystem.stop(testworker.ref());
    testworker.expectNoMsg();
    testactor.tell(new Subscribe(testworkerFallback.ref()), testworkerFallback.ref());
    testworkerFallback.expectMsg(msg2);
    actorSystem.stop(testworkerFallback.ref());
    actorSystem.stop(testactor);
  }

  @Test
  public void testSub_must_receive() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JSub.props(), actorSystem);
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectMsg(Ack.class);
    actorSystem.stop(testactor);
    actorSystem.stop(testworker.ref());
  }

  @Test
  public void testWorker_must_receive_and_acknnowledge_Wieth_MessageProcessed() throws Exception{
    TestProbe testworker = new TestProbe(actorSystem);
    ActorRef testactor = TestActorRef.apply(JWorker.props(), actorSystem);
    testactor.tell(new NewsMessage("Hello World"), testworker.ref());
    testworker.expectMsg(JMessageProtocol.MESSAGE_PROCESSED);
    actorSystem.stop(testactor);
    actorSystem.stop(testworker.ref());
  }
}
