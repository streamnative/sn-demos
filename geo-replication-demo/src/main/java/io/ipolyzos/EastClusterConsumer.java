package io.ipolyzos;

import java.nio.charset.Charset;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;

public class EastClusterConsumer {
    private static final String BROKER_URL = "pulsar://localhost:6651";
    private static final String topicName = "persistent://testt/testns/t1";

    public static void main(String[] args) throws PulsarClientException {
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(BROKER_URL)
                .build();

        Consumer<byte[]> consumer = pulsarClient.newConsumer(Schema.BYTES)
                .topic(topicName)
                .consumerName("test-consumer")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscriptionName("test-subscription")
                .subscribe();

        int messageCount = 0;
        while (true) {
            Message<byte[]> message = consumer.receive();
            System.out.println("Received message: " + new String(message.getData(), Charset.defaultCharset()) + " - Total messages " + messageCount);
            messageCount += 1;
            try {
                consumer.acknowledge(message);
            } catch (Exception e) {
                consumer.negativeAcknowledge(message);
            }
        }
    }
}
