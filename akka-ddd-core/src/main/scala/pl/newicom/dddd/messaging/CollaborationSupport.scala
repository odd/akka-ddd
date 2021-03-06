package pl.newicom.dddd.messaging

import akka.actor.Stash

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Failure

trait CollaborationSupport extends Stash {

  def receiveNext(receive: Receive)(implicit timeout: FiniteDuration = 10.seconds): Unit = {
    import ExecutionContext.Implicits.global
    scheduler.scheduleOnce(timeout, self, Failure(new RuntimeException("time out")))

    context.become (
      receive andThen {
        case _ =>
          unstashAll()
          context.unbecome()
      } orElse {
        case Failure(reason) =>
          //unstashAll() and context.unbecome() will be called automatically
          throw reason;
        case _ =>
          stash()
      }
      , discardOld = false)
  }

  private def scheduler = context.system.scheduler
}
