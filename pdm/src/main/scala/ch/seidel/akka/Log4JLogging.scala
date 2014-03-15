package ch.seidel.akka

import akka.actor.Actor
import org.apache.log4j.Logger
import org.apache.log4j.MDC
//import org.apache.logging.log4j.Logger
//import org.apache.logging.log4j.LogManager
//import org.apache.logging.log4j.ThreadContext

trait Log4JLogging extends Actor with ActorStack {
  val logger: Logger = Logger.getLogger("APPL." + getClass.getName())// LogManager.getLogger(getClass)
  private[this] val myPath = self.path.toString
  
  logger.info("Starting actor " + getClass.getName)
  
  override def receive: Receive = {
    case x =>
      //ThreadContext.push("akkaSource", myPath)
      MDC.put("akkaSource", myPath)
      super.receive(x)
      //ThreadContext.pop();
      MDC.remove("akkaSource")
  }
}