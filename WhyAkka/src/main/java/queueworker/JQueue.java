package queueworker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActor;

import common.JMessageProtocol;

public class JQueue extends UntypedActor implements JMessageProtocol {
  
  public static Props props() {
    return Props.create(JQueue.class);
  }

  private final List<NewsMessage> queue = new LinkedList<NewsMessage>();
  private final Set<ActorRef> workers = new HashSet<ActorRef>();
  private final Map<ActorRef, NewsMessage> workerstates = new HashMap<ActorRef, NewsMessage>();
  
  private void enqueue(NewsMessage message) {
    queue.add(message);
    trySend();
  }
  
  private NewsMessage dequeue() {
    if(queue.isEmpty()) {
      return null;
    }
    else {
      return queue.remove(0);
    }
  }
  
  private void trySend() {
    List<ActorRef> freeWorkers = new ArrayList<ActorRef>();
    for(ActorRef worker : workers) {
      if(!workerstates.containsKey(worker)) {
        freeWorkers.add(worker);
      }
    }
    if(!freeWorkers.isEmpty()) {
      NewsMessage next = dequeue();
      if(next != null) {
        ActorRef worker = freeWorkers.get(0);
        workerstates.put(worker, next);
        worker.tell(next, getContext().self());
        trySend();
      }
    }
  }
  
  @Override
  public void onReceive(Object message) throws Exception {
    if(message instanceof NewsMessage) {
      enqueue((NewsMessage)message);
      System.out.println("new Message to publish: " + message + ". queuesize = " + queue.size());
    }
    else if(message instanceof MessageProcessed) {
      workerstates.remove(getContext().sender());
    }
    else if(message instanceof Subscribe) {
      Subscribe s = (Subscribe)message;
      getContext().watch(s.sub);
      workers.add(s.sub);
    }
    else if(message instanceof Terminated) {
      Terminated t = (Terminated)message;
      getContext().unwatch(t.getActor());
      workers.remove(t.getActor());
      if(workerstates.get(getContext().sender()) instanceof NewsMessage) {
        NewsMessage m = workerstates.remove(getContext().sender());
        enqueue(m);
        System.out.println("worker terminated: " + t.getActor().path() + " - taking message (" + m + ") back in queue. queuesize = " + queue.size());
      }
      else {
        System.out.println("worker terminated: " + t.getActor().path());
      }
    }
  }

}
