package pubsub;

import java.util.HashSet;
import java.util.Set;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActor;

import common.JMessageProtocol;

public class JTopic extends UntypedActor implements JMessageProtocol {
  
  public static Props props() {
    return Props.create(JTopic.class);
  }

  private Set<ActorRef> subscribers = new HashSet<ActorRef>();
  
  @Override
  public void onReceive(Object message) throws Exception {
    if(message instanceof NewsMessage) {
      for(ActorRef actor : subscribers) {
        actor.tell(message, getSelf());
      }
    }
    else if(message instanceof Subscribe) {
      Subscribe s = (Subscribe)message;
      subscribers.add(s.sub);
    }
    else if(message instanceof Terminated) {
      Terminated t = (Terminated)message;
      getContext().unwatch(t.getActor());
      subscribers.remove(t.getActor());
      System.out.println("subscriber terminated: " + t.getActor());
    }
  }

}
