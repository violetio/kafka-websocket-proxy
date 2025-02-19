package net.scalytica.kafka.wsproxy.codecs

import java.util.{Map => JMap}

import org.apache.kafka.common.serialization.{
  Deserializer,
  Serde,
  Serdes => KSerdes,
  Serializer
}

/** Scaffolding for Serdes based on the basic String Serde */
trait StringBasedSerializer[T] extends Serializer[T] {
  protected val ser = KSerdes.String().serializer()

  override def configure(configs: JMap[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}

trait StringBasedDeserializer[T] extends Deserializer[T] {
  protected val des = KSerdes.String().deserializer()

  override def configure(configs: JMap[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}

trait StringBasedSerde[T]
    extends Serde[T]
    with StringBasedSerializer[T]
    with StringBasedDeserializer[T] {

  override def serializer()   = this
  override def deserializer() = this

  override def configure(configs: JMap[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}
