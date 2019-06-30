#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $BASE_DIR

protoc3 --proto_path=./ --java_out=../java/ ./commands.proto