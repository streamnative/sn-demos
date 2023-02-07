
Follow [this tutorial](https://docs.google.com/document/d/1yonds3Sk_aXGFWyCtl7buzTpJt6f5vUM_H8sryVNEwE/edit) to create a Pulsar cluster and a service account.


1. Add maven dependencies:

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.1.0</version>
</dependency>
<dependency>
    <groupId>io.streamnative.pulsar.handlers</groupId>
    <artifactId>oauth-client</artifactId>
    <version>2.8.3.1</version>
</dependency>
```


2. Create a consumer and subscribe the `test-kafka-topic` topic:

```java
package org.example;

import io.streamnative.pulsar.handlers.kop.security.oauth.OauthLoginCallbackHandler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * An OAuth2 authentication example of Kafka consumer to StreamNative Cloud
 */
public class SNCloudOAuth2Consumer {
    public static void main(String[] args) {
        // replace these config with your cluster
        String serverUrl = "kopyhshen-3d0a2d7c-2875-4caf-b74e-7d3260027a9a.gcp-shared-gcp-usce1-martin.streamnative.g.snio.cloud:9093";
        String keyPath = "/Users/labuladong/Downloads/sndev-donglai-admin-test.json";
        String audience = "urn:sn:pulsar:sndev:kop-test";

        // 1. Create properties of oauth2 authentication, which is equivalent to SASL/PLAIN mechanism in Kafka
        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverUrl);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "hello-world");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty("sasl.login.callback.handler.class", OauthLoginCallbackHandler.class.getName());
        props.setProperty("security.protocol", "SASL_SSL");
        props.setProperty("sasl.mechanism", "OAUTHBEARER");
        final String jaasTemplate = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + " oauth.issuer.url=\"%s\""
                + " oauth.credentials.url=\"%s\""
                + " oauth.audience=\"%s\";";
        props.setProperty("sasl.jaas.config", String.format(jaasTemplate,
                "https://auth.streamnative.cloud/",
                "file://" + keyPath,
                audience
        ));

        // 2. Create a consumer
        final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        final String topicName = "persistent://public/default/test-kafka-topic";
        consumer.subscribe(Collections.singleton(topicName));

        // 2. Consume some messages and quit immediately
        boolean running = true;
        while (running) {
            System.out.println("running");
            final ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            if (!records.isEmpty()) {
                records.forEach(record -> System.out.println("Receive record: " + record.value() + " from "
                        + record.topic() + "-" + record.partition() + "@" + record.offset()));
                running = false;
            }
        }
        consumer.close();
    }
}
```

`keyPath` is the key file path of your service account, `serverUrl` and `audience` can be found in [Streamnative Cloud Console](https://console.streamnative.cloud/clients?org=sndev&instance=kop-test&cluster=c7bbbdd7-a72e-4a3c-a9e6-611a46f9a83a&tenant=public&namespace=default&topic=kafka-topic3).

If everything goes well, the consumer will keep printing `running` because there is no producer send message to this topic.


3. Create a producer and send a message to `test-kafka-topic`:

```java
package org.example;

import io.streamnative.pulsar.handlers.kop.security.oauth.OauthLoginCallbackHandler;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * An OAuth2 authentication example of Kafka producer to StreamNative Cloud
 */
public class SNCloudOAuth2Producer {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        // 1. Create a producer with oauth2 authentication, which is equivalent to SASL/PLAIN mechanism in Kafka
        final Properties props = new Properties();
        // replace these config with your cluster
        String serverUrl =
                "kopyhshen-3d0a2d7c-2875-4caf-b74e-7d3260027a9a.gcp-shared-gcp-usce1-martin.streamnative.g.snio.cloud:9093";
        String keyPath = "/Users/labuladong/Downloads/sndev-donglai-admin-test.json";
        String audience = "urn:sn:pulsar:sndev:kop-test";

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverUrl);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.setProperty("sasl.login.callback.handler.class", OauthLoginCallbackHandler.class.getName());
        props.setProperty("security.protocol", "SASL_SSL");
        props.setProperty("sasl.mechanism", "OAUTHBEARER");
        final String jaasTemplate = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + " oauth.issuer.url=\"%s\""
                + " oauth.credentials.url=\"%s\""
                + " oauth.audience=\"%s\";";
        props.setProperty("sasl.jaas.config", String.format(jaasTemplate,
                "https://auth.streamnative.cloud/",
                "file://" + keyPath,
                audience
        ));
        final KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        // 2. Produce one message
        final String topicName = "persistent://public/default/kafka-topic3";
        final Future<RecordMetadata> recordMetadataFuture = producer.send(new ProducerRecord<>(topicName, "hello"));
        final RecordMetadata recordMetadata = recordMetadataFuture.get();
        System.out.println("Send hello to " + recordMetadata);
        producer.close();
    }
}
```

If everything goes well, the producer will exit with this output:

```shell
Send hello to persistent://public/default/test-kafka-topic-0@0
```

And the consumer will exit with this output:

```shell
running
running
running
Receive record: hello from persistent://public/default/test-kafka-topic-0@0
```