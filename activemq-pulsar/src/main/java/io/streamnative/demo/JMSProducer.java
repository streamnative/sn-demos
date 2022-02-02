package io.streamnative.demo;

import com.datastax.oss.pulsar.jms.PulsarConnectionFactory;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

public class JMSProducer {
    // ActiveMQ Cluster
    private static final String BROKER_URL = ActiveMQConnection.DEFAULT_BROKER_URL;
    private static final String MESSAGE_QUEUE = "test_queue";

    // Pulsar Cluster
    private static final String PULSAR_BROKER_URL = "pulsar://localhost:6650";
    private static final String PULSAR_ADMIN_URL = "http://localhost:8080";

    public static void main(String[] args) throws JMSException {
        // Getting JMS connection from the server and starting it
        if (args.length != 1) {
            System.err.println("Usage: JMSProducer [activemq|pulsar]");
            return;
        }

        ConnectionFactory connectionFactory;
        if ("activemq".equalsIgnoreCase(args[0])) {
            connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
        } else if ("pulsar".equalsIgnoreCase(args[0])) {
            // Use JMS client to connect to Pulsar
            Map<String, Object> properties = new HashMap<>();
            properties.put("brokerServiceUrl", PULSAR_BROKER_URL);
            properties.put("webServiceUrl", PULSAR_ADMIN_URL);
            connectionFactory = new PulsarConnectionFactory(properties);
        } else {
            throw new UnsupportedOperationException("Unknown JMS drive: " + args[0]);
        }

        Connection connection = connectionFactory.createConnection();
        connection.start();

        //Creating a non-transactional session to send/receive JMS message.
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination destination = session.createQueue(MESSAGE_QUEUE);
        MessageProducer producer = session.createProducer(destination);

        for (int i = 1; i <= 1000; i++) {
            TextMessage message = session
                    .createTextMessage("This is message " + i);
            producer.send(message);
            System.out.println("Send message: " + message.getText());
        }
        connection.close();
        if (connectionFactory instanceof PulsarConnectionFactory) {
            ((PulsarConnectionFactory) connectionFactory).close();
        }
    }
}
