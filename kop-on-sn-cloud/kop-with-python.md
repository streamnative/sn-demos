Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account.

1️⃣ Install Kafka python client.

```shell
pip install confluent-kafka
```

2️⃣ Create a simple python application send one message to SN cloud, then read one message from SN cloud:

```python
from confluent_kafka import Producer, Consumer, KafkaError, KafkaException

# 1. replace with your config
serverUrl = "SERVER-URL"
jwtToken = "YOUR-TOKEN"
topicName = "persistent://public/default/connect-test"
namespace = "public/default"
password = "token:" + jwtToken

def error_cb(err):
    print("Client error: {}".format(err))
    if err.code() == KafkaError._ALL_BROKERS_DOWN or \
            err.code() == KafkaError._AUTHENTICATION:
        raise KafkaException(err)


# 2. create producer
p = Producer({
    'bootstrap.servers': serverUrl,
    'sasl.mechanism': 'PLAIN',
    'security.protocol': 'SASL_SSL',
    'sasl.username': namespace,
    'sasl.password': password,
})


def acked(err, msg):
    if err is not None:
        print('Failed to deliver message: {}'.format(err.str()))
    else:
        print('Produced to: {} [{}] @ {}'.format(msg.topic(), msg.partition(), msg.offset()))


# 3. send messages
p.produce(topicName, value='hello python', callback=acked)
p.flush(10)

# Create consumer
c = Consumer({
    'bootstrap.servers': serverUrl,
    'sasl.mechanism': 'PLAIN',
    'security.protocol': 'SASL_SSL',
    'sasl.username': namespace,
    'sasl.password': password,
    'group.id': 'test_group_id',  # this will create a new consumer group on each invocation.
    'auto.offset.reset': 'earliest',
    'error_cb': error_cb,
})

c.subscribe([topicName])

try:
    while True:
        print('polling...')
        # Wait for message or event/error
        msg = c.poll(1)
        if msg is None:
            continue
        print('Consumed: {}'.format(msg.value()))
        break

except KeyboardInterrupt:
    pass

finally:
    c.close()
```

The `SERVER-URL` can be found in StreamNative Cloud panel:

![](./images/broker-url.jpg)

The `YOUR-TOKEN` can be generated and copied in Service Account panel:

![](./images/token.jpg)


3️⃣ Run the application you can see the similar output:

```
Produced to: persistent://public/default/connect-test [0] @ 30
polling...
polling...
polling...
polling...
polling...
polling...
Consumed: b'hello world'
```