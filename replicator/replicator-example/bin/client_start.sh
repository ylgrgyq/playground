#! /bin/bash

BASE_DIR=$(dirname $0)/..
CLASSPATH=$(echo $BASE_DIR/target/*.jar | tr ' ' ':')

echo $CLASSPATH

java -cp $CLASSPATH com.github.ylgrgyq.replicator.example.client.ReplicatorClient
