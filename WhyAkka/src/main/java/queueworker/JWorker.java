package queueworker;

import akka.actor.Props;
import akka.actor.UntypedActor;

import common.JMessageProtocol;

public class JWorker extends UntypedActor implements JMessageProtocol {
  
  public static Props props() {
    return Props.create(JWorker.class);
  }

  @Override
  public void onReceive(Object message) throws Exception {
    System.out.println(message + " received in " + getContext().self().path());
    getContext().sender().tell(MESSAGE_PROCESSED, getContext().self());
  }

}
