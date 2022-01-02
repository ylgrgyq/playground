#!/bin/bash

protoc -I=./protos --go_out=./ raft.proto