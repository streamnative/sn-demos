
Follow [this tutorial](https://docs.google.com/document/d/1yonds3Sk_aXGFWyCtl7buzTpJt6f5vUM_H8sryVNEwE/edit) to create a Pulsar cluster and a service account.

1️⃣ Install Kafka go client.

```shell
go get -u github.com/confluentinc/confluent-kafka-go/v2/kafka
```

2️⃣ Create a simple go application send one message to SN cloud, then read one message from SN cloud:

```go
package main

import (
	"fmt"
	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"time"
)

func main() {
	// 1. replace with your config
	serverUrl := "SERVER-URL"
	jwtToken := "YOUR-TOKEN"
	topicName := "persistent://public/default/connect-test"
	namespace := "public/default"
	password := "token:" + jwtToken

	// 2. create a producer to send messages
	producer, err := kafka.NewProducer(&kafka.ConfigMap{
		"bootstrap.servers": serverUrl,
		"security.protocol": "SASL_SSL",
		"sasl.mechanism":    "PLAIN",
		"sasl.username":     namespace,
		"sasl.password":     password,
	})
	if err != nil {
		panic(err)
	}
	defer producer.Close()

	err = producer.Produce(&kafka.Message{
		TopicPartition: kafka.TopicPartition{Topic: &topicName, Partition: kafka.PartitionAny},
		Value:          []byte("hello world"),
	}, nil)

	if err != nil {
		panic(err)
	}
	producer.Flush(1000)

	// wait for delivery report
	e := <-producer.Events()

	message := e.(*kafka.Message)
	if message.TopicPartition.Error != nil {
		fmt.Printf("failed to deliver message: %v\n",
			message.TopicPartition)
	} else {
		fmt.Printf("delivered to topic %s [%d] at offset %v\n",
			*message.TopicPartition.Topic,
			message.TopicPartition.Partition,
			message.TopicPartition.Offset)
	}

	// 3. create a consumer to read messages
	consumer, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers":  serverUrl,
		"security.protocol":  "SASL_SSL",
		"sasl.mechanisms":    "PLAIN",
		"sasl.username":      namespace,
		"sasl.password":      password,
		"session.timeout.ms": 6000,
		"group.id":           "my-group",
		"auto.offset.reset":  "earliest",
	})

	if err != nil {
		panic(fmt.Sprintf("Failed to create consumer: %s", err))
	}
	defer consumer.Close()

	topics := []string{topicName}
	err = consumer.SubscribeTopics(topics, nil)
	if err != nil {
		panic(fmt.Sprintf("Failed to subscribe topics: %s", err))
	}

	// read one message then exit
	for {
		fmt.Println("polling...")
		message, err = consumer.ReadMessage(1 * time.Second)
		if err == nil {
			fmt.Printf("consumed from topic %s [%d] at offset %v: %+v",
				*message.TopicPartition.Topic,
				message.TopicPartition.Partition, message.TopicPartition.Offset, string(message.Value))
			break
		}
	}
}
```

The `SERVER-URL` can be found in StreamNative Cloud panel:

![](./images/broker-url.jpg)

The `YOUR-TOKEN` can be generated and copied in Service Account panel:

![](./images/token.jpg)

3️⃣ Run the application, you can see the similar output:

```shell
delivered to topic persistent://public/default/connect-test [0] at offset 29
polling...
polling...
polling...
polling...
polling...
polling...
polling...
consumed from topic persistent://public/default/connect-test [0] at offset 15: hello world
```