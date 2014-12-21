package pubsub;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.remote.Ack;
import common.JMessageProtocol;

public class JSub extends UntypedActor implements JMessageProtocol {
  
  public static Props props() {
    return Props.create(JSub.class);
  }

  @Override
  public void onReceive(Object message) throws Exception {
    System.out.println(message);
    getSender().tell(Ack.class, getSelf());
  }

}
