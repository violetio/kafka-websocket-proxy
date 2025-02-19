package net.scalytica.kafka.wsproxy.web

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import net.scalytica.kafka.wsproxy.web.SocketProtocol._
import net.scalytica.kafka.wsproxy.models.Formats._
import net.scalytica.kafka.wsproxy.models._
import net.scalytica.kafka.wsproxy.session.SessionId
import org.apache.kafka.clients.consumer.OffsetResetStrategy

import scala.util.Try

trait QueryParamParsers {

  implicit val clientIdUnmarshaller: Unmarshaller[String, WsClientId] =
    Unmarshaller.strict[String, WsClientId] { str =>
      Option(str).map(WsClientId.apply).getOrElse {
        throw Unmarshaller.NoContentException
      }
    }

  implicit val groupIdUnmarshaller: Unmarshaller[String, WsGroupId] =
    Unmarshaller.strict[String, WsGroupId] { str =>
      Option(str).map(WsGroupId.apply).getOrElse {
        throw Unmarshaller.NoContentException
      }
    }

  implicit val topicNameUnmarshaller: Unmarshaller[String, TopicName] =
    Unmarshaller.strict[String, TopicName] { str =>
      Option(str).map(TopicName.apply).getOrElse {
        throw Unmarshaller.NoContentException
      }
    }

  // Unmarshaller for SocketPayload query params
  implicit val socketPayloadUnmarshaller: Unmarshaller[String, SocketPayload] =
    Unmarshaller.strict[String, SocketPayload] { str =>
      Option(str).map(SocketPayload.unsafeFromString).getOrElse {
        throw Unmarshaller.NoContentException
      }
    }

  // Unmarshaller for FormatType query params
  implicit val formatTypeUnmarshaller: Unmarshaller[String, FormatType] =
    Unmarshaller.strict[String, FormatType] { str =>
      Option(str).map(FormatType.unsafeFromString).getOrElse {
        throw Unmarshaller.NoContentException
      }
    }

  // Unmarshaller for OffsetResetStrategy params
  implicit val offResetUnmarshaller: Unmarshaller[String, OffsetResetStrategy] =
    Unmarshaller.strict[String, OffsetResetStrategy] { str =>
      Try(OffsetResetStrategy.valueOf(str.toUpperCase)).getOrElse {
        throw new IllegalArgumentException(
          s"$str is not a valid offset reset strategy"
        )
      }
    }

  sealed trait ParamError
  case object InvalidPath          extends ParamError
  case object MissingRequiredParam extends ParamError

  case class ConsumerParamError(
      sessionId: SessionId,
      wsClientId: WsClientId,
      wsGroupId: WsGroupId
  ) extends ParamError

  case class ProducerParamError(
      sessionId: SessionId,
      wsClientId: WsClientId
  ) extends ParamError

  def paramsOnError(
      request: HttpRequest
  ): Directive[Tuple1[ParamError]] = {
    if (request.uri.path.endsWith("in", ignoreTrailingSlash = true)) {
      parameters(
        Symbol("clientId").as[WsClientId] ?
      ).tmap { t =>
        t._1
          .map[ParamError] { cid =>
            ProducerParamError(SessionId.forProducer(None)(cid), cid)
          }
          .getOrElse(MissingRequiredParam)
      }
    } else if (request.uri.path.endsWith("out", ignoreTrailingSlash = true)) {
      parameters(
        Symbol("clientId").as[WsClientId] ?,
        Symbol("groupId").as[WsGroupId] ?
      ).tmap { t =>
        t._1
          .map[ParamError] { cid =>
            ConsumerParamError(
              SessionId.forConsumer(None)(cid),
              cid,
              WsGroupId.fromOption(t._2)(cid)
            )
          }
          .getOrElse(MissingRequiredParam)
      }
    } else {
      Directives.provide[ParamError](InvalidPath)
    }
  }

  /**
   * @return
   *   Directive extracting query parameters for the outbound (consuming) socket
   *   communication.
   */
  def outParams: Directive[Tuple1[OutSocketArgs]] =
    parameters(
      Symbol("clientId").as[WsClientId],
      Symbol("groupId").as[WsGroupId] ?,
      Symbol("topic").as[TopicName],
      Symbol("socketPayload").as[SocketPayload] ? (JsonPayload: SocketPayload),
      Symbol("keyType").as[FormatType] ?,
      Symbol("valType").as[FormatType] ? (StringType: FormatType),
      Symbol("offsetResetStrategy")
        .as[OffsetResetStrategy] ? OffsetResetStrategy.EARLIEST,
      Symbol("rate").as[Int] ?,
      Symbol("batchSize").as[Int] ?,
      Symbol("autoCommit").as[Boolean] ? true
    ).tmap(OutSocketArgs.fromTupledQueryParams)

  /**
   * @return
   *   Directive extracting query parameters for the inbound (producer) socket
   *   communication.
   */
  def inParams: Directive[Tuple1[InSocketArgs]] =
    parameters(
      Symbol("clientId").as[WsClientId],
      Symbol("topic").as[TopicName],
      Symbol("socketPayload").as[SocketPayload] ? (JsonPayload: SocketPayload),
      Symbol("keyType").as[FormatType] ?,
      Symbol("valType").as[FormatType] ? (StringType: FormatType)
    ).tmap(InSocketArgs.fromTupledQueryParams)

}
