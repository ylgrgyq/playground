// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.replicator.proto;

public interface ReplicatorCommandOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.github.ylgrgyq.replicator.proto.ReplicatorCommand)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.CommandType type = 1;</code>
   */
  int getTypeValue();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.CommandType type = 1;</code>
   */
  com.github.ylgrgyq.replicator.proto.CommandType getType();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.MessageType msg_type = 2;</code>
   */
  int getMsgTypeValue();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.MessageType msg_type = 2;</code>
   */
  com.github.ylgrgyq.replicator.proto.MessageType getMsgType();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.HandshakeRequest handshake_request = 3;</code>
   */
  com.github.ylgrgyq.replicator.proto.HandshakeRequest getHandshakeRequest();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.HandshakeRequest handshake_request = 3;</code>
   */
  com.github.ylgrgyq.replicator.proto.HandshakeRequestOrBuilder getHandshakeRequestOrBuilder();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchLogsRequest fetch_logs_request = 4;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchLogsRequest getFetchLogsRequest();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchLogsRequest fetch_logs_request = 4;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchLogsRequestOrBuilder getFetchLogsRequestOrBuilder();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchSnapshotRequest fetch_snapshot_request = 5;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchSnapshotRequest getFetchSnapshotRequest();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchSnapshotRequest fetch_snapshot_request = 5;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchSnapshotRequestOrBuilder getFetchSnapshotRequestOrBuilder();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.HandshakeResponse handshake_response = 6;</code>
   */
  com.github.ylgrgyq.replicator.proto.HandshakeResponse getHandshakeResponse();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.HandshakeResponse handshake_response = 6;</code>
   */
  com.github.ylgrgyq.replicator.proto.HandshakeResponseOrBuilder getHandshakeResponseOrBuilder();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchLogsResponse fetch_logs_response = 7;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchLogsResponse getFetchLogsResponse();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchLogsResponse fetch_logs_response = 7;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchLogsResponseOrBuilder getFetchLogsResponseOrBuilder();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchSnapshotResponse fetch_snapshot_response = 8;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchSnapshotResponse getFetchSnapshotResponse();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.FetchSnapshotResponse fetch_snapshot_response = 8;</code>
   */
  com.github.ylgrgyq.replicator.proto.FetchSnapshotResponseOrBuilder getFetchSnapshotResponseOrBuilder();

  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.ErrorInfo error = 9;</code>
   */
  boolean hasError();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.ErrorInfo error = 9;</code>
   */
  com.github.ylgrgyq.replicator.proto.ErrorInfo getError();
  /**
   * <code>optional .com.github.ylgrgyq.replicator.proto.ErrorInfo error = 9;</code>
   */
  com.github.ylgrgyq.replicator.proto.ErrorInfoOrBuilder getErrorOrBuilder();

  public com.github.ylgrgyq.replicator.proto.ReplicatorCommand.RequestCase getRequestCase();

  public com.github.ylgrgyq.replicator.proto.ReplicatorCommand.ResponseCase getResponseCase();
}
