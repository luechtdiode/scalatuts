package pubsub;

import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;
import common.JPub;


public class JClusterPub {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("PubSub", 
      ConfigFactory.systemProperties().withFallback(
        ConfigFactory.load().getConfig("publisher")));
    
    ActorRef topic = system.actorOf(JTopic.props(), "Topic");
    
    system.actorOf(JPub.props("west", topic, Duration.create(2, TimeUnit.SECONDS)));
    system.actorOf(JPub.props("ost", topic, Duration.create(5, TimeUnit.SECONDS)));
  }

}
