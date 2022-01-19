kubectl exec -it pulsar-toolset-0 -n demo -- mkdir -p connectors

echo "Uploading resources on the cluster ..."
kubectl cp jars/pulsar-io-activemq-2.7.4.nar pulsar-toolset-0:/pulsar/connectors/ -n demo
kubectl cp config.yaml pulsar-toolset-0:/pulsar/ -n demo

echo "Setting up the connector ..."
kubectl exec -it pulsar-toolset-0 -n demo -- bin/pulsar-admin source create \
                                                     --source-config-file config.yaml \
                                                     --name amqp-source --archive connectors/pulsar-io-activemq-2.7.4.nar

echo "Checking the connector setup ..."
kubectl exec -it pulsar-toolset-0 -n demo -- bin/pulsar-admin sources list
kubectl exec -it pulsar-toolset-0 -n demo -- bin/pulsar-admin sources get --name amqp-source --tenant public --namespace default
kubectl exec -it pulsar-toolset-0 -n demo -- bin/pulsar-admin sources status --name amqp-source --tenant public --namespace default