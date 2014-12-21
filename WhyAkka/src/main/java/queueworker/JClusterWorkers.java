package queueworker;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;
import common.JMessageProtocol;

public class JClusterWorkers {
  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("PubSub", 
        ConfigFactory.systemProperties().withFallback(
            ConfigFactory.load().getConfig("subscriber")));

    ActorSelection queue = system.actorSelection("akka.tcp://PubSub@127.0.0.1:2551/user/Queue");
    ActorRef worker1 = system.actorOf(JWorker.props(), "Worker1");
    ActorRef worker2 = system.actorOf(JWorker.props(), "Worker2");
    ActorRef worker3 = system.actorOf(JWorker.props(), "Worker3");

    queue.tell(new JMessageProtocol.Subscribe(worker1), null);
    queue.tell(new JMessageProtocol.Subscribe(worker2), null);
    queue.tell(new JMessageProtocol.Subscribe(worker3), null);
  }
}
