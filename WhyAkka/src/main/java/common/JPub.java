package common;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;

public class JPub extends UntypedActor implements JMessageProtocol {
  private static class JPubCreator implements Creator<JPub> {
    private static final long serialVersionUID = 1L;
    private final String origin;
    private final ActorRef target;
    private final FiniteDuration interval;
    private JPubCreator(String origin, ActorRef target, FiniteDuration interval) {
      this.origin = origin;
      this.target = target;
      this.interval = interval;
    }
    public JPub create() {
      return new JPub(origin, target, interval);
    }
  }
  
  public static Props props(String origin, ActorRef target, FiniteDuration interval) {
    return Props.create(new JPubCreator(origin, target, interval));
  }
  
  private final String origin;
  private final ActorRef target;
  private final FiniteDuration interval;
  private int counter = 0;
  
  public JPub(String origin, ActorRef target, FiniteDuration interval) {
    this.origin = origin;
    this.target = target;
    this.interval = interval;
  }
  
  @Override
  public void preStart() {
    ActorSystem system = getContext().system();
    system.scheduler().schedule(
        interval, interval, getSelf(), "tick", system.dispatcher(), null);
  }
  
  @Override
  public void onReceive(Object message) throws Exception {
    if("tick".equals(message)) {
      target.tell(new NewsMessage(String.format("information %d from %s", counter++, origin)), getSelf());
    }
  }

}
