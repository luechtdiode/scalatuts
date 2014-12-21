package queueworker;

import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import common.JMessageProtocol;
import common.JPub;

public class JProducerWorkersApp {
  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("PubSub");
    
    ActorRef topic = system.actorOf(JQueue.props(), "Queue");
    
    system.actorOf(JPub.props("west", topic, Duration.create(2, TimeUnit.SECONDS)));
    system.actorOf(JPub.props("ost", topic, Duration.create(5, TimeUnit.SECONDS)));

    ActorRef worker1 = system.actorOf(JWorker.props(), "Worker1");
    ActorRef worker2 = system.actorOf(JWorker.props(), "Worker2");
    ActorRef worker3 = system.actorOf(JWorker.props(), "Worker3");

    topic.tell(new JMessageProtocol.Subscribe(worker1), null);
    topic.tell(new JMessageProtocol.Subscribe(worker2), null);
    topic.tell(new JMessageProtocol.Subscribe(worker3), null);
  }
}
