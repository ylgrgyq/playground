#! /bin/bash

BASE_DIR=$(dirname $0)/..
CLASSPATH=$(echo $BASE_DIR/target/*.jar | tr ' ' ':')

LOG_DIR="$BASE_DIR/logs"
LOG4J_CONFIG="$BASE_DIR/src/main/resources/log4j2.xml"

echo $CLASSPATH

java \
    -Dlog4j.logdir=$LOG_DIR \
    -Dlog4j.configurationFile=file:$LOG4J_CONFIG \
    -cp $CLASSPATH \
    com.github.ylgrgyq.replicator.example.client.ReplicatorClient
