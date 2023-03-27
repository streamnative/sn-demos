## Create a Schema on AWS Glue Studio

1️⃣ Create a Schema Registry on AWS Glue Studio, sepcify your region and registry name:

![](./images/create-registry.jpg)

In this example I use `us-east-1` as my region and `test-registry` as my registry name.

Java client won't create schema automatically, so we have to create schema manually.

2️⃣ Click the `Add schema` button, I create a schema named `stream` with the following schema:

```json
{
  "namespace": "aws_schema_registry.integrationtests",
  "type": "record",
  "name": "StreamTest",
  "fields": [
    {
      "name": "word",
      "type": "string"
    },
    {
      "name": "count",
      "type": "int"
    }
  ]
}
```

This schema will be used for the producer/consumer when handle messages.

And you need to get the credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`) of your AWS account, which can be found in AWS console. 

You should set them as envoriment variables or install [AWS Toolkit for IDE](https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/key-tasks.html#key-tasks-install). The Java client need them to connect to Glue Schema Registry.

## Create a Pulsar Cluster on StreamNative Cloud

1️⃣ Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account.

2️⃣ You need some credentials to connect Pulsar cluster, which can be found in StreamNative Cloud panel. We will use them later.

The `YOUR-SERVER-URL` can be found in StreamNative Cloud panel:

![](../kop-on-sn-cloud/images/broker-url.jpg)

The `YOUR-KEY-FILE-PATH` is the local path of the OAuth key file of your servie account.

The `YOUR-AUDIENCE-STRING` can be found in StreamNative Cloud panel:

![](../kop-on-sn-cloud/images/audience.jpg)


### Write a Stream Application

1️⃣ Create a new Java project, add the following dependencies to `pom.xml`:


```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-streams</artifactId>
    <version>3.1.0</version>
</dependency>
<dependency>
    <groupId>io.streamnative.pulsar.handlers</groupId>
    <artifactId>oauth-client</artifactId>
    <version>2.9.1.5</version>
</dependency>

<!-- Kafka and Avro dependencies -->
<dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <version>1.11.0</version>
</dependency>

<!-- AWS Glue Schema Registry dependencies -->
<dependency>
    <groupId>software.amazon.glue</groupId>
    <artifactId>schema-registry-kafkastreams-serde</artifactId>
    <version>1.1.15</version>
</dependency>


<!-- Optional: add a slf4j dependency to see the log output -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-log4j12</artifactId>
    <version>1.7.30</version>
</dependency>

```

If you encounter the `java.lang.UnsatisfiedLinkError` when start stream application, you can add rocksDB dependeny to fix this error:

```xml
<dependency>
    <groupId>org.rocksdb</groupId>
    <artifactId>rocksdbjni</artifactId>
    <version>7.0.3</version>
</dependency>
```

2️⃣ Create a new Java steam application:

```java
package org.example;

import com.amazonaws.services.schemaregistry.kafkastreams.AWSKafkaAvroSerDe;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import com.amazonaws.services.schemaregistry.utils.AvroRecordType;
import io.streamnative.pulsar.handlers.kop.security.oauth.OauthLoginCallbackHandler;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class SNKafkaStreamAWS {

    public static void main(final String[] args) {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        // replace these config with your cluster
        String serverUrl = "YOUR-SERVER-URL";
        String keyPath = "YOUR-KEY-FILE-PATH";
        String audience = "YOUR-AUDIENCE-STRING";

        String inputTopic = "persistent://public/default/test-stream-with-glue";

        // 1. Create Kafka Stream properties
        Properties props = new Properties();
        // stream application name
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, serverUrl);

        // OAuth config
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

        // AWS glue schema config
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, AWSKafkaAvroSerDe.class.getName());

        props.put(AWSSchemaRegistryConstants.AWS_REGION, "us-east-1");
        props.put(AWSSchemaRegistryConstants.SCHEMA_AUTO_REGISTRATION_SETTING, true);
        props.put(AWSSchemaRegistryConstants.AVRO_RECORD_TYPE, AvroRecordType.GENERIC_RECORD.getName());


        // 2. Stream process
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, GenericRecord> recordStream = builder.stream(inputTopic);
            KTable<String, GenericRecord> wordCounts = recordStream
                    .map((key, value) -> new KeyValue<>(value.get("word").toString(), value))
                    .groupByKey()
                    .reduce((record, newRecord) -> {
                        // sum up the count field
                        Integer newCount = Integer.parseInt(record.get("count").toString()) + Integer.parseInt(
                                newRecord.get("count").toString());
                        record.put("count", newCount);
                        return record;
                    });

        // print the sum of each word
        wordCounts.toStream()
                .foreach((word, count) -> System.out.println("word: " + word + " -> " + count));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread("stream") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            // 3. Start the stream
            streams.start();
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

Run the application, it will wait new messages, you should not see any error messages.

3️⃣ Now, we need to send some messages to the topic `persistent://public/default/test-stream-with-glue`.

Create a `stream.avsc` file:

```json
{
  "type": "record",
  "name": "stream",
  "namespace": "org.example",
  "fields": [
    {
      "name": "word",
      "type": "string"
    },
    {
      "name": "count",
      "type": "int"
    }
  ]
}
```

Then create a producer to send messages to the topic `persistent://public/default/test-stream-with-glue`, the steps are similar to the [Java Kafak client with AWS Glue Schema Registry](./java-produce-consume-example.md) section:

```java
package org.example;

import com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import io.streamnative.pulsar.handlers.kop.security.oauth.OauthLoginCallbackHandler;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import kotlinx.serialization.SerializationException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import software.amazon.awssdk.services.glue.model.DataFormat;

/**
 * An OAuth2 authentication example of Kafka producer to StreamNative Cloud with AWS Glue Schema Registry.
 */
public class StreamProducerAWS {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        // replace these config with your cluster
        String serverUrl = "YOUR-SERVER-URL";
        String keyPath = "YOUR-KEY-FILE-PATH";
        String audience = "YOUR-AUDIENCE-STRING";
        String issueUrl = "https://auth.streamnative.cloud/";

        String topicName = "persistent://public/default/test-stream-with-glue";
        final String schemaName = "stream-schema";
        final String registryName = "test-registry";

        // 1. Create properties of StreamNative oauth2 authentication, which is equivalent to SASL/PLAIN mechanism in Kafka
        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverUrl);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.setProperty("sasl.login.callback.handler.class", OauthLoginCallbackHandler.class.getName());
        properties.setProperty("security.protocol", "SASL_SSL");
        properties.setProperty("sasl.mechanism", "OAUTHBEARER");
        final String jaasTemplate = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + " oauth.issuer.url=\"%s\""
                + " oauth.credentials.url=\"%s\""
                + " oauth.audience=\"%s\";";
        properties.setProperty("sasl.jaas.config", String.format(jaasTemplate,
                issueUrl,
                "file://" + keyPath,
                audience
        ));

        // 2. Set the schema registry properties

        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GlueSchemaRegistryKafkaSerializer.class.getName());
        properties.put(AWSSchemaRegistryConstants.DATA_FORMAT, DataFormat.AVRO.name());
        properties.put(AWSSchemaRegistryConstants.AWS_REGION, "us-east-1");
        properties.put(AWSSchemaRegistryConstants.REGISTRY_NAME, registryName);
        properties.put(AWSSchemaRegistryConstants.SCHEMA_NAME, schemaName);

        Schema streamSchema = null;
        Schema.Parser parser = new Schema.Parser();
        try {
            streamSchema = parser.parse(new File("src/main/resources/stream.avsc"));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }


        // 3. Create a producer and send messages
        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(properties)) {
            for (int i = 0; i < 4; i++) {
                GenericRecord r = new GenericData.Record(streamSchema);
                r.put("word", "cat");
                r.put("count", i);
                // 3. Produce one message
                final ProducerRecord<String, GenericRecord> record;
                record = new ProducerRecord<>(topicName, null, r);

                producer.send(record);
                System.out.println("Sent message " + i);
                Thread.sleep(1000L);
            }
            producer.flush();
            System.out.println("Successfully produced 4 messages to a topic called " + topicName);

        } catch (final InterruptedException | SerializationException e) {
            e.printStackTrace();
        }

    }
}
```

In this code snippet, we send 4 messages to the topic `persistent://public/default/test-stream-with-glue`, the `word` fild is `cat` and the `count` field is `0, 1, 2, 3`.

You should see the following output in you stream applicaiton console:

```text
word: cat -> {"word": "cat", "count": 0}
word: cat -> {"word": "cat", "count": 1}
word: cat -> {"word": "cat", "count": 3}
word: cat -> {"word": "cat", "count": 6}
```

The count will be sum up group by the word.