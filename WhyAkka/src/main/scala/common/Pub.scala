package common

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
//import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

class Pub(origin: String, target: ActorRef, scheduleRule: String, interval: FiniteDuration) extends Actor {
  import common.MessageProtocol._
  var counter = 0
  
  override def preStart = {
  /*
   * Make use of QuartzSchedulerExtension if needed:
   *  see https://github.com/enragedginger/akka-quartz-scheduler
   *      http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html
   */
//    val schedRuleName = origin + "_ScheduleRule"
//    val scheduler = QuartzSchedulerExtension(context.system)
//    scheduler.createSchedule(schedRuleName, None, scheduleRule, None)
//    scheduler.schedule(schedRuleName, self, "tick")

    // simple Akka-Scheduler doesn't support true scheduling   */
    context.system.scheduler.schedule(interval, interval, self, "tick")
  }
  
  override def receive = {
    case "tick" => 
      target ! NewsMessage(s"information #$counter from $origin")
      counter += 1
  }
}

object Pub {
  def props(origin: String, target: ActorRef, interval: FiniteDuration) = 
    Props(classOf[Pub], origin, target, s"*/${interval.toSeconds} * * ? * *", interval)
}