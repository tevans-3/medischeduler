#/bin/bash 

/opt/bitnami/kafka/bin/kafka-topics.sh --create \
 --bootstrap-server kafka:9092 \
 --replication-factor 3 \
 --partitions 30 \
 --topic $MATCH_TOPIC \
 --bootstrap-server kafka:9092

echo "topic $MATCH_TOPIC was created"

/opt/bitnami/kafka/bin/kafka-topics.sh --create \
 --bootstrap-server kafka:9092 \
 --replication-factor 3 \
 --partitions 30 \
 --topic $ROUTE_MATRIX_TOPIC \
 --bootstrap-server kafka:9092

echo "topic $ROUTE_MATRIX_TOPIC was created"

/opt/bitnami/kafka/bin/kafka-topics.sh --create \
 --bootstrap-server kafka:9092 \
 --replication-factor 3 \
 --partitions 30 \
 --topic $OPTIMAL_ASSIGNMENTS_TOPIC \
 --bootstrap-server kafka:9092

echo "topic $OPTIMAL_ASSIGNMENTS_TOPIC was created"