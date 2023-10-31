# kafka-prometheus-exporter-java
Simple Java App to export Prometheus remote-write messages from Kafka to Prometheus or VictoriaMetrics

Based on: https://developer.confluent.io/get-started/java/

To be used with: https://github.com/chuahjw/prometheus-kafka-adapter

Edit `kafka-prometheus-exporter.properties` file to connect to Kafka Cluster.

Build and run:
```
gradle shadowjar
java -jar build/libs/kafka-prometheus-exporter-java-0.0.1.jar kafka-prometheus-exporter.properties <TOPIC_NAME> http://<TARGET>:<PORT>/api/v1/write
```

Example run:
```
java -jar ./kafka-prometheus-exporter-java-0.0.1.jar kafka-prometheus-exporter.properties metrics http://victoriametrics.dev.confluent:8428/api/v1/write
```