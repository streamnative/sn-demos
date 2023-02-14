Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account.

1️⃣ Install Kafka Nodejs client.

```bash
npm install kafkajs
```

2️⃣ Create a simple Nodejs application send one message to SN cloud, then read one message from SN cloud:

```js
const { Kafka } = require('kafkajs');

// replace with your configuration
let serverUrl = "SERVER-URL"
let jwtToken = "YOUR-TOKEN"
let topicName = "persistent://public/default/test-js-topic"

// create the kafka client
const kafka = new Kafka({
    clientId: 'my-app',
    brokers: [serverUrl],
    ssl: true,
    sasl: {
        mechanism: 'oauthbearer',
        oauthBearerProvider: async () => {
            return {
                value: jwtToken,
            };
        },
    },
});

// send a message to topic 
async function send() {
    const producer = kafka.producer();
    await producer.connect();

    let resp = await producer.send({
        topic: topicName,
        messages: [
            {
                value: 'Hello KafkaJS user!'
            },
        ],
    });

    console.log(`Send message:`, resp);

    await producer.disconnect();
}

// read messages from the beginning of the topic 
async function receive() {
    const consumer = kafka.consumer({ groupId: 'my-group' });
    await consumer.connect();
    console.log('Connected to Kafka');

    await consumer.subscribe({ topic: topicName, fromBeginning: true });

    await consumer.run({
        eachMessage: async ({ topic, partition, message }) => {
            console.log(`Received message:`, {
                value: message.value.toString(),
                headers: message.headers,
                topic: topic,
                partition: partition,
                offset: message.offset
            });
        },
    });
}

// send one message then receive
send().then(function () {
    receive()
})
```

The `SERVER-URL` can be found in StreamNative Cloud panel:

![](./images/broker-url.jpg)

The `YOUR-TOKEN` can be generated and copied in Service Account panel:

![](./images/token.jpg)

3️⃣ Assume you save the script as `kop_test.js`, you can run the application with `node`:

```bash
node kop_test.js
```

You can see the similar output:

```shell
Send message: [
  {
    topicName: 'persistent://public/default/test-js-topic',
    partition: 0,
    errorCode: 0,
    baseOffset: '7',
    logAppendTime: '-1',
    logStartOffset: '-1'
  }
]
Connected to Kafka
Received message: {
  value: 'Hello KafkaJS user!',
  headers: {},
  topic: 'persistent://public/default/test-js-topic',
  partition: 0,
  offset: '0'
}
...
```