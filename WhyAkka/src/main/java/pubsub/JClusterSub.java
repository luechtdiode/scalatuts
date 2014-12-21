package pubsub;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;
import common.JMessageProtocol;


public class JClusterSub {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("PubSub", 
        ConfigFactory.systemProperties().withFallback(
            ConfigFactory.load().getConfig("subscriber")));
    
    ActorSelection topic = system.actorSelection("akka.tcp://PubSub@127.0.0.1:2551/user/Topic");
    ActorRef sub = system.actorOf(JSub.props(), "Sub");
    
    topic.tell(new JMessageProtocol.Subscribe(sub), null);
  }

}
