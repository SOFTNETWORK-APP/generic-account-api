package app.softnetwork.account.persistence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.persistence.query.{EventProcessorStream, JournalProvider}
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.account.handlers.AccountHandler
import app.softnetwork.account.message._
import app.softnetwork.scheduler.model.Schedule
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream

import scala.concurrent.Future

/** Created by smanciot on 01/03/2021.
  */
object AccountEventProcessorStreams {

  trait InternalAccountEvents2AccountProcessorStream
      extends EventProcessorStream[InternalAccountEvent]
      with AccountHandler { _: JournalProvider with CommandTypeKey[AccountCommand] =>

    def forTests: Boolean = false

    /** Processing event
      *
      * @param event
      *   - event to process
      * @param persistenceId
      *   - persistence id
      * @param sequenceNr
      *   - sequence number
      * @return
      */
    override protected def processEvent(
      event: InternalAccountEvent,
      persistenceId: PersistenceId,
      sequenceNr: Long
    ): Future[Done] = {
      import event._
      val command = WrapInternalAccountEvent(event)
      ?(uuid, command) map {
        case e: AccountErrorMessage =>
          logger.error(
            s"$platformEventProcessorId - command ${command.getClass} returns unexpectedly ${e.message}"
          )
          Done
        case r: AccountCommandResult =>
          if (forTests) system.eventStream.tell(Publish(r))
          Done
      }
    }
  }

  trait Scheduler2AccountProcessorStream
      extends Scheduler2EntityProcessorStream[AccountCommand, AccountCommandResult]
      with AccountHandler {
    _: JournalProvider with CommandTypeKey[AccountCommand] =>

    override protected def triggerSchedule(schedule: Schedule): Future[Boolean] = {
      !?(TriggerSchedule4Account(schedule)) map {
        case result: Schedule4AccountTriggered =>
          if (forTests) {
            system.eventStream.tell(Publish(result))
          }
          true
        case _ => false
      }
    }
  }
}
