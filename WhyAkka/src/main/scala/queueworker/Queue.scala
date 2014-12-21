package queueworker

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala

class Queue extends Actor {
  import common.MessageProtocol._

  // private state
  var workerstates = Map[ActorRef, Option[NewsMessage]]()
  var queue = List[NewsMessage]()

  def enqueue(msg: NewsMessage) {
    queue = queue :+ msg
    trySend
  }

  def dequeue = {
    if (queue.isEmpty) None
    else {
      val ret = queue.head
      queue = queue.tail
      Some(ret)
    }
  }

  def trySend {
    // extract workers without busy-state
    val idleWorkers = workerstates filter (ws => ws._2 == None) map (_._1)
    if (idleWorkers.nonEmpty) {
      dequeue match {
        case Some(msg) =>
          val worker = idleWorkers.head
          workerstates = workerstates + (worker -> Some(msg))
          worker ! msg
          println("message sent to worker " + worker.path + ". queuesize = " + queue.size)
          trySend
        case None => println("empty queue")
      }
    }
    else if(workerstates.size == 0) {
      println("no worker registered. queuesize = " + queue.size)
    }
    else {
      println("no free worker available. queuesize = " + queue.size)
    }
  }

  override def receive = {

    case Subscribe(sub) =>
      workerstates = workerstates + (sub -> None)
      context.watch(sub)
      println("new worker registered: " + sub.path + ", " + workerstates)
      trySend

    case msg: NewsMessage =>
      enqueue(msg)
      println("new message published from: " + sender.path)

    case MessageProcessed =>
      workerstates = workerstates + (sender -> None)
      trySend

    case Terminated(sub) =>
      workerstates.get(sub) match {
        case Some(Some(msg)) => enqueue(msg)
        case _ =>
      }
      workerstates = workerstates - sub
      context.unwatch(sub)
      println("subscriber terminated: " + sub.path)
  }
}

object Queue {
  def props = Props(classOf[Queue])
}