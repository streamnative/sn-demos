


## Create StreamNative Cloud Service Account

Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account.


## Download Kafka 3.1.0 and Kop dependency

1. Download Kafka tarball in `~/kafka`:

```bash
mkdir -p ~/kafka && cd ~/kafka
# download Kafka 3.1.0
curl -O https://archive.apache.org/dist/kafka/3.1.0/kafka_2.13-3.1.0.tgz
tar xzf ./kafka_2.13-3.1.0.tgz
```

2. Download supplementary libraries of Kop:

```bash
cd ~/kafka/kafka_2.13-3.1.0
# download supplementary libraries
curl -O https://repo1.maven.org/maven2/io/streamnative/pulsar/handlers/oauth-client/2.9.1.5/oauth-client-2.9.1.5.jar --output-dir ./libs
curl -O https://repo1.maven.org/maven2/org/apache/pulsar/pulsar-client-admin-api/2.9.2/pulsar-client-admin-api-2.9.2.jar --output-dir ./libs
curl -O https://repo1.maven.org/maven2/org/apache/pulsar/pulsar-client/2.9.2/pulsar-client-2.9.2.jar --output-dir ./libs
curl -O https://repo1.maven.org/maven2/org/apache/pulsar/pulsar-client-api/2.9.2/pulsar-client-api-2.9.2.jar --output-dir ./libs
```


## Config OAuth2 and test connection

1. Create OAuth configuration file:

```bash
# configure kafka.properties file.
echo 'sasl.login.callback.handler.class=io.streamnative.pulsar.handlers.kop.security.oauth.OauthLoginCallbackHandler
security.protocol=SASL_SSL
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule \
   required oauth.issuer.url="https://auth.streamnative.cloud/"\
   oauth.credentials.url="file:///YOUR-KEY-FILE-PATH"\
   oauth.audience="YOUR-AUDIENCE-STRING";' > ~/kafka/kafka.properties
```

Besure to replace `YOUR-KEY-FILE-PATH` and `YOUR-AUDIENCE-STRING`:

The `YOUR-KEY-FILE-PATH` is the path of the OAuth key file of your servie account.

The `YOUR-AUDIENCE-STRING` can be found in StreamNative Cloud panel:

![](./images/audience.jpg)


1. Run a Kafka consumer start to receive from `kop-test-topic`:

```bash
# run consumer
~/kafka/kafka_2.13-3.1.0/bin/kafka-console-consumer.sh \
    --bootstrap-server "SERVER-URL" \
    --consumer.config ~/kafka/kafka.properties \
    --topic kop-test-topic
```


You can get your `SERVER-URL` in StreamNative Cloud panel:

![](./images/broker-url.jpg)


1. Run a Kafka producer on another terminal:

```bash
# run producer
~/kafka/kafka_2.13-3.1.0/bin/kafka-console-producer.sh \
    --bootstrap-server "SERVER-URL" \
    --producer.config ~/kafka/kafka.properties \
    --topic kop-test-topic
```

You can type some messages, `enter` to send:

```bash
> test message
```

Then the consumer will receive the message.
