// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.replicator.proto;

public interface SnapshotOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.github.ylgrgyq.replicator.proto.Snapshot)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional bytes data = 1;</code>
   */
  com.google.protobuf.ByteString getData();

  /**
   * <code>optional int64 id = 2;</code>
   */
  long getId();

  /**
   * <code>optional string topic = 3;</code>
   */
  java.lang.String getTopic();
  /**
   * <code>optional string topic = 3;</code>
   */
  com.google.protobuf.ByteString
      getTopicBytes();
}
