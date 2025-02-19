package net.scalytica.kafka.wsproxy.session

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.ProducerSettings
import net.scalytica.kafka.wsproxy._
import net.scalytica.kafka.wsproxy.codecs.{BasicSerdes, SessionSerde}
import net.scalytica.kafka.wsproxy.config.Configuration.AppCfg
import net.scalytica.kafka.wsproxy.logging.WithProxyLogger
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

private[session] class SessionDataProducer(
    implicit cfg: AppCfg,
    sys: ActorSystem[_]
) extends WithProxyLogger {

  private[this] val kSer = BasicSerdes.StringSerializer
  private[this] val vSer = new SessionSerde().serializer()

  private[this] val kafkaUrl = cfg.kafkaClient.bootstrapHosts.mkString()

  private[this] val sessionStateTopic =
    cfg.sessionHandler.sessionStateTopicName.value

  private[this] lazy val producerProps =
    ProducerSettings(sys.toClassic, Some(kSer), Some(vSer))
      .withBootstrapServers(kafkaUrl)
      .withProducerFactory(initialiseProducer)

  private[this] def initialiseProducer(
      ps: ProducerSettings[String, Session]
  )(implicit cfg: AppCfg): KafkaProducer[String, Session] = {
    val props =
      cfg.producer.kafkaClientProperties ++
        ps.getProperties.asScala.toMap ++
        producerMetricsProperties

    logger.trace(s"Using producer configuration:\n${props.mkString("\n")}")

    new KafkaProducer[String, Session](
      props,
      ps.keySerializerOpt.orNull,
      ps.valueSerializerOpt.orNull
    )
  }

  private[this] lazy val producer = producerProps.createKafkaProducer()

  /**
   * Writes the [[Session]] data to the session state topic in Kafka.
   *
   * @param session
   *   Session to write
   * @param ec
   *   The [[ExecutionContext]] to use
   * @return
   *   eventually returns [[Done]] when successfully completed
   */
  def publish(
      session: Session
  )(implicit ec: ExecutionContext): Future[Done] = {
    val record = new ProducerRecord[String, Session](
      sessionStateTopic,
      session.sessionId.value,
      session
    )

    val res = producer.send(record).toScalaFuture

    res.onComplete {
      case Success(rm) =>
        logger.debug(
          "Successfully sent session record for session id" +
            s" ${session.sessionId.value} to Kafka. [" +
            s"topic: ${rm.topic()}," +
            s"partition: ${rm.partition()}," +
            s"offset: ${rm.offset()}" +
            "]"
        )
        logger.trace(s"Session data written was: $session")

      case Failure(ex) =>
        logger.error(
          "Failed to send session record for session id" +
            s" ${session.sessionId.value} to Kafka",
          ex
        )
    }

    res.map(_ => Done)
  }

  def publishRemoval(
      sessionId: SessionId
  )(implicit ec: ExecutionContext): Unit = {
    val record = new ProducerRecord[String, Session](
      sessionStateTopic,
      sessionId.value,
      null // scalastyle:ignore
    )
    producer.send(record).toScalaFuture.onComplete {
      case Success(_) =>
        logger.debug(
          s"Successfully sent tombstone for session id ${sessionId.value}" +
            " to Kafka"
        )

      case Failure(ex) =>
        logger.error(
          s"Failed to send tombstone for session id ${sessionId.value}" +
            " to Kafka",
          ex
        )
    }
  }

  /** Closes the underlying Kafka producer */
  def close(): Unit = producer.close()

}
