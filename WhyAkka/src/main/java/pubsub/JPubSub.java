package pubsub;

import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import common.JMessageProtocol;
import common.JPub;


public class JPubSub {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("PubSub");
    
    ActorRef topic = system.actorOf(JTopic.props(), "Topic");
    
    system.actorOf(JPub.props("west", topic, Duration.create(2, TimeUnit.SECONDS)));
    system.actorOf(JPub.props("ost", topic, Duration.create(5, TimeUnit.SECONDS)));

    ActorRef sub = system.actorOf(JSub.props(), "Sub");

    topic.tell(new JMessageProtocol.Subscribe(sub), null);
  }

}
