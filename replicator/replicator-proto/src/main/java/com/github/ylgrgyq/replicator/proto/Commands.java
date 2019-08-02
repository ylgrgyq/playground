// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: commands.proto

package com.github.ylgrgyq.replicator.proto;

public final class Commands {
  private Commands() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_LogEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_LogEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_BatchLogEntries_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_BatchLogEntries_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_Snapshot_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_Snapshot_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_ErrorInfo_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_ErrorInfo_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_HandshakeResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_HandshakeResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_com_github_ylgrgyq_replicator_proto_Error_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_com_github_ylgrgyq_replicator_proto_Error_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\016commands.proto\022#com.github.ylgrgyq.rep" +
      "licator.proto\"$\n\010LogEntry\022\n\n\002id\030\001 \001(\003\022\014\n" +
      "\004data\030\003 \001(\014\"Q\n\017BatchLogEntries\022>\n\007entrie" +
      "s\030\001 \003(\0132-.com.github.ylgrgyq.replicator." +
      "proto.LogEntry\"$\n\010Snapshot\022\n\n\002id\030\001 \001(\003\022\014" +
      "\n\004data\030\002 \001(\014\"2\n\tErrorInfo\022\022\n\nerror_code\030" +
      "\001 \001(\005\022\021\n\terror_msg\030\002 \001(\t\"!\n\020HandshakeReq" +
      "uest\022\r\n\005topic\030\001 \001(\t\"\023\n\021HandshakeResponse" +
      "\"1\n\020FetchLogsRequest\022\016\n\006fromId\030\001 \001(\003\022\r\n\005" +
      "limit\030\002 \001(\005\"W\n\021FetchLogsResponse\022B\n\004logs",
      "\030\001 \001(\01324.com.github.ylgrgyq.replicator.p" +
      "roto.BatchLogEntries\"\026\n\024FetchSnapshotReq" +
      "uest\"X\n\025FetchSnapshotResponse\022?\n\010snapsho" +
      "t\030\001 \001(\0132-.com.github.ylgrgyq.replicator." +
      "proto.Snapshot\"F\n\005Error\022=\n\005error\030\001 \001(\0132." +
      ".com.github.ylgrgyq.replicator.proto.Err" +
      "orInfoB\002P\001b\006proto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_com_github_ylgrgyq_replicator_proto_LogEntry_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_com_github_ylgrgyq_replicator_proto_LogEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_LogEntry_descriptor,
        new java.lang.String[] { "Id", "Data", });
    internal_static_com_github_ylgrgyq_replicator_proto_BatchLogEntries_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_com_github_ylgrgyq_replicator_proto_BatchLogEntries_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_BatchLogEntries_descriptor,
        new java.lang.String[] { "Entries", });
    internal_static_com_github_ylgrgyq_replicator_proto_Snapshot_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_com_github_ylgrgyq_replicator_proto_Snapshot_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_Snapshot_descriptor,
        new java.lang.String[] { "Id", "Data", });
    internal_static_com_github_ylgrgyq_replicator_proto_ErrorInfo_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_com_github_ylgrgyq_replicator_proto_ErrorInfo_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_ErrorInfo_descriptor,
        new java.lang.String[] { "ErrorCode", "ErrorMsg", });
    internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_HandshakeRequest_descriptor,
        new java.lang.String[] { "Topic", });
    internal_static_com_github_ylgrgyq_replicator_proto_HandshakeResponse_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_com_github_ylgrgyq_replicator_proto_HandshakeResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_HandshakeResponse_descriptor,
        new java.lang.String[] { });
    internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_descriptor =
      getDescriptor().getMessageTypes().get(6);
    internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsRequest_descriptor,
        new java.lang.String[] { "FromId", "Limit", });
    internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsResponse_descriptor =
      getDescriptor().getMessageTypes().get(7);
    internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_FetchLogsResponse_descriptor,
        new java.lang.String[] { "Logs", });
    internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotRequest_descriptor =
      getDescriptor().getMessageTypes().get(8);
    internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotRequest_descriptor,
        new java.lang.String[] { });
    internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotResponse_descriptor =
      getDescriptor().getMessageTypes().get(9);
    internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_FetchSnapshotResponse_descriptor,
        new java.lang.String[] { "Snapshot", });
    internal_static_com_github_ylgrgyq_replicator_proto_Error_descriptor =
      getDescriptor().getMessageTypes().get(10);
    internal_static_com_github_ylgrgyq_replicator_proto_Error_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_com_github_ylgrgyq_replicator_proto_Error_descriptor,
        new java.lang.String[] { "Error", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
