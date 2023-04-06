
## Prerequisites

1️⃣ Follow [this tutorial](https://www.notion.so/streamnativeio/StreamNative-Cloud-for-Kafka-DRAFT-6aa74659b5f5495883beaa88e21eabc6) to create a Pulsar cluster and a service account on StreamNative Cloud.

2️⃣ Follow [confluent document](https://docs.docker.com/engine/install/) to download ksql server and ksql cli.

## Start ksql server and ksql cli

1️⃣ Open `etc/ksqldb/ksql-server.properties` and configure the ksql server with the following properties:

```conf
#------ Kafka -------
# The set of Kafka brokers to bootstrap Kafka cluster information from:
bootstrap.servers=<SERVER-URL>
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule \
   required username="public/default" \
   password="token:<YOUR-TOKEN>";
```

The `<SERVER-URL>` can be found in StreamNative Cloud panel:

![](./images/broker-url.jpg)

The `<YOUR-TOKEN>` can be generated and copied in Service Account panel:

![](./images/token.jpg)

2️⃣ Start ksql server:

```shell
bin/ksql-server-start etc/ksqldb/ksql-server.properties
```

Then you should see the following output:

```text
[2023-04-06 14:51:02,811] INFO ksqlDB API server listening on http://0.0.0.0:8088 (io.confluent.ksql.rest.server.KsqlRestApplication:382)

                  ===========================================
                  =       _              _ ____  ____       =
                  =      | | _____  __ _| |  _ \| __ )      =
                  =      | |/ / __|/ _` | | | | |  _ \      =
                  =      |   <\__ \ (_| | | |_| | |_) |     =
                  =      |_|\_\___/\__, |_|____/|____/      =
                  =                   |_|                   =
                  =        The Database purpose-built       =
                  =        for stream processing apps       =
                  ===========================================

Copyright 2017-2022 Confluent Inc.

Server 7.3.2 listening on http://0.0.0.0:8088

To access the KSQL CLI, run:
ksql http://0.0.0.0:8088

[2023-04-06 14:51:02,814] INFO Server up and running (io.confluent.ksql.rest.server.KsqlServerMain:153)
[2023-04-06 14:51:04,117] INFO Successfully submitted metrics to Confluent via secure endpoint (io.confluent.support.metrics.submitters.ConfluentSubmitter:146)
```

3️⃣ Start ksql cli:

```shell
LOG_DIR=./ksql_logs bin/ksql http://localhost:8088
```

Then you should see the following output:

```text
CLI v7.3.2, Server v7.3.2 located at http://localhost:8088
Server Status: RUNNING
```

4️⃣ Create streams and tables with ksql cli.

First, create a stream named `riderLocations`:

```sql
CREATE STREAM riderLocations (profileId VARCHAR, latitude DOUBLE, longitude DOUBLE)
   WITH (kafka_topic='locations', value_format='json', partitions=1);
```

Create two tables to keep track of the latest location of the riders using a materialized view. The first table is named `currentLocation` and the second table is named `ridersNearMountainView`:

```sql
CREATE TABLE currentLocation AS
  SELECT profileId,
         LATEST_BY_OFFSET(latitude) AS la,
         LATEST_BY_OFFSET(longitude) AS lo
  FROM riderlocations
  GROUP BY profileId
  EMIT CHANGES;
```

```sql
CREATE TABLE ridersNearMountainView AS
  SELECT ROUND(GEO_DISTANCE(la, lo, 37.4133, -122.1162), -1) AS distanceInMiles,
         COLLECT_LIST(profileId) AS riders,
         COUNT(*) AS count
  FROM currentLocation
  GROUP BY ROUND(GEO_DISTANCE(la, lo, 37.4133, -122.1162), -1);
```

5️⃣ Insert and query data.

Run a push query over the stream:

```sql
-- Mountain View lat, long: 37.4133, -122.1162
SELECT * FROM riderLocations
  WHERE GEO_DISTANCE(latitude, longitude, 37.4133, -122.1162) <= 5 EMIT CHANGES;
```

Open another ksql cli and insert data into the stream:

```sql
INSERT INTO riderLocations (profileId, latitude, longitude) VALUES ('c2309eec', 37.7877, -122.4205);
INSERT INTO riderLocations (profileId, latitude, longitude) VALUES ('18f4ea86', 37.3903, -122.0643);
INSERT INTO riderLocations (profileId, latitude, longitude) VALUES ('4ab5cbad', 37.3952, -122.0813);
INSERT INTO riderLocations (profileId, latitude, longitude) VALUES ('8b6eae59', 37.3944, -122.0813);
INSERT INTO riderLocations (profileId, latitude, longitude) VALUES ('4a7c7b41', 37.4049, -122.0822);
INSERT INTO riderLocations (profileId, latitude, longitude) VALUES ('4ddad000', 37.7857, -122.4011);
```

Then you should see the following output in the first terminal:

```sql
>  WHERE GEO_DISTANCE(latitude, longitude, 37.4133, -122.1162) <= 5 EMIT CHANGES;
+---------------------------------+---------------------------------+---------------------------------+
|PROFILEID                        |LATITUDE                         |LONGITUDE                        |
+---------------------------------+---------------------------------+---------------------------------+
|4ab5cbad                         |37.3952                          |-122.0813                        |
|8b6eae59                         |37.3944                          |-122.0813                        |
|4a7c7b41                         |37.4049                          |-122.0822                        |
```

## Reference

https://ksqldb.io/quickstart.html#quickstart-content