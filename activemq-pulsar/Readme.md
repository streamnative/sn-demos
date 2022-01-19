Make sure you have a kubernetes cluster up and running\
You can easily start a kubernetes cluster locally by installing minikube
https://minikube.sigs.k8s.io/docs/start/

1. Start Minikube by running
```shell
minikube start --cpus 3 --memory "3900MB" --kubernetes-version=v1.19.3
```
2. Create a namespace
```shell
kubectl create ns demo
```

3. Deploy ActiveMQ
```
kubectl create -f k8s/amqp_deployment.yaml
kubectl create -f k8s/amqp_service.yaml
```

4. Deploy your Pulsar Cluster
```shell
helm install \
--values k8s/values.yaml \
--set initialize=true \
pulsar apache/pulsar
```

5. Expose ActiveMQ ports, so that our producers and consumers are able to connect
```shell
kubectl port-forward service/active-mq 61616:61616 8161:8161 -n demo
```
6. Deploy the ActiveMQ - Pulsar Connector
```shell
./setup.sh
```

7. Expose Broker ports, so that our producers and consumers are able to connect
```shell
kubectl port-forward service/pulsar-proxy 8080:080 6650:6650 -n demo
```