package queueworker;

import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;
import common.JPub;

public class JClusterProducer {
  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("PubSub", 
        ConfigFactory.systemProperties().withFallback(
            ConfigFactory.load().getConfig("publisher")));
    
    ActorRef queue = system.actorOf(JQueue.props(), "Queue");
    
    system.actorOf(JPub.props("west", queue, Duration.create(2, TimeUnit.SECONDS)));
    system.actorOf(JPub.props("ost", queue, Duration.create(5, TimeUnit.SECONDS)));
  }
}
