// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.proto;

public interface SyncLogEntriesOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.github.ylgrgyq.proto.SyncLogEntries)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional int64 startIndex = 1;</code>
   */
  long getStartIndex();

  /**
   * <code>optional string topic = 2;</code>
   */
  java.lang.String getTopic();
  /**
   * <code>optional string topic = 2;</code>
   */
  com.google.protobuf.ByteString
      getTopicBytes();

  /**
   * <code>optional bytes data = 3;</code>
   */
  com.google.protobuf.ByteString getData();
}
