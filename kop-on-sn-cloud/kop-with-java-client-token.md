
Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account.


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

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * An JWT token authentication example of Kafka consumer to StreamNative Cloud
 */
public class SNCloudJWTTokenConsumer {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        // replace these config with your cluster
        String serverUrl = "SERVER-URL"
	    String jwtToken = "YOUR-TOKEN"
        String token = "token:" + jwtToken;

        final String topicName = "persistent://public/default/test-kafka-topic";
        String namespace = "public/default";

        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverUrl);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "hello-world");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 2. Create a producer with token authentication, which is equivalent to SASL/PLAIN mechanism in Kafka
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                namespace, token));

        // 2. Create a consumer
        final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
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

The `SERVER-URL` can be found in StreamNative Cloud panel:

![](./images/broker-url.jpg)

The `YOUR-TOKEN` can be generated and copied in Service Account panel:

![](./images/token.jpg)

If everything goes well, the consumer will keep printing `running` because there is no producer send message to this topic.


3. Create a producer and send a message to `test-kafka-topic`:

```java
package org.example;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * An JWT token authentication example of Kafka producer to StreamNative Cloud
 */
public class SNCloudJWTTokenProducer {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        // replace these config with your cluster
        String serverUrl = "SERVER-URL"
	    String jwtToken = "YOUR-TOKEN"
        String token = "token:" + jwtToken;
        final String topicName = "persistent://public/default/test-kafka-topic";
        String namespace = "public/default";

        // 1. Create properties of oauth2 authentication, which is equivalent to SASL/PLAIN mechanism in Kafka
        // 2. Create a producer with token authentication, which is equivalent to SASL/PLAIN mechanism in Kafka
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverUrl);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                namespace, token));

        // 2. Create a producer
        final KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        // 3. Produce one message
        for (int i = 0; i < 5; i++) {
            String value = "hello world";
            final Future<RecordMetadata> recordMetadataFuture = producer.send(new ProducerRecord<>(topicName, value));
            final RecordMetadata recordMetadata = recordMetadataFuture.get();
            System.out.println("Send " + value + " to " + recordMetadata);
        }
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
