package net.scalytica.kafka.wsproxy.session

import net.scalytica.kafka.wsproxy.logging.WithProxyLogger
import net.scalytica.kafka.wsproxy.models.{WsClientId, WsGroupId, WsServerId}

/**
 * Defines the common attributes and functions for session data used to keep
 * track of active Kafka clients.
 */
sealed trait Session { self =>

  protected val logger = WithProxyLogger.namedLoggerFor[self.type]

  def sessionId: SessionId
  def maxConnections: Int
  // val rateLimit: Int

  def instances: Set[ClientInstance]

  def canOpenSocket: Boolean = {
    logger.trace(
      "Validating if session can open connection:" +
        s" instances=${instances.size + 1} maxConnections=$maxConnections"
    )
    maxConnections == 0 || (instances.size + 1) <= maxConnections
  }

  def hasInstance(consumerId: WsClientId): Boolean = {
    instances.exists(_.clientId == consumerId)
  }

  def updateInstances(updated: Set[ClientInstance]): Session

  def addInstance(clientId: WsClientId, serverId: WsServerId): SessionOpResult

  def addInstance(instance: ClientInstance): SessionOpResult

  def removeInstance(clientId: WsClientId): SessionOpResult = {
    if (hasInstance(clientId)) {
      InstanceRemoved(
        updateInstances(instances.filterNot(_.clientId == clientId))
      )
    } else {
      InstanceDoesNotExists(self)
    }
  }

  def removeInstancesFromServerId(serverId: WsServerId): Session = {
    updateInstances(instances.filterNot(_.serverId == serverId))
  }
}

/**
 * Defines the shape of a Kafka consumer session with 1 or more clients.
 *
 * @param sessionId
 *   The session ID for this session
 * @param groupId
 *   The group ID this session applies to
 * @param maxConnections
 *   The maximum number of allowed connections. 0 means unlimited
 * @param instances
 *   The active client instances
 */
case class ConsumerSession private (
    sessionId: SessionId,
    groupId: WsGroupId,
    maxConnections: Int = 2,
    instances: Set[ClientInstance] = Set.empty
) extends Session {

  require(instances.forall(_.isInstanceOf[ConsumerInstance]))

  override def updateInstances(updated: Set[ClientInstance]): Session = {
    copy(instances = updated)
  }

  override def addInstance(
      clientId: WsClientId,
      serverId: WsServerId
  ) = addInstance(ConsumerInstance(clientId, groupId, serverId))

  override def addInstance(instance: ClientInstance) = {
    instance match {
      case ci: ConsumerInstance =>
        if (hasInstance(ci.clientId)) InstanceExists(this)
        else {
          if (canOpenSocket) {
            InstanceAdded(updateInstances(instances + ci))
          } else {
            InstanceLimitReached(this)
          }
        }

      case _ =>
        InstanceTypeForSessionIncorrect(this)
    }
  }
}

/**
 * Defines the shape of a Kafka producer session with 1 or more clients.
 *
 * @param sessionId
 *   The session ID for this session
 * @param maxConnections
 *   The maximum number of allowed connections. 0 means unlimited
 * @param instances
 *   The active client instances
 */
case class ProducerSession(
    sessionId: SessionId,
    maxConnections: Int = 1,
    instances: Set[ClientInstance] = Set.empty
) extends Session {

  require(instances.forall(_.isInstanceOf[ProducerInstance]))

  override def updateInstances(updated: Set[ClientInstance]): Session = {
    copy(instances = updated)
  }

  override def addInstance(
      producerId: WsClientId,
      serverId: WsServerId
  ) = addInstance(ProducerInstance(producerId, serverId))

  override def addInstance(instance: ClientInstance) = {
    logger.trace(s"Attempting to add producer client: $instance")
    instance match {
      case pi: ProducerInstance =>
        if (hasInstance(pi.clientId)) InstanceExists(this)
        else {
          if (canOpenSocket) {
            InstanceAdded(updateInstances(instances + pi))
          } else {
            logger.warn(s"Client limit was reached for producer $instance")
            InstanceLimitReached(this)
          }
        }

      case _ =>
        InstanceTypeForSessionIncorrect(this)
    }
  }
}
