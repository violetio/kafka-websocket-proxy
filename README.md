[![pipeline status](https://gitlab.com/kpmeen/kafka-websocket-proxy/badges/master/pipeline.svg)](https://gitlab.com/kpmeen/kafka-websocket-proxy/commits/master)
[![coverage report](https://gitlab.com/kpmeen/kafka-websocket-proxy/badges/master/coverage.svg)](https://gitlab.com/kpmeen/kafka-websocket-proxy/commits/master)

# Kafka WebSocket Proxy

The Kafka WebSocket Proxy is mainly created as a more efficient alternative to
the existing HTTP based REST proxy.

With WebSockets, the overhead of the HTTP protocol is removed. Instead, a much
"cheaper" TCP socket is setup between the client and proxy, through any
intermediate networking components.

Since WebSockets are bidirectional. They open up for client - server
implementations that are much closer to the regular Kafka client. The WebSocket
becomes more like an extension of the proxy-internal consumer/producer streams. 

## Getting started

TBD...

## Configuration

The Kafka WebSocket Proxy is built on Akka. More specifically:

* [akka-http](https://doc.akka.io/docs/akka-http/current/scala.html)
* [akka-streams](https://doc.akka.io/docs/akka/current/stream/index.html)
* [alpakka-kafka](https://doc.akka.io/docs/akka-stream-kafka/current/home.html)

All of these libraries/frameworks come with their own set of configuration
parameters and possibilities. In the Kafka WebSocket Proxy, these are kept in
separate configuration files to more easily find and adjust the configurations
for each of them.

The Kafka WebSocket Proxy itself is configured through the `application.conf`
file. Where the following parameters can be adjusted:

> NOTE:
> Some parameters are configurable through specific environment variables. 
> For a complete overview of all the configuration parameters, please refer to
> the `application.conf` file in `src/main/resources`.


 
| Config key                                                      | Environment                              | Default                  | Description   |
|:---                                                             |:----                                     |:------------------------:|:-----         |
| kafka.ws.proxy.server.server-id                                 | WSPROXY_SERVER_ID                        | `node-1`                 | A unique identifier for the specific kafka-websocket-proxy instance. |
| kafka.ws.proxy.server.port                                      | WSPROXY_PORT                             | `8078`                   | Port where the server endpoints will be exposed |
| kafka.ws.proxy.server.kafka-bootstrap-urls                      | WSPROXY_KAFKA_BOOTSTRAP_URLS             |                          | A string with the Kafka brokers to bootstrap against, in the form `<host>:<port>`, separated by comma. |
| kafka.ws.proxy.server.schema-registry-url                       | WSPROXY_SCHEMA_REGISTRY_URL              |                          | URLs for the Confluent Schema Registry. |
| kafka.ws.proxy.server.auto-register-schemas                     | WSPROXY_SCHEMA_AUTO_REGISTER             | `true`                   | By default, the proxy will automatically register any internal Avro schemas it needs. If disabled, these schemas must be registered with the schema registry manually. |
| kafka.ws.proxy.session-handler.session-state-topic-name         | WSPROXY_SESSION_STATE_TOPIC              | `_wsproxy.session.state` | The name of the compacted topic where session state is kept. |
| kafka.ws.proxy.session-handler.session-state-replication-factor | WSPROXY_SESSION_STATE_REPLICATION_FACTOR | `3`                      | How many replicas to keep for the session state topic. |
| kafka.ws.proxy.session-handler.session-state-retention          | WSPROXY_SESSION_STATE_RETENTION          | `30 days`                | How long to keep sessions in the session state topic. |
| kafka.ws.proxy.commit-handler.max-stack-size                    | WSPROXY_CH_MAX_STACK_SIZE                | `100`                    | The maximum number of uncommitted messages, per partition, that will be kept track of in the commit handler stack. |
| kafka.ws.proxy.commit-handler.auto-commit-enabled               | WSPROXY_CH_AUTOCOMMIT_ENABLED            | `false`                  | Whether or not to allow the proxy to perform automatic offset commits of uncommitted messages. |
| kafka.ws.proxy.commit-handler.auto-commit-interval              | WSPROXY_CH_AUTOCOMMIT_INTERVAL           | `1 second`               | The interval to execute the jobo for auto-committing messages of a given age. |
| kafka.ws.proxy.commit-handler.auto-commit-max-age               | WSPROXY_CH_AUTOCOMMIT_MAX_AGE            | `20 seconds`             | The max allowed age of uncommitted messages in the commit handler stack. |



### Kafka Security

The `kafka-websocket-proxy` allows setting Kafka client specific properties
under the key `kafka.ws.proxy.kafka-client-properties` in the `application.conf`
file. To connect to a secure Kafka cluster, the necessary security properties
should be added here. Below is a table containing the properties that are
currently possible to set using specific environment variables:
  

| Config key                            | Environment                              | Default      |
|:---                                   |:----                                     |:------------:|
| security.protocol                     | WSPROXY_KAFKA_SECURITY_PROTOCOL          | `PLAINTEXT`  |
| sasl.mechanism                        | WSPROXY_KAFKA_SASL_MECHANISM             |  not set     |
| sasl.jaas.config                      | WSPROXY_KAFKA_SASL_JAAS_CFG              |  not set     |
| ssl.key.password                      | WSPROXY_KAFKA_SSL_KEY_PASS               |  not set     |
| ssl.endpoint.identification.algorithm | WSPROXY_KAFKA_SASL_ENDPOINT_ID_ALOGO     |  not set     |
| ssl.truststore.location               | WSPROXY_KAFKA_SSL_TRUSTSTORE_LOCATION    |  not set     |
| ssl.truststore.truststore.password    | WSPROXY_KAFKA_SSL_TRUSTSTORE_PASS        |  not set     |
| ssl.keystore.location                 | WSPROXY_KAFKA_SSL_KEYSTORE_LOCATION      |  not set     |
| ssl.keystore.password                 | WSPROXY_KAFKA_SSL_KEYSTORE_PASS          |  not set     |

Additionally, each of the different clients (admin, producer and consumer), can
be configured individually. However, these configurations are not currently
exposed as environment variables.

The client specific configuration keys have the same structure as the
`kafka.ws.proxy.kafka-client-properties` key:

* `kafka.ws.proxy.admin-client.kafka-client-properties`
* `kafka.ws.proxy.consumer.kafka-client-properties`
* `kafka.ws.proxy.producer.kafka-client-properties`


## Endpoints and API

All endpoints are available at the following base URL:

```
ws://<hostname>:<port>/socket
```

**Format types**

The below table shows which data types are allowed as both key and value in
both inbound and outbound messages.

| Value     | Serde      | JSON type   |
|:--------- |:-----------|:----------- |
| json      | string     | json        |
| avro      | byte[]     | base64      |
| protobuf  | byte[]     | base64      |
| bytearray | byte[]     | base64      |
| string    | string     | string      |
| int       | int        | number      |
| short     | short      | number      |
| long      | long       | number      |
| double    | double     | number      |
| float     | float      | number      |

#### `/in`

**Query parameters**:

| Name    | Type        | Required | Default value |
|:------- |:----------- |:--------:|:------------- |
| topic   | string      |     y    |               |
| keyType | format type |     n    |               |
| valType | format type |     y    |               |

##### Input

```json
{
  "key": {
    "value":"foo",
    "format":"string"
  },
  "value": {
    "value":"bar",
    "format":"string"
  }
}
```

##### Output

```json
{
  "topic": "foo",
  "partition": 2,
  "offset": 4,
  "timestamp": 1554896731782
}
```

#### `/out`                                   

**Query parameters**:

| Name                | Type        | Required | Default value |
|:------------------- |:----------- |:--------:|:------------- |
| clientId            | string      |    y     |               |
| groupId             | string      |    n     |               |
| topic               | string      |    y     |               |
| keyType             | format type |    n     |               |
| valType             | format type |    y     |               |
| offsetResetStrategy | string      |    n     | earliest      |
| rate                | integer     |    n     |               |
| batchSize           | integer     |    n     |               |
| autoCommit          | boolean     |    n     | true          |

##### Output

```json
{
  "wsProxyMessageId": "foo-0-1-1554402266846",
  "topic": "foobar",
  "partition": 0,
  "offset": 1,
  "timestamp": 1554402266846,
  "key": { // optional
    "value": "foo",
    "format": "string" // optional
  },
  "value": {
    "value": "bar",
    "format": "string" // optional
  }
}
```

##### Input

```json
{"wsProxyMessageId":"foo-0-1-1554402266846"}
```

# Development

## Testing

Using a CLI tool called `wscat`, interacting with the `kafka-websocket-proxy` is
relatively simple. The tool is freely available on
[github](https://github.com/websockets/wscat), and is highly recommended.

```bash
npm install -g wscat
```


