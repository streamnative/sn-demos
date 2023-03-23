## Create a Schema Registry on AWS Glue Studio

Create a Schema Registry on AWS Glue Studio, sepcify your region and registry name:

![](../images/create-registry.jpg)

You don't need to create schema manually, kafka python client will create schema automatically.

And you need to get the credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`) of your AWS account, which can be found in AWS console. We will use them later.

## Create a Pulsar Cluster on StreamNative Cloud

Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account.

You need some credentials to connect Pulsar cluster, which can be found in StreamNative Cloud panel. We will use them later.

The `SERVER-URL` can be found in StreamNative Cloud panel:

![](../kop-on-sn-cloud/images/broker-url.jpg)

The `YOUR-TOKEN` can be generated and copied in Service Account panel:

![](../kop-on-sn-cloud/images/token.jpg)


## Config Python kafka client to conect Pulsar and Schema Registry

In this example we will use kafka-python as our Kafka client, so we need to have the `kafka-python` extras installed and use the kafka adapter.

```bash
pip3 install boto3 aws-glue-schema-registry kafka-python
```

Before see the code, there are some important things:

1️⃣ The Python Kafka client don't support StreamNative OAuth yet, so you can use `PLAIN` as `sasl_mechanism`. The username is the namespace, the password is the JWT token with a `token:` prefix.

2️⃣ There are some regex check for Kafka client, which is not compatible with Pulsar. So you need to change some code of `kafka-python` package.

3️⃣ We can create a schema file `user.avsc` for consumer/producer:

```json
{
  "namespace": "aws_schema_registry.integrationtests",
  "type": "record",
  "name": "User",
  "fields": [
    {"name": "name", "type": "string"},
    {"name": "Age", "type": "int"}
  ]
}
```

Now we can see the code, there are detailed notes in the code:

```python
import re

from aws_schema_registry import DataAndSchema, SchemaRegistryClient
from aws_schema_registry.avro import AvroSchema
from aws_schema_registry.schema import Schema

# In this example we will use kafka-python as our Kafka client,
# so we need to have the `kafka-python` extras installed and use
# the kafka adapter.
from aws_schema_registry.adapter.kafka import KafkaSerializer, KafkaDeserializer
from kafka import KafkaConsumer, KafkaProducer, TopicPartition, OffsetAndMetadata
import boto3
from kafka.consumer.subscription_state import SubscriptionState

# copy your AWS credentials from the AWS console
AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID"
AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY"
AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN"

# copy your StreamNative Cloud credentials from https://console.streamnative.cloud/
serverUrl = "SERVER-URL"
jwtToken = "YOUR-TOKEN"
namespace = "public/default"
password = "token:" + jwtToken

# our test topic
topicName = "persistent://" + namespace + "/glue-test"
streamnative_config = {
    'security_protocol': 'SASL_SSL',
    'sasl_mechanism': 'PLAIN',
    'sasl_plain_username': namespace,
    'sasl_plain_password': password,
}


# default kafka topic name regex pattern is "^[a-zA-Z0-9._-]+$", which is not enough for pulsar topic name
# so we need to override the original regex pattern to fit "persistent://xx/xx/xx" format
SubscriptionState._TOPIC_LEGAL_CHARS = re.compile('^[:/a-zA-Z0-9._-]+$')

# the default schema naming strategy in AWS glue is {topic-name}-{key|value}
# and only contains (A - Z)(0 - 9)(-)(_)($)(.)(#)
# but pulsar topic name is like persistent://public/default/test-topic, which contains invalid characters for AWS glue
# so we write a custom naming strategy to replace invalid characters
def schema_naming_strategy_for_streamnative_cloud(topic: str, is_key: bool, schema: Schema) -> str:
    """The naming strategy for StreamNative Cloud.

    Message keys are `<topic>-key` and message values are
    `<topic>-value`.

    This is a sensible strategy for topics whose records follow a uniform
    schema, but does not allow mixing different schemas on the same topic.
    """
    # replace pulsar topic name to a valid glue schema name
    #
    topic = topic.replace("://", "-")
    topic = topic.replace("/", "-")
    return f"{topic}-{'key' if is_key else 'value'}"

# create boto3 glue client
session = boto3.Session(aws_access_key_id=AWS_ACCESS_KEY_ID, aws_secret_access_key=AWS_SECRET_ACCESS_KEY,
                        aws_session_token=AWS_SESSION_TOKEN)
glue_client = session.client('glue')
# Create the schema registry client, which is a façade around the boto3 glue client
client = SchemaRegistryClient(glue_client, registry_name='test-registry')


def send_one_message():
    # Create the serializer, set the naming strategy for StreamNative Cloud
    serializer = KafkaSerializer(client, schema_naming_strategy=schema_naming_strategy_for_streamnative_cloud)

    producer_config = {
        "bootstrap_servers": serverUrl,
        "value_serializer": serializer,
        **streamnative_config
    }

    # Create the producer
    producer = KafkaProducer(**producer_config)

    # Our producer needs a schema to send along with the data.
    # In this example we're using Avro, so we'll load an .avsc file.
    with open('user.avsc', 'r') as schema_file:
        schema = AvroSchema(schema_file.read())

    # Send message data along with schema
    data = {
        'name': 'John Doe',
        'Age': 6
    }
    ret = producer.send(topicName, value=(data, schema))
    print("-" * 20, "send message...", "-" * 20)
    print(ret.get())
    print("-" * 20, "send successfully!", "-" * 20, "\n")


def consume_all_messages():
    # Create the deserializer
    deserializer = KafkaDeserializer(client)

    consumer_config = {
        "bootstrap_servers": [serverUrl],
        "value_deserializer": deserializer,
        "group_id": 'test-group',
        "auto_offset_reset": 'earliest',
        "enable_auto_commit": False,
        **streamnative_config,
    }

    consumer = KafkaConsumer(topicName, **consumer_config)

    print('*' * 30, "start consuming...", '*' * 30)
    for message in consumer:
        print("The value is : {}".format(message.value))
        print("The key is : {}".format(message.key))
        print("The topic is : {}".format(message.topic))
        print("The partition is : {}".format(message.partition))
        print("The offset is : {}".format(message.offset))
        print("The timestamp is : {}".format(message.timestamp))
        tp = TopicPartition(message.topic, message.partition)
        om = OffsetAndMetadata(message.offset + 1, message.timestamp)
        # consumer.commit({tp: om})
        print('*' * 70)


if __name__ == '__main__':
    # send one message then receive it
    send_one_message()
    consume_all_messages()
```

Replace your credentials and run the code, you will see the result:

```text
-------------------- send message... --------------------
RecordMetadata(topic='persistent://public/default/glue-test', partition=0, topic_partition=TopicPartition(topic='persistent://public/default/glue-test', partition=0), offset=2, timestamp=1679410346538, log_start_offset=-1, checksum=None, serialized_key_size=-1, serialized_value_size=28, serialized_header_size=-1)
-------------------- send successfully! -------------------- 

****************************** start consuming... ******************************
The value is : DataAndSchema(data={'name': 'John Doe', 'Age': 6}, schema=<AvroSchema {'namespace': 'aws_schema_registry.integrationtests', 'type': 'record', 'name': 'User', 'fields': [{'name': 'name', 'type': 'string'}, {'name': 'Age', 'type': 'int'}]}>)
The key is : None
The topic is : persistent://public/default/glue-test
The partition is : 0
The offset is : 1
The timestamp is : 1679410284665
**********************************************************************
```

In addition, you can also see the schema information of the topic in AWS Glue Schema Registry:

![](../images/schema-name.jpg)

The schema value is the same as the `user.avsc` file, and the schema name is generated by the naming strategy we defined in the code.