package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.delivery.AtLeastOnceDeliverySupport
import pl.newicom.dddd.messaging.event.{EventMessage, EventStreamSubscriber}
import pl.newicom.dddd.messaging.{Message, MetaData}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.process.ReceptorConfig.{StimuliSource, Transduction}
import pl.newicom.dddd.serialization.JsonSerializationHints

object ReceptorConfig {
  type Transduction = PartialFunction[EventMessage, Message]
  type StimuliSource[A] = OfficeInfo[A]
}

abstract class ReceptorConfig {
  def stimuliSource: String
  def transduction: Transduction
  def receiver: ActorPath
  def serializationHints: JsonSerializationHints
}

trait ReceptorGrammar {
  def reactTo[A : StimuliSource]:                    ReceptorGrammar
  def applyTransduction(transduction: Transduction): ReceptorGrammar
  def propagateTo(receiver: ActorPath):              ReceptorConfig
}

object ReceptorBuilder {
  def apply(): ReceptorBuilder = ReceptorBuilder(null, null, null, null)
}

case class ReceptorBuilder(
  stimuliSource: String,
  transduction: Transduction,
  receiver: ActorPath,
  serializationHints: JsonSerializationHints) extends ReceptorGrammar {

  def reactTo[A : StimuliSource] = {
    val source: StimuliSource[_] = implicitly[StimuliSource[_]]
    copy(stimuliSource = source.streamName, serializationHints = source.serializationHints)
  }

  def applyTransduction(transduction: Transduction) =
    copy(transduction = transduction)

  def propagateTo(receiver: ActorPath) =
    new ReceptorConfig() {
      override def stimuliSource: String = ReceptorBuilder.this.stimuliSource
      override def transduction: Transduction = ReceptorBuilder.this.transduction
      override def serializationHints = ReceptorBuilder.this.serializationHints
      override def receiver: ActorPath = ReceptorBuilder.this.receiver
    }
}

abstract class Receptor(val config: ReceptorConfig) extends AtLeastOnceDeliverySupport {
  this: EventStreamSubscriber =>

  override val destination = config.receiver

  override def persistenceId: String = s"Receptor-${config.stimuliSource}"

  override def recoveryCompleted(): Unit =
    subscribe(config.stimuliSource, lastSentDeliveryId)

  override def receiveCommand: Receive =
    receiveEvent(metaDataProvider).orElse(deliveryStateReceive).orElse {
      case other =>
        log.warning(s"RECEIVED: $other")
    }

  override def eventReceived(em: EventMessage, position: Long): Unit =
    config.transduction.lift(em).foreach { msg =>
      deliver(msg, deliveryId = position)
    }

  def metaDataProvider(em: EventMessage): Option[MetaData] = None

}