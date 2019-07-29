#! /bin/bash

BASE_DIR=$(dirname $0)/..
CLASSPATH=$(echo $BASE_DIR/target/*.jar | tr ' ' ':')

echo $CLASSPATH

java \
    -Xmx256m -Xms256m \
    -server \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=2000 \
    -cp $CLASSPATH \
    com.github.ylgrgyq.replicator.benchmark.server.ReplicatorBenchmarkServer
